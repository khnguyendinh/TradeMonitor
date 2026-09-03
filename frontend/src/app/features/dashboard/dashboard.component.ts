import { Component, OnInit, computed, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AnalyticsService } from '../../core/services/analytics.service';
import { TradeStreamService } from '../../core/services/trade-stream.service';
import { PerformanceMetrics, TradeRecord } from '../../core/models/trade.model';
import { Chart, registerables } from 'chart.js';

Chart.register(...registerables);

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="dashboard-grid">
      <!-- KPI Cards -->
      <div class="kpi-row">
        <div class="kpi-card glass-panel">
          <h3>Net Profit</h3>
          <div class="value" [class.positive]="(metrics?.netProfit || 0) >= 0" [class.negative]="(metrics?.netProfit || 0) < 0">
            \${{ metrics?.netProfit | number:'1.2-2' }}
          </div>
        </div>
        <div class="kpi-card glass-panel">
          <h3>Win Rate</h3>
          <div class="value">{{ metrics?.winRate | number:'1.1-1' }}%</div>
        </div>
        <div class="kpi-card glass-panel">
          <h3>Profit Factor</h3>
          <div class="value">{{ metrics?.profitFactor | number:'1.2-2' }}</div>
        </div>
        <div class="kpi-card glass-panel">
          <h3>Max Drawdown</h3>
          <div class="value negative">-\${{ metrics?.maxDrawdownAmount | number:'1.2-2' }}</div>
        </div>
      </div>

      <!-- Main Content Area -->
      <div class="main-row">
        <div class="chart-container glass-panel">
          <h3>Equity Curve</h3>
          <canvas id="equityChart"></canvas>
        </div>
        
        <div class="trades-feed glass-panel">
          <h3>Live Trades <span class="badge">{{streamService.trades().length}}</span></h3>
          <div class="table-container">
            <table>
              <thead>
                <tr>
                  <th>Time</th>
                  <th>Symbol</th>
                  <th>Type</th>
                  <th>Volume</th>
                  <th>Profit</th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let trade of streamService.trades()">
                  <td>{{ trade.closeTime | date:'HH:mm:ss' }}</td>
                  <td>{{ trade.symbol }}</td>
                  <td [class.type-buy]="trade.type === 'BUY'" [class.type-sell]="trade.type === 'SELL'">
                    {{ trade.type }}
                  </td>
                  <td>{{ trade.volume }}</td>
                  <td [class.positive]="trade.profit > 0" [class.negative]="trade.profit < 0">
                    \${{ trade.profit | number:'1.2-2' }}
                  </td>
                </tr>
                <tr *ngIf="streamService.trades().length === 0">
                  <td colspan="5" class="empty-state">No trades yet. Upload a log or wait for SSE stream...</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .dashboard-grid {
      display: flex;
      flex-direction: column;
      gap: 24px;
      height: 100%;
    }
    .kpi-row {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 24px;
    }
    .kpi-card {
      padding: 24px;
    }
    .kpi-card h3 {
      font-size: 0.9rem;
      color: var(--text-muted);
      margin-bottom: 12px;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }
    .kpi-card .value {
      font-size: 2rem;
      font-weight: 700;
    }
    .positive { color: var(--success); }
    .negative { color: var(--danger); }
    
    .main-row {
      display: grid;
      grid-template-columns: 2fr 1fr;
      gap: 24px;
      flex: 1;
      min-height: 400px;
    }
    .chart-container, .trades-feed {
      padding: 24px;
      display: flex;
      flex-direction: column;
    }
    .chart-container h3, .trades-feed h3 {
      margin-bottom: 20px;
    }
    canvas {
      flex: 1;
      width: 100% !important;
    }
    
    .badge {
      background: var(--primary);
      color: white;
      font-size: 0.75rem;
      padding: 2px 8px;
      border-radius: 12px;
      margin-left: 8px;
      vertical-align: middle;
    }

    .table-container {
      flex: 1;
      overflow-y: auto;
    }
    table {
      width: 100%;
      border-collapse: collapse;
      font-size: 0.9rem;
    }
    th {
      text-align: left;
      padding: 12px;
      color: var(--text-muted);
      font-weight: 500;
      border-bottom: 1px solid var(--border-color);
      position: sticky;
      top: 0;
      background: var(--bg-card);
      backdrop-filter: blur(8px);
    }
    td {
      padding: 12px;
      border-bottom: 1px solid rgba(255,255,255,0.05);
    }
    .type-buy { color: var(--primary); font-weight: 600; }
    .type-sell { color: #f59e0b; font-weight: 600; }
    .empty-state { text-align: center; color: var(--text-muted); font-style: italic; padding: 30px!important; }
  `]
})
export class DashboardComponent implements OnInit {
  metrics: PerformanceMetrics | null = null;
  chart: any;

  constructor(
    private analyticsService: AnalyticsService,
    public streamService: TradeStreamService
  ) {
    // Reactive Effect to update chart when stream adds new trades
    effect(() => {
      const trades = this.streamService.trades();
      if (trades.length > 0) {
        this.updateChartFromStream(trades);
      }
    });
  }

  ngOnInit() {
    this.fetchMetrics();
    this.initChart();
  }

  fetchMetrics() {
    this.analyticsService.getMetrics().subscribe({
      next: (res) => {
        this.metrics = res;
        this.renderInitialChart(res.equityCurve);
      },
      error: (err) => console.error(err)
    });
  }

  initChart() {
    const ctx = document.getElementById('equityChart') as HTMLCanvasElement;
    this.chart = new Chart(ctx, {
      type: 'line',
      data: {
        labels: [],
        datasets: [{
          label: 'Equity ($)',
          data: [],
          borderColor: '#3b82f6',
          backgroundColor: 'rgba(59, 130, 246, 0.1)',
          borderWidth: 2,
          fill: true,
          tension: 0.4,
          pointRadius: 0
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false }
        },
        scales: {
          y: { grid: { color: 'rgba(255, 255, 255, 0.05)' } },
          x: { grid: { display: false } }
        }
      }
    });
  }

  renderInitialChart(curve: Record<string, number>) {
    if (!curve || !this.chart) return;
    const labels = Object.keys(curve).map(k => new Date(k).toLocaleTimeString());
    const data = Object.values(curve);
    
    this.chart.data.labels = labels;
    this.chart.data.datasets[0].data = data;
    this.chart.update();
  }

  updateChartFromStream(trades: TradeRecord[]) {
    if (!this.chart) return;
    // Just re-fetching metrics is easier for exact equity calculation 
    // or we could append the last trade's profit to the last equity point.
    // For simplicity, we just trigger a full metrics refresh here.
    this.analyticsService.getMetrics().subscribe(res => {
      this.metrics = res;
      this.renderInitialChart(res.equityCurve);
    });
  }
}
