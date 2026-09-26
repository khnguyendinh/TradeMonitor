package com.trademonitor.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Table("trade_sessions")
public class TradeSession {
    public static final String PROCESSING = "PROCESSING";
    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";

    @Id
    private Long id;
    private String name;
    private LocalDateTime importTime;
    private String status;
    private int tradeCount;
    private String message;
}
