package com.trademonitor.controller;

import com.trademonitor.model.TradeRecord;
import com.trademonitor.model.dto.PerformanceMetrics;
import com.trademonitor.repository.TradeRecordRepository;
import com.trademonitor.service.AnalyticsService;
import com.trademonitor.service.MT5LogParserService;
import com.trademonitor.service.TelegramNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TradeLogController {

    private final TradeRecordRepository tradeRecordRepository;
    private final MT5LogParserService parserService;
    private final AnalyticsService analyticsService;
    private final TelegramNotificationService telegramService;

    @PostMapping(value = "/trades/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<String>> uploadLogFile(@RequestPart("file") Mono<FilePart> filePartMono) {
        return filePartMono
                .flatMap(parserService::parseAndSave)
                .map(session -> ResponseEntity.ok("File uploaded and processing started: " + session.getId()));
    }

    @GetMapping(value = "/trades/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<TradeRecord> streamTrades() {
        return tradeRecordRepository.findAllByOrderByOpenTimeAsc();
    }
    
    @GetMapping("/analytics/metrics")
    public Mono<PerformanceMetrics> getMetrics() {
        return analyticsService.calculateMetrics();
    }
    
    @PostMapping("/telegram/test")
    public Mono<ResponseEntity<String>> testTelegram(@RequestBody Map<String, String> payload) {
        String message = payload.getOrDefault("message", "Test alert from TradeMonitor! 🚀");
        return telegramService.sendAlert(message)
                .thenReturn(ResponseEntity.ok("Alert sent!"));
    }
}
