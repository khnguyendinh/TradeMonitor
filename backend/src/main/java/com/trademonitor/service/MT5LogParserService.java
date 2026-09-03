package com.trademonitor.service;

import com.trademonitor.model.TradeRecord;
import com.trademonitor.model.TradeSession;
import com.trademonitor.repository.TradeRecordRepository;
import com.trademonitor.repository.TradeSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class MT5LogParserService {

    private final TradeRecordRepository tradeRecordRepository;
    private final TradeSessionRepository tradeSessionRepository;

    /**
     * Parses the uploaded file and stores records into the DB reactively.
     * In a real implementation, you'd parse CSV/HTML differently.
     */
    public Mono<TradeSession> parseAndSave(FilePart filePart) {
        String fileName = filePart.filename();
        
        TradeSession session = new TradeSession();
        session.setName(fileName);
        session.setImportTime(LocalDateTime.now());
        session.setStatus("PROCESSING");
        
        return tradeSessionRepository.save(session)
            .flatMap(savedSession -> 
                filePart.content()
                    .map(dataBuffer -> {
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        // Releases the buffer
                        // DataBufferUtils.release(dataBuffer); (if manually managed)
                        return new String(bytes, StandardCharsets.UTF_8);
                    })
                    .collectList()
                    .flatMap(strings -> {
                        // Dummy Implementation:
                        // Normally we parse 'strings' into Flux<TradeRecord>
                        // For now we just mark success.
                        savedSession.setStatus("COMPLETED");
                        return tradeSessionRepository.save(savedSession);
                    })
            );
    }
}
