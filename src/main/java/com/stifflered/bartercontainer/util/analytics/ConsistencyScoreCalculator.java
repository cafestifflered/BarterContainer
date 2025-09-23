package com.stifflered.bartercontainer.util.analytics;

import java.time.*;
import java.util.Objects;

/**
 * Computes a "Consistency Score" in [0.0, 1.0] for a shop over a recent window (default 7 days),
 * combining three signals that answer three simple questions:

 *   1) Stability — “Are the daily numbers smooth?”
 *   2) Recency   — “Did most sales happen recently?”
 *   3) Trend     — “Is the overall line flat or only gently sloped?”

 * Final score (defaults):
 *   finalScore = 0.30 * stability + 0.40 * recency + 0.30 * trend

 * What each piece looks at:
 * - Stability & Trend operate on a **dampened** version of the daily counts: y[d] = log1p(counts[d]).
 *   This softly compresses large spikes (e.g., 500 dirt in one day) so they don’t dominate the math.
 * - Trend uses a **relative slope** = slope(y)/mean(y). This means “how steep is the line per day
 *   compared to its typical level?”, so small and large shops are judged on the same footing.
 * - Recency uses the raw counts and simply asks, “How much of the total happened recently?”

 * All component scores are clamped to [0, 1].
 */
public final class ConsistencyScoreCalculator {

    private ConsistencyScoreCalculator() {}

    /* ------------------------------------------------------------------------------------------------
     * Public structs
     * ---------------------------------------------------------------------------------------------- */

    /**
     * Weighting for the three component scores.

     * You can set any non-negative weights; we normalize by the sum so they don’t have to add to 1.0.
     * Example: (0.30, 0.40, 0.30) → puts slightly more emphasis on “recent activity”.
     */
    public record Weights(double stability, double recency, double trend) {
        public static Weights DEFAULT() { return new Weights(0.30, 0.40, 0.30); }
        public double sum() { return stability + recency + trend; }
    }

    /**
     * Tunable parameters for the scoring model.

     * windowDays
     *   How many days back we look. If windowDays = 7, index 0 is “today”, 1 is “yesterday”, …, 6 is 6 days ago.

     * lambda (λ)
     *   How quickly “yesterday” matters less than “today” in the Recency score.
     *   We weight day d by: weight(d) = e^(-λ * d)
     *     - If λ = 0.5, then each day back multiplies weight by ≈ 0.607.
     *     - The “half-life” is ln(2)/λ. With λ = 0.5, half-life ≈ 1.386 days (i.e., ~1.4 days ago counts half).

     * maxSlope
     *   The maximum **relative** slope magnitude considered “consistent enough” for the Trend score.
     *   We compute:
     *     y[d]     = log1p(dailySales[d])
     *     slopeLog = OLS slope of y vs day index (units: “log-sales per day”)
     *     meanLog  = average of y
     *     relSlope = slopeLog / meanLog   (units: “fraction of baseline per day”)

     *   Conversion to a score (soft curve):
     *     steepness = |relSlope| / maxSlope
     *     trendScore = 1 / (1 + steepness)

     *   Intuition:
     *     - relSlope = 0 → steepness = 0 → trendScore = 1 (perfectly flat).
     *     - relSlope = maxSlope → steepness = 1 → trendScore = 0.5 (moderate penalty).
     *     - Very steep lines approach 0 but never slam to 0 (kinder, more stable UX).
     *     - Example: maxSlope = 0.25 treats ≈ 25%/day relative steepness as “very steep”.

     * weights
     *   Blend of Stability/Recency/Trend. We divide by weights.sum() so you don’t need to normalize by hand.
     */
    public record Params(
            int windowDays,
            double lambda,
            double maxSlope,
            Weights weights
    ) {
        public static Params DEFAULT() {
            // 7-day window, λ is computed so the OLDEST bucket in-window (d = windowDays-1)
            // retains ~10% of today's weight in the Recency calculation.
            //
            // Why this change?
            // - With a fixed λ, short windows (e.g., 7 days) decayed too slowly; day 6 still had ~55% weight.
            // - By targeting a *tail weight*, the decay becomes appropriately "steeper" for short windows
            //   and "gentler" for long windows—while keeping the same *relative* end-of-window emphasis.
            //
            // Math:
            //   weight(d) = e^(-λ * d)
            //   Want: weight(windowDays-1) = tailWeight  ⇒  λ = -ln(tailWeight) / (windowDays-1)
            //
            // Pick tailWeight = 0.10 (10% at the oldest day) for a good 0–100% Recency spread.
            // Example results (window=7):
            //   weights ≈ [1.000, 0.681, 0.464, 0.316, 0.215, 0.146, 0.100]
            int window = 7;
            double tailWeight = 0.10; // adjust to 0.01 for *very* aggressive decay
            double lambda = lambdaForTailWeight(window, tailWeight);

            // Trend “too steep” still ≈ 25%/day (unchanged).
            return new Params(window, lambda, 0.25, Weights.DEFAULT());
        }
    }

