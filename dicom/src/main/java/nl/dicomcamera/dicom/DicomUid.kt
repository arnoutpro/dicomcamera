package nl.dicomcamera.dicom

import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.UUID

/**
 * Generates DICOM UIDs using the UUID-derived OID arc (2.25.<decimal-uuid>).
 * Replace with an organizational root OID before production deployments.
 */
object DicomUid {
    private const val UUID_OID_ROOT = "2.25"

    fun newUid(): String {
        val uuid = UUID.randomUUID()
        val buffer = ByteBuffer.allocate(16)
        buffer.putLong(uuid.mostSignificantBits)
        buffer.putLong(uuid.leastSignificantBits)
        val decimal = BigInteger(1, buffer.array()).toString()
        return "$UUID_OID_ROOT.$decimal"
    }
}
