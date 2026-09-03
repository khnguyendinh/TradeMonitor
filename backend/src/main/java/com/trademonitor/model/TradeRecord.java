package com.trademonitor.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Table("trade_records")
public class TradeRecord {
    @Id
    private Long id;
    private String ticket;
    private String symbol;
    private String type; // BUY, SELL, BALANCE
    private BigDecimal volume;
    private BigDecimal openPrice;
    private BigDecimal closePrice;
    private LocalDateTime openTime;
    private LocalDateTime closeTime;
    private BigDecimal sl;
    private BigDecimal tp;
    private BigDecimal commission;
    private BigDecimal swap;
    private BigDecimal profit;
    private Long sessionId;
}
