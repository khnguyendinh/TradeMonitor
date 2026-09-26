package com.trademonitor.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class TelegramNotificationService {

    private final SettingsService settingsService;
    private final WebClient webClient = WebClient.create("https://api.telegram.org");

    /**
     * Gửi tin nhắn; lỗi được trả về cho caller (dùng cho nút "Gửi thử").
     */
    public Mono<Void> sendAlert(String message) {
        return settingsService.get().flatMap(s -> {
            if (!s.telegramConfigured()) {
                return Mono.error(new IllegalStateException(
                        "Chưa cấu hình Telegram Bot Token / Chat ID (vào màn Settings để nhập)"));
            }
            return webClient.post()
                    .uri("/bot{token}/sendMessage", s.botToken())
                    .bodyValue(Map.of(
                            "chat_id", s.chatId(),
                            "text", message,
                            "parse_mode", "HTML"))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(15))
                    .onErrorMap(WebClientResponseException.class, e -> new IllegalStateException(
                            "Telegram trả lỗi " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString()))
                    .doOnSuccess(v -> log.info("Telegram alert sent"))
                    .then();
        });
    }

    /**
     * Gửi kiểu "fire and forget": chưa cấu hình hoặc lỗi thì chỉ ghi log, không làm hỏng luồng chính.
     */
    public Mono<Void> sendQuietly(String message) {
        return settingsService.get()
                .filter(SettingsService.Settings::telegramConfigured)
                .flatMap(s -> sendAlert(message))
                .onErrorResume(e -> {
                    log.warn("Không gửi được Telegram: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    public static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
