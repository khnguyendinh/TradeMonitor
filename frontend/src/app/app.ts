import { Component, OnInit } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { TradeStreamService } from './core/services/trade-stream.service';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, CommonModule],
  template: `
    <div class="app-layout">
      <nav class="sidebar glass-panel">
        <div class="logo">
          <h2>TradeMonitor</h2>
        </div>
        <ul class="nav-links">
          <li class="active"><a href="#">Dashboard</a></li>
          <li><a href="#">Logs & Upload</a></li>
          <li><a href="#">Analytics</a></li>
          <li><a href="#">Settings & Alerts</a></li>
        </ul>
        <div class="status-badge" [ngClass]="streamService.connectionStatus()">
          <span class="dot"></span>
          {{ streamService.connectionStatus() }}
        </div>
      </nav>
      
      <main class="main-content">
        <header class="topbar glass-panel">
          <h1>Trading Performance Dashboard</h1>
        </header>
        <div class="content-wrapper">
           <router-outlet></router-outlet>
        </div>
      </main>
    </div>
  `,
  styles: [`
    .app-layout {
      display: flex;
      height: 100vh;
      overflow: hidden;
    }
    
    .sidebar {
      width: 260px;
      margin: 16px 0 16px 16px;
      display: flex;
      flex-direction: column;
      padding: 24px;
    }

    .logo h2 {
      font-weight: 700;
      background: linear-gradient(135deg, var(--primary), #60a5fa);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      margin-bottom: 40px;
    }

    .nav-links {
      list-style: none;
      flex: 1;
    }

    .nav-links li {
      margin-bottom: 12px;
    }

    .nav-links a {
      display: block;
      color: var(--text-muted);
      text-decoration: none;
      padding: 12px 16px;
      border-radius: 8px;
      transition: all 0.2s ease;
      font-weight: 500;
    }

    .nav-links li.active a, .nav-links a:hover {
      background: rgba(59, 130, 246, 0.1);
      color: var(--primary);
    }

    .status-badge {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 12px;
      border-radius: 8px;
      font-size: 0.85rem;
      font-weight: 600;
      background: rgba(0,0,0,0.2);
    }
    .status-badge.CONNECTED { color: var(--success); }
    .status-badge.CONNECTING { color: #f59e0b; }
    .status-badge.DISCONNECTED { color: var(--danger); }
    
    .dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
    }
    .CONNECTED .dot { background: var(--success); box-shadow: 0 0 8px var(--success); }
    .CONNECTING .dot { background: #f59e0b; }
    .DISCONNECTED .dot { background: var(--danger); }

    .main-content {
      flex: 1;
      display: flex;
      flex-direction: column;
      padding: 16px;
      overflow: hidden;
    }

    .topbar {
      padding: 20px 32px;
      margin-bottom: 24px;
    }
    
    .topbar h1 {
      font-size: 1.5rem;
      font-weight: 600;
    }

    .content-wrapper {
      flex: 1;
      overflow-y: auto;
      padding-right: 8px;
    }
  `]
})
export class App {
  constructor(public streamService: TradeStreamService) {}

  ngOnInit() {
    // Start SSE connection when app starts
    this.streamService.connectStream();
  }
}
