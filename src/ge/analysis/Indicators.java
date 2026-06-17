package ge.analysis;

/**
 * Pure, side-effect-free technical-analysis primitives.
 *
 * <p>Every method operates on a {@code double[]} series ordered oldest-first
 * (so the last element is the most recent observation). They are the building
 * blocks the {@link Analyzer} combines into a trade signal.
 */
public final class Indicators {

    private Indicators() {
    }

    /** Simple moving average of the last {@code period} values. */
    public static double sma(double[] series, int period) {
        int n = series.length;
        if (n == 0) return Double.NaN;
        int p = Math.min(period, n);
        double sum = 0;
        for (int i = n - p; i < n; i++) sum += series[i];
        return sum / p;
    }

    /** Exponential moving average over the whole series with the given period. */
    public static double ema(double[] series, int period) {
        if (series.length == 0) return Double.NaN;
        double k = 2.0 / (period + 1);
        double ema = series[0];
        for (int i = 1; i < series.length; i++) {
            ema = series[i] * k + ema * (1 - k);
        }
        return ema;
    }

    /** Per-point EMA series (same length as input), seeded with the first value. */
    public static double[] emaSeries(double[] series, int period) {
        double[] out = new double[series.length];
        if (series.length == 0) return out;
        double k = 2.0 / (period + 1);
        double ema = series[0];
        out[0] = ema;
        for (int i = 1; i < series.length; i++) {
            ema = series[i] * k + ema * (1 - k);
            out[i] = ema;
        }
        return out;
    }

    /** MACD line, signal line and histogram (the actionable part is the histogram sign). */
    public record Macd(double macd, double signal, double histogram) {
    }

    public static Macd macd(double[] series, int fast, int slow, int signalPeriod) {
        if (series.length < slow) return new Macd(0, 0, 0);
        double[] emaFast = emaSeries(series, fast);
        double[] emaSlow = emaSeries(series, slow);
        double[] macdLine = new double[series.length];
        for (int i = 0; i < series.length; i++) macdLine[i] = emaFast[i] - emaSlow[i];
        double[] signalLine = emaSeries(macdLine, signalPeriod);
        int last = series.length - 1;
        return new Macd(macdLine[last], signalLine[last], macdLine[last] - signalLine[last]);
    }

    /**
     * Average True Range as a percent, approximated on a close-only series:
     * the mean absolute day-over-day change over the last {@code period} bars,
     * relative to price. (True OHLC ranges are not published per day.)
     */
    public static double atrPercent(double[] series, int period) {
        int n = series.length;
        int p = Math.min(period, n - 1);
        if (p < 1) return 0;
        double sum = 0;
        int count = 0;
        for (int i = n - p; i < n; i++) {
            if (series[i - 1] != 0) {
                sum += Math.abs(series[i] - series[i - 1]) / series[i - 1];
                count++;
            }
        }
        return count == 0 ? 0 : sum / count * 100.0;
    }

    /** Cumulative On-Balance Volume: volume added on up days, subtracted on down days. */
    public static double[] obvSeries(double[] mids, double[] vols) {
        double[] obv = new double[mids.length];
        if (mids.length == 0) return obv;
        for (int i = 1; i < mids.length; i++) {
            double dir = Double.compare(mids[i], mids[i - 1]); // +1 up, -1 down, 0 flat
            obv[i] = obv[i - 1] + dir * (i < vols.length ? vols[i] : 0);
        }
        return obv;
    }

    /**
     * Net OBV accumulation over the last {@code period} days, normalised by the
     * volume traded in that window. Roughly in [-1, 1]: positive means buyers
     * have been accumulating (bullish), negative means distribution (bearish).
     */
    public static double obvTrend(double[] mids, double[] vols, int period) {
        int n = mids.length;
        int p = Math.min(period, n - 1);
        if (p < 1) return 0;
        double[] obv = obvSeries(mids, vols);
        double traded = 0;
        for (int i = n - p; i < n; i++) traded += (i < vols.length ? vols[i] : 0);
        if (traded == 0) return 0;
        return (obv[n - 1] - obv[n - 1 - p]) / traded;
    }

