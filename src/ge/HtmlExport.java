package ge;

import ge.analysis.Analysis;
import ge.analysis.Signal;
import ge.util.Fmt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Renders the shown ideas as a standalone, shareable HTML report with inline SVG charts. */
public final class HtmlExport {

    private HtmlExport() {
    }

    public static void write(String path, List<Analysis> analyses) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
                .append("<title>Grand Exchange Analyzer</title>")
                .append(css())
                .append("</head><body>");
        sb.append("<header><h1>RuneScape Grand Exchange Analyzer</h1>")
                .append("<p class=\"sub\">Generated ")
                .append(ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME))
                .append(" — technical signals over price &amp; daily volume. Not financial advice; RuneScape gp only.</p></header>");

        sb.append(section("BUY — bullish (buy now, sell at target)", "buy",
                analyses.stream().filter(a -> a.signal() == Signal.BULLISH).toList(), analyses));
        sb.append(section("SELL — bearish (sell now, buy back at target)", "sell",
                analyses.stream().filter(a -> a.signal() == Signal.BEARISH).toList(), analyses));

        sb.append("</body></html>");
        Files.writeString(Path.of(path), sb.toString(), StandardCharsets.UTF_8);
    }

    private static String section(String title, String cls, List<Analysis> items, List<Analysis> all) {
        StringBuilder sb = new StringBuilder();
        sb.append("<section><h2 class=\"").append(cls).append("\">").append(esc(title)).append("</h2>");
        if (items.isEmpty()) {
            sb.append("<p class=\"empty\">No items with this signal.</p>");
        } else {
            sb.append("<div class=\"grid\">");
            for (Analysis a : items) sb.append(card(a));
            sb.append("</div>");
        }
        return sb.append("</section>").toString();
    }

    private static String card(Analysis a) {
        boolean bull = a.signal() == Signal.BULLISH;
        String cls = bull ? "bull" : "bear";
        String badge = bull ? "BULLISH ▲" : "BEARISH ▼";
        String planVerb = bull ? "BUY" : "SELL";
        String planVerb2 = bull ? "SELL" : "BUY BACK";

        StringBuilder sb = new StringBuilder();
        sb.append("<article class=\"card ").append(cls).append("\">");
        sb.append("<div class=\"head\"><span class=\"badge ").append(cls).append("\">").append(badge)
                .append("</span><span class=\"name\">").append(esc(a.item().name())).append("</span>")
                .append("<span class=\"meta\">").append(a.item().game()).append(" #").append(a.item().id())
                .append("</span><span class=\"score\">score ").append(String.format("%+.0f", a.score()))
                .append("</span></div>");

        sb.append("<div class=\"chart\">").append(svgChart(a.series(), bull ? "#4caf50" : "#e05260"))
                .append("<span class=\"px\">").append(Fmt.gp(first(a.series()))).append(" → ")
                .append(Fmt.gp(a.historyMid())).append("</span></div>");

        sb.append("<div class=\"plan\"><b>").append(planVerb).append("</b> @ ").append(Fmt.gp(a.entryPrice()))
                .append(" → <b>").append(planVerb2).append("</b> @ ").append(Fmt.gp(a.targetPrice()))
                .append(" &nbsp;·&nbsp; profit ").append(Fmt.gp(a.expectedProfit())).append("/ea (")
                .append(Fmt.pct(a.roiPercent())).append(" after tax)</div>");

        sb.append("<div class=\"vol\">~").append(Fmt.gp(a.avgDailyVolume())).append(" traded/day");
        if (Math.abs(a.volumeTrendPercent()) >= 5) {
            sb.append(" (").append(Fmt.pct(a.volumeTrendPercent())).append(" vs 30d)");
        }
        if (a.item().buyLimit() > 0) {
            sb.append(" · buy limit ").append(a.item().buyLimit());
        }
        sb.append("</div>");

        sb.append("<div class=\"ind\">RSI ").append(String.format("%.0f", a.rsi()))
                .append(" · MACD ").append(a.macdHistogram() >= 0 ? "+" : "").append(Fmt.gp(a.macdHistogram()))
                .append(" · OBV ").append(String.format("%+.2f", a.obvTrend()))
                .append(" · Stoch ").append(String.format("%.0f", a.stochasticK()))
                .append(" · ATR ").append(Fmt.pctUnsigned(a.atrPercent())).append("/day")
                .append(" · slope ").append(Fmt.pct(a.trendSlopePercent())).append("/day</div>");

        sb.append("<ul class=\"reasons\">");
        for (String r : a.reasons()) sb.append("<li>").append(esc(r)).append("</li>");
        sb.append("</ul></article>");
        return sb.toString();
    }

    /** Inline SVG line chart of the price series (last 90 points). */
    private static String svgChart(double[] s, String color) {
        int w = 320, h = 64, pad = 4;
        if (s.length == 0) return "<svg width=\"" + w + "\" height=\"" + h + "\"></svg>";
        int start = Math.max(0, s.length - 90);
        int n = s.length - start;
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (int i = start; i < s.length; i++) {
            min = Math.min(min, s[i]);
            max = Math.max(max, s[i]);
        }
        double range = max - min;
        StringBuilder pts = new StringBuilder();
        for (int i = start; i < s.length; i++) {
            double x = n == 1 ? pad : pad + (double) (i - start) / (n - 1) * (w - 2 * pad);
            double y = range == 0 ? h / 2.0 : h - pad - (s[i] - min) / range * (h - 2 * pad);
            if (i > start) pts.append(' ');
            pts.append(String.format("%.1f,%.1f", x, y));
        }
        return "<svg width=\"" + w + "\" height=\"" + h + "\" viewBox=\"0 0 " + w + " " + h + "\" preserveAspectRatio=\"none\">"
                + "<polyline fill=\"none\" stroke=\"" + color + "\" stroke-width=\"1.5\" points=\"" + pts + "\"/></svg>";
    }

    private static double first(double[] s) {
        return s.length == 0 ? 0 : s[Math.max(0, s.length - 90)];
    }

    private static String css() {
        return "<style>"
                + ":root{--bg:#10141a;--card:#1a212b;--ink:#e7edf3;--dim:#8b97a7;--line:#283341;}"
                + "*{box-sizing:border-box}"
                + "body{margin:0;background:var(--bg);color:var(--ink);font:14px/1.45 -apple-system,Segoe UI,Roboto,sans-serif}"
                + "header{padding:24px 28px;border-bottom:1px solid var(--line)}"
                + "h1{margin:0 0 4px;font-size:20px}.sub{margin:0;color:var(--dim);font-size:12px}"
                + "section{padding:18px 28px}h2{font-size:15px;margin:0 0 12px}h2.buy{color:#4caf50}h2.sell{color:#e05260}"
                + ".grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(360px,1fr));gap:14px}"
                + ".card{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:14px}"
                + ".card.bull{border-left:3px solid #4caf50}.card.bear{border-left:3px solid #e05260}"
                + ".head{display:flex;align-items:center;gap:8px;flex-wrap:wrap;margin-bottom:8px}"
                + ".badge{font-size:11px;font-weight:700;padding:2px 7px;border-radius:6px}"
                + ".badge.bull{background:#173b22;color:#7ee29a}.badge.bear{background:#3b1719;color:#f0939c}"
                + ".name{font-weight:600}.meta{color:var(--dim);font-size:12px}.score{margin-left:auto;color:var(--dim);font-size:12px}"
                + ".chart{display:flex;align-items:center;gap:10px;margin:6px 0}.chart svg{background:#11161d;border-radius:6px}"
                + ".px{color:var(--dim);font-size:12px;font-variant-numeric:tabular-nums}"
                + ".plan{margin:6px 0;font-size:13px}.vol{color:var(--dim);font-size:12px}"
                + ".ind{color:var(--dim);font-size:11px;margin:8px 0 4px;font-variant-numeric:tabular-nums}"
                + ".reasons{margin:6px 0 0;padding-left:18px;color:#c3cdd9;font-size:12px}"
                + ".reasons li{margin:1px 0}.empty{color:var(--dim)}"
                + "</style>";
    }

    private static String esc(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
