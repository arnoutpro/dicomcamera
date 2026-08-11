package nl.dicomcamera.dicom

import org.dcm4che3.data.Attributes
import org.dcm4che3.data.Fragments
import org.dcm4che3.data.Tag
import org.dcm4che3.data.UID
import org.dcm4che3.data.VR
import org.dcm4che3.io.DicomOutputStream
import org.dcm4che3.util.ByteUtils
import java.io.File

/**
 * Encodes a JPEG baseline still as DICOM VL Photographic Image Storage
 * (preferred clinical photo SOP class for Phase 1).
 */
class PhotographicImageEncoder(
    private val uidGenerator: () -> String = { DicomUid.newUid() },
) {
    fun encodeJpegToFile(
        jpegBytes: ByteArray,
        context: PatientStudyContext,
        rows: Int,
        columns: Int,
        outputFile: File,
    ): EncodedInstance {
        require(jpegBytes.isNotEmpty()) { "JPEG bytes required" }
        require(rows > 0 && columns > 0) { "rows/columns required" }
        require(context.patientId.isNotBlank()) { "Patient ID required" }
        require(context.patientName.isNotBlank()) { "Patient Name required" }

        val studyUid = context.studyInstanceUid?.takeIf { it.isNotBlank() } ?: uidGenerator()
        val seriesUid = context.seriesInstanceUid?.takeIf { it.isNotBlank() } ?: uidGenerator()
        val sopUid = uidGenerator()

        val fmi = Attributes(6).apply {
            setBytes(Tag.FileMetaInformationVersion, VR.OB, byteArrayOf(0, 1))
            setString(Tag.MediaStorageSOPClassUID, VR.UI, UID.VLPhotographicImageStorage)
            setString(Tag.MediaStorageSOPInstanceUID, VR.UI, sopUid)
            setString(Tag.TransferSyntaxUID, VR.UI, UID.JPEGBaseline8Bit)
            setString(Tag.ImplementationClassUID, VR.UI, IMPLEMENTATION_CLASS_UID)
            setString(Tag.ImplementationVersionName, VR.SH, IMPLEMENTATION_VERSION)
        }

        val nowDate = DicomDateTime.todayDa()
        val nowTime = DicomDateTime.nowTm()
        val tz = DicomDateTime.timezoneOffsetFromUtc()
        val charset = DicomText.specificCharacterSet(
            context.patientId,
            context.patientName,
            context.studyDescription,
            context.seriesDescription,
        )

        val fragments = Fragments(VR.OB, false, 2).apply {
            add(ByteUtils.EMPTY_BYTES)
            add(jpegBytes)
        }

        val dataset = Attributes().apply {
            setString(Tag.SpecificCharacterSet, VR.CS, charset)
            setString(Tag.ImageType, VR.CS, "ORIGINAL", "PRIMARY")
            setString(Tag.SOPClassUID, VR.UI, UID.VLPhotographicImageStorage)
            setString(Tag.SOPInstanceUID, VR.UI, sopUid)
            setString(Tag.StudyInstanceUID, VR.UI, studyUid)
            setString(Tag.SeriesInstanceUID, VR.UI, seriesUid)
            setInt(Tag.InstanceNumber, VR.IS, 1)
            setInt(Tag.SeriesNumber, VR.IS, 1)

            setString(Tag.PatientID, VR.LO, context.patientId)
            setString(Tag.PatientName, VR.PN, context.patientName)
            // Type 2 attributes — empty if unknown
            setString(Tag.PatientBirthDate, VR.DA, DicomText.normalizeDa(context.patientBirthDate).orEmpty())
            setString(Tag.PatientSex, VR.CS, DicomText.normalizeSex(context.patientSex).orEmpty())
            setString(Tag.AccessionNumber, VR.SH, context.accessionNumber.orEmpty())
            setString(Tag.ReferringPhysicianName, VR.PN, "")
            setString(Tag.StudyID, VR.SH, "")
            context.studyDescription?.takeIf { it.isNotBlank() }?.let {
                setString(Tag.StudyDescription, VR.LO, it)
            }
            setString(
                Tag.SeriesDescription,
                VR.LO,
                context.seriesDescription?.takeIf { it.isNotBlank() } ?: "Clinical photograph",
            )
            context.bodyPartExamined?.takeIf { it.isNotBlank() }?.let {
                setString(Tag.BodyPartExamined, VR.CS, it.trim().uppercase())
            }
            context.laterality?.takeIf { it.isNotBlank() }?.let {
                setString(Tag.Laterality, VR.CS, it.trim().uppercase())
            }

            setString(Tag.Modality, VR.CS, context.modality)
            setString(Tag.StudyDate, VR.DA, nowDate)
            setString(Tag.StudyTime, VR.TM, nowTime)
            setString(Tag.SeriesDate, VR.DA, nowDate)
            setString(Tag.SeriesTime, VR.TM, nowTime)
            setString(Tag.ContentDate, VR.DA, nowDate)
            setString(Tag.ContentTime, VR.TM, nowTime)
            setString(Tag.AcquisitionDate, VR.DA, nowDate)
            setString(Tag.AcquisitionTime, VR.TM, nowTime)
            setString(Tag.InstanceCreationDate, VR.DA, nowDate)
            setString(Tag.InstanceCreationTime, VR.TM, nowTime)
            setString(Tag.TimezoneOffsetFromUTC, VR.SH, tz)

            setString(Tag.Manufacturer, VR.LO, "DICOM Camera")
            setString(Tag.ManufacturerModelName, VR.LO, "Android Phase4")

            // Acquisition Context Sequence Type 2 — empty
            setNull(Tag.AcquisitionContextSequence, VR.SQ)

            setInt(Tag.SamplesPerPixel, VR.US, 3)
            setString(Tag.PhotometricInterpretation, VR.CS, "YBR_FULL_422")
            setInt(Tag.Rows, VR.US, rows)
            setInt(Tag.Columns, VR.US, columns)
            setInt(Tag.BitsAllocated, VR.US, 8)
            setInt(Tag.BitsStored, VR.US, 8)
            setInt(Tag.HighBit, VR.US, 7)
            setInt(Tag.PixelRepresentation, VR.US, 0)
            setString(Tag.LossyImageCompression, VR.CS, "01")
            setString(Tag.LossyImageCompressionMethod, VR.CS, "ISO_10918_1")
            setValue(Tag.PixelData, VR.OB, fragments)
        }

        outputFile.parentFile?.mkdirs()
        DicomOutputStream(outputFile).use { out ->
            out.writeDataset(fmi, dataset)
        }

        return EncodedInstance(
            sopInstanceUid = sopUid,
            studyInstanceUid = studyUid,
            seriesInstanceUid = seriesUid,
            sopClassUid = UID.VLPhotographicImageStorage,
            file = outputFile,
        )
    }

    companion object {
        const val IMPLEMENTATION_CLASS_UID = "2.25.33300112233445566778899"
        const val IMPLEMENTATION_VERSION = "DICOMCAM_0_5"
    }
}

data class EncodedInstance(
    val sopInstanceUid: String,
    val studyInstanceUid: String,
    val seriesInstanceUid: String,
    val sopClassUid: String,
    val file: File,
)
