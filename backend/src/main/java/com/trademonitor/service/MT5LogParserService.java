package com.trademonitor.service;

import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.trademonitor.model.TradeRecord;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Đọc file lịch sử giao dịch MT5 thành danh sách {@link TradeRecord} (chưa lưu DB).
 * <p>
 * Hỗ trợ:
 * <ul>
 *   <li>HTML "Trade History Report" của MT5 (bảng Positions + dòng balance trong bảng Deals)</li>
 *   <li>HTML báo cáo Strategy Tester (chỉ có bảng Deals: ghép lệnh in/out thành position)</li>
 *   <li>CSV (phân cách , ; hoặc tab) có dòng tiêu đề chứa tối thiểu Symbol, Type, Profit</li>
 * </ul>
 * Tự nhận diện encoding UTF-8 / UTF-16 (MT5 xuất HTML dạng UTF-16LE).
 */
@Slf4j
@Service
public class MT5LogParserService {

    public record ParseResult(List<TradeRecord> records, String format, int skippedRows) {}

    private enum TableKind { POSITIONS, DEALS }

    private record Table(TableKind kind, List<String> header, List<List<String>> rows) {}

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME);

    public ParseResult parse(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("File rỗng");
        }
        String text = decode(bytes);
        boolean html = looksLikeHtml(text);
        List<List<String>> rows = html ? htmlRows(text) : csvRows(text);
        List<Table> tables = findTables(rows);

        Optional<Table> positions = tables.stream().filter(t -> t.kind() == TableKind.POSITIONS).findFirst();
        Optional<Table> deals = tables.stream().filter(t -> t.kind() == TableKind.DEALS).findFirst();

        List<TradeRecord> records = new ArrayList<>();
        int[] skipped = {0};
        if (positions.isPresent()) {
            records.addAll(mapPositions(positions.get(), skipped));
            deals.ifPresent(d -> records.addAll(mapBalanceDeals(d)));
        } else if (deals.isPresent()) {
            records.addAll(mapDealsToPositions(deals.get(), skipped));
        } else {
            throw new IllegalArgumentException(
                    "Không tìm thấy bảng lệnh trong file. Cần báo cáo MT5 (HTML) hoặc CSV có các cột Symbol, Type, Profit.");
        }

        String format = (html ? "HTML" : "CSV") + (positions.isPresent() ? " / Positions" : " / Deals");
        log.info("Parsed {} records ({}), skipped {} rows", records.size(), format, skipped[0]);
        return new ParseResult(records, format, skipped[0]);
    }

    // ------------------------------------------------------------------ decode

    static String decode(byte[] b) {
        if (b.length >= 2 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xFE) {
            return new String(b, 2, b.length - 2, StandardCharsets.UTF_16LE);
        }
        if (b.length >= 2 && (b[0] & 0xFF) == 0xFE && (b[1] & 0xFF) == 0xFF) {
            return new String(b, 2, b.length - 2, StandardCharsets.UTF_16BE);
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB && (b[2] & 0xFF) == 0xBF) {
            return new String(b, 3, b.length - 3, StandardCharsets.UTF_8);
        }
        // UTF-16 không BOM: rất nhiều byte 0 ở vị trí lẻ (LE) hoặc chẵn (BE)
        int n = Math.min(b.length, 4000);
        int zeroOdd = 0, zeroEven = 0;
        for (int i = 0; i < n; i++) {
            if (b[i] == 0) {
                if (i % 2 == 1) zeroOdd++; else zeroEven++;
            }
        }
        if (zeroOdd > n / 4) return new String(b, StandardCharsets.UTF_16LE);
        if (zeroEven > n / 4) return new String(b, StandardCharsets.UTF_16BE);
        return new String(b, StandardCharsets.UTF_8);
    }

    private static boolean looksLikeHtml(String text) {
        String head = text.substring(0, Math.min(text.length(), 5000)).toLowerCase(Locale.ROOT);
        return head.contains("<html") || head.contains("<table") || head.contains("<!doctype");
    }

    // ------------------------------------------------------------------ rows

    private static List<List<String>> htmlRows(String html) {
        Document doc = Jsoup.parse(html);
        List<List<String>> rows = new ArrayList<>();
        for (Element tr : doc.select("tr")) {
            List<String> cells = new ArrayList<>();
            for (Element cell : tr.children()) {
                if (!cell.is("td, th") || isHidden(cell)) continue;
                cells.add(clean(cell.text()));
            }
            rows.add(cells);
        }
        return rows;
    }

    private static boolean isHidden(Element cell) {
        if (cell.hasClass("hidden")) return true;
        String style = cell.attr("style").replace(" ", "").toLowerCase(Locale.ROOT);
        return style.contains("display:none");
    }

    private static List<List<String>> csvRows(String text) {
        char sep = detectSeparator(text);
        List<List<String>> rows = new ArrayList<>();
        try (CSVReader reader = new CSVReaderBuilder(new StringReader(text))
                .withCSVParser(new CSVParserBuilder().withSeparator(sep).build())
                .build()) {
            String[] line;
            while ((line = reader.readNext()) != null) {
                List<String> cells = new ArrayList<>(line.length);
                for (String c : line) cells.add(clean(c));
                rows.add(cells);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Không đọc được file CSV: " + e.getMessage(), e);
        }
        return rows;
    }

    private static char detectSeparator(String text) {
        String firstLines = text.lines().filter(l -> !l.isBlank()).limit(5).reduce("", (a, b) -> a + b);
        char best = ',';
        long bestCount = -1;
        for (char c : new char[]{',', ';', '\t'}) {
            long count = firstLines.chars().filter(ch -> ch == c).count();
            if (count > bestCount) {
                best = c;
                bestCount = count;
            }
        }
        return best;
    }

    private static String clean(String s) {
        if (s == null) return "";
        return s.replace(' ', ' ').replace("﻿", "").trim();
    }

    // ------------------------------------------------------------------ tables

    private static List<Table> findTables(List<List<String>> rows) {
        List<Table> tables = new ArrayList<>();
        String section = "";
        int i = 0;
        while (i < rows.size()) {
            List<String> row = rows.get(i);
            if (isTitleRow(row)) {
                section = nonEmpty(row).getFirst().toLowerCase(Locale.ROOT);
                i++;
                continue;
            }
            if (isHeaderRow(row)) {
                List<String> header = row.stream().map(MT5LogParserService::norm).toList();
                List<List<String>> data = new ArrayList<>();
                int j = i + 1;
                while (j < rows.size() && !isTitleRow(rows.get(j)) && !isHeaderRow(rows.get(j))) {
                    if (!nonEmpty(rows.get(j)).isEmpty()) data.add(rows.get(j));
                    j++;
                }
                TableKind kind = classify(header, section);
                if (kind != null) tables.add(new Table(kind, header, data));
                i = j;
                continue;
            }
            i++;
        }
        return tables;
    }

    private static boolean isTitleRow(List<String> row) {
        List<String> ne = nonEmpty(row);
        return ne.size() == 1 && row.size() <= 2 && !Character.isDigit(ne.getFirst().charAt(0));
    }

    private static boolean isHeaderRow(List<String> row) {
        Set<String> n = new HashSet<>();
        for (String c : row) n.add(norm(c));
        return n.contains("symbol") && n.contains("type") && n.contains("profit");
    }

    private static TableKind classify(List<String> header, String section) {
        if (header.contains("direction")) return TableKind.DEALS;
        // Bảng "Open Positions" (lệnh đang mở) không có giờ đóng => bỏ qua
        if (section.contains("open")) return null;
        return TableKind.POSITIONS;
    }

    private static List<String> nonEmpty(List<String> row) {
        return row.stream().filter(c -> !c.isBlank()).toList();
    }

    /** "S / L" -> "sl", "Open Time" -> "opentime" */
    static String norm(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    // ------------------------------------------------------------------ column mapping

    /** Tìm cột theo danh sách tên; occurrence = 0 lấy cột đầu tiên, 1 lấy cột trùng tên thứ hai. */
    private static int col(List<String> header, int occurrence, String... names) {
        for (String name : names) {
            int seen = 0;
            for (int i = 0; i < header.size(); i++) {
                if (header.get(i).equals(name)) {
                    if (seen == occurrence) return i;
                    seen++;
                }
            }
        }
        return -1;
    }

    private static String cell(List<String> row, int idx) {
        return idx >= 0 && idx < row.size() ? row.get(idx) : "";
    }

    private List<TradeRecord> mapPositions(Table table, int[] skipped) {
        List<String> h = table.header();
        int openTime = firstNonNeg(col(h, 0, "opentime", "timeopen"), col(h, 0, "time", "date"));
        int closeTime = firstNonNeg(col(h, 0, "closetime", "timeclose"), col(h, 1, "time", "date"));
        int openPrice = firstNonNeg(col(h, 0, "openprice", "priceopen"), col(h, 0, "price"));
        int closePrice = firstNonNeg(col(h, 0, "closeprice", "priceclose"), col(h, 1, "price"));
        int ticket = col(h, 0, "position", "ticket", "order", "deal", "id");
        int symbol = col(h, 0, "symbol", "instrument", "item");
        int type = col(h, 0, "type");
        int volume = col(h, 0, "volume", "lots", "lot", "size");
        int sl = col(h, 0, "sl", "stoploss");
        int tp = col(h, 0, "tp", "takeprofit");
        int commission = col(h, 0, "commission");
        int fee = col(h, 0, "fee");
        int swap = col(h, 0, "swap");
        int profit = col(h, 0, "profit");

        List<TradeRecord> result = new ArrayList<>();
        int seq = 0;
        for (List<String> row : table.rows()) {
            String typeStr = normalizeType(cell(row, type));
            LocalDateTime ot = parseDate(cell(row, openTime));
            BigDecimal pr = parseNumber(cell(row, profit));
            if (typeStr == null || ot == null || pr == null || cell(row, symbol).isBlank()) {
                // Dòng tổng kết cuối bảng (ô đầu rỗng) không tính là lỗi
                if (!cell(row, 0).isBlank()) skipped[0]++;
                continue;
            }
            seq++;
            TradeRecord t = new TradeRecord();
            t.setTicket(orDefault(cell(row, ticket), "ROW-" + seq));
            t.setSymbol(cell(row, symbol).toUpperCase(Locale.ROOT));
            t.setType(typeStr);
            t.setVolume(orZero(parseNumber(cell(row, volume))));
            t.setOpenPrice(orZero(parseNumber(cell(row, openPrice))));
            t.setClosePrice(closePrice >= 0 ? parseNumber(cell(row, closePrice)) : null);
            t.setOpenTime(ot);
            LocalDateTime ct = closeTime >= 0 ? parseDate(cell(row, closeTime)) : null;
            t.setCloseTime(ct != null ? ct : ot);
            t.setSl(nullIfZero(parseNumber(cell(row, sl))));
            t.setTp(nullIfZero(parseNumber(cell(row, tp))));
            t.setCommission(orZero(parseNumber(cell(row, commission))).add(orZero(parseNumber(cell(row, fee)))));
            t.setSwap(orZero(parseNumber(cell(row, swap))));
            t.setProfit(pr);
            result.add(t);
        }
        return result;
    }

    private record Deal(String ticket, LocalDateTime time, String symbol, String type, String direction,
                        BigDecimal volume, BigDecimal price, BigDecimal commission, BigDecimal swap,
                        BigDecimal profit, String position) {}

    private List<Deal> readDeals(Table table) {
        List<String> h = table.header();
        int time = col(h, 0, "time", "date");
        int deal = col(h, 0, "deal", "ticket");
        int symbol = col(h, 0, "symbol");
        int type = col(h, 0, "type");
        int direction = col(h, 0, "direction");
        int volume = col(h, 0, "volume");
        int price = col(h, 0, "price");
        int commission = col(h, 0, "commission");
        int fee = col(h, 0, "fee");
        int swap = col(h, 0, "swap");
        int profit = col(h, 0, "profit");
        int position = col(h, 0, "position", "order");

        List<Deal> deals = new ArrayList<>();
        for (List<String> row : table.rows()) {
            LocalDateTime t = parseDate(cell(row, time));
            if (t == null) continue;
            deals.add(new Deal(cell(row, deal), t, cell(row, symbol).toUpperCase(Locale.ROOT),
                    cell(row, type).toLowerCase(Locale.ROOT), cell(row, direction).toLowerCase(Locale.ROOT),
                    orZero(parseNumber(cell(row, volume))), orZero(parseNumber(cell(row, price))),
                    orZero(parseNumber(cell(row, commission))).add(orZero(parseNumber(cell(row, fee)))),
                    orZero(parseNumber(cell(row, swap))), orZero(parseNumber(cell(row, profit))),
                    cell(row, position)));
        }
        return deals;
    }

    private List<TradeRecord> mapBalanceDeals(Table table) {
        return readDeals(table).stream()
                .filter(d -> isBalanceType(d.type()))
                .map(MT5LogParserService::balanceRecord)
                .toList();
    }

    /**
     * Báo cáo Strategy Tester chỉ có bảng Deals: ghép deal "in" với deal "out" (FIFO theo symbol)
     * để tạo position đã đóng.
     */
    private List<TradeRecord> mapDealsToPositions(Table table, int[] skipped) {
        List<TradeRecord> result = new ArrayList<>();
        Map<String, Deque<OpenLeg>> open = new HashMap<>();

        for (Deal d : readDeals(table)) {
            if (isBalanceType(d.type())) {
                result.add(balanceRecord(d));
                continue;
            }
            String side = normalizeType(d.type());
            if (side == null) {
                skipped[0]++;
                continue;
            }
            if (d.direction().equals("in")) {
                open.computeIfAbsent(d.symbol(), k -> new ArrayDeque<>()).add(new OpenLeg(d));
                continue;
            }
            if (!d.direction().startsWith("out") && !d.direction().equals("in/out")) {
                skipped[0]++;
                continue;
            }
            OpenLeg leg = Optional.ofNullable(open.get(d.symbol())).map(Deque::peekFirst).orElse(null);
            TradeRecord t = new TradeRecord();
            t.setTicket(orDefault(d.position(), d.ticket()));
            t.setSymbol(d.symbol());
            t.setVolume(d.volume());
            t.setClosePrice(d.price());
            t.setCloseTime(d.time());
            t.setSwap(d.swap());
            t.setProfit(d.profit());
            if (leg != null) {
                t.setType(normalizeType(leg.deal.type()));
                t.setOpenTime(leg.deal.time());
                t.setOpenPrice(leg.deal.price());
                // phí của deal "in" chỉ tính một lần cho lần đóng đầu tiên
                t.setCommission(d.commission().add(leg.commissionPending ? leg.deal.commission() : BigDecimal.ZERO));
                leg.commissionPending = false;
                leg.remaining = leg.remaining.subtract(d.volume());
                if (leg.remaining.signum() <= 0) open.get(d.symbol()).pollFirst();
            } else {
                // không tìm được lệnh mở: đóng lệnh BUY là deal SELL và ngược lại
                t.setType(TradeRecord.BUY.equals(side) ? TradeRecord.SELL : TradeRecord.BUY);
                t.setOpenTime(d.time());
                t.setOpenPrice(d.price());
                t.setCommission(d.commission());
            }
            result.add(t);
        }
        return result;
    }

    private static final class OpenLeg {
        final Deal deal;
        BigDecimal remaining;
        boolean commissionPending = true;

        OpenLeg(Deal deal) {
            this.deal = deal;
            this.remaining = deal.volume();
        }
    }

    private static TradeRecord balanceRecord(Deal d) {
        TradeRecord t = new TradeRecord();
        t.setTicket(orDefault(d.ticket(), "BAL-" + d.time()));
        t.setSymbol("-");
        t.setType(TradeRecord.BALANCE);
        t.setVolume(BigDecimal.ZERO);
        t.setOpenPrice(BigDecimal.ZERO);
        t.setOpenTime(d.time());
        t.setCloseTime(d.time());
        t.setCommission(BigDecimal.ZERO);
        t.setSwap(BigDecimal.ZERO);
        t.setProfit(d.profit());
        return t;
    }

    private static boolean isBalanceType(String type) {
        String t = type.toLowerCase(Locale.ROOT);
        return t.equals("balance") || t.equals("credit") || t.equals("deposit");
    }

    // ------------------------------------------------------------------ value parsing

    /** "buy", "Buy Limit", "sell stop" -> BUY/SELL; loại khác trả null. */
    static String normalizeType(String raw) {
        if (raw == null) return null;
        String t = raw.trim().toLowerCase(Locale.ROOT);
        if (t.startsWith("buy")) return TradeRecord.BUY;
        if (t.startsWith("sell")) return TradeRecord.SELL;
        return null;
    }

    static LocalDateTime parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim().replaceAll("\\s+", " ");
        if (s.length() > 19 && s.charAt(10) == ' ' && s.charAt(19) == '.') {
            s = s.substring(0, 19); // bỏ phần mili-giây "2024.01.02 10:00:00.123"
        }
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                return LocalDateTime.parse(s, f);
            } catch (DateTimeParseException ignored) {
                // thử định dạng tiếp theo
            }
        }
        return null;
    }

    /** Hỗ trợ "1 234.56", "1,234.56", "1234,56", "0.1 / 0.1". */
    static BigDecimal parseNumber(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        int slash = s.indexOf('/');
        if (slash > 0) s = s.substring(0, slash).trim();
        s = s.replace(" ", "").replace(" ", "");
        if (s.isEmpty() || s.equals("-")) return null;
        if (s.contains(",") && s.contains(".")) {
            s = s.replace(",", "");
        } else if (s.contains(",")) {
            s = s.replace(",", ".");
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int firstNonNeg(int a, int b) {
        return a >= 0 ? a : b;
    }

    private static BigDecimal orZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal nullIfZero(BigDecimal v) {
        return v == null || v.signum() == 0 ? null : v;
    }

    private static String orDefault(String v, String def) {
        return v == null || v.isBlank() ? def : v;
    }
}
