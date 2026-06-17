package ge.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Splits a gp budget across the bullish (buy-now) ideas.
 *
 * <p>Each idea is sized by its conviction, then capped by what you could
 * realistically buy: the 4-hour GE buy limit and a fraction of daily traded
 * volume (so the plan never assumes buying more than the market can absorb).
 * Any budget left after the conviction-weighted pass is topped up into the
 * highest-ROI ideas that still have headroom.
 */
public final class Allocator {

    public record Holding(Analysis idea, long units, double cost, double expectedProfit) {
    }

    public record Plan(double budget, double deployed, double expectedProfit, List<Holding> holdings) {
    }

    private Allocator() {
    }

    public static Plan plan(List<Analysis> bullish, double budget, double maxVolumeFraction) {
        List<Analysis> ideas = bullish.stream()
                .filter(a -> a.entryPrice() > 0 && a.expectedProfit() > 0)
                .toList();
        if (ideas.isEmpty() || budget <= 0) {
            return new Plan(budget, 0, 0, List.of());
        }

        double sumConviction = ideas.stream().mapToDouble(Analysis::conviction).sum();
        long[] units = new long[ideas.size()];
        long[] maxUnits = new long[ideas.size()];

        // First pass: conviction-weighted, capped by liquidity and budget.
        double remaining = budget;
        for (int i = 0; i < ideas.size(); i++) {
            Analysis a = ideas.get(i);
            maxUnits[i] = capacity(a, maxVolumeFraction);
            double weight = sumConviction > 0 ? a.conviction() / sumConviction : 1.0 / ideas.size();
            long want = (long) Math.floor(budget * weight / a.entryPrice());
            long take = Math.min(want, Math.min(maxUnits[i], (long) Math.floor(remaining / a.entryPrice())));
            take = Math.max(0, take);
            units[i] = take;
            remaining -= take * a.entryPrice();
        }

        // Second pass: spend leftover on the best ROI ideas with headroom.
        List<Integer> byRoi = new ArrayList<>();
        for (int i = 0; i < ideas.size(); i++) byRoi.add(i);
        byRoi.sort(Comparator.comparingDouble((Integer i) -> ideas.get(i).roiPercent()).reversed());
        for (int i : byRoi) {
            Analysis a = ideas.get(i);
            while (units[i] < maxUnits[i] && remaining >= a.entryPrice()) {
                units[i]++;
                remaining -= a.entryPrice();
            }
        }

        List<Holding> holdings = new ArrayList<>();
        double deployed = 0, profit = 0;
        for (int i = 0; i < ideas.size(); i++) {
            if (units[i] <= 0) continue;
            Analysis a = ideas.get(i);
            double cost = units[i] * a.entryPrice();
            double exp = units[i] * a.expectedProfit();
            deployed += cost;
            profit += exp;
            holdings.add(new Holding(a, units[i], cost, exp));
        }
        holdings.sort(Comparator.comparingDouble(Holding::cost).reversed());
        return new Plan(budget, deployed, profit, holdings);
    }

    /** Max units we'd buy of one item: limited by GE buy limit and a slice of daily volume. */
    private static long capacity(Analysis a, double maxVolumeFraction) {
        long cap = Long.MAX_VALUE;
        if (a.item().buyLimit() > 0) cap = a.item().buyLimit();
        if (a.avgDailyVolume() > 0) {
            cap = Math.min(cap, (long) Math.floor(a.avgDailyVolume() * maxVolumeFraction));
        }
        return Math.max(0, cap);
    }
}
