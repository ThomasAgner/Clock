package ge;

import ge.analysis.Analysis;
import ge.analysis.Analyzer;
import ge.analysis.Signal;
import ge.data.OsrsClient;
import ge.data.Rs3Client;
import ge.data.Watchlist;
import ge.model.Candle;
import ge.model.Game;
import ge.model.ItemMeta;
import ge.model.Quote;
import ge.util.Http;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Command-line Grand Exchange trading analyzer for Old School RuneScape and
 * RuneScape 3.
 *
 * <p>It pulls daily price history and traded volumes, runs a technical analysis
 * (trend, momentum, RSI, Bollinger bands, volume confirmation) over every
 * candidate, and prints two ranked lists per game:
 * <ul>
 *   <li><b>BUY (bullish)</b> &mdash; items trending up: where to buy and the
 *       price to sell into.</li>
 *   <li><b>SELL (bearish)</b> &mdash; items trending down: where to sell now and
 *       the price to buy back.</li>
 * </ul>
 *
 * <p>Run with {@code --help} for options.
 */
public final class GrandExchange {

    public static void main(String[] args) {
        // The charts, banner and bullets use unicode; make sure stdout emits UTF-8
        // regardless of the platform's default console encoding.
        System.setOut(new java.io.PrintStream(System.out, true, java.nio.charset.StandardCharsets.UTF_8));

        Map<String, String> opt = new HashMap<>();
        List<String> flags = new ArrayList<>();
        parseArgs(args, opt, flags);

        if (flags.contains("help") || flags.contains("h")) {
            printHelp();
            return;
        }

        // --- Global configuration -------------------------------------------
        boolean color = !flags.contains("no-color");
        if (flags.contains("no-cache")) Http.setCacheEnabled(false);
        Http.setCacheTtl(Duration.ofMinutes(Long.parseLong(opt.getOrDefault("cache-ttl", "30"))));

        int top = Integer.parseInt(opt.getOrDefault("top", "8"));
        int candidates = Integer.parseInt(opt.getOrDefault("candidates", "150"));
        double minVolume = Double.parseDouble(opt.getOrDefault("min-volume", "10000"));
        double minPrice = Double.parseDouble(opt.getOrDefault("min-price", "50"));
        double maxPrice = Double.parseDouble(opt.getOrDefault("max-price", "2000000000"));
        double minMargin = Double.parseDouble(opt.getOrDefault("min-margin", "0"));
        String members = opt.getOrDefault("members", "any");
        boolean details = !flags.contains("no-detail");
        String csvPath = opt.get("csv");

        Analyzer.Config aCfg = Analyzer.Config.defaults();
        if (opt.containsKey("threshold")) {
            aCfg = new Analyzer.Config(aCfg.shortMa(), aCfg.longMa(), aCfg.rsiPeriod(),
                    aCfg.momentumPeriod(), aCfg.bandPeriod(), aCfg.bandMult(), aCfg.lookback(),
                    Double.parseDouble(opt.get("threshold")));
        }
        Analyzer analyzer = new Analyzer(aCfg);
        Report report = new Report(color);

        List<Game> games = new ArrayList<>();
        String g = opt.getOrDefault("game", "both").toLowerCase();
        if (g.equals("both") || g.equals("all")) {
            games.add(Game.OSRS);
            games.add(Game.RS3);
        } else {
            games.add(Game.parse(g));
        }

        List<Integer> ids = parseIntList(opt.get("ids"));
        List<String> names = parseStringList(opt.get("names"));

        printBanner();

        List<Analysis> shownForCsv = new ArrayList<>();
        for (Game game : games) {
            try {
                List<Analysis> analyses = (game == Game.OSRS)
                        ? analyzeOsrs(analyzer, ids, names, candidates, minVolume, minPrice, maxPrice, members)
                        : analyzeRs3(analyzer, ids, names);
                if (minMargin > 0 && game == Game.OSRS) {
                    analyses = analyses.stream()
                            .filter(a -> Analyzer.flipMargin(a.quote(), Game.OSRS) >= minMargin)
                            .toList();
                }
                shownForCsv.addAll(output(game, analyses, top, report, details));
            } catch (Exception e) {
                System.err.println("Failed to analyze " + game + ": " + e.getMessage());
            }
        }

        if (csvPath != null) {
            try {
                Csv.write(csvPath, shownForCsv);
                System.out.println();
                System.out.println("Wrote " + shownForCsv.size() + " rows to " + csvPath);
            } catch (Exception e) {
                System.err.println("Failed to write CSV " + csvPath + ": " + e.getMessage());
            }
        }

        System.out.println();
        System.out.println("Not financial advice — RuneScape gp only. Markets move; place orders patiently and respect buy limits.");
    }

    // --- OSRS: full-market scan ---------------------------------------------

