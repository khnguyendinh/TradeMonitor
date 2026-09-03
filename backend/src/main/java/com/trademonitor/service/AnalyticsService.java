package com.trademonitor.service;

import com.trademonitor.model.TradeRecord;
import com.trademonitor.model.dto.PerformanceMetrics;
import com.trademonitor.repository.TradeRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final TradeRecordRepository tradeRecordRepository;

    public Mono<PerformanceMetrics> calculateMetrics() {
        return tradeRecordRepository.findAllByOrderByOpenTimeAsc()
            .collectList()
            .map(trades -> {
                int totalTrades = trades.size();
                int winningTrades = 0;
                int losingTrades = 0;
                BigDecimal grossProfit = BigDecimal.ZERO;
                BigDecimal grossLoss = BigDecimal.ZERO;

                BigDecimal currentEquity = BigDecimal.valueOf(10000); // Initial assumed balance
                BigDecimal peakEquity = currentEquity;
                BigDecimal maxDrawdownAmount = BigDecimal.ZERO;
                double maxDrawdownPercentage = 0.0;

                int currentWinStreak = 0;
                int currentLossStreak = 0;
                int maxWinStreak = 0;
                int maxLossStreak = 0;

                Map<String, BigDecimal> equityCurve = new LinkedHashMap<>();

                for (TradeRecord trade : trades) {
                    BigDecimal profit = trade.getProfit();
                    
                    if (profit.compareTo(BigDecimal.ZERO) > 0) {
                        winningTrades++;
                        grossProfit = grossProfit.add(profit);
                        currentWinStreak++;
                        currentLossStreak = 0;
                        if (currentWinStreak > maxWinStreak) maxWinStreak = currentWinStreak;
                    } else if (profit.compareTo(BigDecimal.ZERO) < 0) {
                        losingTrades++;
                        grossLoss = grossLoss.add(profit.abs());
                        currentLossStreak++;
                        currentWinStreak = 0;
                        if (currentLossStreak > maxLossStreak) maxLossStreak = currentLossStreak;
                    }

                    currentEquity = currentEquity.add(profit);
                    
                    if (currentEquity.compareTo(peakEquity) > 0) {
                        peakEquity = currentEquity;
                    }
                    
                    BigDecimal drawdownAmount = peakEquity.subtract(currentEquity);
                    if (drawdownAmount.compareTo(maxDrawdownAmount) > 0) {
                        maxDrawdownAmount = drawdownAmount;
                        maxDrawdownPercentage = drawdownAmount.divide(peakEquity, 4, RoundingMode.HALF_UP).doubleValue() * 100;
                    }
                    
                    if (trade.getCloseTime() != null) {
                        equityCurve.put(trade.getCloseTime().toString(), currentEquity);
                    }
                }

                BigDecimal netProfit = grossProfit.subtract(grossLoss);
                double winRate = totalTrades > 0 ? (double) winningTrades / totalTrades * 100 : 0.0;
                double profitFactor = grossLoss.compareTo(BigDecimal.ZERO) > 0 
                                      ? grossProfit.divide(grossLoss, 2, RoundingMode.HALF_UP).doubleValue() 
                                      : 0.0;
                
                BigDecimal averageWin = winningTrades > 0 ? grossProfit.divide(BigDecimal.valueOf(winningTrades), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                BigDecimal averageLoss = losingTrades > 0 ? grossLoss.divide(BigDecimal.valueOf(losingTrades), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

                return PerformanceMetrics.builder()
                        .totalTrades(totalTrades)
                        .winningTrades(winningTrades)
                        .losingTrades(losingTrades)
                        .winRate(winRate)
                        .grossProfit(grossProfit)
                        .grossLoss(grossLoss)
                        .netProfit(netProfit)
                        .profitFactor(profitFactor)
                        .maxDrawdownAmount(maxDrawdownAmount)
                        .maxDrawdownPercentage(maxDrawdownPercentage)
                        .averageWin(averageWin)
                        .averageLoss(averageLoss)
                        .consecutiveWins(maxWinStreak)
                        .consecutiveLosses(maxLossStreak)
                        .equityCurve(equityCurve)
                        .build();
            });
    }
}
