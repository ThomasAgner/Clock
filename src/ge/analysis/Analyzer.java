package ge.analysis;

import ge.model.Candle;
import ge.model.Game;
import ge.model.ItemMeta;
import ge.model.Quote;
import ge.util.Fmt;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns price/volume history plus a live quote into a trade verdict.
 *
 * <p>The score blends three families of evidence:
 * <ol>
 *   <li><b>Trend</b> &mdash; moving-average spread, least-squares slope and
 *       rate-of-change establish direction and strength.</li>
 *   <li><b>Stretch</b> &mdash; RSI and Bollinger %B flag overbought/oversold
 *       conditions, nudging the score toward mean reversion at extremes.</li>
 *   <li><b>Participation</b> &mdash; the volume trend scales conviction up when
 *       a move is backed by rising turnover and down when it is fading.</li>
 * </ol>
 * From the verdict it derives a concrete plan: where to enter and where to take
 * profit, using recent support/resistance and Bollinger bands as targets and
 * accounting for the OSRS 2% sell tax.
 */
public final class Analyzer {

    /** Tunable parameters; sensible defaults are provided by {@link #defaults()}. */
    public record Config(
            int shortMa,
            int longMa,
            int rsiPeriod,
            int momentumPeriod,
            int bandPeriod,
            double bandMult,
            int lookback,        // window for support/resistance
            double signalThreshold) {

        public static Config defaults() {
            return new Config(5, 20, 14, 14, 20, 2.0, 30, 22);
        }
    }

    private final Config cfg;

    public Analyzer(Config cfg) {
        this.cfg = cfg;
    }