    private static List<Analysis> analyzeOsrs(
            Analyzer analyzer, List<Integer> ids, List<String> names,
            int candidates, double minVolume, double minPrice, double maxPrice, String members)
            throws Exception {

        OsrsClient client = new OsrsClient();
        System.err.println("[OSRS] fetching item catalogue, volumes and live prices…");
        Map<Integer, ItemMeta> mapping = client.mapping();
        Map<Integer, OsrsClient.Vol24h> vols = client.volumes24h();
        Map<Integer, Quote> quotes = client.latestAll();

        List<Integer> targets = new ArrayList<>();
        if (!ids.isEmpty()) {
            targets.addAll(ids);
        } else if (!names.isEmpty()) {
            for (String name : names) {
                mapping.values().stream()
                        .filter(m -> m.name().equalsIgnoreCase(name))
                        .findFirst().ifPresent(m -> targets.add(m.id()));
            }
        } else {
            // Build a liquid, in-range candidate universe and keep the most-traded.
            List<Integer> pool = new ArrayList<>();
            for (Map.Entry<Integer, OsrsClient.Vol24h> e : vols.entrySet()) {
                int id = e.getKey();
                ItemMeta meta = mapping.get(id);
                if (meta == null) continue;
                if (!membersMatch(members, meta.members())) continue;
                Quote q = quotes.get(id);
                double price = q != null && q.mid() > 0 ? q.mid()
                        : (e.getValue().avgHigh() + e.getValue().avgLow()) / 2;
                if (price < minPrice || price > maxPrice) continue;
                if (e.getValue().totalVolume() < minVolume) continue;
                pool.add(id);
            }
            pool.sort(Comparator.comparingDouble(
                    (Integer id) -> vols.get(id).totalVolume()).reversed());
            targets.addAll(pool.subList(0, Math.min(candidates, pool.size())));
        }

        System.err.println("[OSRS] analyzing " + targets.size() + " items…");
        return runParallel(targets, id -> {
            ItemMeta meta = mapping.get(id);
            if (meta == null) return null;
            List<Candle> history = client.dailyHistory(id);
            if (history.size() < 25) return null;
            Quote q = quotes.getOrDefault(id, new Quote(0, 0));
            if (q.mid() <= 0) {
                double last = history.get(history.size() - 1).mid();
                q = new Quote(last, last);
            }
            return analyzer.analyze(meta, history, q);
        });
    }

    // --- RS3: watchlist scan -------------------------------------------------

    private static List<Analysis> analyzeRs3(Analyzer analyzer, List<Integer> ids, List<String> names)
            throws Exception {

        Rs3Client client = new Rs3Client();
        List<String> targetNames = !names.isEmpty() ? names
                : ids.isEmpty() ? Watchlist.RS3_DEFAULT : List.of();

        List<Callable<Analysis>> tasks = new ArrayList<>();

        for (String name : targetNames) {
            tasks.add(() -> {
                Rs3Client.Resolved r = client.resolveByName(name);
                if (r == null) return null;
                List<Candle> history = client.dailyHistory(r.id());
                if (history.size() < 25) return null;
                ItemMeta meta = new ItemMeta(r.id(), name, Game.RS3, 0, 0, 0, false);
                return analyzer.analyze(meta, history, r.quote());
            });
        }
        for (int id : ids) {
            tasks.add(() -> {
                List<Candle> history = client.dailyHistory(id);
                if (history.size() < 25) return null;
                Quote q = client.latest(id);
                ItemMeta meta = new ItemMeta(id, "RS3 #" + id, Game.RS3, 0, 0, 0, false);
                return analyzer.analyze(meta, history, q);
            });
        }

        System.err.println("[RS3] analyzing " + tasks.size() + " items…");
        return runTasks(tasks);
    }

    // --- Output --------------------------------------------------------------

    private static List<Analysis> output(Game game, List<Analysis> analyses, int top, Report report, boolean details) {
        System.out.println();
        System.out.println("==================================================================");
        System.out.println("  " + game.displayName() + " — Grand Exchange opportunities");
        System.out.println("==================================================================");

        List<Analysis> bullish = analyses.stream()
                .filter(a -> a.signal() == Signal.BULLISH)
                .sorted(Comparator.comparingDouble(Analysis::score).reversed())
                .limit(top)
                .toList();
        List<Analysis> bearish = analyses.stream()
                .filter(a -> a.signal() == Signal.BEARISH)
                .sorted(Comparator.comparingDouble(Analysis::score))
                .limit(top)
                .toList();

        // Fallback: if nothing cleared the strength threshold, still surface the
        // strongest directional leans so the user always gets ranked ideas.
        String buyTitle = "BUY  (bullish — buy now, sell at TARGET)";
        if (bullish.isEmpty()) {
            bullish = analyses.stream()
                    .filter(a -> a.score() > 3)
                    .sorted(Comparator.comparingDouble(Analysis::score).reversed())
                    .limit(top)
                    .toList();
            if (!bullish.isEmpty()) buyTitle += "   [weaker leans — below signal threshold]";
        }
        String sellTitle = "SELL (bearish — sell now, buy back at TARGET)";
        if (bearish.isEmpty()) {
            bearish = analyses.stream()
                    .filter(a -> a.score() < -3)
                    .sorted(Comparator.comparingDouble(Analysis::score))
                    .limit(top)
                    .toList();
            if (!bearish.isEmpty()) sellTitle += "   [weaker leans — below signal threshold]";
        }

        report.printSection(buyTitle, bullish);
        report.printSection(sellTitle, bearish);

        if (details) {
            if (!bullish.isEmpty()) {
                System.out.println();
                System.out.println("  ---- Bullish deep dives ----");
                bullish.forEach(report::printDetail);
            }
            if (!bearish.isEmpty()) {
                System.out.println();
                System.out.println("  ---- Bearish deep dives ----");
                bearish.forEach(report::printDetail);
            }
        }

        List<Analysis> shown = new ArrayList<>(bullish);
        shown.addAll(bearish);
        return shown;
    }

