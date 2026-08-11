package nl.dicomcamera.dicom

import java.io.File

/**
 * Batch C-STORE with per-instance progress and exponential backoff retry.
 */
class BatchStore(
    private val clientFactory: () -> PacsClient,
    private val maxAttempts: Int = 3,
    private val initialBackoffMs: Long = 400,
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
) {
    data class ItemOutcome(
        val index: Int,
        val file: File,
        val result: StoreResult,
        val attempts: Int,
    )

    fun storeWithRetry(file: File): Pair<StoreResult, Int> {
        var attempts = 0
        var lastFailure: StoreResult.Failed? = null
        while (attempts < maxAttempts) {
            attempts++
            clientFactory().use { client ->
                when (val result = client.store(file)) {
                    is StoreResult.Success -> return result to attempts
                    is StoreResult.Failed -> {
                        lastFailure = result
                        if (attempts < maxAttempts) {
                            val delay = initialBackoffMs * (1L shl (attempts - 1))
                            sleeper(delay)
                        }
                    }
                }
            }
        }
        return (lastFailure ?: StoreResult.Failed("C-STORE failed after $maxAttempts attempts")) to attempts
    }

    fun storeAll(
        files: List<File>,
        onProgress: (ItemOutcome) -> Unit = {},
    ): List<ItemOutcome> {
        return files.mapIndexed { index, file ->
            val (result, attempts) = storeWithRetry(file)
            val outcome = ItemOutcome(index, file, result, attempts)
            onProgress(outcome)
            outcome
        }
    }
}
