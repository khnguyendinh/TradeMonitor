import { Injectable, NgZone, signal } from '@angular/core';
import { TradeRecord } from '../models/trade.model';

@Injectable({
  providedIn: 'root'
})
export class TradeStreamService {
  private eventSource: EventSource | null = null;
  
  // Angular 22 Signals for reactive state
  public trades = signal<TradeRecord[]>([]);
  public connectionStatus = signal<'DISCONNECTED' | 'CONNECTING' | 'CONNECTED'>('DISCONNECTED');

  constructor(private zone: NgZone) {}

  public connectStream(): void {
    if (this.eventSource) {
      this.eventSource.close();
    }

    this.connectionStatus.set('CONNECTING');
    this.eventSource = new EventSource('http://localhost:8080/api/trades/stream');

    this.eventSource.onmessage = (event) => {
      this.zone.run(() => {
        try {
          const newTrade: TradeRecord = JSON.parse(event.data);
          // Insert at the beginning or end depending on UI preference. We prepend here.
          this.trades.update(current => [newTrade, ...current]);
        } catch (e) {
          console.error('Error parsing SSE message', e);
        }
      });
    };

    this.eventSource.onopen = () => {
      this.zone.run(() => {
        this.connectionStatus.set('CONNECTED');
      });
    };

    this.eventSource.onerror = (error) => {
      this.zone.run(() => {
        this.connectionStatus.set('DISCONNECTED');
        console.error('SSE Error:', error);
        this.eventSource?.close();
      });
    };
  }

  public disconnect(): void {
    if (this.eventSource) {
      this.eventSource.close();
      this.connectionStatus.set('DISCONNECTED');
    }
  }
}
