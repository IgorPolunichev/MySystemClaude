import { useEffect, useState } from 'react';
import { DeviceSettings } from './DeviceSettings';
import { App, Badge, Button, Drawer, Empty, List, Space, Tag, Tooltip, Typography } from 'antd';
import { CloudOutlined, DesktopOutlined, DisconnectOutlined, SyncOutlined } from '@ant-design/icons';
import { useQueryClient } from '@tanstack/react-query';
import { db, type OutboxOp } from './db';
import { useNetwork } from './network';
import { dropOperation, refreshQueueState, resolveConflict, startSync, syncNow } from './sync';

const time = new Intl.DateTimeFormat('ru-RU', { dateStyle: 'short', timeStyle: 'short' });

/** Журнал синхронизации: что ждёт отправки, конфликты и отклонённые изменения (раздел 17.7). */
function SyncJournal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [ops, setOps] = useState<OutboxOp[]>([]);
  const [settings, setSettings] = useState(false);
  const state = useNetwork();
  useEffect(() => {
    if (!open) return;
    let alive = true;
    const load = () =>
      db.outbox
        .orderBy('seq')
        .toArray()
        .then((list) => alive && setOps(list));
    void load();
    const timer = setInterval(() => void load(), 2000);
    return () => {
      alive = false;
      clearInterval(timer);
    };
  }, [open, state.pending, state.problems]);
  return (
    <Drawer open={open} onClose={onClose} title="Синхронизация" width="min(560px, 100vw)">
      <Space direction="vertical" style={{ width: '100%' }}>
        <Space wrap>
          {state.online ? <Tag color="green">онлайн</Tag> : <Tag color="orange">офлайн</Tag>}
          {state.lastSyncAt && (
            <span className="cell-muted">последняя синхронизация: {time.format(state.lastSyncAt)}</span>
          )}
        </Space>
        <Button
          type="primary"
          icon={<SyncOutlined />}
          loading={state.syncing}
          onClick={() => void syncNow(true)}
        >
          {state.online
            ? 'Синхронизировать сейчас (и обновить справочники)'
            : 'Проверить связь и синхронизировать'}
        </Button>
        <Button icon={<DesktopOutlined />} onClick={() => setSettings(true)}>
          Настройки этого ПК
        </Button>
        {ops.length === 0 ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Все изменения отправлены" />
        ) : (
          <List
            dataSource={ops}
            renderItem={(op) => (
              <List.Item
                actions={
                  op.status === 'conflict'
                    ? [
                        <Button
                          key="m"
                          size="small"
                          type="primary"
                          onClick={() => void resolveConflict(op.seq!, true)}
                        >
                          Оставить моё
                        </Button>,
                        <Button key="s" size="small" onClick={() => void resolveConflict(op.seq!, false)}>
                          Взять с сервера
                        </Button>,
                      ]
                    : op.status === 'rejected'
                      ? [
                          <Button key="d" size="small" danger onClick={() => void dropOperation(op.seq!)}>
                            Убрать
                          </Button>,
                        ]
                      : []
                }
              >
                <List.Item.Meta
                  title={
                    <Space wrap>
                      <span>{op.label}</span>
                      {op.status === 'pending' && <Tag>ждёт отправки</Tag>}
                      {op.status === 'conflict' && <Tag color="orange">конфликт</Tag>}
                      {op.status === 'rejected' && <Tag color="red">отклонено сервером</Tag>}
                      {op.user && <Tag>{op.user}</Tag>}
                    </Space>
                  }
                  description={
                    <span className="cell-muted">
                      {time.format(op.createdAt)}
                      {op.error && ` · ${op.error}`}
                    </span>
                  }
                />
              </List.Item>
            )}
          />
        )}
        <Typography.Paragraph type="secondary" className="hint">
          Конфликт — акт изменили на сервере, пока вы работали без связи. «Оставить моё» отправит ваши правки
          поверх, «Взять с сервера» отменит ваши неотправленные правки этого акта. Отклонённые изменения
          (например, акт закрыли) сохраняются здесь, пока вы их не уберёте.
        </Typography.Paragraph>
      </Space>
      <DeviceSettings open={settings} onClose={() => setSettings(false)} />
    </Drawer>
  );
}

/** Индикатор в шапке: онлайн/офлайн, число неотправленных изменений, синхронизация. */
export function SyncIndicator() {
  const state = useNetwork();
  const { notification } = App.useApp();
  const client = useQueryClient();
  const [open, setOpen] = useState(false);

  useEffect(() => {
    startSync();
    void refreshQueueState();
    const onNumbered = (e: Event) =>
      notification.success({
        message: 'Акт получил номер',
        description: (e as CustomEvent<{ number: string }>).detail.number,
      });
    const onStorage = (e: Event) => {
      const d = (e as CustomEvent<{ usedMb: number; quotaMb: number }>).detail;
      notification.warning({
        message: 'На ПК заканчивается место',
        description: `Занято ${d.usedMb} МБ из ${d.quotaMb} МБ. Синхронизируйтесь и очистите старые данные.`,
      });
    };
    window.addEventListener('acts:numbered', onNumbered);
    window.addEventListener('acts:storage-low', onStorage);
    return () => {
      window.removeEventListener('acts:numbered', onNumbered);
      window.removeEventListener('acts:storage-low', onStorage);
    };
  }, [notification]);

  useEffect(() => {
    if (!state.syncing) void client.invalidateQueries();
  }, [state.syncing, client]);

  const label = state.online ? 'Онлайн' : 'Офлайн';
  return (
    <>
      <Tooltip
        title={state.pending ? `Не отправлено изменений: ${state.pending}` : 'Все изменения отправлены'}
      >
        <Badge count={state.pending + state.problems} size="small" color={state.problems ? 'red' : 'orange'}>
          <Button
            size="small"
            className={`sync-indicator ${state.online ? 'online' : 'offline'}`}
            icon={
              state.syncing ? (
                <SyncOutlined spin />
              ) : state.online ? (
                <CloudOutlined />
              ) : (
                <DisconnectOutlined />
              )
            }
            onClick={() => setOpen(true)}
          >
            <span className="sync-label">{label}</span>
          </Button>
        </Badge>
      </Tooltip>
      <SyncJournal open={open} onClose={() => setOpen(false)} />
    </>
  );
}
