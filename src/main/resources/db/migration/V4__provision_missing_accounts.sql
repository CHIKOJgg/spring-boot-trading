-- V4__provision_missing_accounts.sql
--
-- Provisions a default trading account AND bank account for every
-- existing user that does not already have one.
-- This covers users registered before the AuthService auto-provisioning
-- fix was deployed in V3.

-- 1. Trading accounts — 1 000 000 RUB demo balance
INSERT INTO trading_accounts
    (user_id, account_number, currency, cash_balance, frozen_balance, status, created_at, updated_at)
SELECT
    u.id,
    'ACC-' || LPAD(u.id::TEXT, 6, '0') || '-' || LPAD(FLOOR(RANDOM() * 9999 + 1)::TEXT, 4, '0'),
    'RUB',
    1000000.00,
    0.00,
    'ACTIVE',
    NOW(),
    NOW()
FROM users u
WHERE NOT EXISTS (
    SELECT 1 FROM trading_accounts ta WHERE ta.user_id = u.id
);

-- 2. Bank accounts — zero balance, user can deposit via UI
INSERT INTO bank_accounts
    (user_id, account_number, currency, balance, status, created_at, updated_at)
SELECT
    u.id,
    'BNK-' || LPAD(u.id::TEXT, 6, '0') || '-' || LPAD(FLOOR(RANDOM() * 9999 + 1)::TEXT, 4, '0'),
    'RUB',
    0.00,
    'ACTIVE',
    NOW(),
    NOW()
FROM users u
WHERE NOT EXISTS (
    SELECT 1 FROM bank_accounts ba WHERE ba.user_id = u.id
);
