--liquibase formatted sql

--changeset autopayments:002-topups
-- Автопополнение своего счёта: со своей карты/кошелька или со своего счёта в другом банке по СБП (me2me).
-- MONTHLY — каждый месяц в day_of_month на amount; BY_BALANCE — когда баланс <= threshold, на amount;
-- CONSTANT_BALANCE — когда баланс < target_balance, на разницу до него. monthly_limit — сколько всего за месяц.
CREATE TABLE topups (
    id                     UUID           PRIMARY KEY,
    user_id                UUID           NOT NULL,
    name                   VARCHAR(60)    NOT NULL,
    source_kind            VARCHAR(5)     NOT NULL CHECK (source_kind IN ('OWN', 'SBP')),
    -- Свой счёт или счёт-источник «в другом банке» (EXTERNAL_SBP в account)
    source_account_id      UUID           NOT NULL,
    source_title           VARCHAR(100)   NOT NULL,
    source_bank_id         VARCHAR(12),
    destination_account_id UUID           NOT NULL,
    destination_title      VARCHAR(100)   NOT NULL,
    kind                   VARCHAR(16)    NOT NULL CHECK (kind IN ('MONTHLY', 'BY_BALANCE', 'CONSTANT_BALANCE')),
    day_of_month           INTEGER        CHECK (day_of_month BETWEEN 1 AND 31),
    amount                 NUMERIC(19, 2) CHECK (amount > 0),
    threshold              NUMERIC(19, 2) CHECK (threshold >= 0),
    target_balance         NUMERIC(19, 2) CHECK (target_balance > 0),
    monthly_limit          NUMERIC(19, 2) CHECK (monthly_limit > 0),
    next_run_date          DATE,
    next_check_at          TIMESTAMPTZ,
    status                 VARCHAR(10)    NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'PAUSED')),
    last_run_at            TIMESTAMPTZ,
    last_status            VARCHAR(12),
    last_message           VARCHAR(255),
    created_at             TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CHECK (source_account_id <> destination_account_id),
    CHECK (kind <> 'MONTHLY' OR (day_of_month IS NOT NULL AND amount IS NOT NULL AND next_run_date IS NOT NULL)),
    CHECK (kind <> 'BY_BALANCE' OR (threshold IS NOT NULL AND amount IS NOT NULL)),
    CHECK (kind <> 'CONSTANT_BALANCE' OR target_balance IS NOT NULL),
    CHECK (source_kind <> 'SBP' OR source_bank_id IS NOT NULL)
);
CREATE INDEX topups_user_idx ON topups (user_id, created_at);

-- Запуски: trigger_key — дата (раз в месяц) или момент срабатывания по балансу. Ключ идемпотентности платежа
-- строится из id запуска — повтор после сбоя не пополнит дважды.
CREATE TABLE topup_runs (
    id                UUID           PRIMARY KEY,
    topup_id          UUID           NOT NULL REFERENCES topups (id) ON DELETE CASCADE,
    trigger_key       VARCHAR(40)    NOT NULL,
    status            VARCHAR(12)    NOT NULL CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'PROCESSING')),
    amount            NUMERIC(19, 2) NOT NULL,
    balance_before    NUMERIC(19, 2),
    payment_id        UUID,
    commission        NUMERIC(19, 2),
    quote_token       TEXT,
    message           VARCHAR(255),
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    finished_at       TIMESTAMPTZ,
    UNIQUE (topup_id, trigger_key)
);
