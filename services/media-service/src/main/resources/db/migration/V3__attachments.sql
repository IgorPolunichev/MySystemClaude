-- Документы акта (PDF, сканы, файлы Office) не имеют превью и размеров изображения.
ALTER TABLE media_object ALTER COLUMN thumbnail_key DROP NOT NULL;