    /**
     * Result you can show in UI or logs.

     * finalScore     The blended consistency score in [0..1].
     * stabilityScore Higher when day-to-day counts are even; lower when they jump around.
     * recencyScore   Higher if a large fraction of sales happened recently (today > yesterday > older).
     * trendScore     Higher when the overall line is fairly flat (or only gently sloped up/down).

     * meanPerDay     Arithmetic mean of raw counts. “Typical number of sales per day.”
     * stdDevPerDay   Population standard deviation of raw counts. “How much they wiggle around the mean.”
     * slopePerDay    OLS slope on raw counts (sales/day). Positive means “increasing over time.”

     * dailySales     Raw counts per day (0 = today, 1 = yesterday, ...).
     */
    public record Result(
            double finalScore,
            double stabilityScore,
            double recencyScore,
            double trendScore,
            double meanPerDay,
            double stdDevPerDay,
            double slopePerDay,
            int[] dailySales
    ) {}

    /* ------------------------------------------------------------------------------------------------
     * High-level APIs
     * ---------------------------------------------------------------------------------------------- */

    /**
     * Compute consistency from per-day sales counts (index 0 = today, 1 = yesterday, ...).

     * Mental model:
     *   - Stability: “Are your daily numbers smooth?” (steady = good)
     *   - Recency:   “Did most sales happen recently?” (recent = good)
     *   - Trend:     “Is the overall line fairly flat (or only gently sloped)?” (gentle = good)

     * Requirements: dailySales.length >= 2
     */
    public static Result calculateFromDailyCounts(int[] dailySales, Params params) {
        Objects.requireNonNull(dailySales, "dailySales");
        Objects.requireNonNull(params, "params");
        if (dailySales.length < 2) throw new IllegalArgumentException("dailySales must have length >= 2");

        final int n = dailySales.length;

        /* ------------------------------------------------------------------------------------------
         * 1) Stability — inverse of normalized std dev, computed on a dampened series
         *
         * Why dampen?
         *   We don’t want a single “cheap-item spam day” (e.g., 500 sales) to obliterate the score.
         *   So we transform counts with: y[d] = log1p(counts[d]) = log(1 + counts[d]).
         *
         * Quick intuition:
         *   raw:   [0, 1, 2, 10, 100]
         *   log1p: [0.00, 0.69, 1.10, 2.40, 4.62]  ← big numbers compress; small ones still differ
         */
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            y[i] = Math.log1p(Math.max(0, dailySales[i]));
        }

        // meanLog = average level of the dampened series (what “a typical day” looks like after log1p).
        // stdLog  = how much the dampened series wiggles around that level.
        //
        // SOFTENING:
        //   The linear mapping 1 - (std/mean) can slam to 0 on bursty windows (std ≫ mean).
        //   Use a smooth 1 / (1 + x) curve on the coefficient of variation (cv = std/mean)
        //   so we penalize variability without annihilating the score.
        //
        //   cv = 0   → stability = 1.00 (perfectly even)
        //   cv = 1   → stability = 0.50 (moderately uneven)
        //   cv = 3   → stability = 0.25 (very uneven, but not zero)
        // ε (like 1e-9) avoids division by zero if the window is all zeros.
        double meanLog = mean(y);
        double stdLog  = stdDev(y, meanLog);
        double cv = Math.max(0.0, stdLog / (meanLog + 1e-9));  // coefficient of variation on dampened series
        double stability = 1.0 / (1.0 + cv);

        /* ------------------------------------------------------------------------------------------
         * 2) Recency — exponential decay by day index (0 = today)
         *
         * Each day's sales get a weight that shrinks with age:
         *   weight(d) = e^(-lambda * d)
         *
         * NOTE:
         *   In Params.DEFAULT(), λ is derived from (windowDays, tailWeight) so that the oldest
         *   in-window day retains a specific fraction of today's weight. This makes decay "scale"
         *   with the chosen window (short windows decay faster; long windows decay slower).
         *     d = 0 (today):      weight = 1.0
         *     d = 1 (yesterday):  weight = e^(-lambda)
         *     d = 2:              weight = e^(-2*lambda)
         *
         * We compute a "decayed total" and compare it to the plain total:
         *   decayed = sum( dailySales[d] * e^(-lambda * d) )
         *   total   = sum( dailySales[d] )
         *   recency = clamp01( decayed / total )
         *
         * This means:
         *   - If all sales happened today, decayed == total → recency = 1.0
         *   - If sales are older, they count less, so decayed < total → recency < 1.0
         *   - If no sales at all (total=0), we define recency = 0 to avoid NaN.
         */
        double total = sum(dailySales);
        double decayed = 0.0;
        for (int d = 0; d < n; d++) {
            decayed += dailySales[d] * Math.exp(-params.lambda * d);
        }
        double recency = total <= 0 ? 0.0 : clamp01(decayed / total);

