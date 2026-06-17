package ge.data;

import ge.model.Candle;
import ge.model.Game;
import ge.model.ItemMeta;
import ge.model.Quote;
import ge.util.Http;
import ge.util.Json;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client for the Old School RuneScape real-time prices API
 * (<a href="https://prices.runescape.wiki/api/v1/osrs">prices.runescape.wiki</a>).
 *
 * <p>This feed is rich enough to scan the entire market cheaply: one call lists
 * every item, one call gives 24-hour volumes, and one call gives live quotes.
 * Per-item daily history is then fetched only for the chosen candidates.
 */
public final class OsrsClient {

    private static final String BASE = "https://prices.runescape.wiki/api/v1/osrs";

    /** Aggregated 24-hour stats for one item. */
    public record Vol24h(double avgHigh, double avgLow, double highVolume, double lowVolume) {
        public double totalVolume() {
            return highVolume + lowVolume;
        }
    }

    /** Fetch metadata for every tradeable item, keyed by item id. */
    public Map<Integer, ItemMeta> mapping() throws IOException, InterruptedException {
        List<Object> arr = Json.asArray(Json.parse(Http.get(BASE + "/mapping")));
        Map<Integer, ItemMeta> out = new LinkedHashMap<>();
        for (Object o : arr) {
            Map<String, Object> m = Json.asObject(o);
            int id = (int) Json.lng(m, "id", -1);
            if (id < 0) continue;
            out.put(id, new ItemMeta(
                    id,
                    Json.str(m, "name", "#" + id),
                    Game.OSRS,
                    (int) Json.lng(m, "limit", 0),
                    Json.lng(m, "value", 0),
                    (int) Json.lng(m, "highalch", 0),
                    Json.bool(m, "members", false)));
        }
        return out;
    }

    /** Fetch 24-hour averaged prices and traded volumes, keyed by item id. */
    public Map<Integer, Vol24h> volumes24h() throws IOException, InterruptedException {
        Map<String, Object> root = Json.asObject(Json.parse(Http.get(BASE + "/24h")));
        Map<String, Object> data = Json.asObject(root.get("data"));
        Map<Integer, Vol24h> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : data.entrySet()) {
            Map<String, Object> m = Json.asObject(e.getValue());
            out.put(Integer.parseInt(e.getKey()), new Vol24h(
                    Json.num(m, "avgHighPrice", 0),
                    Json.num(m, "avgLowPrice", 0),
                    Json.num(m, "highPriceVolume", 0),
                    Json.num(m, "lowPriceVolume", 0)));
        }
        return out;
    }

    /** Fetch live insta-buy/insta-sell quotes for every item, keyed by item id. */
    public Map<Integer, Quote> latestAll() throws IOException, InterruptedException {
        Map<String, Object> root = Json.asObject(Json.parse(Http.get(BASE + "/latest")));
        Map<String, Object> data = Json.asObject(root.get("data"));
        Map<Integer, Quote> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : data.entrySet()) {
            Map<String, Object> m = Json.asObject(e.getValue());
            out.put(Integer.parseInt(e.getKey()),
                    new Quote(Json.num(m, "high", 0), Json.num(m, "low", 0)));
        }
        return out;
    }

    /**
     * Fetch up to one year of daily history for a single item (oldest first).
     * The daily mid is the volume-weighted blend of the insta-buy and
     * insta-sell averages so that thinly traded sides do not skew the price.
     */
    public List<Candle> dailyHistory(int id) throws IOException, InterruptedException {
        Map<String, Object> root = Json.asObject(
                Json.parse(Http.get(BASE + "/timeseries?timestep=24h&id=" + id)));
        List<Object> data = Json.asArray(root.get("data"));
        List<Candle> out = new ArrayList<>(data.size());
        for (Object o : data) {
            Map<String, Object> m = Json.asObject(o);
            double high = Json.num(m, "avgHighPrice", Double.NaN);
            double low = Json.num(m, "avgLowPrice", Double.NaN);
            double highVol = Json.num(m, "highPriceVolume", 0);
            double lowVol = Json.num(m, "lowPriceVolume", 0);

            double mid;
            if (Double.isNaN(high) && Double.isNaN(low)) {
                continue; // no trades that day
            } else if (Double.isNaN(high)) {
                mid = low;
            } else if (Double.isNaN(low)) {
                mid = high;
            } else if (highVol + lowVol > 0) {
                mid = (high * highVol + low * lowVol) / (highVol + lowVol);
            } else {
                mid = (high + low) / 2;
            }

            long ts = Json.lng(m, "timestamp", 0) * 1000L;
            out.add(new Candle(ts, mid, highVol + lowVol));
        }
        return out;
    }
}
