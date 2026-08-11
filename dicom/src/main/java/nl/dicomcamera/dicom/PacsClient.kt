package nl.dicomcamera.dicom

import org.dcm4che3.data.Attributes
import org.dcm4che3.data.Tag
import org.dcm4che3.data.UID
import org.dcm4che3.data.VR
import org.dcm4che3.io.DicomInputStream
import org.dcm4che3.net.ApplicationEntity
import org.dcm4che3.net.Association
import org.dcm4che3.net.Connection
import org.dcm4che3.net.DataWriterAdapter
import org.dcm4che3.net.Device
import org.dcm4che3.net.DimseRSP
import org.dcm4che3.net.Priority
import org.dcm4che3.net.pdu.AAssociateRQ
import org.dcm4che3.net.pdu.PresentationContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.ArrayList
import java.util.concurrent.Executors

/**
 * DIMSE SCU: C-ECHO, C-STORE, Modality Worklist C-FIND, Study Root C-FIND.
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
    private val connection = Connection().also { conn ->
        if (node.useTls) {
            conn.setTlsProtocols("TLSv1.2", "TLSv1.3")
            conn.setTlsCipherSuites(
                "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
                "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
                "TLS_AES_128_GCM_SHA256",
                "TLS_AES_256_GCM_SHA384",
            )
        }
        dicomDevice.addConnection(conn)
    }
    private val applicationEntity = ApplicationEntity(node.callingAeTitle).also { ae ->
        dicomDevice.addApplicationEntity(ae)
        ae.addConnection(connection)
    }

    fun echo(): EchoResult {
        return try {
            withAssociation(AssociationMode.Echo) { asAssoc ->
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
                    ?: UID.VLPhotographicImageStorage
                val sopInstanceUid = fmi.getString(Tag.MediaStorageSOPInstanceUID)
                    ?: dataset.getString(Tag.SOPInstanceUID)
                    ?: return StoreResult.Failed("Missing SOP Instance UID")
                val transferSyntax = fmi.getString(Tag.TransferSyntaxUID)
                    ?: UID.ExplicitVRLittleEndian

                withAssociation(AssociationMode.Store) { asAssoc ->
                    val rsp = asAssoc.cstore(
                        sopClassUid,
                        sopInstanceUid,
                        Priority.NORMAL,
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

    fun findWorklist(query: WorklistQuery): FindResult<WorklistEntry> {
        return try {
            val keys = buildWorklistKeys(query)
            withAssociation(AssociationMode.Worklist) { asAssoc ->
                val items = ArrayList<WorklistEntry>()
                val rsp = asAssoc.cfind(
                    UID.ModalityWorklistInformationModelFind,
                    Priority.NORMAL,
                    keys,
                    UID.ExplicitVRLittleEndian,
                    0,
                )
                while (rsp.next()) {
                    val status = rsp.command.getInt(Tag.Status, -1)
                    when {
                        status == StatusPending || status == StatusPendingWarning -> {
                            rsp.dataset?.let { items += it.toWorklistEntry() }
                        }
                        status == 0 -> {
                            rsp.dataset?.let { items += it.toWorklistEntry() }
                        }
                        else -> return@withAssociation FindResult.Failed("MWL C-FIND status=$status")
                    }
                }
                FindResult.Success(items)
            }
        } catch (e: Exception) {
            FindResult.Failed(e.message ?: e.javaClass.simpleName, e)
        }
    }

    fun findStudies(query: StudyQuery): FindResult<StudyEntry> {
        return try {
            val keys = Attributes().apply {
                setString(Tag.QueryRetrieveLevel, VR.CS, "STUDY")
                setString(Tag.PatientID, VR.LO, query.patientId.orEmpty())
                setString(Tag.PatientName, VR.PN, query.patientName.orEmpty())
                setString(Tag.AccessionNumber, VR.SH, query.accessionNumber.orEmpty())
                setString(Tag.StudyInstanceUID, VR.UI, query.studyInstanceUid.orEmpty())
                setString(Tag.PatientBirthDate, VR.DA, "")
                setString(Tag.PatientSex, VR.CS, "")
                setString(Tag.StudyDate, VR.DA, "")
                setString(Tag.StudyDescription, VR.LO, "")
                setString(Tag.ModalitiesInStudy, VR.CS, "")
            }
            withAssociation(AssociationMode.StudyFind) { asAssoc ->
                val items = ArrayList<StudyEntry>()
                val rsp = asAssoc.cfind(
                    UID.StudyRootQueryRetrieveInformationModelFind,
                    Priority.NORMAL,
                    keys,
                    UID.ExplicitVRLittleEndian,
                    0,
                )
                while (rsp.next()) {
                    val status = rsp.command.getInt(Tag.Status, -1)
                    when {
                        status == StatusPending || status == StatusPendingWarning -> {
                            rsp.dataset?.let { ds ->
                                val uid = ds.getString(Tag.StudyInstanceUID)
                                if (!uid.isNullOrBlank()) items += ds.toStudyEntry()
                            }
                        }
                        status == 0 -> {
                            rsp.dataset?.let { ds ->
                                val uid = ds.getString(Tag.StudyInstanceUID)
                                if (!uid.isNullOrBlank()) items += ds.toStudyEntry()
                            }
                        }
                        else -> return@withAssociation FindResult.Failed("Study C-FIND status=$status")
                    }
                }
                FindResult.Success(items)
            }
        } catch (e: Exception) {
            FindResult.Failed(e.message ?: e.javaClass.simpleName, e)
        }
    }

    private fun buildWorklistKeys(query: WorklistQuery): Attributes {
        val today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
        val keys = Attributes()
        keys.setString(Tag.PatientID, VR.LO, query.patientId.orEmpty())
        keys.setString(Tag.PatientName, VR.PN, query.patientName.orEmpty())
        keys.setString(Tag.PatientBirthDate, VR.DA, "")
        keys.setString(Tag.PatientSex, VR.CS, "")
        keys.setString(Tag.AccessionNumber, VR.SH, query.accessionNumber.orEmpty())
        keys.setString(Tag.StudyInstanceUID, VR.UI, "")
        keys.setString(Tag.RequestedProcedureID, VR.SH, "")
        keys.setString(Tag.RequestedProcedureDescription, VR.LO, "")

        val sps = Attributes()
        sps.setString(Tag.Modality, VR.CS, query.modality.orEmpty())
        sps.setString(
            Tag.ScheduledStationAETitle,
            VR.AE,
            query.scheduledStationAeTitle.orEmpty(),
        )
        sps.setString(
            Tag.ScheduledProcedureStepStartDate,
            VR.DA,
            query.scheduledDate ?: today,
        )
        sps.setString(Tag.ScheduledProcedureStepStartTime, VR.TM, "")
        sps.setString(Tag.ScheduledProcedureStepID, VR.SH, "")
        sps.setString(Tag.ScheduledProcedureStepDescription, VR.LO, "")
        keys.newSequence(Tag.ScheduledProcedureStepSequence, 1).add(sps)
        return keys
    }

    private inline fun <T> withAssociation(mode: AssociationMode, block: (Association) -> T): T {
        val remote = Connection("remote", node.host, node.port).also { remoteConn ->
            if (node.useTls) {
                remoteConn.setTlsProtocols(*connection.tlsProtocols)
                remoteConn.setTlsCipherSuites(*connection.tlsCipherSuites)
            }
        }
        val rq = AAssociateRQ().apply {
            calledAET = node.calledAeTitle
            var pcId = 1
            addPresentationContext(
                PresentationContext(pcId, UID.Verification, UID.ImplicitVRLittleEndian),
            )
            pcId += 2
            when (mode) {
                AssociationMode.Echo -> Unit
                AssociationMode.Store -> {
                    addPresentationContext(
                        PresentationContext(
                            pcId,
                            UID.VLPhotographicImageStorage,
                            UID.JPEGBaseline8Bit,
                            UID.ExplicitVRLittleEndian,
                            UID.ImplicitVRLittleEndian,
                        ),
                    )
                    pcId += 2
                    addPresentationContext(
                        PresentationContext(
                            pcId,
                            UID.SecondaryCaptureImageStorage,
                            UID.JPEGBaseline8Bit,
                            UID.ExplicitVRLittleEndian,
                            UID.ImplicitVRLittleEndian,
                        ),
                    )
                }
                AssociationMode.Worklist -> {
                    addPresentationContext(
                        PresentationContext(
                            pcId,
                            UID.ModalityWorklistInformationModelFind,
                            UID.ExplicitVRLittleEndian,
                            UID.ImplicitVRLittleEndian,
                        ),
                    )
                }
                AssociationMode.StudyFind -> {
                    addPresentationContext(
                        PresentationContext(
                            pcId,
                            UID.StudyRootQueryRetrieveInformationModelFind,
                            UID.ExplicitVRLittleEndian,
                            UID.ImplicitVRLittleEndian,
                        ),
                    )
                }
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

    private enum class AssociationMode { Echo, Store, Worklist, StudyFind }

    companion object {
        private const val StatusPending = 0xFF00
        private const val StatusPendingWarning = 0xFF01
    }
}

private fun Attributes.toWorklistEntry(): WorklistEntry {
    val sps = getSequence(Tag.ScheduledProcedureStepSequence)?.get(0)
    return WorklistEntry(
        patientId = getString(Tag.PatientID).orEmpty(),
        patientName = getString(Tag.PatientName).orEmpty(),
        patientBirthDate = getString(Tag.PatientBirthDate),
        patientSex = getString(Tag.PatientSex),
        accessionNumber = getString(Tag.AccessionNumber),
        studyInstanceUid = getString(Tag.StudyInstanceUID),
        requestedProcedureId = getString(Tag.RequestedProcedureID),
        scheduledProcedureStepId = sps?.getString(Tag.ScheduledProcedureStepID),
        modality = sps?.getString(Tag.Modality),
        scheduledStationAeTitle = sps?.getString(Tag.ScheduledStationAETitle),
        scheduledStartDate = sps?.getString(Tag.ScheduledProcedureStepStartDate),
        scheduledStartTime = sps?.getString(Tag.ScheduledProcedureStepStartTime),
        studyDescription = getString(Tag.RequestedProcedureDescription)
            ?: sps?.getString(Tag.ScheduledProcedureStepDescription),
    )
}

private fun Attributes.toStudyEntry(): StudyEntry =
    StudyEntry(
        patientId = getString(Tag.PatientID).orEmpty(),
        patientName = getString(Tag.PatientName).orEmpty(),
        patientBirthDate = getString(Tag.PatientBirthDate),
        patientSex = getString(Tag.PatientSex),
        accessionNumber = getString(Tag.AccessionNumber),
        studyInstanceUid = getString(Tag.StudyInstanceUID).orEmpty(),
        studyDate = getString(Tag.StudyDate),
        studyDescription = getString(Tag.StudyDescription),
        modalitiesInStudy = getString(Tag.ModalitiesInStudy),
    )

sealed interface EchoResult {
    data object Success : EchoResult
    data class Failed(val message: String, val cause: Throwable? = null) : EchoResult
}

sealed interface StoreResult {
    data class Success(val sopInstanceUid: String) : StoreResult
    data class Failed(val message: String, val cause: Throwable? = null) : StoreResult
}
