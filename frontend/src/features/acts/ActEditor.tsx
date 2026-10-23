import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  Alert,
  App,
  Button,
  Drawer,
  Dropdown,
  Input,
  Modal,
  Result,
  Space,
  Spin,
  Tag,
  Timeline,
  Tooltip,
  Typography,
} from 'antd';
import {
  ArrowLeftOutlined,
  CheckCircleOutlined,
  CopyOutlined,
  DeleteOutlined,
  HistoryOutlined,
  MoreOutlined,
  RollbackOutlined,
  StopOutlined,
  UnlockOutlined,
} from '@ant-design/icons';
import { useQueryClient } from '@tanstack/react-query';
import {
  getGetActQueryKey,
  useCancelAct,
  useCloseAct,
  useCopyAct,
  useDeleteAct,
  useGetAct,
  useGetActHistory,
  useOpenAct,
  useReopenAct,
} from '../../api/generated/acts/acts';
import type { ActDetail } from '../../api/generated/acts/model';
import { ApiError } from '../../api/http';
import { ok } from '../../api/unwrap';
import { errorText } from '../../shared/lib/apiErrors';
import { invalidateByPrefix } from '../../shared/lib/invalidate';
import { HISTORY_ACTIONS, STATUS, formatDate, formatHours, formatPeriod } from './actMeta';
import { HeaderForm } from './HeaderForm';
import { PartsSection } from './PartsSection';
import { Timesheet } from './Timesheet';

const dateTime = new Intl.DateTimeFormat('ru-RU', { dateStyle: 'short', timeStyle: 'short' });

function HistoryDrawer({ actId, open, onClose }: { actId: string; open: boolean; onClose: () => void }) {
  const { data, isLoading } = useGetActHistory(actId, { query: { enabled: open } });
  const items = ok(data) ?? [];
  return (
    <Drawer open={open} onClose={onClose} title="Журнал изменений" width="min(460px, 100vw)">
      {isLoading ? (
        <Spin />
      ) : (
        <Timeline
          items={[...items].reverse().map((h) => ({
            children: (
              <Space direction="vertical" size={0}>
                <span className="cell-strong">{HISTORY_ACTIONS[h.action] ?? h.action}</span>
                {h.details && <span>{h.details}</span>}
                <span className="cell-muted">
                  {h.at ? dateTime.format(new Date(h.at)) : ''} · {h.user}
                </span>
              </Space>
            ),
          }))}
        />
      )}
    </Drawer>
  );
}

/** Диалог с обязательным комментарием (аннулирование, возврат в работу). */
function CommentDialog({
  title,
  text,
  okText,
  open,
  loading,
  onOk,
  onClose,
}: {
  title: string;
  text: string;
  okText: string;
  open: boolean;
  loading: boolean;
  onOk: (comment: string) => void;
  onClose: () => void;
}) {
  const [comment, setComment] = useState('');
  return (
    <Modal
      open={open}
      title={title}
      okText={okText}
      cancelText="Отмена"
      okButtonProps={{ disabled: comment.trim() === '', danger: okText === 'Аннулировать' }}
      confirmLoading={loading}
      onOk={() => onOk(comment.trim())}
      onCancel={onClose}
      afterClose={() => setComment('')}
    >
      <Typography.Paragraph>{text}</Typography.Paragraph>
      <Input.TextArea
        value={comment}
        onChange={(e) => setComment(e.target.value)}
        maxLength={1000}
        autoSize={{ minRows: 3 }}
        autoFocus
      />
    </Modal>
  );
}

