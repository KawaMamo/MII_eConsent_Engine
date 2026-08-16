package org.example.validation;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.PrePopulatedValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.SnapshotGeneratingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;


public class ValidationSupportFactory {
    private final FhirContext fhirContext;
    private final PrePopulatedValidationSupport prePopulatedSupport;

    public ValidationSupportFactory(FhirContext fhirContext, PrePopulatedValidationSupport prePopulatedSupport) {
        this.fhirContext = fhirContext;
        this.prePopulatedSupport = prePopulatedSupport;
    }

    public ValidationSupportChain createSupportChain() {
        SnapshotGeneratingValidationSupport snapshotSupport = new SnapshotGeneratingValidationSupport(fhirContext);
        InMemoryTerminologyServerValidationSupport terminologySupport = new InMemoryTerminologyServerValidationSupport(fhirContext);

        return new ValidationSupportChain(
                new DefaultProfileValidationSupport(fhirContext),
                terminologySupport,
                prePopulatedSupport,
                snapshotSupport
        );
    }

    public SnapshotGeneratingValidationSupport createSnapshotSupport() {
        return new SnapshotGeneratingValidationSupport(fhirContext);
    }
}
