package com.trademonitor.model.dto;

import java.math.BigDecimal;

/**
 * Cấu hình hiển thị/sửa trên màn Settings.
 * botToken khi trả về client được che bớt; khi gửi lên, để trống hoặc giữ nguyên chuỗi che = không đổi.
 */
public record AppSettingsDto(
        String botToken,
        String chatId,
        Boolean telegramConfigured,
        BigDecimal initialBalance,
        Double maxDrawdownPercent,
        BigDecimal largeLossAmount,
        Boolean alertsEnabled
) {}
