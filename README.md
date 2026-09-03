# 📈 TradeMonitor

TradeMonitor is a real-time trading performance tracking and analytics platform. It automatically processes trading logs (e.g., from MT5), calculates key performance indicators (KPIs) on-the-fly, and pushes updates to a modern, reactive dashboard using Server-Sent Events (SSE). It also integrates with Telegram to send instant notifications for significant trading events (like large drawdowns or profit targets).

## ✨ Features

- **Real-Time Data Streaming:** Uses Server-Sent Events (SSE) to push trade logs and calculated equity curves to the frontend instantly without polling.
- **Premium Dashboard UI:** A beautiful, responsive glassmorphism dark-theme UI designed for financial data. 
- **Live Performance Metrics:** Automatically calculates Net Profit, Win Rate, Profit Factor, and Max Drawdown in real-time.
- **Interactive Charts:** Visualizes the equity curve dynamically as new trades are ingested.
- **Instant Telegram Alerts:** Capable of sending real-time warnings to your Telegram bot (e.g., when a loss threshold is hit).
- **Fully Asynchronous:** Built top-to-bottom on a non-blocking reactive stack to handle high-throughput log streams.

---

## 🛠️ Technology Stack

This project is built using bleeding-edge technologies for maximum performance and a modern development experience:

### ⚙️ Backend
- **Java 25 LTS**
- **Spring Boot 4.1.1**
- **Spring WebFlux:** For fully non-blocking REST APIs and Server-Sent Events (SSE).
- **Spring Data R2DBC:** Reactive relational database connectivity.
- **PostgreSQL:** Primary database for storing trade records and sessions.

### 🎨 Frontend
- **Angular 22 (Zoneless):** Modern Angular using `provideZonelessChangeDetection()` and Signals for blazingly fast, reactive state management.
- **Chart.js:** For rendering the real-time equity curve.
- **Vanilla CSS:** Custom Glassmorphism UI with a premium Dark Mode aesthetic.

---

## 🚀 Getting Started

### Prerequisites
- [JDK 25](https://jdk.java.net/25/)
- [Node.js 24.15.0+](https://nodejs.org/)
- [Maven 3.9+](https://maven.apache.org/)
- [Docker](https://www.docker.com/) (For PostgreSQL)

### 1. Database Setup
Start the PostgreSQL database using the provided `docker-compose.yml` (if you have one) or run the following command:
```bash
docker run -d --name postgres-trademonitor \
  -e POSTGRES_USER=trademonitor \
  -e POSTGRES_PASSWORD=trademonitor123 \
  -e POSTGRES_DB=trademonitor_db \
  -p 5432:5432 postgres:15
```
*Note: The database schema is automatically initialized by Spring Boot on startup via `schema.sql`.*

### 2. Start the Backend
Navigate to the `backend` directory and run the Spring Boot application:
```bash
cd backend
mvn spring-boot:run
```
*The backend server will start on `http://localhost:8080`.*

### 3. Start the Frontend
Navigate to the `frontend` directory, install dependencies, and start the Angular development server:
```bash
cd frontend
npm install
npm start
```
*The frontend application will be available at `http://localhost:4200`.*

---

## 📸 Overview
Once both servers are running, access the dashboard at `http://localhost:4200`. The app will automatically connect to the Backend's SSE stream and display `CONNECTED`. Any trades added to the database will instantly appear on the UI and update the Equity Curve chart.
