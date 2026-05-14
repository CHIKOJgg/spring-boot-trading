-- ============================================================
-- V1__init_schema.sql
-- Complete database schema for Trading Platform
-- ============================================================

-- ROLES
CREATE TABLE roles (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- USERS
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL UNIQUE,
    email           VARCHAR(150) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    is_locked       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    last_login_at   TIMESTAMP
);

CREATE UNIQUE INDEX idx_users_username ON users(username);
CREATE UNIQUE INDEX idx_users_email    ON users(email);

-- USER_ROLES
CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- CLIENT PROFILES
CREATE TABLE client_profiles (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    first_name    VARCHAR(100),
    last_name     VARCHAR(100),
    phone         VARCHAR(30),
    address       VARCHAR(500),
    date_of_birth DATE,
    kyc_status    VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- BANK ACCOUNTS
CREATE TABLE bank_accounts (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT          NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    account_number  VARCHAR(30)     NOT NULL UNIQUE,
    currency        VARCHAR(10)     NOT NULL DEFAULT 'RUB',
    balance         NUMERIC(20, 4)  NOT NULL DEFAULT 0,
    status          VARCHAR(30)     NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_bank_balance_non_negative CHECK (balance >= 0)
);

CREATE UNIQUE INDEX idx_bank_account_number ON bank_accounts(account_number);
CREATE INDEX idx_bank_accounts_user_id      ON bank_accounts(user_id);

-- INSTRUMENTS
CREATE TABLE instruments (
    id              BIGSERIAL PRIMARY KEY,
    ticker          VARCHAR(20)    NOT NULL UNIQUE,
    name            VARCHAR(255)   NOT NULL,
    instrument_type VARCHAR(50)    NOT NULL,
    currency        VARCHAR(10)    NOT NULL DEFAULT 'RUB',
    lot_size        INT            NOT NULL DEFAULT 1,
    tick_size       NUMERIC(20, 8) NOT NULL DEFAULT 0.01,
    is_active       BOOLEAN        NOT NULL DEFAULT TRUE,
    description     TEXT,
    created_at      TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_instruments_ticker ON instruments(ticker);

-- TRADING ACCOUNTS
CREATE TABLE trading_accounts (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT          NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    account_number  VARCHAR(30)     NOT NULL UNIQUE,
    currency        VARCHAR(10)     NOT NULL DEFAULT 'RUB',
    cash_balance    NUMERIC(20, 4)  NOT NULL DEFAULT 0,
    frozen_balance  NUMERIC(20, 4)  NOT NULL DEFAULT 0,
    status          VARCHAR(30)     NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_trading_balance_non_negative CHECK (cash_balance >= 0),
    CONSTRAINT chk_frozen_non_negative          CHECK (frozen_balance >= 0)
);

CREATE INDEX idx_trading_accounts_user_id ON trading_accounts(user_id);

-- TRADING POSITIONS (holdings)
CREATE TABLE trading_positions (
    id                 BIGSERIAL PRIMARY KEY,
    trading_account_id BIGINT         NOT NULL REFERENCES trading_accounts(id),
    instrument_id      BIGINT         NOT NULL REFERENCES instruments(id),
    quantity           INT            NOT NULL DEFAULT 0,
    frozen_quantity    INT            NOT NULL DEFAULT 0,
    avg_cost           NUMERIC(20, 4),
    updated_at         TIMESTAMP      NOT NULL DEFAULT NOW(),
    UNIQUE (trading_account_id, instrument_id),
    CONSTRAINT chk_position_quantity_non_negative CHECK (quantity >= 0)
);

-- ORDERS
CREATE TABLE orders (
    id                 VARCHAR(36)    PRIMARY KEY,
    user_id            BIGINT         NOT NULL REFERENCES users(id),
    trading_account_id BIGINT         NOT NULL REFERENCES trading_accounts(id),
    instrument_id      BIGINT         NOT NULL REFERENCES instruments(id),
    side               VARCHAR(10)    NOT NULL,
    order_type         VARCHAR(20)    NOT NULL DEFAULT 'LIMIT',
    price              NUMERIC(20, 4) NOT NULL,
    quantity           INT            NOT NULL,
    remaining_qty      INT            NOT NULL,
    status             VARCHAR(30)    NOT NULL DEFAULT 'PENDING',
    time_in_force      VARCHAR(10)    NOT NULL DEFAULT 'GTC',
    created_at         TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP      NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_order_quantity_positive  CHECK (quantity > 0),
    CONSTRAINT chk_order_price_positive     CHECK (price > 0),
    CONSTRAINT chk_order_remaining_non_neg  CHECK (remaining_qty >= 0)
);

CREATE INDEX idx_orders_user_id       ON orders(user_id);
CREATE INDEX idx_orders_instrument_id ON orders(instrument_id);
CREATE INDEX idx_orders_status        ON orders(status);
CREATE INDEX idx_orders_created_at    ON orders(created_at);
CREATE UNIQUE INDEX idx_orders_id     ON orders(id);

-- TRADES
CREATE TABLE trades (
    id              VARCHAR(36)    PRIMARY KEY,
    buy_order_id    VARCHAR(36)    NOT NULL REFERENCES orders(id),
    sell_order_id   VARCHAR(36)    NOT NULL REFERENCES orders(id),
    instrument_id   BIGINT         NOT NULL REFERENCES instruments(id),
    price           NUMERIC(20, 4) NOT NULL,
    quantity        INT            NOT NULL,
    traded_at       TIMESTAMP      NOT NULL DEFAULT NOW(),
    buyer_user_id   BIGINT         NOT NULL REFERENCES users(id),
    seller_user_id  BIGINT         NOT NULL REFERENCES users(id),
    status          VARCHAR(30)    NOT NULL DEFAULT 'EXECUTED',
    CONSTRAINT chk_trade_quantity_positive CHECK (quantity > 0),
    CONSTRAINT chk_trade_price_positive    CHECK (price > 0)
);

CREATE INDEX idx_trades_buy_order_id    ON trades(buy_order_id);
CREATE INDEX idx_trades_sell_order_id   ON trades(sell_order_id);
CREATE INDEX idx_trades_instrument_id   ON trades(instrument_id);
CREATE INDEX idx_trades_traded_at       ON trades(traded_at);
CREATE UNIQUE INDEX idx_trades_id       ON trades(id);

-- CASH OPERATIONS
CREATE TABLE cash_operations (
    id              BIGSERIAL      PRIMARY KEY,
    bank_account_id BIGINT         NOT NULL REFERENCES bank_accounts(id),
    user_id         BIGINT         NOT NULL REFERENCES users(id),
    operation_type  VARCHAR(30)    NOT NULL,
    amount          NUMERIC(20, 4) NOT NULL,
    currency        VARCHAR(10)    NOT NULL DEFAULT 'RUB',
    status          VARCHAR(30)    NOT NULL DEFAULT 'COMPLETED',
    description     VARCHAR(500),
    reference_id    VARCHAR(100),
    created_at      TIMESTAMP      NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_cash_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_cash_ops_bank_account_id ON cash_operations(bank_account_id);
CREATE INDEX idx_cash_ops_user_id         ON cash_operations(user_id);
CREATE INDEX idx_cash_ops_created_at      ON cash_operations(created_at);

-- AUDIT LOGS
CREATE TABLE audit_logs (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       REFERENCES users(id),
    action      VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100),
    entity_id   VARCHAR(100),
    details     TEXT,
    ip_address  VARCHAR(50),
    user_agent  VARCHAR(500),
    result      VARCHAR(30)  NOT NULL DEFAULT 'SUCCESS',
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_logs_user_id    ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_action     ON audit_logs(action);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at);

-- NOTIFICATIONS
CREATE TABLE notifications (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title       VARCHAR(255) NOT NULL,
    message     TEXT         NOT NULL,
    type        VARCHAR(50)  NOT NULL,
    is_read     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_user_id   ON notifications(user_id);
CREATE INDEX idx_notifications_is_read   ON notifications(is_read);

-- RISK LIMITS
CREATE TABLE risk_limits (
    id              BIGSERIAL      PRIMARY KEY,
    user_id         BIGINT         REFERENCES users(id) ON DELETE CASCADE,
    instrument_id   BIGINT         REFERENCES instruments(id) ON DELETE CASCADE,
    max_order_qty   INT,
    max_order_value NUMERIC(20, 4),
    max_daily_value NUMERIC(20, 4),
    is_active       BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP      NOT NULL DEFAULT NOW()
);

-- REFRESH TOKENS
CREATE TABLE refresh_tokens (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token       VARCHAR(512) NOT NULL UNIQUE,
    expires_at  TIMESTAMP    NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    is_revoked  BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
