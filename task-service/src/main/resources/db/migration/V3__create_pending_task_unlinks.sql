-- A remoção do vínculo remoto só é permitida após o commit da exclusão local.
CREATE TABLE pending_task_unlinks (
    task_id BIGINT PRIMARY KEY
);
