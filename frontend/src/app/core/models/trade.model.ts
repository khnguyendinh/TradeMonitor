export interface TradeRecord {
  id: number;
  ticket: string;
  symbol: string;
  type: string;
  volume: number;
  openPrice: number;
  closePrice: number;
  openTime: string;
  closeTime: string;
  sl: number;
  tp: number;
  commission: number;
  swap: number;
  profit: number;
  sessionId: number;
}

export interface PerformanceMetrics {
  totalTrades: number;
  winningTrades: number;
  losingTrades: number;
  winRate: number;
  grossProfit: number;
  grossLoss: number;
  netProfit: number;
  profitFactor: number;
  maxDrawdownAmount: number;
  maxDrawdownPercentage: number;
  averageWin: number;
  averageLoss: number;
  consecutiveWins: number;
  consecutiveLosses: number;
  equityCurve: Record<string, number>;
}
