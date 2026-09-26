import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AppSettings } from '../../core/models/trade.model';
import { ApiService, errorMessage } from '../../core/services/api.service';
import { ToastService } from '../../core/services/toast.service';
import { TradeStore } from '../../core/services/trade-store.service';

@Component({
  selector: 'app-settings',
  imports: [FormsModule],
  template: `
    @if (loadError()) {
      <div class="card card-body error">
        Không tải được cấu hình: {{ loadError() }}
        <button class="btn btn-sm" (click)="load()">Thử lại</button>
      </div>
    }

    <form class="grid" (ngSubmit)="save()">
      <!-- Telegram -->
      <section class="card">
        <div class="card-header">
          <h3>Telegram</h3>
          <span class="tag" [class.tag-ok]="configured()" [class.tag-wait]="!configured()">
            {{ configured() ? 'Đã cấu hình' : 'Chưa cấu hình' }}
          </span>
        </div>
        <div class="card-body stack">
          <div class="field">
            <label>Bot token</label>
            <input class="input mono" name="botToken" [(ngModel)]="form.botToken" autocomplete="off"
                   placeholder="123456789:AA..." />
            <span class="hint">Tạo bot qua &#64;BotFather. Token đang lưu được che bớt; để nguyên nếu không đổi.</span>
          </div>
          <div class="field">
            <label>Chat ID</label>
            <input class="input mono" name="chatId" [(ngModel)]="form.chatId" placeholder="-1001234567890" />
            <span class="hint">Gửi 1 tin cho bot rồi mở https://api.telegram.org/bot&lt;token&gt;/getUpdates để lấy chat.id.</span>
          </div>

          <div class="divider"></div>

          <div class="field">
            <label>Gửi tin nhắn thử</label>
            <div class="inline">
              <input class="input" name="testMessage" [(ngModel)]="testMessage" />
              <button type="button" class="btn" (click)="sendTest()" [disabled]="testing()">
                {{ testing() ? 'Đang gửi...' : 'Gửi thử' }}
              </button>
            </div>
            <span class="hint">Nhớ bấm "Lưu cài đặt" trước khi gửi thử nếu vừa đổi token / chat ID.</span>
          </div>
        </div>
      </section>

      <!-- Alerts -->
      <section class="card">
        <div class="card-header"><h3>Cảnh báo & tính toán</h3></div>
        <div class="card-body stack">
          <label class="checkbox">
            <input type="checkbox" name="alertsEnabled" [(ngModel)]="form.alertsEnabled" />
            Bật cảnh báo tự động qua Telegram
          </label>
          <div class="field">
            <label>Ngưỡng drawdown (%)</label>
            <input class="input" type="number" step="0.1" min="0.1" max="99" name="maxDrawdownPercent"
                   [(ngModel)]="form.maxDrawdownPercent" required />
            <span class="hint">Gửi cảnh báo khi drawdown hiện tại (từ đỉnh equity) vượt ngưỡng. Chỉ báo lại sau khi đã hồi phục dưới ngưỡng.</span>
          </div>
          <div class="field">
            <label>Ngưỡng lỗ lớn cho 1 lệnh ($)</label>
            <input class="input" type="number" step="1" min="1" name="largeLossAmount"
                   [(ngModel)]="form.largeLossAmount" required />
            <span class="hint">Lệnh có lỗ ròng (gồm phí, swap) lớn hơn hoặc bằng mức này sẽ được báo.</span>
          </div>
          <div class="field">
            <label>Vốn ban đầu ($)</label>
            <input class="input" type="number" step="1" min="1" name="initialBalance"
                   [(ngModel)]="form.initialBalance" required />
            <span class="hint">Dùng tính equity & drawdown khi dữ liệu không có giao dịch nạp tiền (balance).</span>
          </div>
        </div>
      </section>

      <div class="save-bar">
        <button type="button" class="btn" (click)="load()" [disabled]="saving()">Hoàn tác</button>
        <button type="submit" class="btn btn-primary" [disabled]="saving() || !loaded()">
          {{ saving() ? 'Đang lưu...' : 'Lưu cài đặt' }}
        </button>
      </div>
    </form>
  `,
  styles: `
    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(340px, 1fr)); gap: 20px; align-items: start; }
    .stack { display: flex; flex-direction: column; gap: 16px; }
    .inline { display: flex; gap: 8px; }
    .mono { font-family: ui-monospace, SFMono-Regular, Consolas, monospace; font-size: 13px; }
    .divider { height: 1px; background: var(--border); }
    .save-bar { grid-column: 1 / -1; display: flex; justify-content: flex-end; gap: 8px; }
    .error { display: flex; align-items: center; gap: 12px; margin-bottom: 20px; color: var(--danger); background: var(--danger-soft); }
  `,
})
export class SettingsComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  private readonly store = inject(TradeStore);

  readonly loaded = signal(false);
  readonly loadError = signal<string | null>(null);
  readonly configured = signal(false);
  readonly saving = signal(false);
  readonly testing = signal(false);

  form: AppSettings = {
    botToken: '',
    chatId: '',
    telegramConfigured: false,
    initialBalance: 10000,
    maxDrawdownPercent: 10,
    largeLossAmount: 500,
    alertsEnabled: true,
  };
  testMessage = 'Test alert from TradeMonitor! 🚀';

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.getSettings().subscribe({
      next: (s) => this.apply(s),
      error: (e) => this.loadError.set(errorMessage(e)),
    });
  }

  save(): void {
    this.saving.set(true);
    this.api.updateSettings(this.form).subscribe({
      next: (s) => {
        this.apply(s);
        this.saving.set(false);
        this.toast.success('Đã lưu cài đặt');
        this.store.reload(); // vốn ban đầu thay đổi => KPI thay đổi
      },
      error: (e) => {
        this.saving.set(false);
        this.toast.error(errorMessage(e));
      },
    });
  }

  sendTest(): void {
    this.testing.set(true);
    this.api.testTelegram(this.testMessage).subscribe({
      next: (r) => {
        this.testing.set(false);
        this.toast.success(r.message);
      },
      error: (e) => {
        this.testing.set(false);
        this.toast.error(errorMessage(e));
      },
    });
  }

  private apply(s: AppSettings): void {
    this.form = { ...s };
    this.configured.set(s.telegramConfigured);
    this.loadError.set(null);
    this.loaded.set(true);
  }
}
