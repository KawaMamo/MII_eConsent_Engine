package org.example.validation;


import ca.uhn.fhir.context.support.ValidationSupportContext;
import org.hl7.fhir.common.hapi.validation.support.SnapshotGeneratingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.r4.model.*;


public class SnapshotGeneratorService {
    private final ValidationSupportChain supportChain;
    private final ValidationSupportContext validationContext;
    private final SnapshotGeneratingValidationSupport snapshotSupport;

    public SnapshotGeneratorService(ValidationSupportChain supportChain,
                                    SnapshotGeneratingValidationSupport snapshotSupport) {
        this.supportChain = supportChain;
        this.validationContext = new ValidationSupportContext(supportChain);
        this.snapshotSupport = snapshotSupport;
    }

    public StructureDefinition generateSnapshot(StructureDefinition profile, String baseDefinition) {
        try {
            System.out.println("\nGenerating snapshot for: " + profile.getUrl());
            StructureDefinition snapshot = (StructureDefinition) snapshotSupport.generateSnapshot(
                    validationContext,
                    profile,
                    profile.getUrl(),
                    null,
                    baseDefinition
            );
            System.out.println("Snapshot generated successfully");
            return snapshot;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate snapshot for: " + profile.getUrl(), e);
        }
    }
}
