package org.example.validation;

import ca.uhn.fhir.context.support.ValidationSupportContext;
import org.hl7.fhir.common.hapi.validation.support.SnapshotGeneratingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for generating profile snapshots
 * FIXED: Properly passes profile name and URL
 * FIXED: Creates fresh context each time to avoid stale references
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
     * FIXED: Properly passes profile name and URL
     * Creates fresh ValidationSupportContext each time
     */
    public StructureDefinition generateSnapshot(StructureDefinition profile, String baseDefinition) {
        long startTime = System.currentTimeMillis();

        try {
            String profileUrl = profile.getUrl();
            String profileName = profile.getName();

            logger.info("Generating snapshot for: {} (name: {})", profileUrl, profileName);

            // FIXED: Create fresh context for each call to avoid stale references
            ValidationSupportContext validationContext = new ValidationSupportContext(supportChain);

            // FIXED: Pass the profile name instead of null
            // The generateSnapshot method signature:
            // generateSnapshot(ValidationSupportContext context, StructureDefinition input,
            //                  String theUrl, String theProfileName, String baseDefinition)
            StructureDefinition snapshot = (StructureDefinition) snapshotSupport.generateSnapshot(
                    validationContext,
                    profile,
                    profileUrl,      // theUrl - use profile URL
                    profileName,     // theProfileName - use profile name (was null)
                    baseDefinition
            );

            long duration = System.currentTimeMillis() - startTime;
            logger.info("Snapshot generated successfully for: {} in {}ms", profileUrl, duration);

            System.out.println("Snapshot generated successfully for: " + profileUrl);

            return snapshot;
        } catch (Exception e) {
            logger.error("Failed to generate snapshot for: {}", profile.getUrl(), e);
            System.err.println("Error generating snapshot for: " + profile.getUrl());
            throw new RuntimeException("Failed to generate snapshot for: " + profile.getUrl(), e);
        }
    }
}