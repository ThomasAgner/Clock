# RuneScape Grand Exchange Analyzer (OSRS + RS3)

A zero-dependency, command-line trading analyzer for the Old School RuneScape
and RuneScape 3 Grand Exchanges. It pulls daily price history and traded
volumes, runs a technical analysis on every candidate item, and prints two
ranked lists per game:

- **BUY (bullish)** — items trending up: where to **buy** now and the price to
  **sell** into.
- **SELL (bearish)** — items trending down: where to **sell** now and the price
  to **buy back**.

Each idea comes with a deep dive: an ASCII price chart, the live spread, the
trade plan (entry → target with after-tax profit and ROI), daily traded volume
and buy-limit context, and the indicators that fired.

> ⚠️ RuneScape gp only — not financial advice. The signals are heuristics over
> public market data, not guarantees.

## Requirements

- Java 17+ (developed and tested on Java 21). No external libraries.
- Outbound HTTPS access to the data sources (see below).

## Build & run

From the repository root:

```bash
# compile
javac -d out $(find src/ge -name '*.java')

# scan both markets
java -cp out ge.GrandExchange

# OSRS only, pricier items, top 10 ideas
java -cp out ge.GrandExchange --game osrs --min-price 100000 --top 10

# RS3 specific items
java -cp out ge.GrandExchange --game rs3 --names "Abyssal whip,Magic logs,Shark"

# deep dive on one OSRS item
java -cp out ge.GrandExchange --game osrs --ids 4151
```

## Options

| Option | Description | Default |
| --- | --- | --- |
| `--game osrs\|rs3\|both` | Market(s) to scan | `both` |
| `--top N` | Items per list | `8` |
| `--candidates N` | OSRS scan universe size (most-traded first) | `150` |
| `--min-volume V` | OSRS minimum daily units traded | `10000` |
| `--min-price P` | Minimum price filter | `50` |
| `--max-price P` | Maximum price filter | `2,000,000,000` |
| `--members true\|false\|any` | OSRS members filter | `any` |
| `--ids 1,2,3` | Analyze specific item ids instead of scanning | — |
| `--names "A,B"` | Analyze specific item names | — |
| `--threshold N` | Signal-strength cutoff, 0–100 | `22` |
| `--cache-ttl MIN` | Cache lifetime in minutes | `30` |
| `--no-cache` | Disable the on-disk response cache | off |
| `--no-detail` | Tables only, skip per-item deep dives | off |
| `--no-color` | Disable ANSI colours | off |
| `--help` | Show help | — |

## Data sources

- **OSRS** — [RuneScape Wiki real-time prices API](https://prices.runescape.wiki/)
  (`/mapping`, `/24h`, `/latest`, `/timeseries`). One call lists every item, one
  gives 24-hour volumes and one gives live quotes, so the **entire market** is
  scanned and the most-traded items are analyzed in depth.
- **RS3** — [RuneScape Wiki / Weird Gloop exchange API](https://api.weirdgloop.org/)
  (`/exchange/history/rs/last90d`, `/latest`). RS3 has no cheap whole-market
  volume feed, so it scans a curated **watchlist** of liquid items
  (`ge.data.Watchlist`), resolved by name at runtime so ids never go stale.
  Override with `--names` or `--ids`.

Responses are cached under `.ge-cache/` (git-ignored) to keep repeat scans fast
and to stay polite to the upstream services. Requests send a descriptive
`User-Agent`, as the RuneScape Wiki API requires.

## How the analysis works

For each item the analyzer (`ge.analysis.Analyzer`) builds a daily price series
and combines three families of evidence into a score in `[-100, +100]`
(positive = bullish):

1. **Trend** — the spread between the 5-day and 20-day moving averages, the
   least-squares slope of the recent trend, and rate-of-change momentum.
2. **Stretch** — RSI(14) and Bollinger %B flag overbought/oversold extremes and
   nudge the score toward mean reversion when price is stretched.
3. **Participation** — the volume trend (recent vs 30-day average turnover)
   scales conviction up when a move is backed by rising volume and down when it
   is fading. A liquidity floor (`--min-volume`) keeps illiquid items out.

From the verdict it derives a concrete plan:

- **Bullish** — enter near the current sell side; target the lesser of recent
  resistance and the upper Bollinger band (with a volatility-based floor).
- **Bearish** — sell into the current buy side; target the greater of recent
  support and the lower Bollinger band.

Profit and ROI are computed **after the OSRS 2% Grand Exchange sell tax**
(no tax under 100 gp, capped at 5M per item; RS3 has no GE tax).

## Code layout

```
src/ge/
  GrandExchange.java     CLI entry point & orchestration
  Report.java            console tables, charts and deep dives
  analysis/
    Analyzer.java        scoring + trade-plan logic
    Indicators.java      SMA/EMA/RSI/stddev/slope/Bollinger primitives
    Analysis.java        result record
    Signal.java          BULLISH / BEARISH / NEUTRAL
  data/
    OsrsClient.java      OSRS real-time prices API client
    Rs3Client.java       RS3 / Weird Gloop exchange API client
    Watchlist.java       default RS3 watchlist
  model/                 Game, ItemMeta, Candle, Quote records
  util/
    Http.java            HTTP GET with disk cache + User-Agent
    Json.java            tiny dependency-free JSON parser
    Fmt.java             gp / percentage formatting
```
