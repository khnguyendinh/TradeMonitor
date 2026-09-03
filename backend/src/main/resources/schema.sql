-- Trade Sessions
CREATE TABLE IF NOT EXISTS trade_sessions (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    import_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) NOT NULL
);

-- Trade Records
CREATE TABLE IF NOT EXISTS trade_records (
    id BIGSERIAL PRIMARY KEY,
    ticket VARCHAR(100) NOT NULL,
    symbol VARCHAR(50) NOT NULL,
    type VARCHAR(20) NOT NULL,
    volume DECIMAL(10, 2) NOT NULL,
    open_price DECIMAL(10, 5) NOT NULL,
    close_price DECIMAL(10, 5),
    open_time TIMESTAMP NOT NULL,
    close_time TIMESTAMP,
    sl DECIMAL(10, 5),
    tp DECIMAL(10, 5),
    commission DECIMAL(10, 2) DEFAULT 0,
    swap DECIMAL(10, 2) DEFAULT 0,
    profit DECIMAL(10, 2) NOT NULL,
    session_id BIGINT NOT NULL,
    FOREIGN KEY (session_id) REFERENCES trade_sessions(id) ON DELETE CASCADE
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_trade_records_symbol ON trade_records(symbol);
CREATE INDEX IF NOT EXISTS idx_trade_records_open_time ON trade_records(open_time);
CREATE INDEX IF NOT EXISTS idx_trade_records_session_id ON trade_records(session_id);
