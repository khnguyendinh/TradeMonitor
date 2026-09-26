import { Injectable, computed, inject, signal } from '@angular/core';
import { Subject, catchError, debounceTime, forkJoin, of, switchMap, tap } from 'rxjs';
import { PerformanceMetrics, TradeRecord, TradeSession } from '../models/trade.model';
import { ApiService, errorMessage } from './api.service';
import { TradeStreamService } from './trade-stream.service';

/**
 * Trạng thái dùng chung (signals): danh sách lệnh, KPI, phiên import, bộ lọc phiên.
 * Tự tải lại khi có sự kiện SSE.
 */
@Injectable({ providedIn: 'root' })
export class TradeStore {
  private readonly api = inject(ApiService);
  private readonly stream = inject(TradeStreamService);
  private readonly reload$ = new Subject<void>();
  private started = false;

  readonly sessionId = signal<number | null>(null);
  readonly sessions = signal<TradeSession[]>([]);
  readonly trades = signal<TradeRecord[]>([]);
  readonly metrics = signal<PerformanceMetrics | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly lastUpdated = signal<Date | null>(null);
  /** id các lệnh vừa đến qua SSE, để UI tô sáng. */
  readonly freshIds = signal<ReadonlySet<number>>(new Set());

  readonly positions = computed(() => this.trades().filter((t) => t.type !== 'BALANCE'));
  readonly symbols = computed(() => [...new Set(this.positions().map((t) => t.symbol))].sort());
  readonly hasData = computed(() => (this.metrics()?.totalTrades ?? 0) > 0);

  start(): void {
    if (this.started) return;
    this.started = true;

    this.reload$
      .pipe(
        debounceTime(200),
        tap(() => this.loading.set(true)),
        switchMap(() => {
          const sid = this.sessionId();
          return forkJoin({
            trades: this.api.getTrades(sid),
            metrics: this.api.getMetrics(sid),
            sessions: this.api.getSessions(),
          }).pipe(
            catchError((err) => {
              this.error.set(errorMessage(err));
              this.loading.set(false);
              return of(null);
            }),
          );
        }),
      )
      .subscribe((res) => {
        if (!res) return;
        this.trades.set(res.trades);
        this.metrics.set(res.metrics);
        this.sessions.set(res.sessions);
        // Phiên đang lọc đã bị xoá => quay về "Tất cả"
        const sid = this.sessionId();
        if (sid != null && !res.sessions.some((s) => s.id === sid)) {
          this.sessionId.set(null);
          this.reload();
        }
        this.error.set(null);
        this.loading.set(false);
        this.lastUpdated.set(new Date());
      });

    this.stream.events$.subscribe((ev) => {
      if (ev.type === 'trade') {
        const sid = this.sessionId();
        if (sid == null || sid === ev.trade.sessionId) {
          this.trades.update((list) => [ev.trade, ...list.filter((t) => t.id !== ev.trade.id)]);
          this.markFresh(ev.trade.id);
        }
      }
      this.reload();
    });

    this.stream.connect();
    this.reload();
  }

  reload(): void {
    this.reload$.next();
  }

  setSession(id: number | null): void {
    this.sessionId.set(id);
    this.reload();
  }

  private markFresh(id: number): void {
    this.freshIds.update((s) => new Set(s).add(id));
    setTimeout(() => {
      this.freshIds.update((s) => {
        const next = new Set(s);
        next.delete(id);
        return next;
      });
    }, 4000);
  }
}
