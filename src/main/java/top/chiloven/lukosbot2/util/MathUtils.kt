package top.chiloven.lukosbot2.util

import java.util.*
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

object MathUtils {
    /**
     * Approximate counts for each outcome in a multinomial distribution using a normal approximation on sequential
     * binomial draws.
     *
     * @param total         total number of trials, must be non-negative
     * @param probabilities probabilities for each outcome; values must be &gt;= 0
     * @return an array of counts whose length equals `probabilities.length`
     * @throws IllegalArgumentException if `total` is negative, if `probabilities` is null or empty, if any
     * probability is negative, or if the sum of probabilities is not positive
     */
    @JvmStatic
    fun approximateMultinomial(total: Long, vararg probabilities: Double): LongArray {
        require(total >= 0) { "total must be >= 0" }
        require(probabilities.isNotEmpty()) { "probabilities must not be null or empty" }

        val k = probabilities.size
        var sum = 0.0
        for (p in probabilities) {
            require(!(p < 0.0)) { "probability must be >= 0" }
            sum += p
        }
        require(!(sum <= 0.0)) { "sum of probabilities must be > 0" }

        // Normalize probabilities to sum 1.0
        val probs = DoubleArray(k)
        for (i in 0..<k) {
            probs[i] = probabilities[i] / sum
        }

        val counts = LongArray(k)
        var remaining = total
        var remainingProb = 1.0

        for (i in 0..<k - 1) {
            if (remaining <= 0) {
                // no trials left, remaining outcomes are zero
                counts[i] = 0
                continue
            }

            val pi = if (remainingProb > 0.0) (probs[i] / remainingProb) else 0.0
            if (pi <= 0.0) {
                counts[i] = 0
            } else if (pi >= 1.0) {
                counts[i] = remaining
                remaining = 0
                remainingProb = 0.0
            } else {
                val mean = remaining * pi
                val `var` = remaining * pi * (1.0 - pi)

                var ci: Long
                ci = if (`var` <= 0.0) {
                    mean.roundToInt().toLong()
                } else {
                    (mean + sqrt(`var`) * nextGaussian()).roundToInt().toLong()
                }

                if (ci < 0) ci = 0
                if (ci > remaining) ci = remaining

                counts[i] = ci
                remaining -= ci
                remainingProb -= probs[i]
            }
        }

        // Whatever is left goes to the last bucket to keep the total exact
        counts[k - 1] = remaining
        return counts
    }

    /**
     * Generate a standard normally distributed random number using the Box-Muller transform.
     *
     * @return A random number from a standard normal distribution (mean 0, standard deviation 1).
     */
    fun nextGaussian(): Double {
        val rnd = ThreadLocalRandom.current()
        val u1 = rnd.nextDouble()
        val u2 = rnd.nextDouble()
        return sqrt(-2.0 * ln(u1)) * cos(2.0 * Math.PI * u2)
    }

    /**
     * Generate a stable pseudo-random integer in [min, max] based on the given seed objects.
     *
     *
     * For the same (min, max, seeds...) this method always returns the same value.
     * Different seeds are very likely to produce different values.
     *
     * @param min   inclusive lower bound
     * @param max   inclusive upper bound
     * @param seeds seed objects, may include nulls
     * @return an integer in [min, max] deterministically derived from the seeds
     */
    @JvmStatic
    @Throws(IllegalArgumentException::class)
    fun stableRandom(min: Int, max: Int, vararg seeds: Any?): Int {
        require(min <= max) { "min must be <= max" }
        require(seeds.isNotEmpty()) { "seeds must not be null or empty" }

        // 1) combine all seeds into a 64-bit value
        var hash = -0x61c8864680b583ebL // arbitrary non-zero base
        for (seed in seeds) {
            val h = if (seed == null) 0 else Objects.hashCode(seed)
            hash = hash xor h + -0x61c8864680b583ebL + (hash shl 6) + (hash ushr 2)
        }

        // 2) further (similar to SplitMix64)
        hash = hash xor (hash ushr 30)
        hash *= -0x40a7b892e31b1a47L
        hash = hash xor (hash ushr 27)
        hash *= -0x6b2fb644ecceee15L
        hash = hash xor (hash ushr 31)

        // 3) map to [min, max]
        val nonNegative = (hash and 0x7FFFFFFFL).toInt()
        val range = max.toLong() - min.toLong() + 1L
        val offset = (nonNegative % range).toInt()
        return min + offset
    }
}
