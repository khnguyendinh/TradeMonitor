package com.trademonitor.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Table("trade_records")
public class TradeRecord {
    public static final String BUY = "BUY";
    public static final String SELL = "SELL";
    public static final String BALANCE = "BALANCE";

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

    @JsonIgnore
    public boolean isPosition() {
        return BUY.equals(type) || SELL.equals(type);
    }

    /** Lợi nhuận ròng = profit + commission + swap (commission thường âm). */
    public BigDecimal netProfit() {
        return nz(profit).add(nz(commission)).add(nz(swap));
    }

    /** Thời điểm dùng để sắp xếp / vẽ equity: ưu tiên giờ đóng lệnh. */
    public LocalDateTime effectiveTime() {
        return closeTime != null ? closeTime : openTime;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
