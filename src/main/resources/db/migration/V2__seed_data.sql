-- V2__seed_data.sql

-- Roles
INSERT INTO roles (name, description) VALUES
    ('ROLE_ADMIN',  'System administrator with full access'),
    ('ROLE_TRADER', 'Registered trader with market access'),
    ('ROLE_VIEWER', 'Read-only access to market data')
ON CONFLICT (name) DO NOTHING;

-- Admin user  (password: Admin1234!)
INSERT INTO users (username, email, password_hash, is_active)
VALUES ('admin', 'admin@trading.local',
        '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewdBPj4J/HS.i9i2', TRUE)
ON CONFLICT (username) DO NOTHING;

-- Assign admin role
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.username = 'admin' AND r.name = 'ROLE_ADMIN'
ON CONFLICT DO NOTHING;

-- Demo instruments
INSERT INTO instruments (ticker, name, instrument_type, currency, lot_size, tick_size) VALUES
    ('SBER',  'Сбербанк',             'STOCK', 'RUB', 10,  0.01),
    ('GAZP',  'Газпром',              'STOCK', 'RUB', 10,  0.01),
    ('YNDX',  'Яндекс',              'STOCK', 'RUB', 1,   0.10),
    ('LKOH',  'Лукойл',              'STOCK', 'RUB', 1,   1.00),
    ('ROSN',  'Роснефть',            'STOCK', 'RUB', 10,  0.01),
    ('VTBR',  'ВТБ',                 'STOCK', 'RUB', 10000, 0.0001),
    ('MGNT',  'Магнит',              'STOCK', 'RUB', 1,   1.00),
    ('GMKN',  'Норникель',           'STOCK', 'RUB', 1,   1.00),
    ('TATN',  'Татнефть',            'STOCK', 'RUB', 10,  0.01),
    ('MTSS',  'МТС',                 'STOCK', 'RUB', 10,  0.01),
    ('OFZ26222', 'ОФЗ 26222',        'BOND',  'RUB', 1,   0.01),
    ('USDRUB', 'USD/RUB',            'CURRENCY_PAIR', 'RUB', 1000, 0.001)
ON CONFLICT (ticker) DO NOTHING;
