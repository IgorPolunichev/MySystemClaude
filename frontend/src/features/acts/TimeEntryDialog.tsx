import { useMemo, useState } from 'react';
import {
  Alert,
  App,
  Button,
  Col,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Space,
  TimePicker,
  Typography,
} from 'antd';
import { CopyOutlined, DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import dayjs, { type Dayjs } from 'dayjs';
import type { ActTimeEntry, TimeEntryInput } from '../../api/generated/acts/model';
import { calculateWorkTime, crossesMidnight, formatDuration } from './workTime';

interface Props {
  title: string;
  entry?: ActTimeEntry;
  /** Запись за предыдущий день — для кнопки «Как в прошлый день». */
  previous?: ActTimeEntry;
  saving: boolean;
  onSave: (data: TimeEntryInput) => Promise<boolean>;
  onDelete?: () => Promise<boolean>;
  onClose: () => void;
}

interface FormValues {
  start?: Dayjs | null;
  end?: Dayjs | null;
  breaks?: { start?: Dayjs | null; end?: Dayjs | null }[];
  travelHours?: number | null;
  comment?: string | null;
}

const FORMAT = 'HH:mm';
const t = (v?: string | null) => (v ? dayjs(v, FORMAT) : null);
const s = (v?: Dayjs | null) => (v ? v.format(FORMAT) : null);

function toForm(e?: ActTimeEntry): FormValues {
  return {
    start: t(e?.startTime),
    end: t(e?.endTime),
    breaks: e?.breaks.map((b) => ({ start: t(b.startTime), end: t(b.endTime) })) ?? [],
    travelHours: e?.travelHours ?? null,
    comment: e?.comment ?? null,
  };
}

/**
 * Ввод времени инженера за день: начало и окончание работы, перерывы (обед, ожидание), время в пути.
 * Продуктивное время пересчитывается на лету по тому же правилу, что и на сервере.
 */
export function TimeEntryDialog({ title, entry, previous, saving, onSave, onDelete, onClose }: Props) {
  const [form] = Form.useForm<FormValues>();
  const { message } = App.useApp();
  const [values, setValues] = useState<FormValues>(() => toForm(entry));

  const result = useMemo(() => {
    const start = s(values.start);
    const end = s(values.end);
    if (!start || !end) return null;
    const breaks = (values.breaks ?? [])
      .filter((b) => b && b.start && b.end)
      .map((b) => ({ start: s(b.start)!, end: s(b.end)! }));
    return { ...calculateWorkTime({ start, end }, breaks), overnight: crossesMidnight({ start, end }) };
  }, [values]);

  const submit = async (v: FormValues) => {
    const start = s(v.start);
    const end = s(v.end);
    if ((start == null) !== (end == null)) {
      message.error('Укажите и начало, и окончание работы');
      return;
    }
    if (result?.error) {
      message.error(result.error);
      return;
    }
    const breaks = (v.breaks ?? []).filter((b) => b && (b.start || b.end));
    if (breaks.some((b) => !b.start || !b.end)) {
      message.error('Укажите начало и окончание каждого перерыва');
      return;
    }
    const data: TimeEntryInput = {
      startTime: start,
      endTime: end,
      breaks: breaks.map((b) => ({ startTime: s(b.start)!, endTime: s(b.end)! })),
      travelHours: v.travelHours ?? null,
      comment: v.comment?.trim() ? v.comment.trim() : null,
    };
    if (await onSave(data)) onClose();
  };

  const addBreak = (add: (v: unknown) => void) => {
    const start = values.start;
    const end = values.end;
    // Обед по умолчанию — 12:00–13:00, если попадает в интервал работы; иначе пустой перерыв.
    const lunch = start && end && start.hour() < 12 && (end.hour() >= 13 || end.isBefore(start));
    add(lunch ? { start: dayjs('12:00', FORMAT), end: dayjs('13:00', FORMAT) } : { start: null, end: null });
  };

  return (
    <Modal
      open
      title={title}
      onCancel={onClose}
      width={560}
      destroyOnHidden
      footer={
        <Space style={{ width: '100%', justifyContent: 'space-between' }}>
          <span>
            {onDelete && (
              <Button
                danger
                icon={<DeleteOutlined />}
                onClick={() => void onDelete().then((ok) => ok && onClose())}
              >
                Удалить
              </Button>
            )}
          </span>
          <Space>
            <Button onClick={onClose}>Отмена</Button>
            <Button type="primary" loading={saving} onClick={() => form.submit()}>
              Сохранить
            </Button>
          </Space>
        </Space>
      }
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={toForm(entry)}
        onValuesChange={(_, all) => setValues(all)}
        onFinish={(v) => void submit(v)}
      >
        {previous && !entry && (
          <Button
            size="small"
            icon={<CopyOutlined />}
            style={{ marginBottom: 12 }}
            onClick={() => {
              const v = { ...toForm(previous), comment: null };
              form.setFieldsValue(v);
              setValues(v);
            }}
          >
            Как в предыдущий день: {previous.startTime}–{previous.endTime}
          </Button>
        )}
        <Row gutter={16}>
          <Col span={12}>
            <Form.Item name="start" label="Начало работы">
              <TimePicker
                format={FORMAT}
                minuteStep={5}
                needConfirm={false}
                style={{ width: '100%' }}
                placeholder="10:00"
              />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="end" label="Окончание работы">
              <TimePicker
                format={FORMAT}
                minuteStep={5}
                needConfirm={false}
                style={{ width: '100%' }}
                placeholder="13:00"
              />
            </Form.Item>
          </Col>
        </Row>

        <Typography.Text strong>Перерывы</Typography.Text>
        <Typography.Paragraph type="secondary" style={{ margin: '2px 0 8px', fontSize: 12 }}>
          Обед, ожидание запчастей и другие перерывы не входят в продуктивное время.
        </Typography.Paragraph>
        <Form.List name="breaks">
          {(fields, { add, remove }) => (
            <>
              {fields.map((field) => (
                <Space key={field.key} align="baseline" style={{ display: 'flex' }}>
                  <Form.Item name={[field.name, 'start']} style={{ marginBottom: 8 }}>
                    <TimePicker format={FORMAT} minuteStep={5} needConfirm={false} placeholder="с" />
                  </Form.Item>
                  <span>—</span>
                  <Form.Item name={[field.name, 'end']} style={{ marginBottom: 8 }}>
                    <TimePicker format={FORMAT} minuteStep={5} needConfirm={false} placeholder="по" />
                  </Form.Item>
                  <Button
                    type="text"
                    danger
                    icon={<DeleteOutlined />}
                    aria-label="Удалить перерыв"
                    onClick={() => remove(field.name)}
                  />
                </Space>
              ))}
              <Button
                size="small"
                icon={<PlusOutlined />}
                onClick={() => addBreak(add)}
                style={{ marginBottom: 16 }}
              >
                Добавить перерыв
              </Button>
            </>
          )}
        </Form.List>

        {result &&
          (result.error ? (
            <Alert type="error" showIcon message={result.error} style={{ marginBottom: 16 }} />
          ) : (
            <div className="work-result">
              <span className="cell-muted">Продуктивное время</span>
              <b>{formatDuration(result.minutes)}</b>
              <span className="cell-muted">
                интервал {formatDuration(result.spanMinutes)}
                {result.breakMinutes > 0 && ` − перерывы ${formatDuration(result.breakMinutes)}`}
                {result.overnight && ' · окончание на следующий день'}
              </span>
            </div>
          ))}

        <Row gutter={16}>
          <Col span={12}>
            <Form.Item name="travelHours" label="Время в пути, ч" extra="Не входит в продуктивное время">
              <InputNumber min={0} max={24} step={0.25} style={{ width: '100%' }} decimalSeparator="," />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="comment" label="Комментарий">
              <Input maxLength={500} />
            </Form.Item>
          </Col>
        </Row>
      </Form>
    </Modal>
  );
}
