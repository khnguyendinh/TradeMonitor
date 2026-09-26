import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  {
    path: 'dashboard',
    loadComponent: () => import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
    data: { title: 'Tổng quan hiệu suất', subtitle: 'KPI, đường equity và thống kê theo mã giao dịch' },
  },
  {
    path: 'trades',
    loadComponent: () => import('./features/trades/trades.component').then((m) => m.TradesComponent),
    data: { title: 'Lệnh & Upload', subtitle: 'Import báo cáo MT5, nhập lệnh tay và quản lý các phiên import' },
  },
  {
    path: 'settings',
    loadComponent: () => import('./features/settings/settings.component').then((m) => m.SettingsComponent),
    data: { title: 'Cài đặt & Cảnh báo', subtitle: 'Kết nối Telegram và ngưỡng cảnh báo tự động' },
  },
  { path: '**', redirectTo: 'dashboard' },
];
