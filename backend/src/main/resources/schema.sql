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
    volume DECIMAL(18, 2) NOT NULL,
    open_price DECIMAL(18, 5) NOT NULL,
    close_price DECIMAL(18, 5),
    open_time TIMESTAMP NOT NULL,
    close_time TIMESTAMP,
    sl DECIMAL(18, 5),
    tp DECIMAL(18, 5),
    commission DECIMAL(18, 2) DEFAULT 0,
    swap DECIMAL(18, 2) DEFAULT 0,
    profit DECIMAL(18, 2) NOT NULL,
    session_id BIGINT NOT NULL,
    FOREIGN KEY (session_id) REFERENCES trade_sessions(id) ON DELETE CASCADE
);

-- App settings (key/value)
CREATE TABLE IF NOT EXISTS app_settings (
    setting_key VARCHAR(100) PRIMARY KEY,
    setting_value VARCHAR(1000)
);

-- Migration cho DB tạo từ phiên bản cũ (idempotent)
ALTER TABLE trade_sessions ADD COLUMN IF NOT EXISTS trade_count INT NOT NULL DEFAULT 0;
ALTER TABLE trade_sessions ADD COLUMN IF NOT EXISTS message VARCHAR(1000);
ALTER TABLE trade_records ALTER COLUMN volume TYPE DECIMAL(18, 2);
ALTER TABLE trade_records ALTER COLUMN open_price TYPE DECIMAL(18, 5);
ALTER TABLE trade_records ALTER COLUMN close_price TYPE DECIMAL(18, 5);
ALTER TABLE trade_records ALTER COLUMN sl TYPE DECIMAL(18, 5);
ALTER TABLE trade_records ALTER COLUMN tp TYPE DECIMAL(18, 5);
ALTER TABLE trade_records ALTER COLUMN commission TYPE DECIMAL(18, 2);
ALTER TABLE trade_records ALTER COLUMN swap TYPE DECIMAL(18, 2);
ALTER TABLE trade_records ALTER COLUMN profit TYPE DECIMAL(18, 2);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_trade_records_symbol ON trade_records(symbol);
CREATE INDEX IF NOT EXISTS idx_trade_records_open_time ON trade_records(open_time);
CREATE INDEX IF NOT EXISTS idx_trade_records_session_id ON trade_records(session_id);
CREATE INDEX IF NOT EXISTS idx_trade_records_ticket ON trade_records(ticket);

COMMENT ON TABLE trade_sessions IS 'Phiên import dữ liệu giao dịch (mỗi lần upload file hoặc nhóm lệnh nhập tay)';
COMMENT ON COLUMN trade_sessions.name IS 'Tên phiên, thường là tên file đã upload';
COMMENT ON COLUMN trade_sessions.import_time IS 'Thời điểm import';
COMMENT ON COLUMN trade_sessions.status IS 'Trạng thái xử lý: PROCESSING, COMPLETED, FAILED';
COMMENT ON COLUMN trade_sessions.trade_count IS 'Số lệnh đã lưu trong phiên';
COMMENT ON COLUMN trade_sessions.message IS 'Thông báo kết quả hoặc lỗi khi import';

COMMENT ON TABLE trade_records IS 'Lệnh giao dịch đã đóng (position) và giao dịch nạp/rút tiền';
COMMENT ON COLUMN trade_records.ticket IS 'Mã lệnh (position/ticket) trên MT5';
COMMENT ON COLUMN trade_records.symbol IS 'Mã sản phẩm giao dịch, ví dụ EURUSD';
COMMENT ON COLUMN trade_records.type IS 'Loại giao dịch: BUY, SELL hoặc BALANCE (nạp/rút tiền)';
COMMENT ON COLUMN trade_records.volume IS 'Khối lượng (lot)';
COMMENT ON COLUMN trade_records.open_price IS 'Giá mở lệnh';
COMMENT ON COLUMN trade_records.close_price IS 'Giá đóng lệnh';
COMMENT ON COLUMN trade_records.open_time IS 'Thời gian mở lệnh';
COMMENT ON COLUMN trade_records.close_time IS 'Thời gian đóng lệnh';
COMMENT ON COLUMN trade_records.sl IS 'Mức cắt lỗ (Stop Loss)';
COMMENT ON COLUMN trade_records.tp IS 'Mức chốt lời (Take Profit)';
COMMENT ON COLUMN trade_records.commission IS 'Phí hoa hồng';
COMMENT ON COLUMN trade_records.swap IS 'Phí qua đêm (swap)';
COMMENT ON COLUMN trade_records.profit IS 'Lợi nhuận gộp của lệnh (chưa gồm phí và swap)';
COMMENT ON COLUMN trade_records.session_id IS 'Phiên import chứa lệnh này';

COMMENT ON TABLE app_settings IS 'Cấu hình ứng dụng dạng khoá - giá trị (Telegram, ngưỡng cảnh báo...)';
COMMENT ON COLUMN app_settings.setting_key IS 'Tên khoá cấu hình';
COMMENT ON COLUMN app_settings.setting_value IS 'Giá trị cấu hình';
