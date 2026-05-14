-- V3__fix_admin_and_accounts.sql

-- 1. Reset admin password with a verified BCrypt hash  (password: Admin1234!)
UPDATE users
SET password_hash = '$2a$12$ERQQn51Vqt8Hhbtef77Ri.Jo9bWeauOsBBeQFN.ehjME23gEdyg5.'
WHERE username = 'admin';

-- 2. Ensure all instruments are active so they appear in the market list
UPDATE instruments SET is_active = true;

-- 3. Create trading account for admin if not already present
INSERT INTO trading_accounts (user_id, account_number, currency, cash_balance, frozen_balance, status)
SELECT
    u.id,
    'ACC-ADMIN-001',
    'RUB',
    1000000.00,
    0.00,
    'ACTIVE'
FROM users u
WHERE u.username = 'admin'
  AND NOT EXISTS (
    SELECT 1 FROM trading_accounts ta WHERE ta.user_id = u.id
  );

-- 4. Grant admin ROLE_TRADER too so they can access trading endpoints
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.name = 'ROLE_TRADER'
WHERE u.username = 'admin'
ON CONFLICT DO NOTHING;
