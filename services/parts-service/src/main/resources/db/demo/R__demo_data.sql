-- Демо-данные каталога (подключаются только через SPRING_FLYWAY_LOCATIONS с db/demo).
INSERT INTO part_catalog_item (id, name, catalog_number, manufacturer, default_unit, is_fluid, version, created_at, created_by, updated_at, updated_by) VALUES
    ('01960000-0000-7000-8000-000000000001', 'Фильтр масляный двигателя',      '1R-0750', 'CAT',     'PCS', FALSE, 0, now(), 'demo', now(), 'demo'),
    ('01960000-0000-7000-8000-000000000002', 'Фильтр топливный тонкой очистки', '1R-0751', 'CAT',     'PCS', FALSE, 0, now(), 'demo', now(), 'demo'),
    ('01960000-0000-7000-8000-000000000003', 'Фильтр воздушный первичный',      '6I-2501', 'CAT',     'PCS', FALSE, 0, now(), 'demo', now(), 'demo'),
    ('01960000-0000-7000-8000-000000000004', 'Масло моторное DEO 15W-40',       NULL,      'CAT',     'L',   TRUE,  0, now(), 'demo', now(), 'demo'),
    ('01960000-0000-7000-8000-000000000005', 'Масло трансмиссионное TDTO 30',   NULL,      'CAT',     'L',   TRUE,  0, now(), 'demo', now(), 'demo'),
    ('01960000-0000-7000-8000-000000000006', 'Охлаждающая жидкость ELC',        NULL,      'CAT',     'L',   TRUE,  0, now(), 'demo', now(), 'demo'),
    ('01960000-0000-7000-8000-000000000007', 'Фильтр гидравлический',           '208-60-71123', 'Komatsu', 'PCS', FALSE, 0, now(), 'demo', now(), 'demo'),
    ('01960000-0000-7000-8000-000000000008', 'Смазка литиевая EP2',             NULL,      NULL,      'KG',  FALSE, 0, now(), 'demo', now(), 'demo'),
    ('01960000-0000-7000-8000-000000000009', 'Ремкомплект тормозного суппорта', 'RK-2201', NULL,      'SET', FALSE, 0, now(), 'demo', now(), 'demo')
ON CONFLICT (id) DO NOTHING;
