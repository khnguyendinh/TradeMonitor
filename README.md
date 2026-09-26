# 📈 TradeMonitor

TradeMonitor là công cụ theo dõi hiệu suất giao dịch: import báo cáo lịch sử từ MT5, tự tính KPI (lãi/lỗ ròng, win rate, profit factor, drawdown, kỳ vọng...), vẽ đường equity, đẩy cập nhật realtime lên dashboard qua Server-Sent Events và gửi cảnh báo qua Telegram.

## ✨ Tính năng

- **Import báo cáo MT5**: HTML "Trade History Report" (tự nhận UTF-16), HTML Strategy Tester (ghép deal in/out thành lệnh), CSV (`,` `;` hoặc tab, số kiểu `1 234,56`). Upload lại cùng file sẽ tự bỏ qua lệnh trùng ticket.
- **Nhập lệnh tay** và **tạo dữ liệu demo** để thử giao diện.
- **KPI**: lãi/lỗ ròng (đã gồm commission + swap), số dư, win rate, profit factor, max/current drawdown, kỳ vọng mỗi lệnh, chuỗi thắng/thua, thống kê theo mã. Giao dịch nạp tiền (balance) được dùng làm vốn ban đầu.
- **Realtime**: SSE chỉ đẩy sự kiện mới (lệnh mới / dữ liệu thay đổi), tự kết nối lại khi mất.
- **Cảnh báo Telegram**: lệnh lỗ lớn, drawdown vượt ngưỡng, tóm tắt sau mỗi lần import. Token/Chat ID và ngưỡng cấu hình ngay trên màn **Cài đặt** (lưu DB).
- **Lọc theo phiên import**, xoá phiên / xoá lệnh.
- Giao diện sáng (light theme), responsive cho mobile.

## 🛠️ Công nghệ

| Backend | Frontend |
|---|---|
| Java 25, Spring Boot 4.1 (WebFlux, R2DBC) | Angular 22 (zoneless, signals, standalone) |
| PostgreSQL (hoặc H2 in-memory để chạy thử) | Chart.js 4 |
| Jsoup + OpenCSV (đọc báo cáo) | CSS thuần |

## 🚀 Chạy local

### Yêu cầu
- **JDK 25** (build với JDK thấp hơn sẽ lỗi)
- **Node.js ≥ 22.12** (khuyên dùng 24 — xem `frontend/.nvmrc`). Angular 22 **không chạy được trên Node 18/20**.
- Maven 3.9+
- Docker (chỉ khi dùng PostgreSQL)

### 1. Backend

**Cách A — chạy thử nhanh, không cần database** (dữ liệu nằm trong RAM, mất khi tắt):
```powershell
cd backend
$env:JAVA_HOME="C:\Program Files\Java\jdk-25.0.2"
mvn spring-boot:run "-Dspring-boot.run.profiles=h2"
```

**Cách B — PostgreSQL:**
```powershell
docker compose up -d          # tại thư mục gốc, tạo DB trademonitor_db
cd backend
$env:JAVA_HOME="C:\Program Files\Java\jdk-25.0.2"
mvn spring-boot:run
```
Schema tự khởi tạo/migrate khi start (`schema.sql`, idempotent). Có thể đổi kết nối qua biến môi trường `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.

Backend chạy ở `http://localhost:8080`.

### 2. Frontend
```powershell
cd frontend
nvm use 24.15.0     # hoặc bất kỳ Node >= 22.12
npm install
npm start
```
Mở `http://localhost:4200`. Dev server proxy `/api` sang `http://localhost:8080` (`proxy.conf.json`), nên không cần cấu hình CORS.

### 3. Dùng thử
1. Vào **Tổng quan** → bấm **Tạo 60 lệnh demo**, hoặc
2. Vào **Lệnh & Upload** → kéo thả file báo cáo MT5 (MT5: tab History → chuột phải → Report → HTML).
3. Vào **Cài đặt & Cảnh báo** để nhập Telegram Bot Token / Chat ID, bấm **Lưu** rồi **Gửi thử**.

## 🔌 API chính

| Method | Path | Mô tả |
|---|---|---|
| GET | `/api/trades?sessionId=&symbol=&limit=` | Danh sách lệnh (mới nhất trước) |
| POST | `/api/trades` | Thêm lệnh tay |
| DELETE | `/api/trades/{id}` | Xoá lệnh |
| POST | `/api/trades/upload` | Upload file (multipart, field `file`) |
| GET | `/api/trades/stream` | SSE: event `hello`, `trade`, `refresh` |
| GET / DELETE | `/api/sessions`, `/api/sessions/{id}` | Phiên import |
| GET | `/api/analytics/metrics?sessionId=` | KPI + equity curve + thống kê theo mã |
| POST | `/api/demo/seed?count=50` | Sinh dữ liệu demo |
| GET / PUT | `/api/settings` | Cấu hình Telegram & ngưỡng cảnh báo |
| POST | `/api/telegram/test` | Gửi tin nhắn thử |

## 🧪 Test
```powershell
cd backend;  mvn test        # parser MT5 (HTML/UTF-16/CSV/Deals) + analytics
cd frontend; npm test
```
