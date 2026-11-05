--liquibase formatted sql

--changeset autopayments:004-claims
-- Бронь записи планировщиком: под забирает пачку (FOR UPDATE SKIP LOCKED) и ставит claimed_until — другие поды
-- её не берут, пока бронь не истечёт. Несколько подов делят работу, а не дублируют.
ALTER TABLE autopayments ADD COLUMN claimed_until TIMESTAMPTZ;
ALTER TABLE topups ADD COLUMN claimed_until TIMESTAMPTZ;
CREATE INDEX topups_due_idx ON topups (created_at) WHERE status = 'ACTIVE';
