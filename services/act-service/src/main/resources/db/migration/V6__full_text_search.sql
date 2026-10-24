-- Полнотекстовый поиск по акту (раздел 8 SPEC.md). search_vector пересобирается приложением при каждом
-- изменении акта (ActPersistenceAdapter.refreshAggregates) тем же выражением, что ниже.
-- Веса: A — номер и краткое описание; B — заказчик, площадка, техника; C — подробное описание, рекомендации,
-- место работ, представитель заказчика; D — инженеры, запчасти, подписи к фото.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

ALTER TABLE work_act ADD COLUMN search_vector TSVECTOR;

UPDATE work_act w SET search_vector =
      setweight(to_tsvector('russian', concat_ws(' ', w.number, w.short_description)), 'A')
   || setweight(to_tsvector('russian', concat_ws(' ', w.org_name, w.org_short_name, w.department_name, w.site_name,
          w.equipment_type_name, w.equipment_brand, w.equipment_model, w.equipment_serial, w.equipment_garage,
          w.equipment_plate)), 'B')
   || setweight(to_tsvector('russian', concat_ws(' ', w.detailed_description, w.recommendations, w.location,
          w.customer_rep_name)), 'C')
   || setweight(to_tsvector('russian', concat_ws(' ', w.engineer_names,
          (SELECT string_agg(concat_ws(' ', l.name, l.catalog_number), ' ') FROM act_part_line l WHERE l.act_id = w.id),
          (SELECT string_agg(p.caption, ' ') FROM act_photo p WHERE p.act_id = w.id))), 'D');

CREATE INDEX ix_work_act_search ON work_act USING gin (search_vector);

-- Поиск по части номера и с опечатками: серийный, гаражный, госномер, номер акта, каталожные номера запчастей.
CREATE INDEX ix_work_act_serial_trgm ON work_act USING gin (lower(coalesce(equipment_serial, '')) gin_trgm_ops);
CREATE INDEX ix_work_act_garage_trgm ON work_act USING gin (lower(coalesce(equipment_garage, '')) gin_trgm_ops);
CREATE INDEX ix_work_act_plate_trgm ON work_act USING gin (lower(coalesce(equipment_plate, '')) gin_trgm_ops);
CREATE INDEX ix_work_act_number_trgm ON work_act USING gin (lower(coalesce(number, '')) gin_trgm_ops);
CREATE INDEX ix_part_line_number_trgm ON act_part_line USING gin (lower(coalesce(catalog_number, '')) gin_trgm_ops);
CREATE INDEX ix_work_act_short_description ON work_act (short_description);