function ActActions({ act }: { act: ActDetail }) {
  const { message, modal } = App.useApp();
  const navigate = useNavigate();
  const client = useQueryClient();
  const open = useOpenAct();
  const close = useCloseAct();
  const reopen = useReopenAct();
  const cancel = useCancelAct();
  const copy = useCopyAct();
  const remove = useDeleteAct();
  const [dialog, setDialog] = useState<'cancel' | 'reopen' | null>(null);
  const [history, setHistory] = useState(false);
  const p = act.permissions;

  const refresh = async () => {
    await client.invalidateQueries({ queryKey: getGetActQueryKey(act.id) });
    await invalidateByPrefix(client, '/api/v1/acts?');
  };
  const run = async (action: () => Promise<unknown>, done: string) => {
    try {
      await action();
      await refresh();
      message.success(done);
      setDialog(null);
    } catch (e) {
      message.error(errorText(e));
    }
  };

  const menu = [
    { key: 'copy', icon: <CopyOutlined />, label: 'Создать на основе' },
    ...(p.canCancel ? [{ key: 'cancel', icon: <StopOutlined />, label: 'Аннулировать', danger: true }] : []),
    ...(p.canDelete
      ? [{ key: 'delete', icon: <DeleteOutlined />, label: 'Удалить черновик', danger: true }]
      : []),
  ];

  const onMenu = (key: string) => {
    if (key === 'copy') {
      void (async () => {
        try {
          const created = ok(await copy.mutateAsync({ id: act.id }))!;
          await invalidateByPrefix(client, '/api/v1/acts?');
          message.success('Создан новый черновик на основе акта');
          navigate(`/acts/${created.id}`);
        } catch (e) {
          message.error(errorText(e));
        }
      })();
    } else if (key === 'cancel') {
      setDialog('cancel');
    } else if (key === 'delete') {
      modal.confirm({
        title: 'Удалить черновик?',
        content: 'Черновик будет удалён вместе с табелем и запчастями. Это действие нельзя отменить.',
        okText: 'Удалить',
        okButtonProps: { danger: true },
        cancelText: 'Отмена',
        onOk: async () => {
          try {
            await remove.mutateAsync({ id: act.id });
            await invalidateByPrefix(client, '/api/v1/acts?');
            navigate('/acts');
          } catch (e) {
            message.error(errorText(e));
          }
        },
      });
    }
  };

  const closeBlocked = act.missingForClose.length > 0;

  return (
    <Space wrap>
      {p.canOpen && (
        <Tooltip title={act.equipment ? undefined : 'Сначала выберите технику и сохраните'}>
          <Button
            type="primary"
            icon={<UnlockOutlined />}
            disabled={!act.equipment}
            loading={open.isPending}
            onClick={() => void run(() => open.mutateAsync({ id: act.id }), 'Акт открыт и получил номер')}
          >
            Открыть акт
          </Button>
        </Tooltip>
      )}
      {p.canClose && (
        <Tooltip title={closeBlocked ? `Не заполнено: ${act.missingForClose.join(', ')}` : undefined}>
          <Button
            type="primary"
            icon={<CheckCircleOutlined />}
            disabled={closeBlocked}
            loading={close.isPending}
            onClick={() =>
              modal.confirm({
                title: `Закрыть акт ${act.number}?`,
                content: 'После закрытия изменить акт сможет только администратор.',
                okText: 'Закрыть акт',
                cancelText: 'Отмена',
                onOk: () => run(() => close.mutateAsync({ id: act.id }), 'Акт закрыт'),
              })
            }
          >
            Закрыть акт
          </Button>
        </Tooltip>
      )}
      {p.canReopen && (
        <Button icon={<RollbackOutlined />} onClick={() => setDialog('reopen')}>
          Вернуть в работу
        </Button>
      )}
      <Button icon={<HistoryOutlined />} onClick={() => setHistory(true)}>
        Журнал
      </Button>
      <Dropdown menu={{ items: menu, onClick: ({ key }) => onMenu(key) }} trigger={['click']}>
        <Button icon={<MoreOutlined />} aria-label="Ещё действия" />
      </Dropdown>
      <CommentDialog
        open={dialog === 'cancel'}
        title={`Аннулировать акт ${act.number ?? ''}?`}
        text="Номер акта сохранится, но акт перестанет учитываться в часах. Укажите причину."
        okText="Аннулировать"
        loading={cancel.isPending}
        onOk={(comment) =>
          void run(() => cancel.mutateAsync({ id: act.id, data: { comment } }), 'Акт аннулирован')
        }
        onClose={() => setDialog(null)}
      />
      <CommentDialog
        open={dialog === 'reopen'}
        title="Вернуть акт в работу"
        text="Акт снова станет открытым, и его смогут изменять инженеры. Укажите причину — она попадёт в журнал."
        okText="Вернуть"
        loading={reopen.isPending}
        onOk={(comment) =>
          void run(() => reopen.mutateAsync({ id: act.id, data: { comment } }), 'Акт возвращён в работу')
        }
        onClose={() => setDialog(null)}
      />
      <HistoryDrawer actId={act.id} open={history} onClose={() => setHistory(false)} />
    </Space>
  );
}

