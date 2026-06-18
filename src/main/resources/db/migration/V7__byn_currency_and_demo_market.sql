-- Normalize currency data to BYN and keep legacy RUB rows visible as BYN.

ALTER TABLE bank_accounts      ALTER COLUMN currency SET DEFAULT 'BYN';
ALTER TABLE trading_accounts   ALTER COLUMN currency SET DEFAULT 'BYN';
ALTER TABLE cash_operations    ALTER COLUMN currency SET DEFAULT 'BYN';
ALTER TABLE instruments        ALTER COLUMN currency SET DEFAULT 'BYN';

UPDATE bank_accounts
SET currency = 'BYN'
WHERE currency = 'RUB';

UPDATE trading_accounts
SET currency = 'BYN'
WHERE currency = 'RUB';

UPDATE cash_operations
SET currency = 'BYN'
WHERE currency = 'RUB';

UPDATE instruments
SET currency = 'BYN'
WHERE currency = 'RUB';

UPDATE instruments
SET ticker = 'USDBYN',
    name = 'USD/BYN',
    currency = 'BYN'
WHERE ticker = 'USDRUB';
