package com.trademonitor.model.dto;

/**
 * Sự kiện đẩy qua SSE.
 * type = "trade"   : payload là TradeRecord mới
 * type = "refresh" : dữ liệu thay đổi hàng loạt (import xong, xoá phiên...), client tải lại
 */
public record StreamEvent(String type, Object payload) {
    public static StreamEvent trade(Object trade) {
        return new StreamEvent("trade", trade);
    }

    public static StreamEvent refresh(String reason) {
        return new StreamEvent("refresh", reason);
    }
}
