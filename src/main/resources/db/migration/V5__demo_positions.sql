-- V5__demo_positions.sql
--
-- Seeds demo positions for every existing trading account so users
-- can immediately test SELL orders without having to buy first.
-- Each account gets 1 000 shares in every active instrument.

INSERT INTO trading_positions
    (trading_account_id, instrument_id, quantity, frozen_quantity, avg_cost, updated_at)
SELECT
    ta.id          AS trading_account_id,
    i.id           AS instrument_id,
    1000           AS quantity,
    0              AS frozen_quantity,
    i.tick_size * 10000 AS avg_cost,   -- reasonable cost basis
    NOW()          AS updated_at
FROM trading_accounts ta
CROSS JOIN instruments i
WHERE i.is_active = true
  AND NOT EXISTS (
      SELECT 1 FROM trading_positions tp
      WHERE tp.trading_account_id = ta.id
        AND tp.instrument_id = i.id
  );

-- Give all trading accounts a healthy balance if they somehow have zero
UPDATE trading_accounts
SET cash_balance = 1000000.00,
    updated_at   = NOW()
WHERE cash_balance = 0 OR cash_balance IS NULL;
