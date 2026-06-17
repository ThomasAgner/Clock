package ge.data;

import ge.model.Candle;
import ge.model.Quote;
import ge.util.Http;
import ge.util.Json;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Client for RuneScape 3 Grand Exchange data via the RuneScape Wiki / Weird
 * Gloop exchange API (<a href="https://api.weirdgloop.org">api.weirdgloop.org</a>).
 *
 * <p>Unlike OSRS there is no cheap whole-market volume feed for RS3, so callers
 * supply a watchlist of item ids. Weird Gloop reports recent daily turnover
 * (volume) alongside the guide price, which is exactly what the analyzer needs.
 */
public final class Rs3Client {

    private static final String BASE = "https://api.weirdgloop.org/exchange/history/rs";

    /** Fetch ~90 days of daily price/volume history for one item (oldest first). */
    public List<Candle> dailyHistory(int id) throws IOException, InterruptedException {
        Map<String, Object> root = Json.asObject(Json.parse(Http.get(BASE + "/last90d?id=" + id)));
        List<Object> points = Json.asArray(root.get(String.valueOf(id)));
        List<Candle> out = new ArrayList<>(points.size());
        for (Object o : points) {
            Map<String, Object> m = Json.asObject(o);
            double price = Json.num(m, "price", Double.NaN);
            if (Double.isNaN(price)) continue;
            double volume = Json.num(m, "volume", 0); // null volumes read as 0
            long ts = Json.lng(m, "timestamp", 0);    // already epoch millis
            out.add(new Candle(ts, price, volume));
        }
        return out;
    }

    /** Fetch the latest guide price. RS3 publishes a single price (no spread). */
    public Quote latest(int id) throws IOException, InterruptedException {
        Map<String, Object> root = Json.asObject(Json.parse(Http.get(BASE + "/latest?id=" + id)));
        Object entry = root.get(String.valueOf(id));
        if (entry == null) return new Quote(0, 0);
        double price = Json.num(Json.asObject(entry), "price", 0);
        return new Quote(price, price);
    }

    /** The id and live quote a watchlist name resolves to. */
    public record Resolved(int id, Quote quote) {
    }

    /**
     * Resolve a Grand Exchange item name to its id and live quote. Returns
     * {@code null} when the name is not a tradeable RS3 item, so watchlists can
     * carry friendly names without worrying about ids drifting over time.
     */
    public Resolved resolveByName(String name) throws IOException, InterruptedException {
        String url = BASE + "/latest?name=" + URLEncoder.encode(name, StandardCharsets.UTF_8);
        Object parsed = Json.parse(Http.get(url));
        Map<String, Object> root = Json.asObject(parsed);
        if (root.isEmpty()) return null;
        Object entry = root.values().iterator().next();
        if (entry == null) return null;
        Map<String, Object> m = Json.asObject(entry);
        int id;
        try {
            id = Integer.parseInt(Json.str(m, "id", "-1"));
        } catch (NumberFormatException e) {
            return null;
        }
        if (id < 0) return null;
        double price = Json.num(m, "price", 0);
        return new Resolved(id, new Quote(price, price));
    }
}
