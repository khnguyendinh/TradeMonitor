package com.trademonitor.service;

import com.trademonitor.model.TradeRecord;
import com.trademonitor.model.dto.PerformanceMetrics;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnalyticsServiceTest {

    private static TradeRecord trade(long id, String symbol, String type, String profit, String commission, int hour) {
        TradeRecord t = new TradeRecord();
        t.setId(id);
        t.setTicket(String.valueOf(id));
        t.setSymbol(symbol);
        t.setType(type);
        t.setVolume(BigDecimal.ONE);
        t.setOpenPrice(BigDecimal.ONE);
        t.setOpenTime(LocalDateTime.of(2024, 1, 1, hour, 0));
        t.setCloseTime(LocalDateTime.of(2024, 1, 1, hour, 30));
        t.setProfit(new BigDecimal(profit));
        t.setCommission(new BigDecimal(commission));
        t.setSwap(BigDecimal.ZERO);
        return t;
    }

    @Test
    void computesKpisWithCommissionAndDrawdown() {
        List<TradeRecord> trades = List.of(
                trade(1, "EURUSD", "BUY", "100", "-5", 1),   // +95  -> 1095
                trade(2, "EURUSD", "SELL", "-200", "-5", 2), // -205 -> 890
                trade(3, "XAUUSD", "BUY", "50", "0", 3));    // +50  -> 940

        PerformanceMetrics m = AnalyticsService.compute(trades, new BigDecimal("1000"));

        assertEquals(3, m.getTotalTrades());
        assertEquals(2, m.getWinningTrades());
        assertEquals(1, m.getLosingTrades());
        assertEquals(66.67, m.getWinRate(), 0.001);
        assertEquals(0, new BigDecimal("-60").compareTo(m.getNetProfit()));
        assertEquals(0, new BigDecimal("940").compareTo(m.getCurrentBalance()));
        assertEquals(0, new BigDecimal("-10").compareTo(m.getTotalCommission()));
        assertEquals(0.71, m.getProfitFactor(), 0.001); // 145 / 205
        assertEquals(0, new BigDecimal("205").compareTo(m.getMaxDrawdownAmount()));
        assertEquals(18.72, m.getMaxDrawdownPercentage(), 0.01); // 205 / 1095
        assertEquals(4, m.getEquityCurve().size(), "điểm khởi đầu + 3 lệnh");
        assertEquals("XAUUSD", m.getSymbolStats().getFirst().symbol());
    }

    @Test
    void balanceRecordsAreInitialBalanceNotTrades() {
        TradeRecord deposit = trade(1, "-", "BALANCE", "5000", "0", 0);
        PerformanceMetrics m = AnalyticsService.compute(
                List.of(deposit, trade(2, "EURUSD", "BUY", "100", "0", 1)), new BigDecimal("1000"));

        assertEquals(1, m.getTotalTrades());
        assertEquals(0, new BigDecimal("5000").compareTo(m.getInitialBalance()));
        assertEquals(0, new BigDecimal("5100").compareTo(m.getCurrentBalance()));
        assertNull(m.getProfitFactor(), "không có lệnh lỗ => vô hạn");
    }

    @Test
    void emptyInput() {
        PerformanceMetrics m = AnalyticsService.compute(List.of(), new BigDecimal("1000"));
        assertEquals(0, m.getTotalTrades());
        assertEquals(0.0, m.getProfitFactor());
        assertTrue(m.getEquityCurve().isEmpty());
    }
}
