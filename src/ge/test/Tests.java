package ge.test;

import ge.analysis.Analysis;
import ge.analysis.Analyzer;
import ge.analysis.Backtester;
import ge.analysis.Indicators;
import ge.analysis.Signal;
import ge.model.Candle;
import ge.model.Game;
import ge.model.Quote;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight self-contained test runner for the analysis math — no external
 * test framework. Run with {@code java -cp out ge.test.Tests}; it prints a
 * summary and exits non-zero if anything fails.
 */
public final class Tests {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        indicators();
        taxAndMargin();
        scoring();
        backtest();
        allocation();

        System.out.printf("%n%d passed, %d failed%n", passed, failed);
        if (failed > 0) System.exit(1);
    }

    // --- Indicators ----------------------------------------------------------

    private static void indicators() {
        double[] s = {1, 2, 3, 4, 5};
        approx("sma full", Indicators.sma(s, 5), 3.0);
        approx("sma window", Indicators.sma(s, 3), 4.0);
        approx("stdDev constant", Indicators.stdDev(new double[]{7, 7, 7, 7}, 4), 0.0);

        double[] up = ramp(100, 1, 30);
        double[] down = ramp(100, -1, 30);
        approx("rsi rising", Indicators.rsi(up, 14), 100.0);
        approx("rsi falling", Indicators.rsi(down, 14), 0.0);

        approx("roc", Indicators.rateOfChange(new double[]{100, 110}, 1), 10.0);
        check("slope up positive", Indicators.trendSlopePercent(up, 20) > 0);
        check("slope down negative", Indicators.trendSlopePercent(down, 20) < 0);

        approx("highest", Indicators.highest(s, 5), 5.0);
        approx("lowest", Indicators.lowest(s, 5), 1.0);
        check("percentB high in uptrend", Indicators.percentB(up, 20, 2) > 0.5);

        check("macd hist positive in uptrend", Indicators.macd(growth(100, 1.03, 120), 12, 26, 9).histogram() > 0);
        check("macd hist negative on a sustained drop", Indicators.macd(recentDrop(), 12, 26, 9).histogram() < 0);
        approx("atr constant is zero", Indicators.atrPercent(constant(100, 30), 14), 0.0);
        check("atr positive when moving", Indicators.atrPercent(growth(100, 1.02, 30), 14) > 0);

        double[] upVols = constant(1000, 30);
        check("obv trend positive in uptrend", Indicators.obvTrend(ramp(100, 1, 30), upVols, 14) > 0);
        check("obv trend negative in downtrend", Indicators.obvTrend(ramp(100, -1, 30), upVols, 14) < 0);
        approx("stoch K at top is 100", Indicators.stochasticK(ramp(100, 1, 30), 14), 100.0);
        approx("stoch K at bottom is 0", Indicators.stochasticK(ramp(100, -1, 30), 14), 0.0);
    }

    // --- Tax & flip margin ---------------------------------------------------

    private static void taxAndMargin() {
        approx("osrs tax 2pct", Analyzer.tax(1000, Game.OSRS), 20.0);
        approx("osrs tax under 100 free", Analyzer.tax(99, Game.OSRS), 0.0);
        approx("osrs tax cap 5M", Analyzer.tax(300_000_000, Game.OSRS), 5_000_000.0);
        approx("rs3 no tax", Analyzer.tax(1000, Game.RS3), 0.0);

        approx("osrs margin after tax", Analyzer.flipMargin(new Quote(1000, 950), Game.OSRS), 30.0);
        approx("rs3 margin zero", Analyzer.flipMargin(new Quote(1000, 1000), Game.RS3), 0.0);
    }

    // --- Scoring directionality ---------------------------------------------

    private static void scoring() {
        Analyzer analyzer = new Analyzer(Analyzer.Config.defaults());
        double[] vols = constant(100_000, 120);

        Analyzer.Scored bull = analyzer.score(growth(100, 1.03, 120), vols, false);
        check("strong uptrend is BULLISH", bull.signal() == Signal.BULLISH);

        Analyzer.Scored bear = analyzer.score(growth(100, 0.97, 120), vols, false);
        check("strong downtrend is BEARISH", bear.signal() == Signal.BEARISH);

        Analyzer.Scored flat = analyzer.score(constant(100, 120), vols, false);
        check("flat market is NEUTRAL", flat.signal() == Signal.NEUTRAL);
    }

    // --- Backtest sanity -----------------------------------------------------

    private static void backtest() {
        Analyzer analyzer = new Analyzer(Analyzer.Config.defaults());
        List<Candle> history = new ArrayList<>();
        double[] mids = growth(100, 1.02, 160);
        for (int i = 0; i < mids.length; i++) history.add(new Candle(i * 86_400_000L, mids[i], 100_000));

        Backtester.Result r = new Backtester(analyzer).run(List.of(history), 14);
        check("backtest saw bullish signals", r.bullSignals() > 0);
        approx("uptrend bullish win rate ~100", r.bullWinRate(), 100.0, 0.01);
        check("baseline forward return positive", r.baselineAvgReturn() > 0);
    }

    // --- Capital allocation --------------------------------------------------

    private static void allocation() {
        // Two bullish ideas, equal conviction. Caps are non-binding here, so a
        // 50k budget splits 50/50 by conviction: 250 units of A, 25 units of B.
        List<Analysis> ideas = List.of(
                bullishIdea("A", 100, 10, 1000, 100_000),
                bullishIdea("B", 1000, 50, 100, 100_000));
        ge.analysis.Allocator.Plan plan = ge.analysis.Allocator.plan(ideas, 50_000, 0.10);

        check("allocation has both holdings", plan.holdings().size() == 2);
        approx("allocation deploys full budget", plan.deployed(), 50_000.0, 0.5);
        approx("allocation expected profit", plan.expectedProfit(), 3_750.0, 0.5);
        check("allocation never overspends", plan.deployed() <= plan.budget() + 1e-6);
    }

    private static Analysis bullishIdea(String name, double entry, double profit, int buyLimit, double dailyVol) {
        ge.model.ItemMeta meta = new ge.model.ItemMeta(1, name, Game.OSRS, buyLimit, 0, 0, false);
        Quote q = new Quote(entry, entry);
        return new Analysis(meta, q, Signal.BULLISH, 50, entry, entry + profit, profit,
                profit / entry * 100, dailyVol, 0, 50, 0, 0, 0, 0.5, 0, 0, 0, 50,
                entry, new double[]{entry}, List.of());
    }

    // --- Helpers -------------------------------------------------------------

    private static double[] ramp(double start, double step, int n) {
        double[] a = new double[n];
        for (int i = 0; i < n; i++) a[i] = start + step * i;
        return a;
    }

    private static double[] growth(double start, double factor, int n) {
        double[] a = new double[n];
        double v = start;
        for (int i = 0; i < n; i++) {
            a[i] = v;
            v *= factor;
        }
        return a;
    }

    /** Flat for 100 days, then a sustained 5%/day decline for 20 days. */
    private static double[] recentDrop() {
        double[] a = new double[120];
        for (int i = 0; i < 100; i++) a[i] = 100;
        for (int i = 100; i < 120; i++) a[i] = a[i - 1] * 0.95;
        return a;
    }

    private static double[] constant(double value, int n) {
        double[] a = new double[n];
        java.util.Arrays.fill(a, value);
        return a;
    }

    private static void approx(String name, double actual, double expected) {
        approx(name, actual, expected, 1e-6);
    }

    private static void approx(String name, double actual, double expected, double tol) {
        check(name + " (got " + actual + ", want " + expected + ")", Math.abs(actual - expected) <= tol);
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  PASS  " + name);
        } else {
            failed++;
            System.out.println("  FAIL  " + name);
        }
    }

    private Tests() {
    }
}
