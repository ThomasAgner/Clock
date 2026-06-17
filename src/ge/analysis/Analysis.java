package ge.analysis;

import ge.model.ItemMeta;
import ge.model.Quote;

import java.util.List;

/**
 * The full result of analysing one item: the verdict, the supporting numbers,
 * and the concrete trade plan (entry price and target price).
 *
 * <p>Interpretation of the trade plan depends on {@link #signal}:
 * <ul>
 *   <li><b>BULLISH</b> &mdash; {@code entryPrice} is where to <em>buy</em>,
 *       {@code targetPrice} is where to <em>sell</em>.</li>
 *   <li><b>BEARISH</b> &mdash; {@code entryPrice} is where to <em>sell</em>
 *       now, {@code targetPrice} is where to <em>buy back</em>.</li>
 * </ul>
 */
public record Analysis(
        ItemMeta item,
        Quote quote,
        Signal signal,
        double score,            // -100 (very bearish) .. +100 (very bullish)
        double entryPrice,
        double targetPrice,
        double expectedProfit,   // per unit, after tax
        double roiPercent,       // target vs entry, after tax
        double avgDailyVolume,
        double volumeTrendPercent,
        double rsi,
        double smaShort,
        double smaLong,
        double trendSlopePercent,
        double percentB,
        double macdHistogram,
        double atrPercent,       // ATR-style daily volatility, percent
        double historyMid,       // most recent daily mid used for the chart
        double[] series,         // daily mids (oldest first) for the sparkline
        List<String> reasons) {

    /** Absolute conviction, used for ranking opportunities. */
    public double conviction() {
        return Math.abs(score);
    }
}
