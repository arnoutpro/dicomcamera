package nl.dicomcamera.dicom

import org.dcm4che3.data.Attributes
import org.dcm4che3.data.Fragments
import org.dcm4che3.data.Tag
import org.dcm4che3.data.UID
import org.dcm4che3.data.VR
import org.dcm4che3.io.DicomOutputStream
import org.dcm4che3.util.ByteUtils
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Legacy Secondary Capture encoder (Phase 0). Prefer [PhotographicImageEncoder] for new captures.
 */
class SecondaryCaptureEncoder(
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

        val studyUid = context.studyInstanceUid?.takeIf { it.isNotBlank() } ?: uidGenerator()
        val seriesUid = context.seriesInstanceUid?.takeIf { it.isNotBlank() } ?: uidGenerator()
        val sopUid = uidGenerator()

        val fmi = Attributes(6).apply {
            setBytes(Tag.FileMetaInformationVersion, VR.OB, byteArrayOf(0, 1))
            setString(Tag.MediaStorageSOPClassUID, VR.UI, UID.SecondaryCaptureImageStorage)
            setString(Tag.MediaStorageSOPInstanceUID, VR.UI, sopUid)
            setString(Tag.TransferSyntaxUID, VR.UI, UID.JPEGBaseline8Bit)
            setString(Tag.ImplementationClassUID, VR.UI, PhotographicImageEncoder.IMPLEMENTATION_CLASS_UID)
            setString(Tag.ImplementationVersionName, VR.SH, PhotographicImageEncoder.IMPLEMENTATION_VERSION)
        }

        val nowDate = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
        val nowTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"))

        val fragments = Fragments(VR.OB, false, 2).apply {
            add(ByteUtils.EMPTY_BYTES)
            add(jpegBytes)
        }

        val dataset = Attributes().apply {
            setString(Tag.SOPClassUID, VR.UI, UID.SecondaryCaptureImageStorage)
            setString(Tag.SOPInstanceUID, VR.UI, sopUid)
            setString(Tag.StudyInstanceUID, VR.UI, studyUid)
            setString(Tag.SeriesInstanceUID, VR.UI, seriesUid)
            setInt(Tag.InstanceNumber, VR.IS, 1)
            setInt(Tag.SeriesNumber, VR.IS, 1)

            setString(Tag.PatientID, VR.LO, context.patientId)
            setString(Tag.PatientName, VR.PN, context.patientName)
            setString(Tag.PatientBirthDate, VR.DA, context.patientBirthDate.orEmpty())
            setString(Tag.PatientSex, VR.CS, context.patientSex.orEmpty())
            setString(Tag.AccessionNumber, VR.SH, context.accessionNumber.orEmpty())
            context.studyDescription?.let { setString(Tag.StudyDescription, VR.LO, it) }
            context.seriesDescription?.let { setString(Tag.SeriesDescription, VR.LO, it) }

            setString(Tag.Modality, VR.CS, context.modality)
            setString(Tag.ConversionType, VR.CS, "DI")
            setString(Tag.StudyDate, VR.DA, nowDate)
            setString(Tag.StudyTime, VR.TM, nowTime)
            setString(Tag.SeriesDate, VR.DA, nowDate)
            setString(Tag.SeriesTime, VR.TM, nowTime)
            setString(Tag.ContentDate, VR.DA, nowDate)
            setString(Tag.ContentTime, VR.TM, nowTime)
            setString(Tag.InstanceCreationDate, VR.DA, nowDate)
            setString(Tag.InstanceCreationTime, VR.TM, nowTime)

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
            sopClassUid = UID.SecondaryCaptureImageStorage,
            file = outputFile,
        )
    }
}
