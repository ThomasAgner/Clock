package ge.model;

/**
 * One daily data point of price history.
 *
 * <p>{@code mid} is the representative price for the day. For OSRS it is the
 * volume-weighted midpoint of the insta-buy/insta-sell averages; for RS3 it is
 * the single published guide price. {@code volume} is the number of units
 * traded that day (0 when the source does not report it).
 *
 * @param timestampMillis day of the observation (epoch millis, UTC)
 * @param mid             representative price for the day
 * @param volume          units traded that day, or 0 when unknown
 */
public record Candle(long timestampMillis, double mid, double volume) {
}
