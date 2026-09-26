import { Injectable, OnDestroy, signal } from '@angular/core';
import { Subject } from 'rxjs';
import { TradeRecord } from '../models/trade.model';

export type ConnectionStatus = 'DISCONNECTED' | 'CONNECTING' | 'CONNECTED';

export type StreamEvent =
  | { type: 'trade'; trade: TradeRecord }
  | { type: 'refresh'; reason: string }
  | { type: 'reconnected' };

/**
 * Kết nối SSE tới /api/trades/stream.
 * Server chỉ đẩy sự kiện mới; mất kết nối thì tự thử lại với thời gian chờ tăng dần.
 */
@Injectable({ providedIn: 'root' })
export class TradeStreamService implements OnDestroy {
  private eventSource: EventSource | null = null;
  private retryTimer: ReturnType<typeof setTimeout> | null = null;
  private retryDelay = 1000;
  private everConnected = false;

  readonly connectionStatus = signal<ConnectionStatus>('DISCONNECTED');
  readonly events$ = new Subject<StreamEvent>();

  connect(): void {
    this.clearRetry();
    this.eventSource?.close();
    this.connectionStatus.set('CONNECTING');

    const es = new EventSource('/api/trades/stream');
    this.eventSource = es;

    es.addEventListener('hello', () => {
      this.connectionStatus.set('CONNECTED');
      this.retryDelay = 1000;
      // Kết nối lại sau khi mất: có thể đã lỡ sự kiện => yêu cầu tải lại dữ liệu
      if (this.everConnected) this.events$.next({ type: 'reconnected' });
      this.everConnected = true;
    });

    es.addEventListener('trade', (e) => {
      try {
        this.events$.next({ type: 'trade', trade: JSON.parse((e as MessageEvent).data) });
      } catch (err) {
        console.error('SSE: không đọc được trade', err);
      }
    });

    es.addEventListener('refresh', (e) => {
      this.events$.next({ type: 'refresh', reason: String((e as MessageEvent).data ?? '') });
    });

    es.onerror = () => {
      // Tự đóng và thử lại để kiểm soát thời gian chờ (EventSource mặc định retry 3s, không tăng dần)
      es.close();
      if (this.eventSource !== es) return;
      this.connectionStatus.set('DISCONNECTED');
      this.scheduleRetry();
    };
  }

  disconnect(): void {
    this.clearRetry();
    this.eventSource?.close();
    this.eventSource = null;
    this.connectionStatus.set('DISCONNECTED');
  }

  ngOnDestroy(): void {
    this.disconnect();
  }

  private scheduleRetry(): void {
    this.clearRetry();
    this.retryTimer = setTimeout(() => this.connect(), this.retryDelay);
    this.retryDelay = Math.min(this.retryDelay * 2, 30000);
  }

  private clearRetry(): void {
    if (this.retryTimer) {
      clearTimeout(this.retryTimer);
      this.retryTimer = null;
    }
  }
}
