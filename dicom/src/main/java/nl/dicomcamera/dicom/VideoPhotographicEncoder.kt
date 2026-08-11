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
 * Encodes an MP4/H.264 clip as DICOM Video Photographic Image Storage
 * (see ADR 0002).
 */
class VideoPhotographicEncoder(
    private val uidGenerator: () -> String = { DicomUid.newUid() },
) {
    fun encodeMp4ToFile(
        mp4Bytes: ByteArray,
        context: PatientStudyContext,
        rows: Int,
        columns: Int,
        frameCount: Int = 1,
        framesPerSecond: Int = 30,
        outputFile: File,
    ): EncodedInstance {
        require(mp4Bytes.isNotEmpty()) { "MP4 bytes required" }
        require(rows > 0 && columns > 0) { "rows/columns required" }
        require(context.patientId.isNotBlank()) { "Patient ID required" }
        require(context.patientName.isNotBlank()) { "Patient Name required" }

        val studyUid = context.studyInstanceUid?.takeIf { it.isNotBlank() } ?: uidGenerator()
        val seriesUid = context.seriesInstanceUid?.takeIf { it.isNotBlank() } ?: uidGenerator()
        val sopUid = uidGenerator()

        val fmi = Attributes(6).apply {
            setBytes(Tag.FileMetaInformationVersion, VR.OB, byteArrayOf(0, 1))
            setString(Tag.MediaStorageSOPClassUID, VR.UI, UID.VideoPhotographicImageStorage)
            setString(Tag.MediaStorageSOPInstanceUID, VR.UI, sopUid)
            setString(Tag.TransferSyntaxUID, VR.UI, UID.MPEG4HP41)
            setString(Tag.ImplementationClassUID, VR.UI, PhotographicImageEncoder.IMPLEMENTATION_CLASS_UID)
            setString(Tag.ImplementationVersionName, VR.SH, PhotographicImageEncoder.IMPLEMENTATION_VERSION)
        }

        val nowDate = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
        val nowTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"))

        val fragments = Fragments(VR.OB, false, 2).apply {
            add(ByteUtils.EMPTY_BYTES)
            add(mp4Bytes)
        }

        val dataset = Attributes().apply {
            setString(Tag.SpecificCharacterSet, VR.CS, "ISO_IR 100")
            setString(Tag.ImageType, VR.CS, "ORIGINAL", "PRIMARY")
            setString(Tag.SOPClassUID, VR.UI, UID.VideoPhotographicImageStorage)
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
            setString(Tag.ReferringPhysicianName, VR.PN, "")
            setString(Tag.StudyID, VR.SH, "")
            context.studyDescription?.takeIf { it.isNotBlank() }?.let {
                setString(Tag.StudyDescription, VR.LO, it)
            }
            setString(
                Tag.SeriesDescription,
                VR.LO,
                context.seriesDescription?.takeIf { it.isNotBlank() } ?: "Clinical video",
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

            setString(Tag.Manufacturer, VR.LO, "DICOM Camera")
            setString(Tag.ManufacturerModelName, VR.LO, "Android Phase3")
            setNull(Tag.AcquisitionContextSequence, VR.SQ)

            setInt(Tag.SamplesPerPixel, VR.US, 3)
            setString(Tag.PhotometricInterpretation, VR.CS, "YBR_PARTIAL_420")
            setInt(Tag.Rows, VR.US, rows)
            setInt(Tag.Columns, VR.US, columns)
            setInt(Tag.NumberOfFrames, VR.IS, frameCount.coerceAtLeast(1))
            setInt(Tag.CineRate, VR.IS, framesPerSecond.coerceAtLeast(1))
            setString(Tag.FrameTime, VR.DS, (1000.0 / framesPerSecond.coerceAtLeast(1)).toString())
            setInt(Tag.BitsAllocated, VR.US, 8)
            setInt(Tag.BitsStored, VR.US, 8)
            setInt(Tag.HighBit, VR.US, 7)
            setInt(Tag.PixelRepresentation, VR.US, 0)
            setString(Tag.LossyImageCompression, VR.CS, "01")
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
            sopClassUid = UID.VideoPhotographicImageStorage,
            file = outputFile,
        )
    }
}