    // --- Parallel execution helpers -----------------------------------------

    private interface IdTask {
        Analysis run(int id) throws Exception;
    }

    private static List<Analysis> runParallel(List<Integer> ids, IdTask task) {
        List<Callable<Analysis>> tasks = new ArrayList<>();
        for (int id : ids) tasks.add(() -> task.run(id));
        return runTasks(tasks);
    }

    private static List<Analysis> runTasks(List<Callable<Analysis>> tasks) {
        List<Analysis> out = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Future<Analysis>> futures = new ArrayList<>();
            for (Callable<Analysis> t : tasks) futures.add(pool.submit(t));
            for (Future<Analysis> f : futures) {
                try {
                    Analysis a = f.get();
                    if (a != null) out.add(a);
                } catch (Exception ignored) {
                    // Skip items whose data could not be fetched/parsed.
                }
            }
        } finally {
            pool.shutdown();
        }
        return out;
    }

    // --- Argument parsing ----------------------------------------------------

    private static void parseArgs(String[] args, Map<String, String> opt, List<String> flags) {
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (!a.startsWith("--")) continue;
            String key = a.substring(2);
            if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                opt.put(key, args[++i]);
            } else {
                flags.add(key);
            }
        }
    }

    private static List<Integer> parseIntList(String csv) {
        List<Integer> out = new ArrayList<>();
        if (csv == null || csv.isBlank()) return out;
        for (String s : csv.split(",")) {
            s = s.trim();
            if (!s.isEmpty()) out.add(Integer.parseInt(s));
        }
        return out;
    }

    private static List<String> parseStringList(String csv) {
        List<String> out = new ArrayList<>();
        if (csv == null || csv.isBlank()) return out;
        for (String s : csv.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    private static boolean membersMatch(String filter, boolean isMembers) {
        return switch (filter.toLowerCase()) {
            case "true", "members", "p2p" -> isMembers;
            case "false", "f2p", "free" -> !isMembers;
            default -> true;
        };
    }

    private static void printBanner() {
        System.out.println();
        System.out.println("  ╔══════════════════════════════════════════════════════════╗");
        System.out.println("  ║   RuneScape Grand Exchange Analyzer  (OSRS + RS3)         ║");
        System.out.println("  ║   technical signals · volume · entry/target prices       ║");
        System.out.println("  ╚══════════════════════════════════════════════════════════╝");
    }

    private static void printHelp() {
        System.out.println("""
                RuneScape Grand Exchange Analyzer

                Usage:
                  java ge.GrandExchange [options]

                What it does:
                  Scans the Grand Exchange and reports which items to BUY (bullish:
                  buy now, sell at the target) and which to SELL (bearish: sell now,
                  buy back at the target), with a technical read on each item's chart
                  and daily traded volume.

                Options:
                  --game osrs|rs3|both   Market(s) to scan (default: both)
                  --top N                Items per list (default: 8)
                  --candidates N         OSRS scan universe size, most-traded first (default: 150)
                  --min-volume V         OSRS minimum daily units traded (default: 10000)
                  --min-price P          Minimum price filter (default: 50)
                  --max-price P          Maximum price filter (default: 2,000,000,000)
                  --min-margin P         OSRS: keep only items whose live flip margin
                                         (insta-buy − insta-sell − tax) ≥ P (default: 0)
                  --members true|false|any   OSRS members filter (default: any)
                  --ids 1,2,3            Analyze specific item ids instead of scanning
                  --names "Abyssal whip,Shark"   Analyze specific item names
                  --threshold N          Signal strength cutoff, 0-100 (default: 22)
                  --csv PATH             Also export the shown ideas to a CSV file
                  --cache-ttl MIN        Cache lifetime in minutes (default: 30)
                  --no-cache             Disable the on-disk response cache
                  --no-detail            Tables only, skip per-item deep dives
                  --no-color             Disable ANSI colours
                  --help                 Show this help

                Examples:
                  java ge.GrandExchange --game osrs --top 10
                  java ge.GrandExchange --game osrs --min-price 100000 --members true
                  java ge.GrandExchange --game osrs --min-margin 5000 --csv ideas.csv
                  java ge.GrandExchange --game rs3 --names "Abyssal whip,Magic logs,Shark"
                  java ge.GrandExchange --ids 4151 --game osrs
                """);
    }

    private GrandExchange() {
    }
}
