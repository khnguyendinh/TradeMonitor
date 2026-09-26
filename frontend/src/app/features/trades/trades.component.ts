import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NewTrade, TradeRecord, TradeSession, netProfit } from '../../core/models/trade.model';
import { TradeStore } from '../../core/services/trade-store.service';
import { ApiService, errorMessage } from '../../core/services/api.service';
import { ToastService } from '../../core/services/toast.service';

const PAGE = 100;

function toLocalInput(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function emptyTrade(): NewTrade {
  const now = new Date();
  return {
    symbol: '',
    type: 'BUY',
    volume: 0.1,
    openPrice: null,
    closePrice: null,
    openTime: toLocalInput(new Date(now.getTime() - 3600_000)),
    closeTime: toLocalInput(now),
    commission: null,
    swap: null,
    profit: null,
  };
}

@Component({
  selector: 'app-trades',
  imports: [DatePipe, DecimalPipe, FormsModule],
  template: `
    <section class="top-row">
      <!-- Upload -->
      <div class="card">
        <div class="card-header"><h3>Upload báo cáo MT5</h3></div>
        <div class="card-body">
          <label class="dropzone" [class.dragging]="dragging()" [class.busy]="uploading()"
                 (dragover)="onDragOver($event)" (dragleave)="dragging.set(false)" (drop)="onDrop($event)">
            <input type="file" accept=".htm,.html,.csv,.txt" (change)="onFilePicked($event)" [disabled]="uploading()" hidden />
            <svg viewBox="0 0 24 24" width="30" height="30" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" /><polyline points="17 8 12 3 7 8" /><line x1="12" y1="3" x2="12" y2="15" />
            </svg>
            @if (uploading()) {
              <strong>Đang xử lý {{ uploadName() }}...</strong>
            } @else {
              <strong>Kéo thả file vào đây hoặc bấm để chọn</strong>
              <span class="muted">HTML "Trade History Report" / Strategy Tester của MT5, hoặc CSV (tối đa 20MB)</span>
            }
          </label>
          <details class="help">
            <summary>Cách xuất báo cáo từ MT5</summary>
            <ol>
              <li>Mở tab <b>History</b> (Toolbox → Lịch sử).</li>
              <li>Chuột phải → chọn khoảng thời gian → <b>Report → HTML</b> (hoặc Open XML).</li>
              <li>Upload file .html vừa lưu. Lệnh đã có (trùng ticket) sẽ tự bỏ qua.</li>
            </ol>
            <p class="muted">CSV cần dòng tiêu đề có tối thiểu các cột <code>Symbol</code>, <code>Type</code>, <code>Profit</code>
              (nên có thêm Time, Volume, Price, Commission, Swap).</p>
          </details>
        </div>
      </div>

      <!-- Manual -->
      <div class="card">
        <div class="card-header">
          <h3>Nhập lệnh tay</h3>
          <button class="btn btn-sm" (click)="seedDemo()" [disabled]="seeding()">
            {{ seeding() ? 'Đang tạo...' : '+ 30 lệnh demo' }}
          </button>
        </div>
        <form class="card-body form-grid" (ngSubmit)="addTrade()" #f="ngForm">
          <div class="field">
            <label>Mã (symbol) *</label>
            <input class="input" name="symbol" [(ngModel)]="form.symbol" required placeholder="EURUSD" list="symbol-list" />
            <datalist id="symbol-list">
              @for (s of store.symbols(); track s) { <option [value]="s"></option> }
            </datalist>
          </div>
          <div class="field">
            <label>Loại *</label>
            <select class="input" name="type" [(ngModel)]="form.type">
              <option value="BUY">BUY</option>
              <option value="SELL">SELL</option>
            </select>
          </div>
          <div class="field">
            <label>Khối lượng (lot) *</label>
            <input class="input" type="number" step="0.01" min="0.01" name="volume" [(ngModel)]="form.volume" required />
          </div>
          <div class="field">
            <label>Lãi/lỗ (profit) *</label>
            <input class="input" type="number" step="0.01" name="profit" [(ngModel)]="form.profit" required placeholder="-25.50" />
          </div>
          <div class="field">
            <label>Giá mở</label>
            <input class="input" type="number" step="any" name="openPrice" [(ngModel)]="form.openPrice" />
          </div>
          <div class="field">
            <label>Giá đóng</label>
            <input class="input" type="number" step="any" name="closePrice" [(ngModel)]="form.closePrice" />
          </div>
          <div class="field">
            <label>Mở lúc</label>
            <input class="input" type="datetime-local" name="openTime" [(ngModel)]="form.openTime" />
          </div>
          <div class="field">
            <label>Đóng lúc</label>
            <input class="input" type="datetime-local" name="closeTime" [(ngModel)]="form.closeTime" />
          </div>
          <div class="field">
            <label>Commission</label>
            <input class="input" type="number" step="0.01" name="commission" [(ngModel)]="form.commission" placeholder="0" />
          </div>
          <div class="field">
            <label>Swap</label>
            <input class="input" type="number" step="0.01" name="swap" [(ngModel)]="form.swap" placeholder="0" />
          </div>
          <div class="form-actions">
            <button type="button" class="btn" (click)="resetForm()">Xoá form</button>
            <button type="submit" class="btn btn-primary" [disabled]="saving() || f.invalid">
              {{ saving() ? 'Đang lưu...' : 'Thêm lệnh' }}
            </button>
          </div>
        </form>
      </div>
    </section>

    <!-- Sessions -->
    <section class="card">
      <div class="card-header">
        <h3>Phiên import <span class="count">{{ store.sessions().length }}</span></h3>
      </div>
      <div class="table-wrap sessions">
        <table class="table">
          <thead>
            <tr><th>#</th><th>Tên</th><th>Thời gian</th><th>Trạng thái</th><th class="right">Số lệnh</th><th>Ghi chú</th><th></th></tr>
          </thead>
          <tbody>
            @for (s of store.sessions(); track s.id) {
              <tr [class.selected]="s.id === store.sessionId()">
                <td class="muted num">{{ s.id }}</td>
                <td><strong>{{ s.name }}</strong></td>
                <td class="num">{{ s.importTime | date: 'dd/MM/yyyy HH:mm' }}</td>
                <td>
                  <span class="tag" [class.tag-ok]="s.status === 'COMPLETED'" [class.tag-fail]="s.status === 'FAILED'"
                        [class.tag-wait]="s.status === 'PROCESSING'">{{ statusLabel(s) }}</span>
                </td>
                <td class="right num">{{ s.tradeCount }}</td>
                <td class="muted msg" [title]="s.message ?? ''">{{ s.message }}</td>
                <td class="right">
                  <div class="actions-cell">
                    <button class="btn btn-sm" (click)="store.setSession(s.id === store.sessionId() ? null : s.id)">
                      {{ s.id === store.sessionId() ? 'Bỏ lọc' : 'Lọc' }}
                    </button>
                    <button class="btn btn-sm btn-danger" (click)="deleteSession(s)">Xoá</button>
                  </div>
                </td>
              </tr>
            } @empty {
              <tr><td colspan="7" class="empty-state">Chưa có phiên nào. Upload file hoặc nhập lệnh để bắt đầu.</td></tr>
            }
          </tbody>
        </table>
      </div>
    </section>

    <!-- Trades -->
    <section class="card">
      <div class="card-header wrap">
        <h3>Danh sách lệnh <span class="count">{{ filtered().length }}</span></h3>
        <div class="filters">
          <input class="input" placeholder="Tìm ticket..." [ngModel]="search()" (ngModelChange)="search.set($event); limit.set(PAGE)" />
          <select class="input" [ngModel]="symbolFilter()" (ngModelChange)="symbolFilter.set($event); limit.set(PAGE)">
            <option value="">Mọi mã</option>
            @for (s of store.symbols(); track s) { <option [value]="s">{{ s }}</option> }
          </select>
          <select class="input" [ngModel]="typeFilter()" (ngModelChange)="typeFilter.set($event); limit.set(PAGE)">
            <option value="">Mọi loại</option>
            <option value="BUY">BUY</option>
            <option value="SELL">SELL</option>
            <option value="WIN">Chỉ lệnh lãi</option>
            <option value="LOSS">Chỉ lệnh lỗ</option>
            <option value="BALANCE">Nạp/rút</option>
          </select>
        </div>
      </div>
      <div class="table-wrap">
        <table class="table">
          <thead>
            <tr>
              <th>Ticket</th><th>Mở lúc</th><th>Đóng lúc</th><th>Mã</th><th>Loại</th>
              <th class="right">Lot</th><th class="right">Giá mở</th><th class="right">Giá đóng</th>
              <th class="right">Phí + swap</th><th class="right">Profit</th><th class="right">Ròng</th><th></th>
            </tr>
          </thead>
          <tbody>
            @for (t of visible(); track t.id) {
              <tr [class.fresh]="store.freshIds().has(t.id)">
                <td class="muted num">{{ t.ticket }}</td>
                <td class="num">{{ t.openTime | date: 'dd/MM/yy HH:mm' }}</td>
                <td class="num">{{ t.closeTime | date: 'dd/MM/yy HH:mm' }}</td>
                <td><strong>{{ t.symbol }}</strong></td>
                <td>
                  <span class="tag" [class.tag-buy]="t.type === 'BUY'" [class.tag-sell]="t.type === 'SELL'"
                        [class.tag-balance]="t.type === 'BALANCE'">{{ t.type === 'BALANCE' ? 'NẠP/RÚT' : t.type }}</span>
                </td>
                <td class="right num">{{ t.volume | number: '1.2-2' }}</td>
                <td class="right num">{{ t.openPrice | number: '1.2-5' }}</td>
                <td class="right num">{{ t.closePrice != null ? (t.closePrice | number: '1.2-5') : '—' }}</td>
                <td class="right num muted">{{ (t.commission + t.swap) | number: '1.2-2' }}</td>
                <td class="right num">{{ t.profit | number: '1.2-2' }}</td>
                <td class="right num" [class.positive]="net(t) > 0" [class.negative]="net(t) < 0"><strong>{{ net(t) | number: '1.2-2' }}</strong></td>
                <td class="right"><button class="btn btn-sm btn-danger icon" title="Xoá lệnh" (click)="deleteTrade(t)">✕</button></td>
              </tr>
            } @empty {
              <tr><td colspan="12" class="empty-state">Không có lệnh phù hợp</td></tr>
            }
          </tbody>
        </table>
      </div>
      @if (filtered().length > visible().length) {
        <div class="more">
          <button class="btn" (click)="limit.set(limit() + PAGE)">
            Xem thêm ({{ visible().length }}/{{ filtered().length }})
          </button>
        </div>
      }
    </section>
  `,
  styles: `
    :host { display: flex; flex-direction: column; gap: 20px; }
    .top-row { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1.3fr); gap: 20px; align-items: start; }

    .dropzone {
      display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 6px;
      padding: 32px 16px; border: 2px dashed var(--border-strong); border-radius: var(--radius);
      background: var(--surface-2); color: var(--text-muted); text-align: center; cursor: pointer;
      transition: border-color .15s, background .15s;
    }
    .dropzone strong { color: var(--text); }
    .dropzone:hover, .dropzone.dragging { border-color: var(--primary); background: var(--primary-soft); color: var(--primary); }
    .dropzone.busy { cursor: progress; opacity: .7; }
    .help { margin-top: 14px; font-size: 13px; color: var(--text-2); }
    .help summary { cursor: pointer; font-weight: 500; color: var(--primary); }
    .help ol { margin: 8px 0 8px 20px; display: flex; flex-direction: column; gap: 2px; }
    code { background: var(--surface-hover); padding: 1px 5px; border-radius: 4px; font-size: 12px; }

    .form-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 12px 14px; }
    .form-actions { grid-column: 1 / -1; display: flex; justify-content: flex-end; gap: 8px; }

    .count {
      display: inline-block; margin-left: 6px; padding: 0 8px; border-radius: 999px;
      background: var(--surface-hover); color: var(--text-muted); font-size: 12px; font-weight: 600;
    }
    .sessions { max-height: 320px; }
    tr.selected td { background: var(--primary-soft); }
    .msg { max-width: 360px; overflow: hidden; text-overflow: ellipsis; }
    .actions-cell { display: flex; gap: 6px; justify-content: flex-end; }
    .btn.icon { width: 28px; padding: 0; }

    .card-header.wrap { flex-wrap: wrap; }
    .filters { display: flex; gap: 8px; flex-wrap: wrap; }
    .filters .input { width: 160px; }
    .more { display: flex; justify-content: center; padding: 14px; border-top: 1px solid var(--border); }

    tr.fresh td { animation: flash 2.5s ease-out; }
    @keyframes flash { from { background: #fef9c3; } to { background: transparent; } }

    @media (max-width: 1100px) { .top-row { grid-template-columns: minmax(0, 1fr); } }
    @media (max-width: 600px) { .filters, .filters .input { width: 100%; } }
  `,
})
export class TradesComponent {
  readonly store = inject(TradeStore);
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);

  readonly PAGE = PAGE;
  readonly net = netProfit;

  readonly dragging = signal(false);
  readonly uploading = signal(false);
  readonly uploadName = signal('');
  readonly saving = signal(false);
  readonly seeding = signal(false);

  readonly search = signal('');
  readonly symbolFilter = signal('');
  readonly typeFilter = signal('');
  readonly limit = signal(PAGE);

  form: NewTrade = emptyTrade();

  readonly filtered = computed(() => {
    const q = this.search().trim().toLowerCase();
    const sym = this.symbolFilter();
    const type = this.typeFilter();
    return this.store.trades().filter((t) => {
      if (q && !t.ticket.toLowerCase().includes(q)) return false;
      if (sym && t.symbol !== sym) return false;
      switch (type) {
        case 'BUY':
        case 'SELL':
        case 'BALANCE':
          return t.type === type;
        case 'WIN':
          return t.type !== 'BALANCE' && netProfit(t) > 0;
        case 'LOSS':
          return t.type !== 'BALANCE' && netProfit(t) < 0;
        default:
          return true;
      }
    });
  });

  readonly visible = computed(() => this.filtered().slice(0, this.limit()));

  // ---------------------------------------------------------------- upload

  onDragOver(e: DragEvent): void {
    e.preventDefault();
    this.dragging.set(true);
  }

  onDrop(e: DragEvent): void {
    e.preventDefault();
    this.dragging.set(false);
    const file = e.dataTransfer?.files?.[0];
    if (file) this.upload(file);
  }

  onFilePicked(e: Event): void {
    const input = e.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) this.upload(file);
    input.value = ''; // cho phép chọn lại cùng file
  }

  private upload(file: File): void {
    if (this.uploading()) return;
    if (file.size > 20 * 1024 * 1024) {
      this.toast.error('File vượt quá 20MB');
      return;
    }
    this.uploading.set(true);
    this.uploadName.set(file.name);
    this.api.upload(file).subscribe({
      next: (s) => {
        this.uploading.set(false);
        this.toast.success(`Đã import "${s.name}": ${s.message ?? s.tradeCount + ' lệnh'}`);
        this.store.reload();
      },
      error: (e) => {
        this.uploading.set(false);
        this.toast.error('Import thất bại: ' + errorMessage(e));
        this.store.reload();
      },
    });
  }

  // ---------------------------------------------------------------- manual

  addTrade(): void {
    const f = this.form;
    if (!f.symbol.trim() || f.volume == null || f.profit == null) {
      this.toast.error('Nhập đủ Symbol, Khối lượng và Lãi/lỗ');
      return;
    }
    this.saving.set(true);
    this.api.addTrade({ ...f, symbol: f.symbol.trim().toUpperCase() }).subscribe({
      next: (t) => {
        this.saving.set(false);
        this.toast.success(`Đã thêm lệnh ${t.symbol} ${t.type}`);
        this.resetForm();
      },
      error: (e) => {
        this.saving.set(false);
        this.toast.error(errorMessage(e));
      },
    });
  }

  resetForm(): void {
    this.form = emptyTrade();
  }

  seedDemo(): void {
    this.seeding.set(true);
    this.api.seedDemo(30).subscribe({
      next: () => {
        this.seeding.set(false);
        this.toast.success('Đã tạo 30 lệnh demo');
      },
      error: (e) => {
        this.seeding.set(false);
        this.toast.error(errorMessage(e));
      },
    });
  }

  // ---------------------------------------------------------------- delete

  deleteSession(s: TradeSession): void {
    if (!confirm(`Xoá phiên "${s.name}" cùng ${s.tradeCount} lệnh? Không thể hoàn tác.`)) return;
    this.api.deleteSession(s.id).subscribe({
      next: () => this.toast.success(`Đã xoá phiên "${s.name}"`),
      error: (e) => this.toast.error(errorMessage(e)),
    });
  }

  deleteTrade(t: TradeRecord): void {
    if (!confirm(`Xoá lệnh #${t.ticket} (${t.symbol})?`)) return;
    this.api.deleteTrade(t.id).subscribe({
      next: () => this.toast.success(`Đã xoá lệnh #${t.ticket}`),
      error: (e) => this.toast.error(errorMessage(e)),
    });
  }

  statusLabel(s: TradeSession): string {
    return { COMPLETED: 'Hoàn tất', FAILED: 'Lỗi', PROCESSING: 'Đang xử lý' }[s.status] ?? s.status;
  }
}
