package com.trademonitor.service;

import com.trademonitor.model.TradeRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.trademonitor.service.TelegramNotificationService.escapeHtml;

/**
 * Kiểm tra ngưỡng cảnh báo sau khi có lệnh mới và gửi Telegram.
 * - Lệnh lỗ lớn: lỗ ròng của 1 lệnh >= ngưỡng
 * - Drawdown hiện tại >= ngưỡng % (chỉ báo 1 lần cho tới khi drawdown giảm lại dưới ngưỡng)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    /** Số lệnh lỗ lớn tối đa liệt kê trong 1 lần import để tránh spam. */
    private static final int MAX_LOSS_ALERTS = 5;

    private final SettingsService settingsService;
    private final AnalyticsService analyticsService;
    private final TelegramNotificationService telegram;

    private final AtomicBoolean drawdownAlerted = new AtomicBoolean(false);

    public Mono<Void> onTradesAdded(List<TradeRecord> newRecords) {
        List<TradeRecord> trades = newRecords.stream().filter(TradeRecord::isPosition).toList();
        if (trades.isEmpty()) return Mono.empty();

        return settingsService.get()
                .filter(SettingsService.Settings::alertsEnabled)
                .flatMap(s -> analyticsService.calculateMetrics(null).flatMap(m -> {
                    List<String> messages = new ArrayList<>();

                    List<TradeRecord> bigLosses = trades.stream()
                            .filter(t -> t.netProfit().negate().compareTo(s.largeLossAmount()) >= 0)
                            .toList();
                    bigLosses.stream().limit(MAX_LOSS_ALERTS).forEach(t -> messages.add(String.format(
                            "🔻 <b>Lệnh lỗ lớn</b>%n%s %s %s lot%nLỗ: <b>%s</b> (ngưỡng %s)",
                            escapeHtml(t.getSymbol()), t.getType(), t.getVolume(),
                            t.netProfit().toPlainString(), s.largeLossAmount().toPlainString())));
                    if (bigLosses.size() > MAX_LOSS_ALERTS) {
                        messages.add("… và " + (bigLosses.size() - MAX_LOSS_ALERTS) + " lệnh lỗ lớn khác");
                    }

                    double dd = m.getCurrentDrawdownPercentage();
                    if (dd >= s.maxDrawdownPercent()) {
                        if (drawdownAlerted.compareAndSet(false, true)) {
                            messages.add(String.format(
                                    "⚠️ <b>Drawdown vượt ngưỡng</b>%nDrawdown hiện tại: <b>%.2f%%</b> (ngưỡng %.2f%%)%nSố dư: %s",
                                    dd, s.maxDrawdownPercent(), m.getCurrentBalance().toPlainString()));
                        }
                    } else {
                        drawdownAlerted.set(false);
                    }

                    return Flux.fromIterable(messages).concatMap(telegram::sendQuietly).then();
                }))
                .onErrorResume(e -> {
                    log.warn("Kiểm tra cảnh báo lỗi: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    public Mono<Void> importSummary(String fileName, int saved, int duplicates, List<TradeRecord> records) {
        BigDecimal net = records.stream().filter(TradeRecord::isPosition)
                .map(TradeRecord::netProfit).reduce(BigDecimal.ZERO, BigDecimal::add);
        String msg = String.format("📥 <b>Import xong</b> %s%nĐã lưu: %d lệnh, bỏ qua trùng: %d%nLãi/lỗ ròng: <b>%s</b>",
                escapeHtml(fileName), saved, duplicates, net.toPlainString());
        return telegram.sendQuietly(msg);
    }
}