        /* ------------------------------------------------------------------------------------------
         * 3) Trend — “How flat is the line?” measured as a relative slope on the dampened series
         *
         * Steps:
         *   a) Fit a straight line to (x = day index, y = log1p(counts)) using OLS (least squares).
         *      - Positive slopeLog → on average, y increases a bit each day (uptrend).
         *      - Negative slopeLog → downtrend.
         *      - Zero slopeLog     → flat.
         *
         *   b) Make it “relative” to the typical level so scale doesn’t bias the score:
         *        relSlope = slopeLog / meanLog
         *      Intuition: “what fraction of the baseline do we change per day?”
         *
         *   c) Convert to a [0..1] score where flat is best:
         *        - Instead of a harsh linear cutoff, we use a soft curve:
         *            trend = 1 / (1 + steepness)
         *            steepness = |relSlope| / maxSlope
         *
         *      Interpretation:
         *        - If relSlope ≈ 0 → steepness = 0 → trend = 1.0 (perfectly flat).
         *        - If relSlope = maxSlope → steepness = 1 → trend = 0.5 (moderately penalized).
         *        - If relSlope ≫ maxSlope → trend decays toward 0 but never fully reaches it.
         *        - This avoids “all sales in one burst” dropping trend unfairly to 0.
         */
        double slopeLog = regressionSlope(y);
        double relSlope = (meanLog <= 0 ? 0.0 : slopeLog / (meanLog + 1e-9));
        double steepness = Math.abs(relSlope) / params.maxSlope;
        double trend = 1.0 / (1.0 + steepness);

        /* ------------------------------------------------------------------------------------------
         * Final blend — weight and normalize the three component scores
         *
         * You can change the Weights to tweak behavior (e.g., value Recency more during events).
         * We divide by the sum to keep the score in [0..1] without requiring weights to sum to 1.
         */
        Weights w = params.weights;
        double wsum = w.sum();
        double finalScore = clamp01(
                (w.stability * stability + w.recency * recency + w.trend * trend) / (wsum == 0 ? 1.0 : wsum)
        );

        // For the UI, we also expose raw descriptive stats (no dampening) so players see familiar numbers.
        double meanRaw  = mean(dailySales);
        double stdRaw   = stdDev(dailySales, meanRaw);
        double slopeRaw = regressionSlope(dailySales);

