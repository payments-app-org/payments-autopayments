--liquibase formatted sql

--changeset autopayments:003-full-name
-- Полное ФИО в шаблонах и автоплатежах (раньше в профиле была только буква фамилии)
UPDATE templates SET fields = replace(fields::text, '"Р Артур Константинович"', '"Руднев Артур Константинович"')::jsonb
WHERE fields::text LIKE '%Р Артур Константинович%';
UPDATE autopayments SET fields = replace(fields::text, '"Р Артур Константинович"', '"Руднев Артур Константинович"')::jsonb
WHERE fields::text LIKE '%Р Артур Константинович%';
