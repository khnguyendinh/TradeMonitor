package com.trademonitor.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
@Slf4j
public class TelegramNotificationService {

    @Value("${telegram.bot-token}")
    private String botToken;

    @Value("${telegram.chat-id}")
    private String chatId;

    private final WebClient webClient;

    public TelegramNotificationService() {
        this.webClient = WebClient.create("https://api.telegram.org");
    }

    public Mono<Void> sendAlert(String message) {
        if (botToken == null || botToken.isEmpty() || botToken.contains("placeholder_token")) {
            log.warn("Telegram Bot Token is not configured. Skipping alert: {}", message);
            return Mono.empty();
        }

        String url = String.format("/bot%s/sendMessage", botToken);
        
        return webClient.post()
                .uri(url)
                .bodyValue(Map.of(
                        "chat_id", chatId,
                        "text", message,
                        "parse_mode", "HTML"
                ))
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(v -> log.info("Telegram alert sent successfully"))
                .doOnError(e -> log.error("Failed to send Telegram alert: {}", e.getMessage()));
    }
}
