import { useState } from 'react';
import { App, Button, Space, Table, Tag, Tooltip, Typography } from 'antd';
import { DownloadOutlined } from '@ant-design/icons';
import { useListMessages } from '../../api/generated/mail/mail';
import type { Message } from '../../api/generated/mail/model';
import { MESSAGE_STATUS } from './messageStatus';
import { downloadFile } from '../../api/http';
import { ok } from '../../api/unwrap';
import { errorText } from '../../shared/lib/apiErrors';

const dateTime = new Intl.DateTimeFormat('ru-RU', { dateStyle: 'short', timeStyle: 'short' });

/** Журнал отправок: кому, когда, какие акты, статус; .eml можно скачать снова. */
export function JournalPage({ actId }: { actId?: string }) {
  const { message } = App.useApp();
  const [page, setPage] = useState({ page: 0, size: 20 });
  const { data, isFetching } = useListMessages(
    { actId, page: page.page, size: page.size },
    { query: { refetchInterval: 15_000 } },
  );
  const result = ok(data);
  return (
    <div className="page-panel">
      <Table<Message>
        rowKey="id"
        loading={isFetching && !result}
        dataSource={result?.items}
        size="middle"
        scroll={{ x: 900 }}
        pagination={{
          current: page.page + 1,
          pageSize: page.size,
          total: result?.totalElements,
          onChange: (p, s) => setPage({ page: p - 1, size: s }),
          showTotal: (t) => `Писем: ${t}`,
        }}
        locale={{ emptyText: 'Писем пока нет' }}
        columns={[
          {
            title: 'Когда',
            key: 'when',
            width: 150,
            render: (_, m) =>
              (m.sentAt ?? m.createdAt) ? dateTime.format(new Date((m.sentAt ?? m.createdAt)!)) : '—',
          },
          {
            title: 'Статус',
            key: 'status',
            width: 170,
            render: (_, m) => (
              <Space direction="vertical" size={0}>
                <Tag color={MESSAGE_STATUS[m.status].color}>{MESSAGE_STATUS[m.status].label}</Tag>
                {m.status !== 'SENT' && m.attempts > 0 && (
                  <span className="cell-muted">попыток: {m.attempts}</span>
                )}
              </Space>
            ),
          },
          {
            title: 'Тема',
            key: 'subject',
            render: (_, m) => (
              <Space direction="vertical" size={0}>
                <span>{m.subject}</span>
                {m.error && (
                  <Typography.Text type="danger" style={{ fontSize: 12 }}>
                    {m.error}
                  </Typography.Text>
                )}
              </Space>
            ),
          },
          { title: 'Кому', key: 'to', width: 220, render: (_, m) => [...m.to, ...m.cc].join(', ') || '—' },
          {
            title: 'Акты',
            key: 'acts',
            width: 200,
            render: (_, m) => <span className="cell-codes">{m.actNumbers.join(', ')}</span>,
          },
          { title: 'Отправил', dataIndex: 'createdBy', width: 120 },
          {
            title: '',
            key: 'eml',
            width: 50,
            render: (_, m) => (
              <Tooltip title="Скачать .eml">
                <Button
                  type="text"
                  size="small"
                  icon={<DownloadOutlined />}
                  onClick={() =>
                    void downloadFile(`/api/v1/mail/messages/${m.id}/eml`, 'Письмо с актами.eml').catch((e) =>
                      message.error(errorText(e)),
                    )
                  }
                />
              </Tooltip>
            ),
          },
        ]}
      />
    </div>
  );
}
