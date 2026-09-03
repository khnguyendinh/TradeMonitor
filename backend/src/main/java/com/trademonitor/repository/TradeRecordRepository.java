package com.trademonitor.repository;

import com.trademonitor.model.TradeRecord;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;

public interface TradeRecordRepository extends R2dbcRepository<TradeRecord, Long> {
    Flux<TradeRecord> findAllByOrderByOpenTimeAsc();
    Flux<TradeRecord> findBySessionIdOrderByOpenTimeAsc(Long sessionId);
    Flux<TradeRecord> findBySymbolOrderByOpenTimeAsc(String symbol);
}