        return new Result(finalScore, stability, recency, trend, meanRaw, stdRaw, slopeRaw, dailySales.clone());
    }

    /**
     * Compute consistency from raw sale timestamps (epoch millis / Instants).

     * We drop events into day buckets relative to “today” in the given time zone,
     * then call {@link #calculateFromDailyCounts(int[], Params)}.
     *
     * @param saleInstants Can be in any order. Only events within the window are counted.
     * @param params       See {@link Params}.
     * @param clock        The reference clock (use Clock.systemUTC() or your server clock).
     * @param zone         The time zone whose calendar days matter for “today/yesterday”.
     */
    public static Result calculateFromInstants(Iterable<Instant> saleInstants, Params params, Clock clock, ZoneId zone) {
        Objects.requireNonNull(saleInstants, "saleInstants");
        Objects.requireNonNull(params, "params");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(zone, "zone");

        int[] daily = bucketizeByDay(saleInstants, params.windowDays, clock, zone);
        return calculateFromDailyCounts(daily, params);
    }

    /* ------------------------------------------------------------------------------------------------
     * Helpers
     * ---------------------------------------------------------------------------------------------- */

    /**
     * Bucket Instants into [0..windowDays-1] where 0 = “today”, 1 = “yesterday”, etc.

     * Implementation detail:
     *   - Convert each timestamp to a local calendar date (in the provided ZoneId).
     *   - Compute how many midnights ago it was: daysAgo = floor difference in days.
     *   - Ignore anything < 0 (future) or ≥ windowDays (too old).
     */
    public static int[] bucketizeByDay(Iterable<Instant> instants, int windowDays, Clock clock, ZoneId zone) {
        if (windowDays < 1) throw new IllegalArgumentException("windowDays must be >= 1");

        int[] counts = new int[windowDays];
        LocalDate today = LocalDate.now(clock.withZone(zone));

        for (Instant ts : instants) {
            if (ts == null) continue;

            LocalDate day = LocalDateTime.ofInstant(ts, zone).toLocalDate();

            long daysAgo = Duration.between(
                    day.atStartOfDay(zone).toInstant(),
                    today.atStartOfDay(zone).toInstant()
            ).toDays();

            if (daysAgo >= 0 && daysAgo < windowDays) {
                counts[(int) daysAgo]++;
            }
        }
        return counts;
    }

    /* ----- small math utilities (int[] and double[] variants) ------------------------------------ */

    /** Arithmetic average — int[]. Example: [2,4,6] → 4.0 */
    private static double mean(int[] a) {
        return a.length == 0 ? 0.0 : (double) sum(a) / a.length;
    }

    /** Arithmetic average — double[]. */
    private static double mean(double[] a) {
        if (a.length == 0) return 0.0;
        double s = 0.0;
        for (double v : a) s += v;
        return s / a.length;
    }

    /** Sum — int[]. Example: [2,4,6] → 12 */
    private static int sum(int[] a) {
        int s = 0;
        for (int v : a) s += v;
        return s;
    }

    /**
     * Population standard deviation (not sample) — int[].
     * Meaning: “typical distance from the average.”
     * If all values are identical → std = 0. Bigger up/down swings → bigger std.
     */
    private static double stdDev(int[] a, double mean) {
        if (a.length == 0) return 0.0;
        double ss = 0.0;
        for (int v : a) {
            double d = v - mean;
            ss += d * d;
        }
        return Math.sqrt(ss / a.length);
    }

    /** Population standard deviation — double[]. */
    private static double stdDev(double[] a, double mean) {
        if (a.length == 0) return 0.0;
        double ss = 0.0;
        for (double v : a) {
            double d = v - mean;
            ss += d * d;
        }
        return Math.sqrt(ss / a.length);
    }

    /**
     * Ordinary Least Squares (OLS) slope with x = 0..n-1 and y = int[] (raw counts).

     * What does “slope” mean here?
     *   - Draw the “best fit” straight line through points (dayIndex, sales).
     *   - If slope ≈ 0 → the line is flat (consistent).
     *   - If slope > 0 → the line trends upward (sales increasing each day).
     *   - If slope < 0 → the line trends downward.

     * Efficient formula (single pass for sums):
     *   slope = (n * Σ(x*y) - Σx * Σy) / (n * Σ(x^2) - (Σx)^2)
     */
    private static double regressionSlope(int[] y) {
        int n = y.length;
        if (n < 2) return 0.0;

        double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
        for (int i = 0; i < n; i++) {
            sumX += i;
            sumY += y[i];
            sumXY += i * (double) y[i];
            sumXX += i * (double) i;
        }
        double denom = n * sumXX - sumX * sumX;
        if (denom == 0) return 0.0; // degenerate case
        return (n * sumXY - sumX * sumY) / denom;
    }

    /**
     * Ordinary Least Squares (OLS) slope with x = 0..n-1 and y = double[] (dampened counts).

     * Same idea as the int[] version, just operating on log1p(counts).
     */
    private static double regressionSlope(double[] y) {
        int n = y.length;
        if (n < 2) return 0.0;

        double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
        for (int i = 0; i < n; i++) {
            sumX  += i;
            sumY  += y[i];
            sumXY += i * y[i];
            sumXX += i * (double) i;
        }
        double denom = n * sumXX - sumX * sumX;
        if (denom == 0) return 0.0;
        return (n * sumXY - sumX * sumY) / denom;
    }

    /** Clamp into [0, 1]. Treats 0 = 0% and 1 = 100%. */
    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    /* ------------------------------------------------------------------------------------------------
     * Lambda helpers (window-aware decay)
     * ---------------------------------------------------------------------------------------------- */

    /**
     * Compute λ such that the oldest bucket in the window (d = windowDays - 1)
     * has weight exactly {@code tailWeight} relative to today (weight=1 at d=0).

     * λ = -ln(tailWeight) / max(1, windowDays-1)

     * Examples:
     *  - (7, 0.10)  → λ ≈ 0.3846  (day6 ≈ 10%)
     *  - (7, 0.01)  → λ ≈ 0.6597  (day6 ≈ 1%)
     *  - (30, 0.10) → λ ≈ 0.0793  (day29 ≈ 10%)
     */
    private static double lambdaForTailWeight(int windowDays, double tailWeight) {
        int span = Math.max(1, windowDays - 1);
        double t = Math.max(0.0, Math.min(1.0, tailWeight));
        if (t <= 0.0) return 1e9; // essentially zero beyond d>0
        if (t >= 1.0) return 0.0; // no decay
        return -Math.log(t) / span;
    }

    /**
     * Convenience profile: choose a window and desired end-of-window weight,
     * and get a Params with λ computed accordingly.

     * NOTE: Not used by DEFAULT() (which already calls lambdaForTailWeight),
     * but handy if you want another preset elsewhere.
     */
    public static Params aggressiveShortWindow(int windowDays, double tailWeight) {
        return new Params(windowDays, lambdaForTailWeight(windowDays, tailWeight), 0.25, Weights.DEFAULT());
    }
}