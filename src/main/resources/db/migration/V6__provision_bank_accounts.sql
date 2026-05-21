-- V6__provision_bank_accounts.sql
--
-- BUG FIX #7: New users registered before this fix have a trading account
-- but no bank account, so they can't use "Fund Trading Account".
-- Create a default RUB bank account for every user who doesn't have one.

INSERT INTO bank_accounts
    (user_id, account_number, currency, balance, status, created_at, updated_at)
SELECT
    u.id,
    'B-' || UPPER(SUBSTRING(MD5(u.id::TEXT || 'bankfix'), 1, 10)),
    'RUB',
    0.00,
    'ACTIVE',
    NOW(),
    NOW()
FROM users u
WHERE NOT EXISTS (
    SELECT 1 FROM bank_accounts ba WHERE ba.user_id = u.id
);
