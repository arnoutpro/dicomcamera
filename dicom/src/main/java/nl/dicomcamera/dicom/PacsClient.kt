package nl.dicomcamera.dicom

import org.dcm4che3.data.Tag
import org.dcm4che3.data.UID
import org.dcm4che3.io.DicomInputStream
import org.dcm4che3.net.ApplicationEntity
import org.dcm4che3.net.Association
import org.dcm4che3.net.Connection
import org.dcm4che3.net.DataWriterAdapter
import org.dcm4che3.net.Device
import org.dcm4che3.net.DimseRSP
import org.dcm4che3.net.pdu.AAssociateRQ
import org.dcm4che3.net.pdu.PresentationContext
import java.io.File
import java.util.concurrent.Executors

/**
 * Minimal DIMSE SCU for Phase 0: C-ECHO and C-STORE.
 */
class PacsClient(
    private val node: DicomNode,
) : AutoCloseable {
    private val executor = Executors.newCachedThreadPool()
    private val scheduledExecutor = Executors.newSingleThreadScheduledExecutor()
    private val dicomDevice = Device("dicomcamera-scu").also {
        it.executor = executor
        it.scheduledExecutor = scheduledExecutor
    }
    private val connection = Connection().also { dicomDevice.addConnection(it) }
    private val applicationEntity = ApplicationEntity(node.callingAeTitle).also { ae ->
        dicomDevice.addApplicationEntity(ae)
        ae.addConnection(connection)
    }

    fun echo(): EchoResult {
        return try {
            withAssociation(includeStorage = false) { asAssoc ->
                val rsp: DimseRSP = asAssoc.cecho()
                rsp.next()
                val status = rsp.command.getInt(Tag.Status, -1)
                if (status == 0) EchoResult.Success else EchoResult.Failed("C-ECHO status=$status")
            }
        } catch (e: Exception) {
            EchoResult.Failed(e.message ?: e.javaClass.simpleName, e)
        }
    }

    fun store(dicomFile: File): StoreResult {
        return try {
            DicomInputStream(dicomFile).use { input ->
                val fmi = input.readFileMetaInformation()
                    ?: return StoreResult.Failed("Missing DICOM file meta information")
                val dataset = input.readDataset()
                val sopClassUid = fmi.getString(Tag.MediaStorageSOPClassUID)
                    ?: dataset.getString(Tag.SOPClassUID)
                    ?: UID.SecondaryCaptureImageStorage
                val sopInstanceUid = fmi.getString(Tag.MediaStorageSOPInstanceUID)
                    ?: dataset.getString(Tag.SOPInstanceUID)
                    ?: return StoreResult.Failed("Missing SOP Instance UID")
                val transferSyntax = fmi.getString(Tag.TransferSyntaxUID)
                    ?: UID.ExplicitVRLittleEndian

                withAssociation(includeStorage = true) { asAssoc ->
                    val rsp = asAssoc.cstore(
                        sopClassUid,
                        sopInstanceUid,
                        0,
                        DataWriterAdapter(dataset),
                        transferSyntax,
                    )
                    rsp.next()
                    val status = rsp.command.getInt(Tag.Status, -1)
                    if (status == 0) {
                        StoreResult.Success(sopInstanceUid)
                    } else {
                        StoreResult.Failed("C-STORE status=$status")
                    }
                }
            }
        } catch (e: Exception) {
            StoreResult.Failed(e.message ?: e.javaClass.simpleName, e)
        }
    }

    private inline fun <T> withAssociation(includeStorage: Boolean, block: (Association) -> T): T {
        val remote = Connection("remote", node.host, node.port)
        val rq = AAssociateRQ().apply {
            calledAET = node.calledAeTitle
            addPresentationContext(
                PresentationContext(1, UID.Verification, UID.ImplicitVRLittleEndian),
            )
            if (includeStorage) {
                addPresentationContext(
                    PresentationContext(
                        3,
                        UID.SecondaryCaptureImageStorage,
                        UID.JPEGBaseline8Bit,
                        UID.ExplicitVRLittleEndian,
                        UID.ImplicitVRLittleEndian,
                    ),
                )
            }
        }
        val asAssoc = applicationEntity.connect(connection, remote, rq)
        return try {
            block(asAssoc)
        } finally {
            try {
                asAssoc.release()
            } catch (_: Exception) {
                try {
                    asAssoc.abort()
                } catch (_: Exception) {
                    // ignore
                }
            }
        }
    }

    override fun close() {
        executor.shutdownNow()
        scheduledExecutor.shutdownNow()
    }
}

sealed interface EchoResult {
    data object Success : EchoResult
    data class Failed(val message: String, val cause: Throwable? = null) : EchoResult
}

sealed interface StoreResult {
    data class Success(val sopInstanceUid: String) : StoreResult
    data class Failed(val message: String, val cause: Throwable? = null) : StoreResult
}
