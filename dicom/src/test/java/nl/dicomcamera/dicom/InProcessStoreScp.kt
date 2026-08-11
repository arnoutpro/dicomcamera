package nl.dicomcamera.dicom

import org.dcm4che3.data.Attributes
import org.dcm4che3.data.Tag
import org.dcm4che3.data.UID
import org.dcm4che3.io.DicomInputStream
import org.dcm4che3.io.DicomOutputStream
import org.dcm4che3.net.ApplicationEntity
import org.dcm4che3.net.Association
import org.dcm4che3.net.Connection
import org.dcm4che3.net.Device
import org.dcm4che3.net.PDVInputStream
import org.dcm4che3.net.TransferCapability
import org.dcm4che3.net.pdu.PresentationContext
import org.dcm4che3.net.service.BasicCEchoSCP
import org.dcm4che3.net.service.BasicCStoreSCP
import org.dcm4che3.net.service.DicomServiceRegistry
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * In-process Storage + Verification SCP for unit/integration tests (no Docker required).
 */
class InProcessStoreScp(
    private val aeTitle: String = "TESTPACS",
    port: Int = -1,
    private val storageDir: File,
) : AutoCloseable {
    private val listenPort: Int = if (port > 0) port else {
        java.net.ServerSocket(0).use { it.localPort }
    }
    private val executor = Executors.newCachedThreadPool()
    private val scheduledExecutor = Executors.newSingleThreadScheduledExecutor()
    private val stored = ConcurrentHashMap<String, File>()

    private val storeScp = object : BasicCStoreSCP("*") {
        override fun store(
            asAssoc: Association,
            pc: PresentationContext,
            rq: Attributes,
            data: PDVInputStream,
            rsp: Attributes,
        ) {
            val cuid = rq.getString(Tag.AffectedSOPClassUID)
            val iuid = rq.getString(Tag.AffectedSOPInstanceUID)
            val ts = pc.transferSyntax
            val out = File(storageDir, "$iuid.dcm")
            DicomOutputStream(out).use { dos ->
                val fmi = asAssoc.createFileMetaInformation(iuid, cuid, ts)
                dos.writeFileMetaInformation(fmi)
                data.copyTo(dos)
            }
            stored[iuid] = out
        }
    }

    private val dicomDevice = Device("test-storescp").also {
        it.executor = executor
        it.scheduledExecutor = scheduledExecutor
    }

    private val connection = Connection().also {
        it.port = listenPort
        dicomDevice.addConnection(it)
    }

    private val applicationEntity = ApplicationEntity(aeTitle).also { ae ->
        dicomDevice.addApplicationEntity(ae)
        ae.addConnection(connection)
        ae.isAssociationAcceptor = true
        val registry = DicomServiceRegistry().apply {
            addDicomService(BasicCEchoSCP())
            addDicomService(storeScp)
        }
        ae.dimseRQHandler = registry
        ae.addTransferCapability(
            TransferCapability(
                null,
                UID.Verification,
                TransferCapability.Role.SCP,
                UID.ImplicitVRLittleEndian,
            ),
        )
        ae.addTransferCapability(
            TransferCapability(
                null,
                UID.SecondaryCaptureImageStorage,
                TransferCapability.Role.SCP,
                UID.JPEGBaseline8Bit,
                UID.ExplicitVRLittleEndian,
                UID.ImplicitVRLittleEndian,
            ),
        )
    }

    val boundPort: Int
        get() = connection.port

    fun start() {
        // Touch AE so it is not optimized away; bind listening socket.
        check(applicationEntity.aeTitle == aeTitle)
        storageDir.mkdirs()
        dicomDevice.bindConnections()
    }

    fun storedFiles(): Map<String, File> = stored.toMap()

    fun readPatientId(sopInstanceUid: String): String? {
        val file = stored[sopInstanceUid] ?: return null
        DicomInputStream(file).use { input ->
            input.readFileMetaInformation()
            val dataset = input.readDataset()
            return dataset.getString(Tag.PatientID)
        }
    }

    override fun close() {
        try {
            dicomDevice.unbindConnections()
        } finally {
            executor.shutdownNow()
            scheduledExecutor.shutdownNow()
        }
    }
}
