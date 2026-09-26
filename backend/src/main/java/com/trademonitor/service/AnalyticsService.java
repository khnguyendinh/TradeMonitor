package com.trademonitor.service;

import com.trademonitor.model.TradeRecord;
import com.trademonitor.model.dto.PerformanceMetrics;
import com.trademonitor.model.dto.PerformanceMetrics.EquityPoint;
import com.trademonitor.model.dto.PerformanceMetrics.SymbolStat;
import com.trademonitor.repository.TradeRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private static final Comparator<TradeRecord> BY_TIME = Comparator
            .comparing(TradeRecord::effectiveTime, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(TradeRecord::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private final TradeRecordRepository tradeRecordRepository;
    private final SettingsService settingsService;

    public Mono<PerformanceMetrics> calculateMetrics(Long sessionId) {
        Flux<TradeRecord> source = sessionId == null
                ? tradeRecordRepository.findAll()
                : tradeRecordRepository.findBySessionIdOrderByOpenTimeAsc(sessionId);
        return Mono.zip(source.collectList(), settingsService.get())
                .map(t -> compute(t.getT1(), t.getT2().initialBalance()));
    }

    /**
     * Tính KPI từ danh sách lệnh.
     * - Chỉ lệnh BUY/SELL được tính là giao dịch; BALANCE (nạp/rút) dùng làm số dư ban đầu nếu có.
     * - Lãi/lỗ mỗi lệnh = profit + commission + swap.
     */
    public static PerformanceMetrics compute(List<TradeRecord> records, BigDecimal defaultInitialBalance) {
        List<TradeRecord> trades = records.stream()
                .filter(TradeRecord::isPosition)
                .filter(t -> t.getProfit() != null)
                .sorted(BY_TIME)
                .toList();

        BigDecimal deposits = records.stream()
                .filter(r -> TradeRecord.BALANCE.equals(r.getType()) && r.getProfit() != null)
                .map(TradeRecord::getProfit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal initialBalance = deposits.signum() > 0 ? deposits : defaultInitialBalance;

        int wins = 0, losses = 0;
        BigDecimal grossProfit = BigDecimal.ZERO;
        BigDecimal grossLoss = BigDecimal.ZERO;
        BigDecimal totalCommission = BigDecimal.ZERO;
        BigDecimal totalSwap = BigDecimal.ZERO;
        BigDecimal best = null, worst = null;

        BigDecimal equity = initialBalance;
        BigDecimal peak = equity;
        BigDecimal maxDdAmount = BigDecimal.ZERO;
        double maxDdPct = 0.0;

        int winStreak = 0, lossStreak = 0, maxWinStreak = 0, maxLossStreak = 0;

        List<EquityPoint> curve = new ArrayList<>();
        Map<String, SymbolAcc> bySymbol = new LinkedHashMap<>();

        if (!trades.isEmpty()) {
            curve.add(new EquityPoint(trades.getFirst().getOpenTime(), equity, BigDecimal.ZERO, null));
        }

        for (TradeRecord t : trades) {
            BigDecimal net = t.netProfit();
            totalCommission = totalCommission.add(nz(t.getCommission()));
            totalSwap = totalSwap.add(nz(t.getSwap()));

            if (net.signum() > 0) {
                wins++;
                grossProfit = grossProfit.add(net);
                winStreak++;
                lossStreak = 0;
                maxWinStreak = Math.max(maxWinStreak, winStreak);
            } else if (net.signum() < 0) {
                losses++;
                grossLoss = grossLoss.add(net.abs());
                lossStreak++;
                winStreak = 0;
                maxLossStreak = Math.max(maxLossStreak, lossStreak);
            }
            if (best == null || net.compareTo(best) > 0) best = net;
            if (worst == null || net.compareTo(worst) < 0) worst = net;

            equity = equity.add(net);
            if (equity.compareTo(peak) > 0) peak = equity;

            BigDecimal dd = peak.subtract(equity);
            if (dd.compareTo(maxDdAmount) > 0) maxDdAmount = dd;
            if (peak.signum() > 0) {
                maxDdPct = Math.max(maxDdPct, dd.divide(peak, 6, RoundingMode.HALF_UP).doubleValue() * 100);
            }

            curve.add(new EquityPoint(t.effectiveTime(), equity, net, t.getTicket()));
            bySymbol.computeIfAbsent(t.getSymbol(), SymbolAcc::new).add(t, net);
        }

        int total = trades.size();
        BigDecimal netProfit = grossProfit.subtract(grossLoss);
        double winRate = total > 0 ? round2((double) wins / total * 100) : 0.0;
        // grossLoss = 0 và có lãi => profit factor vô hạn, trả null để client hiển thị "∞"
        Double profitFactor;
        if (grossLoss.signum() > 0) {
            profitFactor = grossProfit.divide(grossLoss, 2, RoundingMode.HALF_UP).doubleValue();
        } else {
            profitFactor = grossProfit.signum() > 0 ? null : Double.valueOf(0.0);
        }

        List<SymbolStat> symbolStats = bySymbol.values().stream()
                .map(SymbolAcc::toStat)
                .sorted(Comparator.comparing(SymbolStat::netProfit).reversed())
                .toList();

        return PerformanceMetrics.builder()
                .totalTrades(total)
                .winningTrades(wins)
                .losingTrades(losses)
                .winRate(winRate)
                .initialBalance(initialBalance)
                .currentBalance(equity)
                .grossProfit(grossProfit)
                .grossLoss(grossLoss)
                .netProfit(netProfit)
                .totalCommission(totalCommission)
                .totalSwap(totalSwap)
                .profitFactor(profitFactor)
                .maxDrawdownAmount(maxDdAmount)
                .maxDrawdownPercentage(round2(maxDdPct))
                .currentDrawdownPercentage(peak.signum() > 0
                        ? round2(peak.subtract(equity).divide(peak, 6, RoundingMode.HALF_UP).doubleValue() * 100)
                        : 0.0)
                .averageWin(wins > 0 ? grossProfit.divide(BigDecimal.valueOf(wins), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                .averageLoss(losses > 0 ? grossLoss.divide(BigDecimal.valueOf(losses), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                .expectancy(total > 0 ? netProfit.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                .bestTrade(best == null ? BigDecimal.ZERO : best)
                .worstTrade(worst == null ? BigDecimal.ZERO : worst)
                .consecutiveWins(maxWinStreak)
                .consecutiveLosses(maxLossStreak)
                .equityCurve(curve)
                .symbolStats(symbolStats)
                .build();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static final class SymbolAcc {
        final String symbol;
        int trades, wins;
        BigDecimal net = BigDecimal.ZERO;
        BigDecimal volume = BigDecimal.ZERO;

        SymbolAcc(String symbol) {
            this.symbol = symbol;
        }

        void add(TradeRecord t, BigDecimal tradeNet) {
            trades++;
            if (tradeNet.signum() > 0) wins++;
            net = net.add(tradeNet);
            volume = volume.add(nz(t.getVolume()));
        }

        SymbolStat toStat() {
            return new SymbolStat(symbol, trades, wins, round2((double) wins / trades * 100), net, volume);
        }
    }
}
