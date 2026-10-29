-- Chave estável para identificar o projeto de importação sem reservar IDs de negócio.
ALTER TABLE projects ADD COLUMN system_key VARCHAR(50) UNIQUE;

INSERT INTO projects (name, description, system_key)
VALUES ('Projeto de importação',
        'Tarefas preexistentes que não possuíam vínculo com um projeto.',
        'TASK_IMPORT');
