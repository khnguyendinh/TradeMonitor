package com.trademonitor.model.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PerformanceMetrics {
    private int totalTrades;
    private int winningTrades;
    private int losingTrades;
    private double winRate;
    private BigDecimal initialBalance;
    private BigDecimal currentBalance;
    private BigDecimal grossProfit;
    private BigDecimal grossLoss;
    private BigDecimal netProfit;
    private BigDecimal totalCommission;
    private BigDecimal totalSwap;
    private Double profitFactor; // null = vô hạn (không có lệnh lỗ)
    private BigDecimal maxDrawdownAmount;
    private double maxDrawdownPercentage;
    private double currentDrawdownPercentage;
    private BigDecimal averageWin;
    private BigDecimal averageLoss;
    private BigDecimal expectancy;
    private BigDecimal bestTrade;
    private BigDecimal worstTrade;
    private int consecutiveWins;
    private int consecutiveLosses;

    private List<EquityPoint> equityCurve;
    private List<SymbolStat> symbolStats;

    public record EquityPoint(LocalDateTime time, BigDecimal equity, BigDecimal profit, String ticket) {}

    public record SymbolStat(String symbol, int trades, int wins, double winRate, BigDecimal netProfit, BigDecimal volume) {}
}
