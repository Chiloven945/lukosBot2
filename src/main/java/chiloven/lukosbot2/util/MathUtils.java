package chiloven.lukosbot2.util;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public class MathUtils {
    public MathUtils() {
    }

    /**
     * Approximate counts for each outcome in a multinomial distribution using a normal approximation on sequential
     * binomial draws.
     *
     * @param total         total number of trials, must be non-negative
     * @param probabilities probabilities for each outcome; values must be &gt;= 0
     * @return an array of counts whose length equals {@code probabilities.length}
     * @throws IllegalArgumentException if {@code total} is negative, if {@code probabilities} is null or empty, if any
     *                                  probability is negative, or if the sum of probabilities is not positive
     */
    public long[] approximateMultinomial(long total, double... probabilities) {
        if (total < 0) {
            throw new IllegalArgumentException("total must be >= 0");
        }
        if (probabilities == null || probabilities.length == 0) {
            throw new IllegalArgumentException("probabilities must not be null or empty");
        }

        int k = probabilities.length;
        double sum = 0.0;
        for (double p : probabilities) {
            if (p < 0.0) {
                throw new IllegalArgumentException("probability must be >= 0");
            }
            sum += p;
        }
        if (sum <= 0.0) {
            throw new IllegalArgumentException("sum of probabilities must be > 0");
        }

        // Normalize probabilities to sum 1.0
        double[] probs = new double[k];
        for (int i = 0; i < k; i++) {
            probs[i] = probabilities[i] / sum;
        }

        long[] counts = new long[k];
        long remaining = total;
        double remainingProb = 1.0;

        for (int i = 0; i < k - 1; i++) {
            if (remaining <= 0) {
                // no trials left, remaining outcomes are zero
                counts[i] = 0;
                continue;
            }

            double pi = (remainingProb > 0.0) ? (probs[i] / remainingProb) : 0.0;
            if (pi <= 0.0) {
                counts[i] = 0;
            } else if (pi >= 1.0) {
                counts[i] = remaining;
                remaining = 0;
                remainingProb = 0.0;
            } else {
                double mean = remaining * pi;
                double var = remaining * pi * (1.0 - pi);

                long ci;
                if (var <= 0.0) {
                    ci = Math.round(mean);
                } else {
                    ci = Math.round(mean + Math.sqrt(var) * nextGaussian());
                }

                if (ci < 0) ci = 0;
                if (ci > remaining) ci = remaining;

                counts[i] = ci;
                remaining -= ci;
                remainingProb -= probs[i];
            }
        }

        // Whatever is left goes to the last bucket to keep the total exact
        counts[k - 1] = remaining;
        return counts;
    }

    /**
     * Generate a standard normally distributed random number using the Box-Muller transform.
     *
     * @return A random number from a standard normal distribution (mean 0, standard deviation 1).
     */
    public double nextGaussian() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double u1 = rnd.nextDouble();
        double u2 = rnd.nextDouble();
        return Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
    }

    /**
     * Generate a stable pseudo-random integer in [min, max] based on the given seed objects.
     * <p>
     * For the same (min, max, seeds...) this method always returns the same value.
     * Different seeds are very likely to produce different values.
     *
     * @param min   inclusive lower bound
     * @param max   inclusive upper bound
     * @param seeds seed objects, may include nulls
     * @return an integer in [min, max] deterministically derived from the seeds
     */
    public int stableRandom(int min, int max, Object... seeds) throws IllegalArgumentException {
        if (min > max) {
            throw new IllegalArgumentException("min must be <= max");
        }
        if (seeds == null || seeds.length == 0) {
            throw new IllegalArgumentException("seeds must not be null or empty");
        }

        // 1) combine all seeds into a 64-bit value
        long hash = 0x9E3779B97F4A7C15L; // arbitrary non-zero base
        for (Object seed : seeds) {
            int h = (seed == null) ? 0 : Objects.hashCode(seed);
            hash ^= h + 0x9E3779B97F4A7C15L + (hash << 6) + (hash >>> 2);
        }

        // 2) further (similar to SplitMix64)
        hash ^= (hash >>> 30);
        hash *= 0xBF58476D1CE4E5B9L;
        hash ^= (hash >>> 27);
        hash *= 0x94D049BB133111EBL;
        hash ^= (hash >>> 31);

        // 3) map to [min, max]
        int nonNegative = (int) (hash & 0x7FFFFFFF);
        long range = (long) max - (long) min + 1L;
        int offset = (int) (nonNegative % range);
        return min + offset;
    }
}
