package com.trademonitor.controller;

import com.trademonitor.model.TradeRecord;
import com.trademonitor.model.TradeSession;
import com.trademonitor.model.dto.PerformanceMetrics;
import com.trademonitor.repository.TradeRecordRepository;
import com.trademonitor.repository.TradeSessionRepository;
import com.trademonitor.service.AnalyticsService;
import com.trademonitor.service.TradeEventBus;
import com.trademonitor.service.TradeImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Comparator;
import java.util.Locale;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TradeLogController {

    private static final Comparator<TradeRecord> NEWEST_FIRST = Comparator
            .comparing(TradeRecord::effectiveTime, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(TradeRecord::getId, Comparator.nullsLast(Comparator.reverseOrder()));

    private final TradeRecordRepository tradeRecordRepository;
    private final TradeSessionRepository tradeSessionRepository;
    private final TradeImportService importService;
    private final AnalyticsService analyticsService;
    private final TradeEventBus eventBus;

    // ------------------------------------------------------------------ trades

    /** Danh sách lệnh, mới nhất trước. */
    @GetMapping("/trades")
    public Flux<TradeRecord> listTrades(@RequestParam(required = false) Long sessionId,
                                        @RequestParam(required = false) String symbol,
                                        @RequestParam(defaultValue = "1000") int limit) {
        Flux<TradeRecord> source = sessionId == null
                ? tradeRecordRepository.findAll()
                : tradeRecordRepository.findBySessionIdOrderByOpenTimeAsc(sessionId);
        String sym = symbol == null || symbol.isBlank() ? null : symbol.trim().toUpperCase(Locale.ROOT);
        return source
                .filter(t -> sym == null || sym.equals(t.getSymbol()))
                .sort(NEWEST_FIRST)
                .take(Math.max(1, Math.min(limit, 10000)));
    }

    @PostMapping("/trades")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<TradeRecord> addTrade(@RequestBody TradeRecord trade) {
        return importService.addManualTrade(trade);
    }

    @DeleteMapping("/trades/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteTrade(@PathVariable Long id) {
        return importService.deleteTrade(id);
    }

    @PostMapping(value = "/trades/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<TradeSession> uploadLogFile(@RequestPart("file") Mono<FilePart> filePartMono) {
        return filePartMono.flatMap(importService::importFile);
    }

    @PostMapping("/demo/seed")
    public Mono<TradeSession> seedDemo(@RequestParam(defaultValue = "50") int count) {
        return importService.seedDemo(count);
    }

    /**
     * Luồng SSE: chỉ đẩy sự kiện MỚI (event "trade" / "refresh") + ping mỗi 15s để giữ kết nối.
     * Dữ liệu hiện có client lấy qua GET /api/trades.
     */
    @GetMapping(value = "/trades/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> streamTrades() {
        Flux<ServerSentEvent<Object>> hello = Flux.just(
                ServerSentEvent.builder((Object) "connected").event("hello").build());
        Flux<ServerSentEvent<Object>> events = eventBus.events()
                .map(e -> ServerSentEvent.builder(e.payload()).event(e.type()).build());
        Flux<ServerSentEvent<Object>> heartbeat = Flux.interval(Duration.ofSeconds(15))
                .map(i -> ServerSentEvent.builder().comment("ping").build());
        return hello.concatWith(Flux.merge(events, heartbeat));
    }

    // ------------------------------------------------------------------ sessions

    @GetMapping("/sessions")
    public Flux<TradeSession> listSessions() {
        return tradeSessionRepository.findAllByOrderByImportTimeDesc();
    }

    @DeleteMapping("/sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteSession(@PathVariable Long id) {
        return importService.deleteSession(id);
    }

    // ------------------------------------------------------------------ analytics

    @GetMapping("/analytics/metrics")
    public Mono<PerformanceMetrics> getMetrics(@RequestParam(required = false) Long sessionId) {
        return analyticsService.calculateMetrics(sessionId);
    }
}
