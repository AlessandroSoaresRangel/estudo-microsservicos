-- Preserva projetos e vínculos existentes; o antigo projeto de importação
-- passa a obedecer às mesmas regras de qualquer outro projeto.
ALTER TABLE projects DROP COLUMN system_key;
