package com.trademonitor.model.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class PerformanceMetrics {
    private int totalTrades;
    private int winningTrades;
    private int losingTrades;
    private double winRate;
    private BigDecimal grossProfit;
    private BigDecimal grossLoss;
    private BigDecimal netProfit;
    private double profitFactor;
    private BigDecimal maxDrawdownAmount;
    private double maxDrawdownPercentage;
    private BigDecimal averageWin;
    private BigDecimal averageLoss;
    private int consecutiveWins;
    private int consecutiveLosses;
    
    // Equity curve over time (timestamp -> equity)
    private Map<String, BigDecimal> equityCurve;
}
