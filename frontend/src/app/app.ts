import { Component, OnInit, computed, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, map } from 'rxjs';
import { TradeStreamService } from './core/services/trade-stream.service';
import { TradeStore } from './core/services/trade-store.service';
import { ToastService } from './core/services/toast.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, DatePipe],
  template: `
    <div class="layout">
      <aside class="sidebar">
        <div class="brand">
          <span class="logo">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="3 17 9 11 13 15 21 7"></polyline><polyline points="15 7 21 7 21 13"></polyline>
            </svg>
          </span>
          <span>TradeMonitor</span>
        </div>

        <nav class="nav">
          @for (item of nav; track item.path) {
            <a [routerLink]="item.path" routerLinkActive="active" [title]="item.label">
              <span class="nav-icon">
                <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  @switch (item.icon) {
                    @case ('dashboard') {
                      <rect x="3" y="3" width="7" height="9" /><rect x="14" y="3" width="7" height="5" />
                      <rect x="14" y="12" width="7" height="9" /><rect x="3" y="16" width="7" height="5" />
                    }
                    @case ('upload') {
                      <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" /><polyline points="17 8 12 3 7 8" />
                      <line x1="12" y1="3" x2="12" y2="15" />
                    }
                    @case ('bell') {
                      <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" /><path d="M13.73 21a2 2 0 0 1-3.46 0" />
                    }
                  }
                </svg>
              </span>
              <span class="nav-label">{{ item.label }}</span>
            </a>
          }
        </nav>

        <div class="status" [class]="'status ' + stream.connectionStatus()">
          <span class="dot"></span>
          <div>
            <div class="status-label">{{ statusText() }}</div>
            @if (store.lastUpdated(); as t) {
              <div class="status-sub">Cập nhật {{ t | date: 'HH:mm:ss' }}</div>
            }
          </div>
          @if (stream.connectionStatus() === 'DISCONNECTED') {
            <button class="btn btn-sm" (click)="stream.connect()">Kết nối lại</button>
          }
        </div>
      </aside>

      <main class="main">
        <header class="topbar">
          <div>
            <h1>{{ title() }}</h1>
            <p class="muted">{{ subtitle() }}</p>
          </div>
          <div class="topbar-actions">
            <select class="input session-select"
                    [value]="store.sessionId() ?? ''"
                    (change)="onSessionChange($any($event.target).value)"
                    title="Lọc theo phiên import">
              <option value="">Tất cả phiên</option>
              @for (s of store.sessions(); track s.id) {
                <option [value]="s.id" [selected]="s.id === store.sessionId()">{{ s.name }} ({{ s.tradeCount }})</option>
              }
            </select>
            <button class="btn" (click)="store.reload()" [disabled]="store.loading()" title="Tải lại dữ liệu">
              <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" [class.spin]="store.loading()">
                <path d="M21 12a9 9 0 1 1-2.64-6.36"></path><polyline points="21 3 21 9 15 9"></polyline>
              </svg>
              Làm mới
            </button>
          </div>
        </header>

        @if (store.error(); as err) {
          <div class="banner">
            <strong>Lỗi tải dữ liệu:</strong> {{ err }}
            <button class="btn btn-sm" (click)="store.reload()">Thử lại</button>
          </div>
        }

        <div class="content">
          <router-outlet />
        </div>
      </main>
    </div>

    <div class="toasts">
      @for (t of toast.toasts(); track t.id) {
        <div class="toast" [class]="'toast ' + t.kind" (click)="toast.dismiss(t.id)">{{ t.text }}</div>
      }
    </div>
  `,
  styles: `
    :host { display: block; height: 100vh; }
    .layout { display: flex; height: 100%; }

    .sidebar {
      width: 240px; flex-shrink: 0;
      display: flex; flex-direction: column;
      background: var(--surface);
      border-right: 1px solid var(--border);
      padding: 20px 14px;
    }
    .brand { display: flex; align-items: center; gap: 10px; font-weight: 700; font-size: 17px; padding: 0 8px 24px; }
    .logo {
      display: grid; place-items: center; width: 30px; height: 30px; border-radius: 8px;
      background: var(--primary); color: #fff;
    }
    .nav { display: flex; flex-direction: column; gap: 4px; flex: 1; }
    .nav a {
      display: flex; align-items: center; gap: 10px;
      padding: 9px 12px; border-radius: var(--radius-sm);
      color: var(--text-2); font-weight: 500;
      transition: background .15s, color .15s;
    }
    .nav a:hover { background: var(--surface-hover); }
    .nav a.active { background: var(--primary-soft); color: var(--primary); }
    .nav-icon { display: inline-flex; }

    .status {
      display: flex; align-items: center; gap: 10px; flex-wrap: wrap;
      padding: 12px; border-radius: var(--radius-sm);
      background: var(--surface-2); border: 1px solid var(--border);
      font-size: 12.5px;
    }
    .status-label { font-weight: 600; }
    .status-sub { color: var(--text-muted); font-size: 11.5px; }
    .dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
    .CONNECTED .dot { background: var(--success); box-shadow: 0 0 0 3px var(--success-soft); }
    .CONNECTED .status-label { color: var(--success); }
    .CONNECTING .dot { background: var(--warning); }
    .CONNECTING .status-label { color: var(--warning); }
    .DISCONNECTED .dot { background: var(--danger); }
    .DISCONNECTED .status-label { color: var(--danger); }

    .main { flex: 1; min-width: 0; display: flex; flex-direction: column; }
    .topbar {
      display: flex; align-items: center; justify-content: space-between; gap: 16px; flex-wrap: wrap;
      padding: 18px 28px; background: var(--surface); border-bottom: 1px solid var(--border);
    }
    .topbar h1 { font-size: 20px; }
    .topbar p { font-size: 13px; }
    .topbar-actions { display: flex; gap: 8px; align-items: center; }
    .session-select { width: 240px; }
    .banner {
      display: flex; align-items: center; gap: 12px; flex-wrap: wrap;
      margin: 16px 28px 0; padding: 10px 14px; border-radius: var(--radius-sm);
      background: var(--danger-soft); color: var(--danger); border: 1px solid #fecaca;
    }
    .content { flex: 1; overflow-y: auto; padding: 24px 28px 40px; }

    .spin { animation: spin 0.8s linear infinite; }
    @keyframes spin { to { transform: rotate(360deg); } }

    .toasts { position: fixed; right: 20px; bottom: 20px; display: flex; flex-direction: column; gap: 8px; z-index: 100; }
    .toast {
      max-width: 380px; padding: 12px 16px; border-radius: var(--radius-sm);
      background: var(--surface); border: 1px solid var(--border); box-shadow: var(--shadow-lg);
      border-left: 4px solid var(--primary); cursor: pointer; font-weight: 500;
      animation: slide-in .2s ease-out;
    }
    .toast.success { border-left-color: var(--success); }
    .toast.error { border-left-color: var(--danger); color: var(--danger); }
    @keyframes slide-in { from { transform: translateY(8px); opacity: 0; } }

    @media (max-width: 900px) {
      .layout { flex-direction: column; }
      .sidebar { width: 100%; flex-direction: row; align-items: center; padding: 10px 16px; gap: 12px; border-right: 0; border-bottom: 1px solid var(--border); }
      .brand { padding: 0; }
      .brand span:last-child { display: none; }
      .nav { flex-direction: row; overflow-x: auto; gap: 2px; }
      .nav a { padding: 8px 10px; white-space: nowrap; }
      .status { padding: 8px 10px; background: transparent; border: 0; }
      .status-sub, .status .btn { display: none; }
      .topbar h1 { font-size: 17px; }
      .topbar, .content { padding-left: 16px; padding-right: 16px; }
      .session-select { width: 170px; }
    }
    @media (max-width: 600px) {
      .nav-label, .status-label { display: none; }
      .topbar p { display: none; }
      .banner { margin: 12px 16px 0; }
    }
  `,
})
export class App implements OnInit {
  readonly stream = inject(TradeStreamService);
  readonly store = inject(TradeStore);
  readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly nav = [
    { path: '/dashboard', label: 'Tổng quan', icon: 'dashboard' },
    { path: '/trades', label: 'Lệnh & Upload', icon: 'upload' },
    { path: '/settings', label: 'Cài đặt & Cảnh báo', icon: 'bell' },
  ];

  private readonly routeData = toSignal(
    this.router.events.pipe(
      filter((e) => e instanceof NavigationEnd),
      map(() => {
        let r = this.route;
        while (r.firstChild) r = r.firstChild;
        return r.snapshot.data as { title?: string; subtitle?: string };
      }),
    ),
    { initialValue: {} as { title?: string; subtitle?: string } },
  );

  readonly title = computed(() => this.routeData().title ?? 'TradeMonitor');
  readonly subtitle = computed(() => this.routeData().subtitle ?? '');

  readonly statusText = computed(() => {
    switch (this.stream.connectionStatus()) {
      case 'CONNECTED':
        return 'Realtime: đã kết nối';
      case 'CONNECTING':
        return 'Đang kết nối...';
      default:
        return 'Mất kết nối backend';
    }
  });

  ngOnInit(): void {
    this.store.start();
  }

  onSessionChange(value: string): void {
    this.store.setSession(value === '' ? null : Number(value));
  }
}
