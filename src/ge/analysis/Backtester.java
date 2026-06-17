package ge.analysis;

import ge.model.Candle;

import java.util.Arrays;
import java.util.List;

/**
 * Walk-forward backtest of the scoring rule.
 *
 * <p>For every item and every historical day with enough warm-up, the score is
 * computed from data <em>up to that day only</em> and compared against the
 * actual forward return {@code horizon} days later. This measures whether the
 * BULLISH / BEARISH calls would have paid off, with no look-ahead: bullish
 * trades win when price rose, bearish trades win when price fell.
 *
 * <p>The buy-and-hold baseline (average forward return across all days) is the
 * yardstick — a signal only adds value when it beats simply holding.
 */
public final class Backtester {

    public record Result(
            int items,
            int horizon,
            int bullSignals, double bullWinRate, double bullAvgReturn,
            int bearSignals, double bearWinRate, double bearAvgReturn,
            int samples, double baselineAvgReturn) {
    }

    private final Analyzer analyzer;
    private final int warmup;

    public Backtester(Analyzer analyzer) {
        this.analyzer = analyzer;
        // Need enough bars for the longest lookback before the first decision.
        Analyzer.Config c = Analyzer.Config.defaults();
        this.warmup = Math.max(c.longMa(), c.lookback()) + c.shortMa();
    }

    /** Run the backtest over a set of item histories (oldest-first daily candles). */
    public Result run(List<List<Candle>> histories, int horizon) {
        int items = 0;
        long bullN = 0, bullWin = 0, bearN = 0, bearWin = 0, baseN = 0;
        double bullSum = 0, bearSum = 0, baseSum = 0;

        for (List<Candle> history : histories) {
            if (history.size() < warmup + horizon + 1) continue;
            items++;
            double[] mids = history.stream().mapToDouble(Candle::mid).toArray();
            double[] vols = history.stream().mapToDouble(Candle::volume).toArray();

            for (int t = warmup; t + horizon < mids.length; t++) {
                double now = mids[t];
                if (now <= 0) continue;
                double forward = (mids[t + horizon] - now) / now; // signed return of holding long

                baseN++;
                baseSum += forward;

                Signal signal = analyzer.score(
                        Arrays.copyOfRange(mids, 0, t + 1),
                        Arrays.copyOfRange(vols, 0, t + 1), false).signal();

                if (signal == Signal.BULLISH) {
                    bullN++;
                    bullSum += forward;
                    if (forward > 0) bullWin++;
                } else if (signal == Signal.BEARISH) {
                    bearN++;
                    bearSum += -forward; // a bearish call profits when price falls
                    if (forward < 0) bearWin++;
                }
            }
        }

        return new Result(
                items, horizon,
                (int) bullN, rate(bullWin, bullN), avg(bullSum, bullN),
                (int) bearN, rate(bearWin, bearN), avg(bearSum, bearN),
                (int) baseN, avg(baseSum, baseN));
    }

    private static double rate(long win, long n) {
        return n == 0 ? 0 : (double) win / n * 100.0;
    }

    private static double avg(double sum, long n) {
        return n == 0 ? 0 : sum / n * 100.0;
    }
}
