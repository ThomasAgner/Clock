package ge.model;

/**
 * A live market quote.
 *
 * <p>{@code instaBuy} is the price you pay to buy immediately (the standing
 * sell offer you can take), and {@code instaSell} is the price you receive
 * selling immediately. For RS3 only a single guide price is published, so both
 * fields hold that price.
 *
 * @param instaBuy  price to buy now (a.k.a. the "high"/buy price)
 * @param instaSell price to receive selling now (a.k.a. the "low"/sell price)
 */
public record Quote(double instaBuy, double instaSell) {

    /** Midpoint of the spread. */
    public double mid() {
        return (instaBuy + instaSell) / 2.0;
    }
}
