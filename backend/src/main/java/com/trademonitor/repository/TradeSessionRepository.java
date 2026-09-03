package com.trademonitor.repository;

import com.trademonitor.model.TradeSession;
import org.springframework.data.r2dbc.repository.R2dbcRepository;

public interface TradeSessionRepository extends R2dbcRepository<TradeSession, Long> {
}
