package com.trademonitor.controller;

import com.trademonitor.model.dto.AppSettingsDto;
import com.trademonitor.service.SettingsService;
import com.trademonitor.service.TelegramNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;
    private final TelegramNotificationService telegramService;

    @GetMapping("/settings")
    public Mono<AppSettingsDto> getSettings() {
        return settingsService.getDto();
    }

    @PutMapping("/settings")
    public Mono<AppSettingsDto> updateSettings(@RequestBody AppSettingsDto dto) {
        return settingsService.update(dto);
    }

    @PostMapping("/telegram/test")
    public Mono<Map<String, String>> testTelegram(@RequestBody(required = false) Map<String, String> payload) {
        String message = payload == null || payload.getOrDefault("message", "").isBlank()
                ? "Test alert from TradeMonitor! 🚀"
                : payload.get("message");
        return telegramService.sendAlert(TelegramNotificationService.escapeHtml(message))
                .thenReturn(Map.of("message", "Đã gửi tin nhắn Telegram"));
    }
}