    /**
     * Stochastic oscillator %K over {@code period} on the close series:
     * where the latest close sits between the period low and high, 0..100.
     */
    public static double stochasticK(double[] series, int period) {
        double hi = highest(series, period);
        double lo = lowest(series, period);
        if (hi == lo) return 50;
        double last = series[series.length - 1];
        return (last - lo) / (hi - lo) * 100.0;
    }

    /** Sample standard deviation of the last {@code period} values. */
    public static double stdDev(double[] series, int period) {
        int n = series.length;
        int p = Math.min(period, n);
        if (p < 2) return 0;
        double mean = sma(series, p);
        double sq = 0;
        for (int i = n - p; i < n; i++) {
            double d = series[i] - mean;
            sq += d * d;
        }
        return Math.sqrt(sq / (p - 1));
    }

    /**
     * Wilder's Relative Strength Index over {@code period} (typically 14).
     * Returns a value in [0,100]; 50 when the series is too short.
     */
    public static double rsi(double[] series, int period) {
        int n = series.length;
        if (n < period + 1) return 50;
        double gain = 0, loss = 0;
        // Seed with the first `period` changes.
        for (int i = n - period; i < n; i++) {
            double change = series[i] - series[i - 1];
            if (change >= 0) gain += change;
            else loss -= change;
        }
        double avgGain = gain / period;
        double avgLoss = loss / period;
        if (avgLoss == 0) return 100;
        double rs = avgGain / avgLoss;
        return 100 - (100 / (1 + rs));
    }

    /** Percentage rate of change over the last {@code period} observations. */
    public static double rateOfChange(double[] series, int period) {
        int n = series.length;
        if (n <= period) return 0;
        double past = series[n - 1 - period];
        double now = series[n - 1];
        if (past == 0) return 0;
        return (now - past) / past * 100.0;
    }

    /**
     * Slope of the least-squares line fitted to the last {@code period} points,
     * expressed as percent-per-day relative to the series mean. Positive means
     * an upward-sloping trend.
     */
    public static double trendSlopePercent(double[] series, int period) {
        int n = series.length;
        int p = Math.min(period, n);
        if (p < 2) return 0;
        int start = n - p;
        double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
        for (int k = 0; k < p; k++) {
            double x = k;
            double y = series[start + k];
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumXX += x * x;
        }
        double denom = p * sumXX - sumX * sumX;
        if (denom == 0) return 0;
        double slope = (p * sumXY - sumX * sumY) / denom;
        double mean = sumY / p;
        if (mean == 0) return 0;
        return slope / mean * 100.0;
    }

    /** Highest value over the last {@code period} observations (resistance). */
    public static double highest(double[] series, int period) {
        int n = series.length;
        int p = Math.min(period, n);
        double max = Double.NEGATIVE_INFINITY;
        for (int i = n - p; i < n; i++) max = Math.max(max, series[i]);
        return max;
    }

    /** Lowest value over the last {@code period} observations (support). */
    public static double lowest(double[] series, int period) {
        int n = series.length;
        int p = Math.min(period, n);
        double min = Double.POSITIVE_INFINITY;
        for (int i = n - p; i < n; i++) min = Math.min(min, series[i]);
        return min;
    }

    /**
     * Bollinger %B: where the latest price sits within the band
     * (mean ± {@code mult}·σ). 0 = lower band, 1 = upper band; can exceed
     * [0,1] when price pierces a band.
     */
    public static double percentB(double[] series, int period, double mult) {
        double mean = sma(series, period);
        double sd = stdDev(series, period);
        if (sd == 0) return 0.5;
        double upper = mean + mult * sd;
        double lower = mean - mult * sd;
        double last = series[series.length - 1];
        return (last - lower) / (upper - lower);
    }
}
