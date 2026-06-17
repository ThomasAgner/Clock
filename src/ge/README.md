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
| `--min-margin P` | OSRS: keep only items whose live flip margin (insta-buy − insta-sell − tax) ≥ P | `0` |
| `--members true\|false\|any` | OSRS members filter | `any` |
| `--ids 1,2,3` | Analyze specific item ids instead of scanning | — |
| `--names "A,B"` | Analyze specific item names | — |
| `--threshold N` | Signal-strength cutoff, 0–100 | `22` |
| `--sort KEY` | Rank tables by `score`/`roi`/`profit`/`volume`/`margin` | `score` |
| `--backtest` | Walk-forward test of how the signals performed historically | off |
| `--horizon N` | Backtest holding period in days | `14` |
| `--budget N` | Suggest how to split N gp across the bullish ideas | — |
| `--csv PATH` | Also export the shown ideas to a CSV file | — |
| `--json PATH` | Also export the shown ideas to a JSON file | — |
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
   least-squares slope of the recent trend, rate-of-change momentum, and the
   sign of the **MACD** histogram (12/26/9) as a momentum confirmation.
2. **Stretch** — RSI(14) and Bollinger %B flag overbought/oversold extremes and
   nudge the score toward mean reversion when price is stretched.
3. **Participation** — the volume trend (recent vs 30-day average turnover)
   scales conviction up when a move is backed by rising volume and down when it
   is fading. A liquidity floor (`--min-volume`) keeps illiquid items out.

Each deep dive also reports an **ATR**-style daily volatility (mean absolute
day-over-day move), which feeds the size of the take-profit/buy-back targets.

From the verdict it derives a concrete plan:

- **Bullish** — enter near the current sell side; target the lesser of recent
  resistance and the upper Bollinger band (with a volatility-based floor).
- **Bearish** — sell into the current buy side; target the greater of recent
  support and the lower Bollinger band.

Profit and ROI are computed **after the OSRS 2% Grand Exchange sell tax**
(no tax under 100 gp, capped at 5M per item; RS3 has no GE tax).

## Flip-margin filtering & CSV export

- **`--min-margin P`** (OSRS) keeps only items whose *current* flip margin —
  `insta-buy − insta-sell − tax` — is at least `P`. Combine it with the trend
  signal to find items that are both moving your way *and* already profitable to
  flip right now. The live margin is also shown in each OSRS deep dive.
- **`--csv PATH`** writes every shown idea to a spreadsheet-friendly file with
  full columns (signal, score, entry/target, after-tax profit & ROI, daily
  volume, volume trend, live flip margin, RSI, SMAs, slope, %B and the reasons),
  e.g.:

  ```bash
  java -cp out ge.GrandExchange --game osrs --min-margin 5000 --csv ideas.csv
  ```
- **`--json PATH`** writes the same ideas as a JSON array (including each item's
  recent price `series` and `reasons`), for feeding dashboards or other tools.

## Capital allocation

`--budget N` turns the bullish list into a concrete buy plan for N gp. Each idea
is sized by its conviction, then capped by what you could realistically buy —
the 4-hour GE buy limit and ~10% of daily traded volume — so the plan never
assumes moving more than the market can absorb. Leftover budget is topped up
into the highest-ROI ideas with headroom; anything that can't be deployed
(because of those caps) is reported as unspent.

```
  Suggested allocation of 50M across bullish ideas
    ITEM                        UNITS       COST  EXP.PROFIT     ROI
    Diamond dragon bolts (e)      11K     30.48M     287.43K   +0.9%
    Blighted super restore(4       2K      4.24M      60.68K   +1.4%
    TOTAL                                 34.72M     348.11K   +1.0%
    Unspent (capped by buy limits / liquidity): 15.28M
```

## Backtesting the signals

`--backtest` runs a **walk-forward** evaluation: for every item and every
historical day with enough warm-up, the score is computed from data *up to that
day only* and compared against the actual forward return `--horizon` days later
(no look-ahead). Bullish calls "win" when price rose; bearish calls "win" when
it fell. The buy-and-hold baseline is the yardstick — a signal only adds value
when its average return beats simply holding ("edge vs hold").

```
  Backtest (walk-forward, 14-day horizon, 60 items, 18646 samples)
    Buy & hold baseline forward return: +0.3% per 14d
    BULLISH calls  3721 signals | win 48.3% | avg +0.9% | edge vs hold +0.6%
    BEARISH calls  3922 signals | win 52.2% | avg +0.0% | edge vs hold +0.3%
```

Treat the edge as modest and the win rates as realistic — this is a screening
aid, not a money printer.

## Tests

Pure-math correctness (indicators, tax, flip margin, signal directionality and
backtest sanity) is covered by a dependency-free runner:

```bash
javac -d out $(find src/ge -name '*.java')
java -cp out ge.test.Tests
```

## Code layout

```
src/ge/
  GrandExchange.java     CLI entry point & orchestration
  Report.java            console tables, charts and deep dives
  Csv.java               CSV export of the shown ideas
  JsonExport.java        JSON export of the shown ideas
  analysis/
    Analyzer.java        scoring + trade-plan logic (reusable score() method)
    Backtester.java      walk-forward signal evaluation
    Allocator.java       budget allocation across bullish ideas
    Indicators.java      SMA/EMA/RSI/stddev/slope/Bollinger/MACD/ATR primitives
    Analysis.java        result record
    Signal.java          BULLISH / BEARISH / NEUTRAL
  test/
    Tests.java           dependency-free test runner
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
