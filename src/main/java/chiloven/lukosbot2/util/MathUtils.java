package chiloven.lukosbot2.util;

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
}
