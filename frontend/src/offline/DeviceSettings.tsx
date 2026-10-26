import { useEffect, useState } from 'react';
import { App, Button, Form, Modal, Select, Typography } from 'antd';
import type { Organization, Site } from '../api/generated/directory/model';
import { db, getDeviceScope, getRef, setMeta, type DeviceScope } from './db';
import { checkStorage, syncNow } from './sync';
import { PinSetup } from './PinSetup';
import { currentUsername } from './user';
import { getAccessToken } from '../app/auth/tokenStore';

/**
 * «Настройки этого ПК» (решение 19.11 SPEC.md): заказчики и площадки, акты которых хранятся на этом ПК для
 * работы без связи. Без выбора — только акты пользователя.
 */
export function DeviceSettings({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { message, modal } = App.useApp();
  const [scope, setScope] = useState<DeviceScope>({ organizationIds: [], siteIds: [] });
  const [organizations, setOrganizations] = useState<Organization[]>([]);
  const [sites, setSites] = useState<Record<string, Site[]>>({});
  const [storage, setStorage] = useState<{ usedMb: number; quotaMb: number; low: boolean }>();
  const [counts, setCounts] = useState({ acts: 0, equipment: 0 });
  const [pin, setPin] = useState(false);

  const [loading, setLoading] = useState(false);
  const reload = async () => {
    setOrganizations((await getRef<Organization[]>('organizations')) ?? []);
    setSites((await getRef<Record<string, Site[]>>('sites')) ?? {});
    setCounts({
      acts: await db.acts.count(),
      equipment: ((await getRef<unknown[]>('equipment')) ?? []).length,
    });
  };
  const loadNow = async () => {
    setLoading(true);
    try {
      await syncNow(true);
      await reload();
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!open) return;
    void (async () => {
      setScope(await getDeviceScope());
      setOrganizations((await getRef<Organization[]>('organizations')) ?? []);
      setSites((await getRef<Record<string, Site[]>>('sites')) ?? {});
      setStorage(await checkStorage());
      setCounts({
        acts: await db.acts.count(),
        equipment: ((await getRef<unknown[]>('equipment')) ?? []).length,
      });
    })();
  }, [open]);

  const siteOptions = scope.organizationIds.flatMap((orgId) => {
    const org = organizations.find((o) => o.id === orgId);
    return (sites[orgId] ?? [])
      .filter((s) => s.active)
      .map((s) => ({ value: s.id, label: `${org?.shortName ?? org?.name ?? ''} — ${s.name}` }));
  });

  const save = async () => {
    await setMeta('deviceScope', scope);
    message.success('Настройки ПК сохранены — загружаю акты');
    onClose();
    await syncNow();
  };

  const wipe = () =>
    modal.confirm({
      title: 'Очистить данные на этом ПК?',
      content: 'Будут удалены справочники, акты и неотправленные изменения всех пользователей этого ПК.',
      okText: 'Очистить',
      okButtonProps: { danger: true },
      cancelText: 'Отмена',
      onOk: async () => {
        const { clearLocalData } = await import('./db');
        await clearLocalData();
        (await import('./refs')).invalidateAllRefs();
        message.success('Данные на ПК очищены');
        onClose();
      },
    });

  return (
    <Modal
      open={open}
      title="Настройки этого ПК"
      okText="Сохранить"
      cancelText="Отмена"
      onOk={() => void save()}
      onCancel={onClose}
    >
      <Typography.Paragraph type="secondary">
        Справочники (техника, заказчики и площадки, инженеры, каталог запчастей) хранятся на ПК всегда.
        Выберите заказчиков и площадки, с которыми работают на этом ПК: их открытые акты и акты за последние
        90 дней будут доступны без связи всем пользователям ПК.
      </Typography.Paragraph>
      <Form layout="vertical">
        {organizations.length === 0 && (
          <Button
            type="primary"
            loading={loading}
            style={{ marginBottom: 12 }}
            onClick={() => void loadNow()}
          >
            Загрузить справочники сейчас
          </Button>
        )}
        <Form.Item label="Заказчики">
          <Select
            mode="multiple"
            value={scope.organizationIds}
            onChange={(organizationIds) =>
              setScope((s) => ({
                organizationIds,
                siteIds: s.siteIds.filter((id) =>
                  organizationIds.some((o) => (sites[o] ?? []).some((x) => x.id === id)),
                ),
              }))
            }
            optionFilterProp="label"
            options={organizations.map((o) => ({ value: o.id, label: o.shortName ?? o.name }))}
            placeholder="Все акты выбранных заказчиков"
          />
        </Form.Item>
        <Form.Item
          label="Площадки"
          extra="Если выбраны площадки — на ПК хранятся акты только этих площадок, а не всего заказчика"
        >
          <Select
            mode="multiple"
            value={scope.siteIds}
            onChange={(siteIds) => setScope((s) => ({ ...s, siteIds }))}
            optionFilterProp="label"
            options={siteOptions}
            disabled={siteOptions.length === 0}
            placeholder="Все площадки выбранных заказчиков"
          />
        </Form.Item>
      </Form>
      <Typography.Paragraph type="secondary" style={{ marginBottom: 4 }}>
        На ПК сейчас: актов — {counts.acts}, единиц техники — {counts.equipment}
        {storage && `; занято ${storage.usedMb} МБ из ${storage.quotaMb} МБ`}.
      </Typography.Paragraph>
      {storage?.low && (
        <Typography.Text type="danger">Место для данных приложения заканчивается.</Typography.Text>
      )}
      <div>
        {getAccessToken() && currentUsername() && (
          <Button type="link" style={{ padding: 0, marginRight: 16 }} onClick={() => setPin(true)}>
            Задать или сменить мой ПИН-код
          </Button>
        )}
        <Button danger type="link" style={{ padding: 0 }} onClick={wipe}>
          Очистить данные на этом ПК
        </Button>
      </div>
      {pin && currentUsername() && (
        <PinSetup username={currentUsername()!} force onClose={() => setPin(false)} />
      )}
    </Modal>
  );
}
