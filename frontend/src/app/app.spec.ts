import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { App } from './app';
import { TradeStore } from './core/services/trade-store.service';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        // Không mở SSE / gọi API trong unit test
        { provide: TradeStore, useValue: stubStore() },
      ],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render brand and navigation', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.brand')?.textContent).toContain('TradeMonitor');
    expect(el.querySelectorAll('.nav a').length).toBe(3);
  });
});

function stubStore() {
  const sig = <T>(v: T) => Object.assign(() => v, { set: () => {} });
  return {
    start: () => {},
    reload: () => {},
    setSession: () => {},
    sessionId: sig(null),
    sessions: sig([]),
    loading: sig(false),
    error: sig(null),
    lastUpdated: sig(null),
  };
}