/** Карточка и редактор акта. */
export function ActEditor() {
  const { id = '' } = useParams();
  const { data, isLoading, error } = useGetAct(id);
  const act = ok(data);

  if (isLoading) return <Spin size="large" style={{ display: 'block', margin: '80px auto' }} />;
  if (error || !act) {
    const notFound = error instanceof ApiError && error.status === 404;
    return (
      <Result
        status={notFound ? '404' : 'error'}
        title={notFound ? 'Акт не найден' : 'Не удалось загрузить акт'}
        subTitle={notFound ? undefined : errorText(error)}
        extra={
          <Link to="/acts">
            <Button type="primary">К списку актов</Button>
          </Link>
        }
      />
    );
  }

  const status = STATUS[act.status];
  const eq = act.equipment;

  return (
    <section className="page act-page">
      <Link to="/acts" className="back-link">
        <ArrowLeftOutlined /> Акты
      </Link>
      <div className="act-head">
        <div>
          <Space size={12} align="center" wrap>
            <Typography.Title level={2} className="page-title" style={{ margin: 0 }}>
              <span className="act-number">{act.number ?? 'Черновик'}</span>
            </Typography.Title>
            <Tag color={status.color} className="status-tag">
              {status.label}
            </Tag>
          </Space>
          <div className="act-subtitle">
            {eq ? `${eq.brand} ${eq.model}` : 'Техника не выбрана'}
            {eq?.garageNumber && ` · гар. № ${eq.garageNumber}`}
            {eq?.plateNumber && ` · ${eq.plateNumber}`}
            {act.organization && ` · ${act.organization.shortName ?? act.organization.name}`}
          </div>
        </div>
        <ActActions act={act} />
      </div>

      <div className="act-facts">
        <div>
          <span className="cell-muted">Период работ</span>
          <b>{formatPeriod(act.workStartDate, act.workEndDate)}</b>
        </div>
        <div>
          <span className="cell-muted">Дней</span>
          <b>{act.calendarDays ? `${act.calendarDays} (работы: ${act.workedDays})` : '—'}</b>
        </div>
        <div>
          <span className="cell-muted">Часы</span>
          <b>{formatHours(act.totalHours)}</b>
        </div>
        <div>
          <span className="cell-muted">Инженеров</span>
          <b>{act.engineers.length}</b>
        </div>
        <div>
          <span className="cell-muted">Позиций запчастей</span>
          <b>{act.parts.length}</b>
        </div>
      </div>

      {act.status === 'CLOSED' && (
        <Alert
          type={act.permissions.canEdit ? 'warning' : 'info'}
          showIcon
          message={`Акт закрыт ${formatDate(act.closedAt)}${act.closedBy ? `, ${act.closedBy}` : ''}`}
          description={
            act.permissions.canEdit
              ? 'Вы администратор: изменения возможны, при сохранении потребуется комментарий.'
              : 'Изменить закрытый акт может только администратор.'
          }
        />
      )}
      {act.status === 'CANCELLED' && (
        <Alert type="error" showIcon message="Акт аннулирован" description="Подробности — в журнале." />
      )}
      {act.status === 'OPEN' && act.missingForClose.length > 0 && act.permissions.canClose && (
        <Alert
          type="info"
          showIcon
          message={`Чтобы закрыть акт, заполните: ${act.missingForClose.join(', ')}`}
        />
      )}
      {act.status === 'DRAFT' && (
        <Alert
          type="info"
          showIcon
          message="Черновик без номера"
          description="Выберите технику и нажмите «Открыть акт» — акт получит номер."
        />
      )}

      <div className="act-body">
        <HeaderForm key={`${act.id}:${act.version}`} act={act} />
        <Timesheet act={act} />
        <PartsSection act={act} />
      </div>
    </section>
  );
}
