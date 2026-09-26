package com.trademonitor.repository;

import com.trademonitor.model.TradeSession;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TradeSessionRepository extends R2dbcRepository<TradeSession, Long> {
    Flux<TradeSession> findAllByOrderByImportTimeDesc();
    Mono<TradeSession> findFirstByName(String name);
}
