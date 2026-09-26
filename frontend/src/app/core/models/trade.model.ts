export type TradeType = 'BUY' | 'SELL' | 'BALANCE';

export interface TradeRecord {
  id: number;
  ticket: string;
  symbol: string;
  type: TradeType;
  volume: number;
  openPrice: number;
  closePrice: number | null;
  openTime: string;
  closeTime: string | null;
  sl: number | null;
  tp: number | null;
  commission: number;
  swap: number;
  profit: number;
  sessionId: number;
}

export interface TradeSession {
  id: number;
  name: string;
  importTime: string;
  status: 'PROCESSING' | 'COMPLETED' | 'FAILED';
  tradeCount: number;
  message: string | null;
}

export interface EquityPoint {
  time: string;
  equity: number;
  profit: number;
  ticket: string | null;
}

export interface SymbolStat {
  symbol: string;
  trades: number;
  wins: number;
  winRate: number;
  netProfit: number;
  volume: number;
}

export interface PerformanceMetrics {
  totalTrades: number;
  winningTrades: number;
  losingTrades: number;
  winRate: number;
  initialBalance: number;
  currentBalance: number;
  grossProfit: number;
  grossLoss: number;
  netProfit: number;
  totalCommission: number;
  totalSwap: number;
  /** null = vô hạn (không có lệnh lỗ) */
  profitFactor: number | null;
  maxDrawdownAmount: number;
  maxDrawdownPercentage: number;
  currentDrawdownPercentage: number;
  averageWin: number;
  averageLoss: number;
  expectancy: number;
  bestTrade: number;
  worstTrade: number;
  consecutiveWins: number;
  consecutiveLosses: number;
  equityCurve: EquityPoint[];
  symbolStats: SymbolStat[];
}

export interface AppSettings {
  botToken: string;
  chatId: string;
  telegramConfigured: boolean;
  initialBalance: number;
  maxDrawdownPercent: number;
  largeLossAmount: number;
  alertsEnabled: boolean;
}

export interface NewTrade {
  symbol: string;
  type: 'BUY' | 'SELL';
  volume: number | null;
  openPrice: number | null;
  closePrice: number | null;
  openTime: string;
  closeTime: string;
  commission: number | null;
  swap: number | null;
  profit: number | null;
}

/** Lãi/lỗ ròng của một lệnh (profit + commission + swap). */
export function netProfit(t: TradeRecord): number {
  return (t.profit ?? 0) + (t.commission ?? 0) + (t.swap ?? 0);
}
