-- «Доступно всем для редактирования»: работу начал один инженер, продолжает и закрывает другой.
ALTER TABLE work_act ADD COLUMN open_for_all BOOLEAN NOT NULL DEFAULT FALSE;
