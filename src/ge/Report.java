package ge;

import ge.analysis.Analysis;
import ge.analysis.Signal;
import ge.model.Game;
import ge.util.Fmt;

import java.util.List;

/** Renders analyses to the console: ranked tables plus per-item deep dives with charts. */
public final class Report {

    private static final char[] SPARK = {'▁', '▂', '▃', '▄', '▅', '▆', '▇', '█'};

    // ANSI colours (suppressed when colour is disabled).
    private static final String RESET = "[0m";
    private static final String BOLD = "[1m";
    private static final String GREEN = "[32m";
    private static final String RED = "[31m";
    private static final String YELLOW = "[33m";
    private static final String DIM = "[2m";

    private final boolean color;

    public Report(boolean color) {
        this.color = color;
    }

    // --- Sparkline chart -----------------------------------------------------

    /** Render the tail of a price series as a unicode sparkline. */
    public static String sparkline(double[] series, int points) {
        if (series.length == 0) return "";
        int start = Math.max(0, series.length - points);
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (int i = start; i < series.length; i++) {
            min = Math.min(min, series[i]);
            max = Math.max(max, series[i]);
        }
        double range = max - min;
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < series.length; i++) {
            int level = range == 0 ? 0 : (int) Math.round((series[i] - min) / range * (SPARK.length - 1));
            sb.append(SPARK[level]);
        }
        return sb.toString();
    }

    // --- Tables --------------------------------------------------------------

    public void printSection(String title, List<Analysis> items) {
        System.out.println();
        System.out.println(bold(title));
        if (items.isEmpty()) {
            System.out.println(dim("  (none found with the current filters)"));
            return;
        }
        System.out.println(dim(
                "  # " + Fmt.pad("ITEM", 24) + Fmt.padLeft("NOW", 10) + "  "
                        + Fmt.padLeft("ENTRY", 10) + Fmt.padLeft("TARGET", 10)
                        + Fmt.padLeft("PROFIT", 10) + Fmt.padLeft("ROI", 8)
                        + Fmt.padLeft("VOL/d", 9) + "  " + Fmt.pad("CONV", 6)));
        int rank = 1;
        for (Analysis a : items) {
            String row = "  " + Fmt.padLeft(String.valueOf(rank++), 1) + " "
                    + Fmt.pad(a.item().name(), 24)
                    + Fmt.padLeft(Fmt.gp(a.quote().mid()), 10) + "  "
                    + Fmt.padLeft(Fmt.gp(a.entryPrice()), 10)
                    + Fmt.padLeft(Fmt.gp(a.targetPrice()), 10)
                    + Fmt.padLeft(Fmt.gp(a.expectedProfit()), 10)
                    + Fmt.padLeft(Fmt.pct(a.roiPercent()), 8)
                    + Fmt.padLeft(Fmt.gp(a.avgDailyVolume()), 9) + "  "
                    + Fmt.pad(String.format("%.0f", a.conviction()), 6);
            System.out.println(colorFor(a.signal(), row));
        }
    }

    // --- Deep dive -----------------------------------------------------------

    public void printDetail(Analysis a) {
        String tag = switch (a.signal()) {
            case BULLISH -> "BULLISH ▲";
            case BEARISH -> "BEARISH ▼";
            case NEUTRAL -> "NEUTRAL  •";
        };
        System.out.println();
        System.out.println(colorFor(a.signal(), bold(String.format(
                "[%s  score %+.0f]  %s (%s)  #%d",
                tag, a.score(), a.item().name(), a.item().game(), a.item().id()))));

        String chart = sparkline(a.series(), 60);
        double first = a.series().length > 0 ? a.series()[Math.max(0, a.series().length - 60)] : 0;
        System.out.printf("  Chart (last %dd):  %s  %s %s %s%n",
                Math.min(60, a.series().length), chart,
                Fmt.gp(first), "→", Fmt.gp(a.historyMid()));

        double spread = a.quote().instaBuy() - a.quote().instaSell();
        if (a.item().game() == Game.OSRS && spread > 0) {
            System.out.printf("  Live:  insta-buy %s / insta-sell %s  (spread %s)%n",
                    Fmt.gp(a.quote().instaBuy()), Fmt.gp(a.quote().instaSell()), Fmt.gp(spread));
        } else {
            System.out.printf("  Live:  guide price %s%n", Fmt.gp(a.quote().mid()));
        }

        if (a.signal() == Signal.BEARISH) {
            System.out.printf("  Plan:  %s @ %s  →  %s @ %s    profit %s/ea (%s after tax)%n",
                    bold("SELL"), Fmt.gp(a.entryPrice()),
                    bold("BUY BACK"), Fmt.gp(a.targetPrice()),
                    Fmt.gp(a.expectedProfit()), Fmt.pct(a.roiPercent()));
        } else {
            System.out.printf("  Plan:  %s @ %s  →  %s @ %s    profit %s/ea (%s after tax)%n",
                    bold("BUY"), Fmt.gp(a.entryPrice()),
                    bold("SELL"), Fmt.gp(a.targetPrice()),
                    Fmt.gp(a.expectedProfit()), Fmt.pct(a.roiPercent()));
        }

        StringBuilder liq = new StringBuilder(String.format(
                "  Volume: ~%s units/day", Fmt.gp(a.avgDailyVolume())));
        if (Math.abs(a.volumeTrendPercent()) >= 5) {
            liq.append(String.format(" (%s vs 30d avg)", Fmt.pct(a.volumeTrendPercent())));
        }
        if (a.item().buyLimit() > 0) {
            double maxStake = (double) a.item().buyLimit() * a.entryPrice();
            liq.append(String.format("  |  buy limit %d (~%s / 4h)",
                    a.item().buyLimit(), Fmt.gp(maxStake)));
        }
        System.out.println(liq);

        System.out.printf("  Indicators: RSI %.0f | %dd/%dd SMA %s/%s | slope %s/day | %%B %.2f%n",
                a.rsi(), 5, 20, Fmt.gp(a.smaShort()), Fmt.gp(a.smaLong()),
                Fmt.pct(a.trendSlopePercent()), a.percentB());

        System.out.println("  Signals:");
        for (String r : a.reasons()) {
            System.out.println(dim("    • ") + r);
        }
    }

    // --- Colour helpers ------------------------------------------------------

    private String colorFor(Signal s, String text) {
        if (!color) return text;
        return switch (s) {
            case BULLISH -> GREEN + text + RESET;
            case BEARISH -> RED + text + RESET;
            case NEUTRAL -> YELLOW + text + RESET;
        };
    }

    private String bold(String s) {
        return color ? BOLD + s + RESET : s;
    }

    private String dim(String s) {
        return color ? DIM + s + RESET : s;
    }
}
