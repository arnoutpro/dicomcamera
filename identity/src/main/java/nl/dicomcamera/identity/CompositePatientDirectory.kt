package nl.dicomcamera.identity

/**
 * Site-selectable composite: HL7 façade and/or FHIR Patient lookup.
 * Skips adapters that are not provided (not configured).
 */
class CompositePatientDirectory(
    private val modeProvider: () -> IdentityLookupMode,
    private val hl7: PatientDirectory? = null,
    private val fhir: PatientDirectory? = null,
) : PatientDirectory {
    override val source: IdentitySource
        get() = when (modeProvider()) {
            IdentityLookupMode.FHIR_ONLY, IdentityLookupMode.FHIR_THEN_HL7 ->
                if (fhir != null) IdentitySource.FHIR else IdentitySource.HL7_V2
            IdentityLookupMode.HL7_ONLY, IdentityLookupMode.HL7_THEN_FHIR ->
                if (hl7 != null) IdentitySource.HL7_V2 else IdentitySource.FHIR
        }

    override suspend fun findPatients(query: PatientQuery): List<PatientDemographics> {
        val ordered = orderedDirectories()
        require(ordered.isNotEmpty()) {
            "No identity adapter configured for mode ${modeProvider()}"
        }
        var lastError: Exception? = null
        for ((index, directory) in ordered.withIndex()) {
            try {
                val hits = directory.findPatients(query)
                if (hits.isNotEmpty() || index == ordered.lastIndex) return hits
            } catch (e: Exception) {
                lastError = e
                if (index == ordered.lastIndex) {
                    throw lastError
                }
            }
        }
        throw lastError ?: IllegalStateException("Identity lookup failed")
    }

    private fun orderedDirectories(): List<PatientDirectory> {
        val mode = modeProvider()
        val fhirDir = fhir
        val hl7Dir = hl7
        return when (mode) {
            IdentityLookupMode.FHIR_ONLY -> listOfNotNull(fhirDir)
            IdentityLookupMode.HL7_ONLY -> listOfNotNull(hl7Dir)
            IdentityLookupMode.FHIR_THEN_HL7 -> listOfNotNull(fhirDir, hl7Dir)
            IdentityLookupMode.HL7_THEN_FHIR -> listOfNotNull(hl7Dir, fhirDir)
        }
    }
}
