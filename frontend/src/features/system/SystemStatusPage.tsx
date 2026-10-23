import { useQueries } from '@tanstack/react-query';
import { Badge, Button, Table, Tag, Typography } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { getGetSystemInfoQueryOptions } from '../../api/generated/system/system';
import { ServiceName, type SystemInfo } from '../../api/generated/system/model';

const SERVICES = Object.values(ServiceName);

interface Row {
  key: ServiceName;
  state: 'checking' | 'ok' | 'fail';
  info?: SystemInfo;
  error?: string;
}

/** Проверка связки «браузер → шлюз → сервис» с токеном Keycloak. */
export function SystemStatusPage() {
  const { t } = useTranslation();
  const results = useQueries({
    queries: SERVICES.map((service) =>
      getGetSystemInfoQueryOptions(service, { query: { retry: false, staleTime: 0 } }),
    ),
  });

  const rows: Row[] = SERVICES.map((service, i) => {
    const r = results[i];
    if (r.isPending || r.isFetching) return { key: service, state: 'checking' };
    if (r.isError)
      return { key: service, state: 'fail', error: r.error instanceof Error ? r.error.message : '' };
    const info = r.data?.status === 200 ? r.data.data : undefined;
    return { key: service, state: info ? 'ok' : 'fail', info };
  });

  return (
    <section className="page">
      <div className="page-head">
        <Typography.Title level={2} className="page-title">
          {t('system.title')}
        </Typography.Title>
        <Button icon={<ReloadOutlined />} onClick={() => results.forEach((r) => void r.refetch())}>
          {t('system.refresh')}
        </Button>
      </div>
      <Typography.Paragraph type="secondary" className="page-intro">
        {t('system.intro')}
      </Typography.Paragraph>
      <div className="page-panel">
        <Table<Row>
          rowKey="key"
          pagination={false}
          dataSource={rows}
          scroll={{ x: 640 }}
          columns={[
            {
              title: t('system.service'),
              dataIndex: 'key',
              render: (key: ServiceName) => t(`system.names.${key}`),
            },
            {
              title: t('system.status'),
              dataIndex: 'state',
              render: (state: Row['state'], row) =>
                state === 'checking' ? (
                  <Badge status="processing" text={t('system.checking')} />
                ) : state === 'ok' ? (
                  <Badge status="success" text={t('system.ok')} />
                ) : (
                  <Badge
                    status="error"
                    text={row.error ? `${t('system.fail')}: ${row.error}` : t('system.fail')}
                  />
                ),
            },
            { title: t('system.version'), render: (_, row) => row.info?.version ?? '—' },
            { title: t('system.user'), render: (_, row) => row.info?.user ?? '—' },
            {
              title: t('system.roles'),
              render: (_, row) =>
                row.info?.roles.map((role) => <Tag key={role}>{t(`roles.${role}`, role)}</Tag>) ?? '—',
            },
          ]}
        />
      </div>
    </section>
  );
}
