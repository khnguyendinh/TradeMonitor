package com.trademonitor.service;

import com.trademonitor.model.TradeRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MT5LogParserServiceTest {

    private final MT5LogParserService parser = new MT5LogParserService();

    /** Rút gọn từ cấu trúc "Trade History Report" thật của MT5. */
    private static final String MT5_HTML = """
            <html><body><table>
            <tr><th colspan="14"><div><b>Trade History Report</b></div></th></tr>
            <tr><td colspan="3">Name:</td><td colspan="10"><b>Demo Account</b></td></tr>
            <tr><th colspan="14"><div><b>Positions</b></div></th></tr>
            <tr>
              <td>Time</td><td>Position</td><td>Symbol</td><td>Type</td><td class="hidden" colspan="8"></td>
              <td>Volume</td><td>Price</td><td>S / L</td><td>T / P</td><td>Time</td><td>Price</td>
              <td>Commission</td><td>Swap</td><td>Profit</td>
            </tr>
            <tr>
              <td>2024.01.02 10:00:00</td><td>1001</td><td>EURUSD</td><td>buy</td><td class="hidden" colspan="8"></td>
              <td>0.1</td><td>1.10000</td><td></td><td>1.10500</td><td>2024.01.02 12:30:00</td><td>1.10500</td>
              <td>-0.70</td><td>0.00</td><td>50.00</td>
            </tr>
            <tr>
              <td>2024.01.03 09:15:00</td><td>1002</td><td>XAUUSD</td><td>sell</td><td class="hidden" colspan="8"></td>
              <td>1</td><td>2 050.10</td><td>2 060.00</td><td></td><td>2024.01.03 11:00:00</td><td>2 060.00</td>
              <td>-7.00</td><td>-1.50</td><td>-990.00</td>
            </tr>
            <tr><td colspan="10"></td><td>-7.70</td><td>-1.50</td><td>-940.00</td></tr>
            <tr><th colspan="14"><div><b>Orders</b></div></th></tr>
            <tr><td>Open Time</td><td>Order</td><td>Symbol</td><td>Type</td><td>Volume</td><td>Price</td><td>State</td></tr>
            <tr><td>2024.01.02 10:00:00</td><td>1001</td><td>EURUSD</td><td>buy</td><td>0.1 / 0.1</td><td>1.1</td><td>filled</td></tr>
            <tr><th colspan="14"><div><b>Deals</b></div></th></tr>
            <tr><td>Time</td><td>Deal</td><td>Symbol</td><td>Type</td><td>Direction</td><td>Volume</td><td>Price</td>
                <td>Order</td><td>Commission</td><td>Fee</td><td>Swap</td><td>Profit</td><td>Balance</td><td>Comment</td></tr>
            <tr><td>2024.01.01 08:00:00</td><td>1</td><td></td><td>balance</td><td></td><td></td><td></td>
                <td></td><td>0.00</td><td>0.00</td><td>0.00</td><td>5 000.00</td><td>5 000.00</td><td>Deposit</td></tr>
            </table></body></html>
            """;

    @Test
    void parsesMt5HtmlPositionsAndBalance() {
        var result = parser.parse(MT5_HTML.getBytes(StandardCharsets.UTF_8));
        List<TradeRecord> r = result.records();

        assertEquals(3, r.size(), "2 positions + 1 balance");
        assertEquals(0, result.skippedRows());

        TradeRecord first = r.get(0);
        assertEquals("1001", first.getTicket());
        assertEquals("EURUSD", first.getSymbol());
        assertEquals("BUY", first.getType());
        assertEquals(0, new BigDecimal("0.1").compareTo(first.getVolume()));
        assertEquals(LocalDateTime.of(2024, 1, 2, 10, 0), first.getOpenTime());
        assertEquals(LocalDateTime.of(2024, 1, 2, 12, 30), first.getCloseTime());
        assertEquals(0, new BigDecimal("1.10500").compareTo(first.getTp()));
        assertNull(first.getSl());
        assertEquals(0, new BigDecimal("50.00").compareTo(first.getProfit()));

        TradeRecord second = r.get(1);
        assertEquals("SELL", second.getType());
        assertEquals(0, new BigDecimal("2050.10").compareTo(second.getOpenPrice()));
        assertEquals(0, new BigDecimal("-990.00").compareTo(second.getProfit()));

        TradeRecord balance = r.get(2);
        assertEquals("BALANCE", balance.getType());
        assertEquals(0, new BigDecimal("5000.00").compareTo(balance.getProfit()));
    }

    @Test
    void parsesUtf16LeWithBom() {
        byte[] body = MT5_HTML.getBytes(StandardCharsets.UTF_16LE);
        byte[] bytes = new byte[body.length + 2];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xFE;
        System.arraycopy(body, 0, bytes, 2, body.length);

        assertEquals(3, parser.parse(bytes).records().size());
    }

    @Test
    void parsesSemicolonCsvWithDecimalComma() {
        String csv = """
                Open Time;Ticket;Symbol;Type;Lots;Open Price;Close Time;Close Price;Commission;Swap;Profit
                02.01.2024 10:00;5001;gbpusd;Buy;0,50;1,27000;02.01.2024 11:00;1,27200;-3,5;0;100,00
                02.01.2024 12:00;5002;GBPUSD;Sell Limit;0,50;1,27300;02.01.2024 13:00;1,27400;-3,5;0;-50,00
                """;
        var r = parser.parse(csv.getBytes(StandardCharsets.UTF_8)).records();

        assertEquals(2, r.size());
        assertEquals("GBPUSD", r.get(0).getSymbol());
        assertEquals(0, new BigDecimal("0.50").compareTo(r.get(0).getVolume()));
        assertEquals(LocalDateTime.of(2024, 1, 2, 11, 0), r.get(0).getCloseTime());
        assertEquals("SELL", r.get(1).getType());
        assertEquals(0, new BigDecimal("-50").compareTo(r.get(1).getProfit()));
    }

    @Test
    void buildsPositionsFromTesterDeals() {
        String html = """
                <table>
                <tr><th colspan="13">Deals</th></tr>
                <tr><td>Time</td><td>Deal</td><td>Symbol</td><td>Type</td><td>Direction</td><td>Volume</td>
                    <td>Price</td><td>Order</td><td>Commission</td><td>Swap</td><td>Profit</td><td>Balance</td><td>Comment</td></tr>
                <tr><td>2024.01.01 00:00:00</td><td>1</td><td></td><td>balance</td><td></td><td></td><td></td>
                    <td></td><td>0.00</td><td>0.00</td><td>10 000.00</td><td>10 000.00</td><td></td></tr>
                <tr><td>2024.01.02 10:00:00</td><td>2</td><td>EURUSD</td><td>buy</td><td>in</td><td>1.00</td>
                    <td>1.10000</td><td>2</td><td>-3.50</td><td>0.00</td><td>0.00</td><td>9 996.50</td><td></td></tr>
                <tr><td>2024.01.02 15:00:00</td><td>3</td><td>EURUSD</td><td>sell</td><td>out</td><td>1.00</td>
                    <td>1.10200</td><td>3</td><td>-3.50</td><td>0.00</td><td>200.00</td><td>10 193.00</td><td>tp</td></tr>
                </table>
                """;
        var r = parser.parse(html.getBytes(StandardCharsets.UTF_8)).records();

        assertEquals(2, r.size());
        TradeRecord trade = r.stream().filter(TradeRecord::isPosition).findFirst().orElseThrow();
        assertEquals("BUY", trade.getType());
        assertEquals(LocalDateTime.of(2024, 1, 2, 10, 0), trade.getOpenTime());
        assertEquals(LocalDateTime.of(2024, 1, 2, 15, 0), trade.getCloseTime());
        assertEquals(0, new BigDecimal("-7.00").compareTo(trade.getCommission()));
        assertEquals(0, new BigDecimal("200.00").compareTo(trade.getProfit()));
    }

    @Test
    void rejectsFileWithoutTradeTable() {
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse("hello,world\n1,2".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void parseNumberFormats() {
        assertEquals(0, new BigDecimal("1234.56").compareTo(MT5LogParserService.parseNumber("1 234.56")));
        assertEquals(0, new BigDecimal("1234.56").compareTo(MT5LogParserService.parseNumber("1,234.56")));
        assertEquals(0, new BigDecimal("12.5").compareTo(MT5LogParserService.parseNumber("12,5")));
        assertEquals(0, new BigDecimal("0.1").compareTo(MT5LogParserService.parseNumber("0.1 / 0.1")));
        assertNull(MT5LogParserService.parseNumber(""));
    }
}
