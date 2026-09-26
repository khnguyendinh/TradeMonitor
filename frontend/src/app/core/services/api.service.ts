import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  AppSettings,
  NewTrade,
  PerformanceMetrics,
  TradeRecord,
  TradeSession,
} from '../models/trade.model';

/** Gọi REST backend. Đường dẫn tương đối /api được dev server proxy sang :8080 (proxy.conf.json). */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api';

  getTrades(sessionId?: number | null, symbol?: string | null, limit = 1000): Observable<TradeRecord[]> {
    let params = new HttpParams().set('limit', limit);
    if (sessionId != null) params = params.set('sessionId', sessionId);
    if (symbol) params = params.set('symbol', symbol);
    return this.http.get<TradeRecord[]>(`${this.base}/trades`, { params });
  }

  addTrade(trade: NewTrade): Observable<TradeRecord> {
    return this.http.post<TradeRecord>(`${this.base}/trades`, trade);
  }

  deleteTrade(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/trades/${id}`);
  }

  upload(file: File): Observable<TradeSession> {
    const form = new FormData();
    form.append('file', file, file.name);
    return this.http.post<TradeSession>(`${this.base}/trades/upload`, form);
  }

  seedDemo(count: number): Observable<TradeSession> {
    return this.http.post<TradeSession>(`${this.base}/demo/seed`, null, {
      params: new HttpParams().set('count', count),
    });
  }

  getSessions(): Observable<TradeSession[]> {
    return this.http.get<TradeSession[]>(`${this.base}/sessions`);
  }

  deleteSession(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/sessions/${id}`);
  }

  getMetrics(sessionId?: number | null): Observable<PerformanceMetrics> {
    let params = new HttpParams();
    if (sessionId != null) params = params.set('sessionId', sessionId);
    return this.http.get<PerformanceMetrics>(`${this.base}/analytics/metrics`, { params });
  }

  getSettings(): Observable<AppSettings> {
    return this.http.get<AppSettings>(`${this.base}/settings`);
  }

  updateSettings(s: Partial<AppSettings>): Observable<AppSettings> {
    return this.http.put<AppSettings>(`${this.base}/settings`, s);
  }

  testTelegram(message: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.base}/telegram/test`, { message });
  }
}

/** Lấy thông báo lỗi dễ đọc từ response backend ({ error: "..." }). */
export function errorMessage(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 0) return 'Không kết nối được backend (http://localhost:8080). Backend đã chạy chưa?';
    const body = err.error;
    if (body && typeof body === 'object' && 'error' in body) return String(body.error);
    if (typeof body === 'string' && body) {
      try {
        const parsed = JSON.parse(body);
        if (parsed?.error) return String(parsed.error);
      } catch {
        /* không phải JSON */
      }
      return body;
    }
    return `${err.status} ${err.statusText}`;
  }
  return err instanceof Error ? err.message : String(err);
}
