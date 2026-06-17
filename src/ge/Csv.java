package ge;

import ge.analysis.Analysis;
import ge.analysis.Analyzer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Exports analyses to a spreadsheet-friendly CSV file. */
public final class Csv {

    private static final String HEADER = String.join(",",
            "game", "id", "name", "signal", "score",
            "now", "entry", "target", "profit_after_tax", "roi_pct",
            "daily_volume", "volume_trend_pct", "flip_margin_now",
            "rsi", "sma_short", "sma_long", "slope_pct", "percent_b", "reasons");

    private Csv() {
    }

    public static void write(String path, List<Analysis> analyses) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(HEADER).append('\n');
        for (Analysis a : analyses) {
            sb.append(field(a.item().game().name())).append(',')
                    .append(a.item().id()).append(',')
                    .append(field(a.item().name())).append(',')
                    .append(a.signal().name()).append(',')
                    .append(round(a.score(), 1)).append(',')
                    .append(round(a.quote().mid(), 2)).append(',')
                    .append(round(a.entryPrice(), 2)).append(',')
                    .append(round(a.targetPrice(), 2)).append(',')
                    .append(round(a.expectedProfit(), 2)).append(',')
                    .append(round(a.roiPercent(), 2)).append(',')
                    .append(round(a.avgDailyVolume(), 0)).append(',')
                    .append(round(a.volumeTrendPercent(), 1)).append(',')
                    .append(round(Analyzer.flipMargin(a.quote(), a.item().game()), 0)).append(',')
                    .append(round(a.rsi(), 1)).append(',')
                    .append(round(a.smaShort(), 2)).append(',')
                    .append(round(a.smaLong(), 2)).append(',')
                    .append(round(a.trendSlopePercent(), 3)).append(',')
                    .append(round(a.percentB(), 3)).append(',')
                    .append(field(String.join("; ", a.reasons())))
                    .append('\n');
        }
        Files.writeString(Path.of(path), sb.toString(), StandardCharsets.UTF_8);
    }

    /** Quote and escape a field per RFC 4180 when it contains a comma, quote or newline. */
    private static String field(String value) {
        if (value == null) return "";
        boolean needsQuote = value.contains(",") || value.contains("\"") || value.contains("\n");
        if (!needsQuote) return value;
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static String round(double v, int decimals) {
        if (decimals == 0) return String.valueOf(Math.round(v));
        return String.format("%." + decimals + "f", v);
    }
}
