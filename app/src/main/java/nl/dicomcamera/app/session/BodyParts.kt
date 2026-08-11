package nl.dicomcamera.app.session

/** Common body parts for clinical photography (DICOM Body Part Examined). */
object BodyParts {
    val options: List<Pair<String, String>> = listOf(
        "HAND" to "Hand",
        "FOOT" to "Foot",
        "FACE" to "Face",
        "NECK" to "Neck",
        "ARM" to "Arm",
        "LEG" to "Leg",
        "CHEST" to "Chest",
        "ABDOMEN" to "Abdomen",
        "BACK" to "Back",
        "SKIN" to "Skin",
        "WOUND" to "Wound",
        "OTHER" to "Other",
    )
}
