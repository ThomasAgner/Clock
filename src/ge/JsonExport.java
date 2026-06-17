package ge;

import ge.analysis.Analysis;
import ge.analysis.Analyzer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Exports analyses as a JSON array (dependency-free serializer). */
public final class JsonExport {

    private JsonExport() {
    }

    public static void write(String path, List<Analysis> analyses) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int i = 0; i < analyses.size(); i++) {
            Analysis a = analyses.get(i);
            sb.append("  {")
                    .append(kv("game", a.item().game().name())).append(',')
                    .append(kv("id", a.item().id())).append(',')
                    .append(kv("name", a.item().name())).append(',')
                    .append(kv("signal", a.signal().name())).append(',')
                    .append(kv("score", a.score())).append(',')
                    .append(kv("now", a.quote().mid())).append(',')
                    .append(kv("entry", a.entryPrice())).append(',')
                    .append(kv("target", a.targetPrice())).append(',')
                    .append(kv("profit_after_tax", a.expectedProfit())).append(',')
                    .append(kv("roi_pct", a.roiPercent())).append(',')
                    .append(kv("daily_volume", a.avgDailyVolume())).append(',')
                    .append(kv("volume_trend_pct", a.volumeTrendPercent())).append(',')
                    .append(kv("flip_margin_now", Analyzer.flipMargin(a.quote(), a.item().game()))).append(',')
                    .append(kv("rsi", a.rsi())).append(',')
                    .append(kv("sma_short", a.smaShort())).append(',')
                    .append(kv("sma_long", a.smaLong())).append(',')
                    .append(kv("slope_pct", a.trendSlopePercent())).append(',')
                    .append(kv("percent_b", a.percentB())).append(',')
                    .append(kv("macd_hist", a.macdHistogram())).append(',')
                    .append(kv("atr_pct", a.atrPercent())).append(',')
                    .append(kv("obv_trend", a.obvTrend())).append(',')
                    .append(kv("stoch_k", a.stochasticK())).append(',')
                    .append(strArray("reasons", a.reasons())).append(',')
                    .append(numArray("series", a.series(), 90))
                    .append('}');
            sb.append(i + 1 < analyses.size() ? ",\n" : "\n");
        }
        sb.append("]\n");
        Files.writeString(Path.of(path), sb.toString(), StandardCharsets.UTF_8);
    }

    private static String kv(String key, String value) {
        return "\"" + key + "\":" + quote(value);
    }

    private static String kv(String key, double value) {
        return "\"" + key + "\":" + num(value);
    }

    private static String strArray(String key, List<String> values) {
        StringBuilder sb = new StringBuilder("\"" + key + "\":[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(quote(values.get(i)));
        }
        return sb.append(']').toString();
    }

    private static String numArray(String key, double[] values, int tail) {
        int start = Math.max(0, values.length - tail);
        StringBuilder sb = new StringBuilder("\"" + key + "\":[");
        for (int i = start; i < values.length; i++) {
            if (i > start) sb.append(',');
            sb.append(num(values[i]));
        }
        return sb.append(']').toString();
    }

    /** Format a number without scientific notation, dropping a trailing ".0". */
    private static String num(double v) {
        if (v == Math.rint(v) && !Double.isInfinite(v)) {
            return String.valueOf((long) v);
        }
        return String.format("%.4f", v);
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }
}
