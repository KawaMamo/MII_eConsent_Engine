package org.example.validation;

import ca.uhn.fhir.context.support.ValidationSupportContext;
import org.hl7.fhir.common.hapi.validation.support.SnapshotGeneratingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for generating profile snapshots
 * FIXED: No stale context - creates fresh context each time
 */
public class SnapshotGeneratorService {

    private static final Logger logger = LoggerFactory.getLogger(SnapshotGeneratorService.class);

    private final ValidationSupportChain supportChain;
    private final SnapshotGeneratingValidationSupport snapshotSupport;

    public SnapshotGeneratorService(ValidationSupportChain supportChain,
                                    SnapshotGeneratingValidationSupport snapshotSupport) {
        this.supportChain = supportChain;
        this.snapshotSupport = snapshotSupport;
        logger.info("SnapshotGeneratorService initialized");
    }

    /**
     * Generate a snapshot for a StructureDefinition
     * FIXED: Creates fresh ValidationSupportContext each time
     */
    public StructureDefinition generateSnapshot(StructureDefinition profile, String baseDefinition) {
        long startTime = System.currentTimeMillis();

        try {
            logger.info("Generating snapshot for: {}", profile.getUrl());

            // FIXED: Create fresh context for each call to avoid stale references
            ValidationSupportContext validationContext = new ValidationSupportContext(supportChain);

            StructureDefinition snapshot = (StructureDefinition) snapshotSupport.generateSnapshot(
                    validationContext,
                    profile,
                    profile.getUrl(),
                    null,
                    baseDefinition
            );

            long duration = System.currentTimeMillis() - startTime;
            logger.info("Snapshot generated successfully for: {} in {}ms", profile.getUrl(), duration);

            // Also print to console for visibility
            System.out.println("Snapshot generated successfully for: " + profile.getUrl());

            return snapshot;
        } catch (Exception e) {
            logger.error("Failed to generate snapshot for: {}", profile.getUrl(), e);
            System.err.println("Error generating snapshot for: " + profile.getUrl());
            throw new RuntimeException("Failed to generate snapshot for: " + profile.getUrl(), e);
        }
    }
}