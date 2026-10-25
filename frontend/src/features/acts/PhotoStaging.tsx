import { Button, Input, Modal, Space, Typography } from 'antd';
import { DeleteOutlined, HolderOutlined } from '@ant-design/icons';
import {
  DndContext,
  PointerSensor,
  TouchSensor,
  closestCenter,
  useSensor,
  useSensors,
  type DragEndEvent,
} from '@dnd-kit/core';
import { SortableContext, arrayMove, rectSortingStrategy, useSortable } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { release, type StagedPhoto } from './stagedPhotos';

function StagedCard({
  item,
  number,
  onCaption,
  onRemove,
}: {
  item: StagedPhoto;
  number: number;
  onCaption: (v: string) => void;
  onRemove: () => void;
}) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: item.key,
  });
  return (
    <div
      ref={setNodeRef}
      className={`photo-card staged${isDragging ? ' dragging' : ''}`}
      style={{ transform: CSS.Transform.toString(transform), transition }}
    >
      <div className="photo-thumb" {...attributes} {...listeners} title="Перетащите, чтобы изменить порядок">
        <img src={item.url} alt={item.file.name} />
      </div>
      <Space size={4} className="photo-meta">
        <HolderOutlined className="photo-handle" {...listeners} />
        <span className="photo-number">Фото {number}</span>
        <Button
          type="text"
          size="small"
          danger
          icon={<DeleteOutlined />}
          onClick={onRemove}
          title="Не добавлять это фото"
        />
      </Space>
      <Input.TextArea
        value={item.caption}
        onChange={(e) => onCaption(e.target.value)}
        placeholder="Подпись (можно позже)"
        autoSize={{ minRows: 1, maxRows: 3 }}
        maxLength={500}
      />
    </div>
  );
}

/**
 * Окно «Новые фото»: несколько выбранных или перетащенных фото можно расставить в нужном порядке, подписать и убрать
 * лишние — затем они добавляются в акт именно в этом порядке (раздел 19.17 SPEC.md).
 */
export function PhotoStaging({
  items,
  onChange,
  onConfirm,
  onCancel,
  startNumber,
}: {
  items: StagedPhoto[];
  onChange: (items: StagedPhoto[]) => void;
  onConfirm: (items: StagedPhoto[]) => void;
  onCancel: () => void;
  startNumber: number;
}) {
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
    useSensor(TouchSensor, { activationConstraint: { delay: 200, tolerance: 6 } }),
  );

  const onDragEnd = ({ active, over }: DragEndEvent) => {
    if (!over || active.id === over.id) return;
    const from = items.findIndex((i) => i.key === active.id);
    const to = items.findIndex((i) => i.key === over.id);
    onChange(arrayMove(items, from, to));
  };

  return (
    <Modal
      open
      width="min(980px, 96vw)"
      title={`Новые фото: ${items.length}`}
      okText={`Добавить ${items.length} фото`}
      cancelText="Отмена"
      okButtonProps={{ disabled: items.length === 0 }}
      onOk={() => onConfirm(items)}
      onCancel={onCancel}
      destroyOnHidden
    >
      <Typography.Paragraph type="secondary">
        Перетащите фото, чтобы расставить их по порядку — в акте и техотчёте они будут именно так. Подписи
        можно добавить сейчас или позже.
      </Typography.Paragraph>
      <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={onDragEnd}>
        <SortableContext items={items.map((i) => i.key)} strategy={rectSortingStrategy}>
          <div className="photo-grid">
            {items.map((item, i) => (
              <StagedCard
                key={item.key}
                item={item}
                number={startNumber + i}
                onCaption={(caption) =>
                  onChange(items.map((x) => (x.key === item.key ? { ...x, caption } : x)))
                }
                onRemove={() => {
                  release([item]);
                  onChange(items.filter((x) => x.key !== item.key));
                }}
              />
            ))}
          </div>
        </SortableContext>
      </DndContext>
    </Modal>
  );
}
