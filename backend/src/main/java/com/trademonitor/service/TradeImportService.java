package com.trademonitor.service;

import com.trademonitor.model.TradeRecord;
import com.trademonitor.model.TradeSession;
import com.trademonitor.model.dto.StreamEvent;
import com.trademonitor.repository.TradeRecordRepository;
import com.trademonitor.repository.TradeSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Nghiệp vụ ghi dữ liệu giao dịch: import file, nhập tay, sinh dữ liệu demo, xoá phiên.
 * Mọi thay đổi đều phát sự kiện SSE và kiểm tra cảnh báo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradeImportService {

    public static final String MANUAL_SESSION = "Nhập tay";
    public static final String DEMO_SESSION = "Dữ liệu demo";

    private static final int IN_CHUNK = 500;

    private final TradeRecordRepository tradeRepo;
    private final TradeSessionRepository sessionRepo;
    private final MT5LogParserService parser;
    private final TradeEventBus eventBus;
    private final AlertService alertService;

    // ------------------------------------------------------------------ import file

    public Mono<TradeSession> importFile(FilePart filePart) {
        return DataBufferUtils.join(filePart.content())
                .map(buf -> {
                    byte[] bytes = new byte[buf.readableByteCount()];
                    buf.read(bytes);
                    DataBufferUtils.release(buf);
                    return bytes;
                })
                .switchIfEmpty(Mono.error(new IllegalArgumentException("File rỗng")))
                .flatMap(bytes -> importBytes(filePart.filename(), bytes));
    }

    public Mono<TradeSession> importBytes(String fileName, byte[] bytes) {
        MT5LogParserService.ParseResult parsed;
        try {
            parsed = parser.parse(bytes);
        } catch (IllegalArgumentException e) {
            return Mono.error(e);
        } catch (Exception e) {
            log.error("Parse error", e);
            return Mono.error(new IllegalArgumentException("Không đọc được file: " + e.getMessage()));
        }
        if (parsed.records().isEmpty()) {
            return Mono.error(new IllegalArgumentException("File không có lệnh nào đã đóng để import"));
        }

        TradeSession session = new TradeSession();
        session.setName(fileName == null || fileName.isBlank() ? "upload" : fileName);
        session.setImportTime(LocalDateTime.now());
        session.setStatus(TradeSession.PROCESSING);

        return sessionRepo.save(session).flatMap(saved ->
                removeDuplicates(parsed.records())
                        .flatMap(fresh -> {
                            fresh.forEach(r -> r.setSessionId(saved.getId()));
                            return tradeRepo.saveAll(fresh).collectList();
                        })
                        .flatMap(stored -> {
                            int duplicates = parsed.records().size() - stored.size();
                            saved.setStatus(TradeSession.COMPLETED);
                            saved.setTradeCount(stored.size());
                            saved.setMessage(String.format("%s: lưu %d dòng, bỏ qua %d dòng trùng, %d dòng không hợp lệ",
                                    parsed.format(), stored.size(), duplicates, parsed.skippedRows()));
                            return sessionRepo.save(saved)
                                    .doOnSuccess(s -> {
                                        eventBus.publish(StreamEvent.refresh("import"));
                                        fireAndForget(alertService.importSummary(s.getName(), stored.size(), duplicates, stored)
                                                .then(alertService.onTradesAdded(stored)));
                                    });
                        })
                        .onErrorResume(e -> {
                            log.error("Import failed", e);
                            saved.setStatus(TradeSession.FAILED);
                            saved.setMessage(truncate("Lỗi khi lưu: " + e.getMessage(), 1000));
                            return sessionRepo.save(saved)
                                    .then(Mono.error(new IllegalStateException(saved.getMessage())));
                        }));
    }

    /** Bỏ các lệnh đã có trong DB (cùng ticket + loại) và trùng lặp ngay trong file. */
    private Mono<List<TradeRecord>> removeDuplicates(List<TradeRecord> records) {
        List<String> tickets = records.stream().map(TradeRecord::getTicket).distinct().toList();
        List<List<String>> chunks = new ArrayList<>();
        for (int i = 0; i < tickets.size(); i += IN_CHUNK) {
            chunks.add(tickets.subList(i, Math.min(i + IN_CHUNK, tickets.size())));
        }
        return Flux.fromIterable(chunks)
                .concatMap(tradeRepo::findByTicketIn)
                .map(TradeImportService::dedupKey)
                .collect(HashSet<String>::new, Set::add)
                .map(existing -> {
                    List<TradeRecord> fresh = new ArrayList<>();
                    for (TradeRecord r : records) {
                        if (existing.add(dedupKey(r))) fresh.add(r);
                    }
                    return fresh;
                });
    }

    private static String dedupKey(TradeRecord r) {
        return r.getTicket() + "|" + r.getType() + "|" + r.getCloseTime();
    }

    // ------------------------------------------------------------------ manual trade

    public Mono<TradeRecord> addManualTrade(TradeRecord input) {
        TradeRecord t = validateManual(input);
        return findOrCreateSession(MANUAL_SESSION)
                .flatMap(session -> {
                    t.setSessionId(session.getId());
                    return tradeRepo.save(t).flatMap(saved -> {
                        session.setTradeCount(session.getTradeCount() + 1);
                        return sessionRepo.save(session).thenReturn(saved);
                    });
                })
                .doOnSuccess(saved -> {
                    eventBus.publish(StreamEvent.trade(saved));
                    fireAndForget(alertService.onTradesAdded(List.of(saved)));
                });
    }

    private static TradeRecord validateManual(TradeRecord in) {
        if (in == null) throw new IllegalArgumentException("Thiếu dữ liệu lệnh");
        if (in.getSymbol() == null || in.getSymbol().isBlank()) {
            throw new IllegalArgumentException("Symbol không được để trống");
        }
        String type = MT5LogParserService.normalizeType(in.getType());
        if (type == null) throw new IllegalArgumentException("Type phải là BUY hoặc SELL");
        if (in.getVolume() == null || in.getVolume().signum() <= 0) {
            throw new IllegalArgumentException("Volume phải lớn hơn 0");
        }
        if (in.getProfit() == null) throw new IllegalArgumentException("Profit không được để trống");

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        TradeRecord t = new TradeRecord();
        t.setTicket(in.getTicket() == null || in.getTicket().isBlank() ? "M" + System.currentTimeMillis() : in.getTicket().trim());
        t.setSymbol(in.getSymbol().trim().toUpperCase(Locale.ROOT));
        t.setType(type);
        t.setVolume(in.getVolume());
        t.setOpenPrice(in.getOpenPrice() == null ? BigDecimal.ZERO : in.getOpenPrice());
        t.setClosePrice(in.getClosePrice());
        t.setOpenTime(in.getOpenTime() == null ? now : in.getOpenTime());
        t.setCloseTime(in.getCloseTime() == null ? now : in.getCloseTime());
        if (t.getCloseTime().isBefore(t.getOpenTime())) {
            throw new IllegalArgumentException("Thời gian đóng lệnh phải sau thời gian mở lệnh");
        }
        t.setSl(in.getSl());
        t.setTp(in.getTp());
        t.setCommission(in.getCommission() == null ? BigDecimal.ZERO : in.getCommission());
        t.setSwap(in.getSwap() == null ? BigDecimal.ZERO : in.getSwap());
        t.setProfit(in.getProfit());
        return t;
    }

    // ------------------------------------------------------------------ demo data

    private static final String[][] DEMO_SYMBOLS = {
            {"EURUSD", "1.08500", "0.00001", "100000"},
            {"GBPUSD", "1.27000", "0.00001", "100000"},
            {"USDJPY", "150.000", "0.001", "666"},
            {"XAUUSD", "2350.00", "0.01", "100"},
            {"BTCUSD", "105000.00", "0.01", "1"},
    };

    public Mono<TradeSession> seedDemo(int count) {
        int n = Math.max(1, Math.min(count, 1000));
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        LocalDateTime time = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES).minusHours(n * 6L);
        long baseTicket = System.currentTimeMillis() / 1000;

        List<TradeRecord> trades = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String[] s = DEMO_SYMBOLS[rnd.nextInt(DEMO_SYMBOLS.length)];
            BigDecimal price = new BigDecimal(s[1]).multiply(BigDecimal.valueOf(1 + rnd.nextGaussian() * 0.01));
            int scale = new BigDecimal(s[2]).scale();
            BigDecimal volume = BigDecimal.valueOf(rnd.nextInt(1, 21)).divide(BigDecimal.TEN, 2, RoundingMode.HALF_UP);
            boolean buy = rnd.nextBoolean();
            // ~55% lệnh thắng, lãi/lỗ tỷ lệ với volume
            double r = rnd.nextDouble() < 0.55 ? rnd.nextDouble(0.3, 2.0) : -rnd.nextDouble(0.3, 1.6);
            BigDecimal profit = BigDecimal.valueOf(r * 150).multiply(volume).setScale(2, RoundingMode.HALF_UP);
            BigDecimal move = profit.divide(volume.multiply(new BigDecimal(s[3])), scale, RoundingMode.HALF_UP);

            time = time.plusMinutes(rnd.nextInt(30, 600));
            TradeRecord t = new TradeRecord();
            t.setTicket(String.valueOf(baseTicket + i));
            t.setSymbol(s[0]);
            t.setType(buy ? TradeRecord.BUY : TradeRecord.SELL);
            t.setVolume(volume);
            t.setOpenPrice(price.setScale(scale, RoundingMode.HALF_UP));
            t.setClosePrice(price.add(buy ? move : move.negate()).setScale(scale, RoundingMode.HALF_UP));
            t.setOpenTime(time);
            time = time.plusMinutes(rnd.nextInt(5, 480));
            t.setCloseTime(time);
            t.setCommission(volume.multiply(BigDecimal.valueOf(-7)).setScale(2, RoundingMode.HALF_UP));
            t.setSwap(rnd.nextInt(4) == 0 ? BigDecimal.valueOf(-rnd.nextInt(1, 10)) : BigDecimal.ZERO);
            t.setProfit(profit);
            trades.add(t);
        }

        // Dời toàn bộ mốc thời gian để lệnh cuối cùng đóng đúng thời điểm hiện tại (không có lệnh ở tương lai)
        long shiftMinutes = ChronoUnit.MINUTES.between(time, LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES));
        trades.forEach(t -> {
            t.setOpenTime(t.getOpenTime().plusMinutes(shiftMinutes));
            t.setCloseTime(t.getCloseTime().plusMinutes(shiftMinutes));
        });

        return findOrCreateSession(DEMO_SESSION).flatMap(session -> {
            trades.forEach(t -> t.setSessionId(session.getId()));
            return tradeRepo.saveAll(trades).collectList().flatMap(saved -> {
                session.setTradeCount(session.getTradeCount() + saved.size());
                session.setMessage("Sinh ngẫu nhiên để thử giao diện");
                return sessionRepo.save(session)
                        .doOnSuccess(x -> {
                            eventBus.publish(StreamEvent.refresh("demo"));
                            fireAndForget(alertService.onTradesAdded(saved));
                        });
            });
        });
    }

    // ------------------------------------------------------------------ sessions

    public Mono<Void> deleteSession(Long id) {
        return sessionRepo.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Không tìm thấy phiên #" + id)))
                // Xoá lệnh trước (không phụ thuộc ON DELETE CASCADE)
                .flatMap(s -> tradeRepo.findBySessionIdOrderByOpenTimeAsc(id)
                        .collectList()
                        .flatMap(tradeRepo::deleteAll)
                        .then(sessionRepo.delete(s)))
                .doOnSuccess(v -> eventBus.publish(StreamEvent.refresh("delete-session")));
    }

    public Mono<Void> deleteTrade(Long id) {
        return tradeRepo.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Không tìm thấy lệnh #" + id)))
                .flatMap(t -> tradeRepo.delete(t)
                        .then(sessionRepo.findById(t.getSessionId()))
                        .flatMap(s -> {
                            s.setTradeCount(Math.max(0, s.getTradeCount() - 1));
                            return sessionRepo.save(s);
                        }))
                .then()
                .doOnSuccess(v -> eventBus.publish(StreamEvent.refresh("delete-trade")));
    }

    private Mono<TradeSession> findOrCreateSession(String name) {
        return sessionRepo.findFirstByName(name).switchIfEmpty(Mono.defer(() -> {
            TradeSession s = new TradeSession();
            s.setName(name);
            s.setImportTime(LocalDateTime.now());
            s.setStatus(TradeSession.COMPLETED);
            return sessionRepo.save(s);
        }));
    }

    /** Cảnh báo Telegram chạy nền, không làm chậm response API. */
    private static void fireAndForget(Mono<Void> task) {
        task.subscribe(null, e -> log.warn("Alert task failed: {}", e.getMessage()));
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
