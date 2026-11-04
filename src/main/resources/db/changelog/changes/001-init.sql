--liquibase formatted sql

--changeset autopayments:001-init
-- Шаблоны платежей и автоплатежи. Платим поставщикам через payment (тип BILL): поставщик и его поля — как в biller.
-- Данные поставщика для витрины (название, логотип) копируем при сохранении, чтобы список не ходил в biller.

CREATE TABLE templates (
    id                UUID           PRIMARY KEY,
    user_id           UUID           NOT NULL,
    name              VARCHAR(40)    NOT NULL,
    provider_code     VARCHAR(40)    NOT NULL,
    provider_group    VARCHAR(30)    NOT NULL,
    provider_name     VARCHAR(150)   NOT NULL,
    provider_domain   VARCHAR(100),
    provider_color    VARCHAR(7)     NOT NULL,
    provider_country  VARCHAR(2),
    -- Поля поставщика {key: value} и главное из них для показа: «+7 996 448-80-61»
    fields            JSONB          NOT NULL,
    target            VARCHAR(150),
    amount            NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    source_account_id UUID           NOT NULL,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX templates_user_idx ON templates (user_id, created_at);

CREATE TABLE autopayments (
    id                UUID           PRIMARY KEY,
    user_id           UUID           NOT NULL,
    name              VARCHAR(60)    NOT NULL,
    provider_code     VARCHAR(40)    NOT NULL,
    provider_group    VARCHAR(30)    NOT NULL,
    provider_name     VARCHAR(150)   NOT NULL,
    provider_domain   VARCHAR(100),
    provider_color    VARCHAR(7)     NOT NULL,
    provider_country  VARCHAR(2),
    fields            JSONB          NOT NULL,
    target            VARCHAR(150),
    amount            NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    -- Основной счёт и запасной: если на основном не хватит денег, вся сумма спишется с запасного
    source_account_id UUID           NOT NULL,
    backup_account_id UUID,
    -- MONTHLY — каждый месяц в day_of_month (в коротком месяце — в последний день),
    -- PERIOD — каждые period_days дней начиная со start_date
    schedule_kind     VARCHAR(10)    NOT NULL CHECK (schedule_kind IN ('MONTHLY', 'PERIOD')),
    day_of_month      INTEGER        CHECK (day_of_month BETWEEN 1 AND 31),
    period_days       INTEGER        CHECK (period_days BETWEEN 1 AND 180),
    start_date        DATE           NOT NULL,
    next_run_date     DATE           NOT NULL,
    status            VARCHAR(10)    NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'PAUSED')),
    last_run_date     DATE,
    last_status       VARCHAR(12),
    last_message      VARCHAR(255),
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CHECK (schedule_kind <> 'MONTHLY' OR day_of_month IS NOT NULL),
    CHECK (schedule_kind <> 'PERIOD' OR period_days IS NOT NULL),
    CHECK (backup_account_id IS NULL OR backup_account_id <> source_account_id)
);
CREATE INDEX autopayments_user_idx ON autopayments (user_id, created_at);
CREATE INDEX autopayments_due_idx ON autopayments (next_run_date) WHERE status = 'ACTIVE';

-- Запуски автоплатежа: один на дату. Ключ идемпотентности платежа строится из (autopayment_id, run_date),
-- поэтому повторный запуск после сбоя не спишет деньги второй раз.
CREATE TABLE autopayment_runs (
    id                UUID           PRIMARY KEY,
    autopayment_id    UUID           NOT NULL REFERENCES autopayments (id) ON DELETE CASCADE,
    run_date          DATE           NOT NULL,
    status            VARCHAR(12)    NOT NULL CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'PROCESSING')),
    payment_id        UUID,
    -- С какого счёта сейчас пробуем и с какой котировкой: повтор после сбоя шлёт в payment тот же запрос
    account_id        UUID,
    quote_token       TEXT,
    amount            NUMERIC(19, 2) NOT NULL,
    commission        NUMERIC(19, 2),
    message           VARCHAR(255),
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    finished_at       TIMESTAMPTZ,
    UNIQUE (autopayment_id, run_date)
);
