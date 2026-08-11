package nl.dicomcamera.dicom

import org.dcm4che3.data.Attributes
import org.dcm4che3.data.Tag
import org.dcm4che3.data.UID
import org.dcm4che3.data.VR
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
import org.dcm4che3.net.service.BasicCFindSCP
import org.dcm4che3.net.service.BasicCStoreSCP
import org.dcm4che3.net.service.BasicQueryTask
import org.dcm4che3.net.service.DicomServiceRegistry
import org.dcm4che3.net.service.QueryTask
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-process SCP for Verification, Storage, MWL C-FIND, and Study Root C-FIND.
 */
class InProcessDicomScp(
    private val aeTitle: String = "TESTPACS",
    port: Int = -1,
    private val storageDir: File,
) : AutoCloseable {
    private val listenPort = if (port > 0) port else ServerSocket(0).use { it.localPort }
    private val executor = Executors.newCachedThreadPool()
    private val scheduledExecutor = Executors.newSingleThreadScheduledExecutor()
    private val stored = ConcurrentHashMap<String, File>()
    private val worklist = CopyOnWriteArrayList<Attributes>()
    private val studies = CopyOnWriteArrayList<Attributes>()

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

    private val findScp = object : BasicCFindSCP(
        UID.ModalityWorklistInformationModelFind,
        UID.StudyRootQueryRetrieveInformationModelFind,
    ) {
        override fun calculateMatches(
            asAssoc: Association,
            pc: PresentationContext,
            rq: Attributes,
            keys: Attributes,
        ): QueryTask {
            val cuid = rq.getString(Tag.AffectedSOPClassUID)
            val matches = when (cuid) {
                UID.ModalityWorklistInformationModelFind -> matchWorklist(keys)
                UID.StudyRootQueryRetrieveInformationModelFind -> matchStudies(keys)
                else -> emptyList()
            }
            return object : BasicQueryTask(asAssoc, pc, rq, keys) {
                private val index = AtomicInteger(0)
                override fun hasMoreMatches(): Boolean = index.get() < matches.size
                override fun nextMatch(): Attributes = matches[index.getAndIncrement()]
            }
        }
    }

    private val dicomDevice = Device("test-dicom-scp").also {
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
            addDicomService(findScp)
        }
        ae.dimseRQHandler = registry
        fun tc(uid: String, vararg ts: String) {
            ae.addTransferCapability(TransferCapability(null, uid, TransferCapability.Role.SCP, *ts))
        }
        tc(UID.Verification, UID.ImplicitVRLittleEndian)
        tc(
            UID.SecondaryCaptureImageStorage,
            UID.JPEGBaseline8Bit,
            UID.ExplicitVRLittleEndian,
            UID.ImplicitVRLittleEndian,
        )
        tc(
            UID.VLPhotographicImageStorage,
            UID.JPEGBaseline8Bit,
            UID.ExplicitVRLittleEndian,
            UID.ImplicitVRLittleEndian,
        )
        tc(
            UID.ModalityWorklistInformationModelFind,
            UID.ExplicitVRLittleEndian,
            UID.ImplicitVRLittleEndian,
        )
        tc(
            UID.StudyRootQueryRetrieveInformationModelFind,
            UID.ExplicitVRLittleEndian,
            UID.ImplicitVRLittleEndian,
        )
    }

    val boundPort: Int get() = connection.port

    fun start() {
        check(applicationEntity.aeTitle == aeTitle)
        storageDir.mkdirs()
        dicomDevice.bindConnections()
    }

    fun addWorklistItem(entry: WorklistEntry) {
        worklist += Attributes().apply {
            setString(Tag.PatientID, VR.LO, entry.patientId)
            setString(Tag.PatientName, VR.PN, entry.patientName)
            setString(Tag.PatientBirthDate, VR.DA, entry.patientBirthDate.orEmpty())
            setString(Tag.PatientSex, VR.CS, entry.patientSex.orEmpty())
            setString(Tag.AccessionNumber, VR.SH, entry.accessionNumber.orEmpty())
            setString(Tag.StudyInstanceUID, VR.UI, entry.studyInstanceUid.orEmpty())
            setString(Tag.RequestedProcedureID, VR.SH, entry.requestedProcedureId.orEmpty())
            setString(Tag.RequestedProcedureDescription, VR.LO, entry.studyDescription.orEmpty())
            val sps = Attributes()
            sps.setString(Tag.Modality, VR.CS, entry.modality.orEmpty())
            sps.setString(Tag.ScheduledStationAETitle, VR.AE, entry.scheduledStationAeTitle.orEmpty())
            sps.setString(Tag.ScheduledProcedureStepStartDate, VR.DA, entry.scheduledStartDate.orEmpty())
            sps.setString(Tag.ScheduledProcedureStepStartTime, VR.TM, entry.scheduledStartTime.orEmpty())
            sps.setString(Tag.ScheduledProcedureStepID, VR.SH, entry.scheduledProcedureStepId.orEmpty())
            sps.setString(Tag.ScheduledProcedureStepDescription, VR.LO, entry.studyDescription.orEmpty())
            newSequence(Tag.ScheduledProcedureStepSequence, 1).add(sps)
        }
    }

    fun addStudy(entry: StudyEntry) {
        studies += Attributes().apply {
            setString(Tag.QueryRetrieveLevel, VR.CS, "STUDY")
            setString(Tag.PatientID, VR.LO, entry.patientId)
            setString(Tag.PatientName, VR.PN, entry.patientName)
            setString(Tag.PatientBirthDate, VR.DA, entry.patientBirthDate.orEmpty())
            setString(Tag.PatientSex, VR.CS, entry.patientSex.orEmpty())
            setString(Tag.AccessionNumber, VR.SH, entry.accessionNumber.orEmpty())
            setString(Tag.StudyInstanceUID, VR.UI, entry.studyInstanceUid)
            setString(Tag.StudyDate, VR.DA, entry.studyDate.orEmpty())
            setString(Tag.StudyDescription, VR.LO, entry.studyDescription.orEmpty())
            setString(Tag.ModalitiesInStudy, VR.CS, entry.modalitiesInStudy.orEmpty())
        }
    }

    fun readPatientId(sopInstanceUid: String): String? {
        val file = stored[sopInstanceUid] ?: return null
        DicomInputStream(file).use { input ->
            input.readFileMetaInformation()
            return input.readDataset().getString(Tag.PatientID)
        }
    }

    fun readStudyUid(sopInstanceUid: String): String? {
        val file = stored[sopInstanceUid] ?: return null
        DicomInputStream(file).use { input ->
            input.readFileMetaInformation()
            return input.readDataset().getString(Tag.StudyInstanceUID)
        }
    }

    private fun matchWorklist(keys: Attributes): List<Attributes> {
        val patientId = keys.getString(Tag.PatientID).orEmpty()
        val accession = keys.getString(Tag.AccessionNumber).orEmpty()
        val spsKey = keys.getSequence(Tag.ScheduledProcedureStepSequence)?.get(0)
        val modality = spsKey?.getString(Tag.Modality).orEmpty()
        val date = spsKey?.getString(Tag.ScheduledProcedureStepStartDate).orEmpty()
        val station = spsKey?.getString(Tag.ScheduledStationAETitle).orEmpty()
        return worklist.filter { item ->
            val sps = item.getSequence(Tag.ScheduledProcedureStepSequence)?.get(0)
            (patientId.isBlank() || item.getString(Tag.PatientID) == patientId) &&
                (accession.isBlank() || item.getString(Tag.AccessionNumber) == accession) &&
                (modality.isBlank() || sps?.getString(Tag.Modality) == modality) &&
                (date.isBlank() || sps?.getString(Tag.ScheduledProcedureStepStartDate) == date) &&
                (station.isBlank() || sps?.getString(Tag.ScheduledStationAETitle) == station)
        }
    }

    private fun matchStudies(keys: Attributes): List<Attributes> {
        val patientId = keys.getString(Tag.PatientID).orEmpty()
        val accession = keys.getString(Tag.AccessionNumber).orEmpty()
        val studyUid = keys.getString(Tag.StudyInstanceUID).orEmpty()
        return studies.filter { item ->
            (patientId.isBlank() || item.getString(Tag.PatientID) == patientId) &&
                (accession.isBlank() || item.getString(Tag.AccessionNumber) == accession) &&
                (studyUid.isBlank() || item.getString(Tag.StudyInstanceUID) == studyUid)
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
