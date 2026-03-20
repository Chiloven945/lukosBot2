package top.chiloven.lukosbot2.util.concurrent

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import top.chiloven.lukosbot2.util.concurrent.Coroutines.forEachLimited
import top.chiloven.lukosbot2.util.concurrent.Coroutines.mapLimited
import top.chiloven.lukosbot2.util.concurrent.Coroutines.runBlockingIo
import kotlin.math.max

/**
 * Coroutine helpers for bridging blocking Java-style call sites and structured Kotlin concurrency.
 *
 * The utilities in this object are intentionally small:
 * - [runBlockingIo] exposes a Java-friendly entry point for invoking suspend code from existing blocking APIs.
 * - [forEachLimited] and [mapLimited] provide bounded parallelism for I/O-heavy work without leaking coroutine setup
 *   details into command or utility classes.
 *
 * `maxConcurrency` values lower than `1` are coerced to `1`.
 */
object Coroutines {

    /**
     * Runs the given suspending [block] in a blocking caller while defaulting execution to [Dispatchers.IO].
     *
     * This is primarily meant for Java interop or legacy synchronous APIs that need to call coroutine-based helpers
     * without turning the surrounding method into `suspend`.
     *
     * @param block the suspending work to execute.
     * @return the result returned by [block].
     */
    @JvmStatic
    fun <T> runBlockingIo(block: suspend CoroutineScope.() -> T): T =
        runBlocking(Dispatchers.IO, block)

    /**
     * Applies [block] to each item with bounded concurrency.
     *
     * A lightweight coroutine is created per input element, but only up to [maxConcurrency] blocks may execute at the
     * same time. This is useful for batched network or file operations where full fan-out would be too aggressive.
     *
     * @param items the input items to process.
     * @param maxConcurrency the maximum number of concurrent executions; values below `1` are treated as `1`.
     * @param dispatcher the dispatcher used to launch worker coroutines, defaulting to [Dispatchers.IO].
     * @param block the suspending action applied to each item.
     */
    suspend fun <T> forEachLimited(
        items: Iterable<T>,
        maxConcurrency: Int,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        block: suspend (T) -> Unit,
    ) {
        val semaphore = Semaphore(max(1, maxConcurrency))
        coroutineScope {
            items.map { item ->
                async(dispatcher) {
                    semaphore.withPermit {
                        block(item)
                    }
                }
            }.awaitAll()
        }
    }

    /**
     * Transforms [items] with bounded concurrency and returns the resulting values in input order.
     *
     * Like [forEachLimited], this helper limits the number of actively running blocks, but it also collects and returns
     * each transformed value once all work completes.
     *
     * @param items the input items to transform.
     * @param maxConcurrency the maximum number of concurrent executions; values below `1` are treated as `1`.
     * @param dispatcher the dispatcher used to launch worker coroutines, defaulting to [Dispatchers.IO].
     * @param block the suspending transformation applied to each item.
     * @return a list of transformed values whose order matches the original [items] iteration order.
     */
    suspend fun <T, R> mapLimited(
        items: Iterable<T>,
        maxConcurrency: Int,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        block: suspend (T) -> R,
    ): List<R> {
        val semaphore = Semaphore(max(1, maxConcurrency))
        return coroutineScope {
            items.map { item ->
                async(dispatcher) {
                    semaphore.withPermit {
                        block(item)
                    }
                }
            }.awaitAll()
        }
    }

}
