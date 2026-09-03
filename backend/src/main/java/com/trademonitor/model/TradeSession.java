package com.trademonitor.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Table("trade_sessions")
public class TradeSession {
    @Id
    private Long id;
    private String name;
    private LocalDateTime importTime;
    private String status;
}
