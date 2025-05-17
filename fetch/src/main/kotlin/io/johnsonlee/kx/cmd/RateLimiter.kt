package io.johnsonlee.kx.cmd

internal class RateLimiter(private val maxTokensPerMinute: Int) {
    private var availableTokens = 1
    private val refillRatePerSecond: Double = maxTokensPerMinute / 60.0
    private var lastRefillTimeNanos = System.nanoTime()

    @Synchronized
    fun acquire() {
        refill()

        if (availableTokens > 0) {
            availableTokens--
            return
        }

        safeSleepNanos((1.0 / refillRatePerSecond * 1_000_000_000L).toLong())
        acquire()
    }

    private fun refill() {
        val now = System.nanoTime()
        val elapsedSeconds = (now - lastRefillTimeNanos) / 1_000_000_000.0
        val tokensToAdd = (elapsedSeconds * refillRatePerSecond).toInt()

        if (tokensToAdd > 0) {
            availableTokens = minOf(maxTokensPerMinute, availableTokens + tokensToAdd)
            lastRefillTimeNanos = now
        }
    }

}

private fun safeSleepNanos(nanos: Long) {
    val start = System.nanoTime()
    val deadline = start + nanos
    var remaining = nanos

    while (remaining > 0) {
        try {
            Thread.sleep(remaining / 1_000_000, (remaining % 1_000_000).toInt())
            return
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            remaining = deadline - System.nanoTime()
        }
    }
}