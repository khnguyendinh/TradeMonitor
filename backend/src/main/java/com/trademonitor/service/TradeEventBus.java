package com.trademonitor.service;

import com.trademonitor.model.dto.StreamEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * Kênh phát sự kiện realtime cho các client SSE.
 * Chỉ phát sự kiện MỚI; dữ liệu cũ client tự tải qua REST.
 */
@Component
public class TradeEventBus {

    private final Sinks.Many<StreamEvent> sink = Sinks.many().multicast().directBestEffort();

    public synchronized void publish(StreamEvent event) {
        // FAIL_ZERO_SUBSCRIBER (chưa ai nghe) là bình thường, bỏ qua
        sink.tryEmitNext(event);
    }

    public Flux<StreamEvent> events() {
        return sink.asFlux();
    }
}
