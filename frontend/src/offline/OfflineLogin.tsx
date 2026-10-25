import { useEffect, useState } from 'react';
import { Alert, Button, Card, Input, List, Result, Space, Typography } from 'antd';
import { DisconnectOutlined, ReloadOutlined, UserOutlined } from '@ant-design/icons';
import { offlineCandidates, offlineLogin, OFFLINE_DAYS, type OfflineProfile } from './offlineAuth';

type Candidate = OfflineProfile & { expired: boolean; locked: boolean };

/** Экран входа без связи с сервером компании: выбрать себя и ввести ПИН (решение 19.12 SPEC.md). */
export function OfflineLogin({ onRetry }: { onRetry: () => void }) {
  const [candidates, setCandidates] = useState<Candidate[] | null>(null);
  const [selected, setSelected] = useState<string>();
  const [pin, setPinValue] = useState('');
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    void offlineCandidates().then((list) => {
      setCandidates(list);
      if (list.length === 1) setSelected(list[0].username);
    });
  }, []);

  const login = async () => {
    if (!selected) return;
    setBusy(true);
    setError(undefined);
    try {
      await offlineLogin(selected, pin);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
      setPinValue('');
      setCandidates(await offlineCandidates());
    } finally {
      setBusy(false);
    }
  };

  if (candidates && candidates.length === 0) {
    return (
      <Result
        icon={<DisconnectOutlined />}
        title="Нет связи с сервером компании"
        subTitle="Первый вход на этом ПК возможен только при связи с сервером. После входа задайте ПИН-код — с ним можно будет работать без связи."
        extra={
          <Button type="primary" icon={<ReloadOutlined />} onClick={onRetry}>
            Проверить связь
          </Button>
        }
      />
    );
  }

  const current = candidates?.find((c) => c.username === selected);
  return (
    <div className="fullscreen-center">
      <Card style={{ width: 'min(420px, 94vw)' }}>
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Typography.Title level={4} style={{ margin: 0 }}>
            <DisconnectOutlined /> Вход без связи
          </Typography.Title>
          <Typography.Text type="secondary">
            Сервер компании недоступен. Выберите себя и введите ПИН-код — будут доступны данные на этом ПК.
            Изменения уйдут на сервер, когда вы войдёте со связью.
          </Typography.Text>
          <List
            size="small"
            bordered
            dataSource={candidates ?? []}
            renderItem={(c) => (
              <List.Item
                className={`offline-user${c.username === selected ? ' selected' : ''}`}
                onClick={() => !c.expired && !c.locked && (setSelected(c.username), setError(undefined))}
                style={{
                  cursor: c.expired || c.locked ? 'not-allowed' : 'pointer',
                  opacity: c.expired || c.locked ? 0.55 : 1,
                }}
              >
                <Space>
                  <UserOutlined />
                  <span>{c.fullName}</span>
                  {c.expired && (
                    <Typography.Text type="secondary">
                      — нужен вход со связью (прошло {OFFLINE_DAYS} дней)
                    </Typography.Text>
                  )}
                  {c.locked && !c.expired && (
                    <Typography.Text type="danger">— ПИН заблокирован</Typography.Text>
                  )}
                </Space>
              </List.Item>
            )}
          />
          <Input.Password
            value={pin}
            onChange={(e) => setPinValue(e.target.value.replace(/\D/g, '').slice(0, 8))}
            onPressEnter={() => void login()}
            placeholder="ПИН-код"
            inputMode="numeric"
            autoFocus
            disabled={!current}
          />
          {error && <Alert type="error" showIcon message={error} />}
          <Button
            type="primary"
            block
            loading={busy}
            disabled={!current || pin.length < 4}
            onClick={() => void login()}
          >
            Войти без связи
          </Button>
          <Button block icon={<ReloadOutlined />} onClick={onRetry}>
            Проверить связь и войти обычным способом
          </Button>
        </Space>
      </Card>
    </div>
  );
}