    /** Run the full analysis. {@code history} must be oldest-first. */
    public Analysis analyze(ItemMeta item, List<Candle> history, Quote quote) {
        double[] mids = history.stream().mapToDouble(Candle::mid).toArray();
        double[] vols = history.stream().mapToDouble(Candle::volume).toArray();

        double smaShort = Indicators.sma(mids, cfg.shortMa());
        double smaLong = Indicators.sma(mids, cfg.longMa());
        double rsi = Indicators.rsi(mids, cfg.rsiPeriod());
        double roc = Indicators.rateOfChange(mids, cfg.momentumPeriod());
        double slope = Indicators.trendSlopePercent(mids, cfg.longMa());
        double pctB = Indicators.percentB(mids, cfg.bandPeriod(), cfg.bandMult());

        double mean = Indicators.sma(mids, cfg.bandPeriod());
        double sd = Indicators.stdDev(mids, cfg.bandPeriod());
        double upperBand = mean + cfg.bandMult() * sd;
        double lowerBand = mean - cfg.bandMult() * sd;
        double resistance = Indicators.highest(mids, cfg.lookback());
        double support = Indicators.lowest(mids, cfg.lookback());
        double volatilityPct = mean == 0 ? 0 : sd / mean * 100.0;

        double avgVolRecent = Indicators.sma(vols, 7);
        double avgVolBaseline = Indicators.sma(vols, 30);
        double volTrend = avgVolBaseline == 0 ? 0 : (avgVolRecent - avgVolBaseline) / avgVolBaseline * 100.0;

        List<String> reasons = new ArrayList<>();

        // --- Trend block ----------------------------------------------------
        double maGap = smaLong == 0 ? 0 : (smaShort - smaLong) / smaLong * 100.0;
        double maScore = clamp(maGap * 6, -30, 30);
        double slopeScore = clamp(slope * 15, -25, 25);
        double rocScore = clamp(roc, -20, 20);
        double trendScore = maScore + slopeScore + rocScore;

        if (maGap > 0.5) reasons.add(String.format("Uptrend: %dd SMA is %s above %dd SMA",
                cfg.shortMa(), Fmt.pctUnsigned(maGap), cfg.longMa()));
        else if (maGap < -0.5) reasons.add(String.format("Downtrend: %dd SMA is %s below %dd SMA",
                cfg.shortMa(), Fmt.pctUnsigned(-maGap), cfg.longMa()));
        if (Math.abs(roc) >= 3) reasons.add(String.format("Momentum %s over %dd", Fmt.pct(roc), cfg.momentumPeriod()));
        if (Math.abs(slope) >= 0.15) reasons.add(String.format("Trend slope %s/day", Fmt.pct(slope)));

        // --- Stretch block (RSI + Bollinger) --------------------------------
        double rsiAdj;
        if (rsi >= 80) {
            rsiAdj = -15;
            reasons.add(String.format("RSI %.0f — overbought, pullback risk", rsi));
        } else if (rsi >= 70) {
            rsiAdj = -7;
            reasons.add(String.format("RSI %.0f — getting hot", rsi));
        } else if (rsi <= 20) {
            rsiAdj = 15;
            reasons.add(String.format("RSI %.0f — oversold, bounce potential", rsi));
        } else if (rsi <= 30) {
            rsiAdj = 7;
            reasons.add(String.format("RSI %.0f — nearing oversold", rsi));
        } else {
            rsiAdj = 0;
        }

        double bandAdj;
        if (pctB >= 1.0) {
            bandAdj = -8;
            reasons.add("Price above upper Bollinger band (stretched)");
        } else if (pctB <= 0.0) {
            bandAdj = 8;
            reasons.add("Price below lower Bollinger band (bargain zone)");
        } else {
            bandAdj = 0;
        }

        double score = trendScore + rsiAdj + bandAdj;

        // --- Participation block (volume confirmation) ----------------------
        if (volTrend > 25 && Math.abs(score) > 5) {
            score *= 1.10;
            reasons.add(String.format("Turnover rising %s vs 30d avg — move is backed by volume", Fmt.pct(volTrend)));
        } else if (volTrend < -30) {
            score *= 0.85;
            reasons.add(String.format("Turnover fading %s vs 30d avg — weak conviction", Fmt.pct(volTrend)));
        }
        score = clamp(score, -100, 100);

        Signal signal;
        if (score >= cfg.signalThreshold()) signal = Signal.BULLISH;
        else if (score <= -cfg.signalThreshold()) signal = Signal.BEARISH;
        else signal = Signal.NEUTRAL;

        // --- Trade plan -----------------------------------------------------
        double entry, target;
        double moveBudget = clamp(volatilityPct * 1.2, 3, 40) / 100.0;
        if (signal == Signal.BEARISH) {
            // Sell into the standing buy side now, aim to repurchase lower.
            entry = quote.instaBuy() > 0 ? quote.instaBuy() : quote.mid();
            double projLower = Math.max(support, lowerBand);
            double volTarget = entry * (1 - moveBudget);
            target = Math.max(Math.min(projLower, volTarget), entry * 0.5);
            if (target >= entry) target = entry * (1 - Math.max(moveBudget, 0.02));
        } else {
            // Bullish or neutral: buy near the sell side, aim to offload higher.
            entry = quote.instaSell() > 0 ? quote.instaSell() : quote.mid();
            double projUpper = Math.min(resistance, upperBand);
            double volTarget = entry * (1 + moveBudget);
            target = Math.min(Math.max(projUpper, volTarget), entry * 1.6);
            if (target <= entry) target = entry * (1 + Math.max(moveBudget, 0.02));
        }

        double expectedProfit;
        double roi;
        if (signal == Signal.BEARISH) {
            double proceeds = entry - tax(entry, item.game());
            expectedProfit = proceeds - target;
            roi = entry == 0 ? 0 : expectedProfit / entry * 100.0;
        } else {
            double proceeds = target - tax(target, item.game());
            expectedProfit = proceeds - entry;
            roi = entry == 0 ? 0 : expectedProfit / entry * 100.0;
        }

        double avgDailyVolume = avgVolRecent > 0 ? avgVolRecent : avgVolBaseline;
        if (avgDailyVolume > 0) {
            reasons.add(String.format("~%s units/day traded", Fmt.gp(avgDailyVolume)));
        }

        return new Analysis(
                item, quote, signal, score, entry, target, expectedProfit, roi,
                avgDailyVolume, volTrend, rsi, smaShort, smaLong, slope, pctB,
                mids.length == 0 ? 0 : mids[mids.length - 1], mids, reasons);
    }

    /** OSRS Grand Exchange sell tax: 2%, none below 100gp, capped at 5M/item. RS3 has none. */
    static double tax(double price, Game game) {
        if (game != Game.OSRS) return 0;
        if (price < 100) return 0;
        return Math.min(Math.floor(price * 0.02), 5_000_000);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
