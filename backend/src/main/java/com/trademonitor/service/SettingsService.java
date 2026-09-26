package com.trademonitor.service;

import com.trademonitor.model.dto.AppSettingsDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cấu hình ứng dụng: giá trị mặc định lấy từ application.yml,
 * giá trị người dùng sửa trên UI được lưu vào bảng app_settings và ưu tiên hơn.
 */
@Service
public class SettingsService {

    static final String BOT_TOKEN = "telegram.bot-token";
    static final String CHAT_ID = "telegram.chat-id";
    static final String INITIAL_BALANCE = "initial-balance";
    static final String MAX_DD = "alerts.max-drawdown-percent";
    static final String LARGE_LOSS = "alerts.large-loss-amount";
    static final String ALERTS_ENABLED = "alerts.enabled";

    private final DatabaseClient db;
    private final Map<String, String> defaults = new LinkedHashMap<>();

    public record Settings(String botToken, String chatId, BigDecimal initialBalance,
                           double maxDrawdownPercent, BigDecimal largeLossAmount, boolean alertsEnabled) {
        public boolean telegramConfigured() {
            return botToken != null && !botToken.isBlank() && chatId != null && !chatId.isBlank();
        }
    }

    public SettingsService(DatabaseClient db,
                           @Value("${trademonitor.telegram.bot-token:}") String botToken,
                           @Value("${trademonitor.telegram.chat-id:}") String chatId,
                           @Value("${trademonitor.initial-balance:10000}") String initialBalance,
                           @Value("${trademonitor.alerts.max-drawdown-percent:10}") String maxDd,
                           @Value("${trademonitor.alerts.large-loss-amount:500}") String largeLoss) {
        this.db = db;
        defaults.put(BOT_TOKEN, botToken);
        defaults.put(CHAT_ID, chatId);
        defaults.put(INITIAL_BALANCE, initialBalance);
        defaults.put(MAX_DD, maxDd);
        defaults.put(LARGE_LOSS, largeLoss);
        defaults.put(ALERTS_ENABLED, "true");
    }

    public Mono<Settings> get() {
        return db.sql("SELECT setting_key, setting_value FROM app_settings")
                .map((row, meta) -> Map.entry(row.get("setting_key", String.class),
                        nullToEmpty(row.get("setting_value", String.class))))
                .all()
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .map(stored -> {
                    Map<String, String> merged = new LinkedHashMap<>(defaults);
                    merged.putAll(stored);
                    return toSettings(merged);
                });
    }

    public Mono<AppSettingsDto> getDto() {
        return get().map(SettingsService::toDto);
    }

    public Mono<AppSettingsDto> update(AppSettingsDto dto) {
        Map<String, String> changes = new LinkedHashMap<>();
        // Token bị che (chứa '*') => giữ nguyên
        if (dto.botToken() != null && !dto.botToken().contains("*")) {
            changes.put(BOT_TOKEN, dto.botToken().trim());
        }
        if (dto.chatId() != null) changes.put(CHAT_ID, dto.chatId().trim());
        if (dto.initialBalance() != null) {
            if (dto.initialBalance().signum() <= 0) {
                return Mono.error(new IllegalArgumentException("Số dư ban đầu phải lớn hơn 0"));
            }
            changes.put(INITIAL_BALANCE, dto.initialBalance().toPlainString());
        }
        if (dto.maxDrawdownPercent() != null) {
            if (dto.maxDrawdownPercent() <= 0 || dto.maxDrawdownPercent() >= 100) {
                return Mono.error(new IllegalArgumentException("Ngưỡng drawdown phải trong khoảng (0, 100)"));
            }
            changes.put(MAX_DD, dto.maxDrawdownPercent().toString());
        }
        if (dto.largeLossAmount() != null) {
            if (dto.largeLossAmount().signum() <= 0) {
                return Mono.error(new IllegalArgumentException("Ngưỡng lỗ lớn phải lớn hơn 0"));
            }
            changes.put(LARGE_LOSS, dto.largeLossAmount().toPlainString());
        }
        if (dto.alertsEnabled() != null) changes.put(ALERTS_ENABLED, String.valueOf(dto.alertsEnabled()));

        return Flux.fromIterable(changes.entrySet())
                .concatMap(e -> db.sql("DELETE FROM app_settings WHERE setting_key = :k")
                        .bind("k", e.getKey())
                        .then()
                        .then(db.sql("INSERT INTO app_settings (setting_key, setting_value) VALUES (:k, :v)")
                                .bind("k", e.getKey())
                                .bind("v", e.getValue())
                                .then()))
                .then(getDto());
    }

    private static Settings toSettings(Map<String, String> m) {
        return new Settings(
                m.get(BOT_TOKEN),
                m.get(CHAT_ID),
                parseDecimal(m.get(INITIAL_BALANCE), new BigDecimal("10000")),
                parseDecimal(m.get(MAX_DD), BigDecimal.TEN).doubleValue(),
                parseDecimal(m.get(LARGE_LOSS), new BigDecimal("500")),
                !"false".equalsIgnoreCase(m.get(ALERTS_ENABLED)));
    }

    private static AppSettingsDto toDto(Settings s) {
        return new AppSettingsDto(mask(s.botToken()), s.chatId(), s.telegramConfigured(),
                s.initialBalance(), s.maxDrawdownPercent(), s.largeLossAmount(), s.alertsEnabled());
    }

    static String mask(String token) {
        if (token == null || token.isBlank()) return "";
        if (token.length() <= 8) return "********";
        return token.substring(0, 4) + "********" + token.substring(token.length() - 4);
    }

    private static BigDecimal parseDecimal(String v, BigDecimal fallback) {
        try {
            return v == null || v.isBlank() ? fallback : new BigDecimal(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
