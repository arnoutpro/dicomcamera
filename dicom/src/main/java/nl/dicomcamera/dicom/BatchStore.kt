package nl.dicomcamera.dicom

import java.io.File

/**
 * Batch store with per-instance progress and exponential backoff retry.
 * Transport-agnostic: works with DIMSE C-STORE or DICOMweb STOW-RS.
 */
class BatchStore(
    private val storeFn: (File) -> StoreResult,
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
            when (val result = storeFn(file)) {
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
        return (lastFailure ?: StoreResult.Failed("Store failed after $maxAttempts attempts")) to attempts
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

    companion object {
        fun dimse(node: DicomNode, maxAttempts: Int = 3): BatchStore =
            BatchStore(
                storeFn = { file -> PacsClient(node).use { it.store(file) } },
                maxAttempts = maxAttempts,
            )

        fun gateway(endpoint: PacsEndpoint, maxAttempts: Int = 3): BatchStore =
            BatchStore(
                storeFn = { file -> PacsGateway.fromEndpoint(endpoint).store(file) },
                maxAttempts = maxAttempts,
            )
    }
}
