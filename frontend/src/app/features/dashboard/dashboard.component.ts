import {
  Component,
  ElementRef,
  OnDestroy,
  afterNextRender,
  computed,
  effect,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Chart, registerables } from 'chart.js';
import { PerformanceMetrics, netProfit } from '../../core/models/trade.model';
import { TradeStore } from '../../core/services/trade-store.service';
import { ApiService, errorMessage } from '../../core/services/api.service';
import { ToastService } from '../../core/services/toast.service';

Chart.register(...registerables);

/** "dd/MM HH:mm" */
function formatShort(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${pad(d.getDate())}/${pad(d.getMonth() + 1)} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

@Component({
  selector: 'app-dashboard',
  imports: [DecimalPipe, DatePipe, RouterLink],
  template: `
    @let m = store.metrics();

    <!-- KPI -->
    <section class="kpis">
      <div class="card kpi">
        <span class="kpi-label">Lãi/lỗ ròng</span>
        <span class="kpi-value num" [class.positive]="(m?.netProfit ?? 0) > 0" [class.negative]="(m?.netProfit ?? 0) < 0">
          {{ money(m?.netProfit) }}
        </span>
        <span class="kpi-sub num" [class.positive]="returnPct() > 0" [class.negative]="returnPct() < 0">
          {{ returnPct() > 0 ? '+' : '' }}{{ returnPct() | number: '1.2-2' }}% so với vốn
        </span>
      </div>
      <div class="card kpi">
        <span class="kpi-label">Số dư hiện tại</span>
        <span class="kpi-value num">{{ money(m?.currentBalance) }}</span>
        <span class="kpi-sub num">Vốn ban đầu {{ money(m?.initialBalance) }}</span>
      </div>
      <div class="card kpi">
        <span class="kpi-label">Tỷ lệ thắng</span>
        <span class="kpi-value num">{{ (m?.winRate ?? 0) | number: '1.1-1' }}%</span>
        <div class="bar"><div class="bar-fill" [style.width.%]="m?.winRate ?? 0"></div></div>
        <span class="kpi-sub num">{{ m?.winningTrades ?? 0 }} thắng · {{ m?.losingTrades ?? 0 }} thua · {{ m?.totalTrades ?? 0 }} lệnh</span>
      </div>
      <div class="card kpi">
        <span class="kpi-label">Profit factor</span>
        <span class="kpi-value num" [class.positive]="pfGood()" [class.negative]="pfBad()">{{ profitFactor() }}</span>
        <span class="kpi-sub num">Lãi gộp {{ money(m?.grossProfit) }} / Lỗ gộp {{ money(m?.grossLoss) }}</span>
      </div>
      <div class="card kpi">
        <span class="kpi-label">Max drawdown</span>
        <span class="kpi-value num negative">{{ (m?.maxDrawdownPercentage ?? 0) | number: '1.2-2' }}%</span>
        <span class="kpi-sub num">-{{ money(m?.maxDrawdownAmount) }} · hiện tại {{ (m?.currentDrawdownPercentage ?? 0) | number: '1.2-2' }}%</span>
      </div>
      <div class="card kpi">
        <span class="kpi-label">Kỳ vọng / lệnh</span>
        <span class="kpi-value num" [class.positive]="(m?.expectancy ?? 0) > 0" [class.negative]="(m?.expectancy ?? 0) < 0">
          {{ money(m?.expectancy) }}
        </span>
        <span class="kpi-sub num">TB thắng {{ money(m?.averageWin) }} · TB thua -{{ money(m?.averageLoss) }}</span>
      </div>
    </section>

    <!-- Chart + recent -->
    <section class="row">
      <div class="card chart-card">
        <div class="card-header">
          <h3>Đường equity</h3>
          <span class="muted small">{{ (m?.equityCurve?.length ?? 1) - 1 }} điểm</span>
        </div>
        <div class="chart-box">
          <canvas #equityCanvas></canvas>
          @if (!store.hasData()) {
            <div class="empty-overlay">
              @if (store.loading() && !store.lastUpdated()) {
                <p class="muted">Đang tải dữ liệu...</p>
              } @else {
                <h4>Chưa có dữ liệu giao dịch</h4>
                <p class="muted">Upload báo cáo MT5 (HTML/CSV) hoặc tạo dữ liệu mẫu để xem thử.</p>
                <div class="actions">
                  <a class="btn btn-primary" routerLink="/trades">Upload báo cáo</a>
                  <button class="btn" (click)="seedDemo()" [disabled]="seeding()">
                    {{ seeding() ? 'Đang tạo...' : 'Tạo 60 lệnh demo' }}
                  </button>
                </div>
              }
            </div>
          }
        </div>
      </div>

      <div class="card recent-card">
        <div class="card-header">
          <h3>Lệnh gần nhất</h3>
          <a routerLink="/trades" class="small">Xem tất cả →</a>
        </div>
        <div class="table-wrap recent-table">
          <table class="table">
            <thead>
              <tr><th>Đóng lúc</th><th>Mã</th><th>Loại</th><th class="right">Lãi/lỗ</th></tr>
            </thead>
            <tbody>
              @for (t of recent(); track t.id) {
                <tr [class.fresh]="store.freshIds().has(t.id)">
                  <td class="num">{{ (t.closeTime ?? t.openTime) | date: 'dd/MM HH:mm' }}</td>
                  <td><strong>{{ t.symbol }}</strong><span class="muted lot">{{ t.volume }} lot</span></td>
                  <td><span class="tag" [class.tag-buy]="t.type === 'BUY'" [class.tag-sell]="t.type === 'SELL'">{{ t.type }}</span></td>
                  <td class="right num" [class.positive]="net(t) > 0" [class.negative]="net(t) < 0">{{ money(net(t)) }}</td>
                </tr>
              } @empty {
                <tr><td colspan="4" class="empty-state">Chưa có lệnh</td></tr>
              }
            </tbody>
          </table>
        </div>
      </div>
    </section>

    <!-- Symbol stats + extra -->
    <section class="row">
      <div class="card">
        <div class="card-header"><h3>Theo mã giao dịch</h3></div>
        <div class="table-wrap">
          <table class="table">
            <thead>
              <tr>
                <th>Mã</th><th class="right">Số lệnh</th><th class="right">Tỷ lệ thắng</th>
                <th class="right">Khối lượng</th><th class="right">Lãi/lỗ ròng</th><th>Đóng góp</th>
              </tr>
            </thead>
            <tbody>
              @for (s of m?.symbolStats ?? []; track s.symbol) {
                <tr>
                  <td><strong>{{ s.symbol }}</strong></td>
                  <td class="right num">{{ s.trades }}</td>
                  <td class="right num">{{ s.winRate | number: '1.1-1' }}%</td>
                  <td class="right num">{{ s.volume | number: '1.2-2' }}</td>
                  <td class="right num" [class.positive]="s.netProfit > 0" [class.negative]="s.netProfit < 0">{{ money(s.netProfit) }}</td>
                  <td class="contrib">
                    <div class="contrib-bar" [class.neg]="s.netProfit < 0" [style.width.%]="contribution(s.netProfit)"></div>
                  </td>
                </tr>
              } @empty {
                <tr><td colspan="6" class="empty-state">Chưa có dữ liệu</td></tr>
              }
            </tbody>
          </table>
        </div>
      </div>

      <div class="card">
        <div class="card-header"><h3>Chỉ số khác</h3></div>
        <dl class="stats">
          <dt>Lệnh lãi lớn nhất</dt><dd class="num positive">{{ money(m?.bestTrade) }}</dd>
          <dt>Lệnh lỗ lớn nhất</dt><dd class="num negative">{{ money(m?.worstTrade) }}</dd>
          <dt>Chuỗi thắng dài nhất</dt><dd class="num">{{ m?.consecutiveWins ?? 0 }} lệnh</dd>
          <dt>Chuỗi thua dài nhất</dt><dd class="num">{{ m?.consecutiveLosses ?? 0 }} lệnh</dd>
          <dt>Tổng commission</dt><dd class="num">{{ money(m?.totalCommission) }}</dd>
          <dt>Tổng swap</dt><dd class="num">{{ money(m?.totalSwap) }}</dd>
        </dl>
      </div>
    </section>
  `,
  styles: `
    :host { display: flex; flex-direction: column; gap: 20px; }
    .small { font-size: 12.5px; }

    .kpis { display: grid; grid-template-columns: repeat(auto-fit, minmax(175px, 1fr)); gap: 16px; }
    .kpi { padding: 16px 18px; display: flex; flex-direction: column; gap: 4px; }
    .kpi-label { font-size: 12px; font-weight: 600; color: var(--text-muted); text-transform: uppercase; letter-spacing: .4px; }
    .kpi-value { font-size: 24px; font-weight: 700; color: var(--text); }
    .kpi-sub { font-size: 12px; color: var(--text-muted); }
    .bar { height: 5px; border-radius: 3px; background: var(--danger-soft); overflow: hidden; margin: 4px 0 2px; }
    .bar-fill { height: 100%; background: var(--success); border-radius: 3px; transition: width .4s; }

    .row { display: grid; grid-template-columns: minmax(0, 2fr) minmax(0, 1fr); gap: 20px; }
    .chart-card, .recent-card { display: flex; flex-direction: column; min-height: 400px; }
    .chart-box { position: relative; flex: 1; padding: 16px 20px 20px; min-height: 340px; }
    .chart-box canvas { position: absolute; inset: 16px 20px 20px; width: calc(100% - 40px) !important; height: calc(100% - 36px) !important; }
    .empty-overlay {
      position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center;
      gap: 8px; text-align: center; background: rgba(255,255,255,.85); padding: 24px;
    }
    .actions { display: flex; gap: 8px; margin-top: 8px; flex-wrap: wrap; justify-content: center; }

    .recent-table { flex: 1; max-height: 400px; }
    .lot { margin-left: 6px; font-size: 12px; }
    tr.fresh td { animation: flash 2.5s ease-out; }
    @keyframes flash { from { background: #fef9c3; } to { background: transparent; } }

    .contrib { width: 120px; }
    .contrib-bar { height: 8px; border-radius: 4px; background: var(--success); min-width: 2px; }
    .contrib-bar.neg { background: var(--danger); }

    .stats { display: grid; grid-template-columns: 1fr auto; padding: 8px 20px 16px; }
    .stats dt, .stats dd { padding: 10px 0; border-bottom: 1px solid var(--border); }
    .stats dt { color: var(--text-muted); }
    .stats dd { text-align: right; font-weight: 600; }

    @media (max-width: 1100px) { .row { grid-template-columns: minmax(0, 1fr); } }
    @media (max-width: 600px) {
      .kpis { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
      .kpi { padding: 12px; }
      .kpi-value { font-size: 19px; }
      .chart-box { min-height: 260px; padding: 12px; }
      .chart-box canvas { inset: 12px; width: calc(100% - 24px) !important; height: calc(100% - 24px) !important; }
    }
  `,
})
export class DashboardComponent implements OnDestroy {
  readonly store = inject(TradeStore);
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);

  private readonly canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('equityCanvas');
  private chart: Chart<'line'> | null = null;

  readonly seeding = signal(false);
  readonly net = netProfit;

  readonly recent = computed(() => this.store.positions().slice(0, 15));

  readonly returnPct = computed(() => {
    const m = this.store.metrics();
    return m && m.initialBalance ? (m.netProfit / m.initialBalance) * 100 : 0;
  });

  readonly profitFactor = computed(() => {
    const m = this.store.metrics();
    if (!m || m.totalTrades === 0) return '—';
    return m.profitFactor == null ? '∞' : m.profitFactor.toFixed(2);
  });
  readonly pfGood = computed(() => {
    const m = this.store.metrics();
    return !!m && m.totalTrades > 0 && (m.profitFactor == null || m.profitFactor >= 1);
  });
  readonly pfBad = computed(() => {
    const m = this.store.metrics();
    return !!m && m.totalTrades > 0 && m.profitFactor != null && m.profitFactor < 1;
  });

  private readonly maxAbsSymbolProfit = computed(() =>
    Math.max(1, ...(this.store.metrics()?.symbolStats ?? []).map((s) => Math.abs(s.netProfit))),
  );

  constructor() {
    afterNextRender(() => {
      this.chart = this.createChart(this.canvas().nativeElement);
      this.renderChart(this.store.metrics());
    });
    effect(() => this.renderChart(this.store.metrics()));
  }

  ngOnDestroy(): void {
    this.chart?.destroy();
    this.chart = null;
  }

  money(v: number | null | undefined): string {
    const n = v ?? 0;
    const s = Math.abs(n).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    return (n < 0 ? '-$' : '$') + s;
  }

  contribution(v: number): number {
    return (Math.abs(v) / this.maxAbsSymbolProfit()) * 100;
  }

  seedDemo(): void {
    this.seeding.set(true);
    this.api.seedDemo(60).subscribe({
      next: () => {
        this.seeding.set(false);
        this.toast.success('Đã tạo 60 lệnh demo');
      },
      error: (e) => {
        this.seeding.set(false);
        this.toast.error(errorMessage(e));
      },
    });
  }

  private createChart(canvas: HTMLCanvasElement): Chart<'line'> {
    const ctx = canvas.getContext('2d')!;
    const gradient = ctx.createLinearGradient(0, 0, 0, 340);
    gradient.addColorStop(0, 'rgba(37, 99, 235, 0.18)');
    gradient.addColorStop(1, 'rgba(37, 99, 235, 0)');

    return new Chart(ctx, {
      type: 'line',
      data: {
        labels: [],
        datasets: [
          {
            label: 'Equity',
            data: [],
            borderColor: '#2563eb',
            backgroundColor: gradient,
            borderWidth: 2,
            fill: true,
            tension: 0.25,
            pointRadius: 0,
            pointHoverRadius: 4,
            pointHoverBackgroundColor: '#2563eb',
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        animation: { duration: 300 },
        interaction: { mode: 'index', intersect: false },
        plugins: {
          legend: { display: false },
          tooltip: {
            backgroundColor: '#0f172a',
            padding: 10,
            callbacks: {
              label: (c) => ` Equity: ${this.money(c.parsed.y)}`,
              afterLabel: (c) => {
                const p = this.store.metrics()?.equityCurve[c.dataIndex];
                return p && p.ticket ? ` Lệnh #${p.ticket}: ${this.money(p.profit)}` : '';
              },
            },
          },
        },
        scales: {
          x: {
            grid: { display: false },
            ticks: { color: '#64748b', maxTicksLimit: 8, maxRotation: 0 },
            border: { color: '#e2e8f0' },
          },
          y: {
            grid: { color: '#eef2f7' },
            ticks: { color: '#64748b', callback: (v) => this.money(Number(v)) },
            border: { display: false },
          },
        },
      },
    });
  }

  private renderChart(m: PerformanceMetrics | null): void {
    if (!this.chart) return;
    const curve = m?.equityCurve ?? [];
    this.chart.data.labels = curve.map((p) => (p.time ? formatShort(new Date(p.time)) : ''));
    this.chart.data.datasets[0].data = curve.map((p) => p.equity);
    this.chart.update();
  }
}
