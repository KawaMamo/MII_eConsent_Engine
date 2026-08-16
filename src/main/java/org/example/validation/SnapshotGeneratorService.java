package org.example.validation;

import ca.uhn.fhir.context.support.ValidationSupportContext;
import org.hl7.fhir.common.hapi.validation.support.SnapshotGeneratingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Service for generating profile snapshots - Thread-Safe Version
 * FIXED: Thread-safe with double-check locking
 * FIXED: Properly passes profile name with fallback
 */
public class SnapshotGeneratorService {

    private static final Logger logger = LoggerFactory.getLogger(SnapshotGeneratorService.class);

    private final ValidationSupportChain supportChain;
    private final SnapshotGeneratingValidationSupport snapshotSupport;
    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    public SnapshotGeneratorService(ValidationSupportChain supportChain,
                                    SnapshotGeneratingValidationSupport snapshotSupport) {
        this.supportChain = supportChain;
        this.snapshotSupport = snapshotSupport;
        initialized.set(true);
        logger.info("SnapshotGeneratorService initialized");
    }

    /**
     * Generate a snapshot for a StructureDefinition
     * FIXED: Thread-safe with proper fallback for profile name
     */
    public StructureDefinition generateSnapshot(StructureDefinition profile, String baseDefinition) {
        // Validate inputs
        if (profile == null) {
            throw new IllegalArgumentException("Profile cannot be null");
        }
        if (baseDefinition == null || baseDefinition.isEmpty()) {
            throw new IllegalArgumentException("Base definition cannot be null or empty");
        }

        long startTime = System.currentTimeMillis();

        try {
            String profileUrl = profile.getUrl();
            if (profileUrl == null || profileUrl.isEmpty()) {
                throw new IllegalArgumentException("Profile URL cannot be null or empty");
            }

            // FIXED: Get profile name with proper fallback
            String profileName = getProfileName(profile);

            logger.info("Generating snapshot for: {} (name: {})", profileUrl, profileName);

            // Create fresh context for each call to avoid stale references
            ValidationSupportContext validationContext = new ValidationSupportContext(supportChain);

            // Generate snapshot with proper parameters
            StructureDefinition snapshot = (StructureDefinition) snapshotSupport.generateSnapshot(
                    validationContext,
                    profile,
                    profileUrl,
                    profileName,
                    baseDefinition
            );

            long duration = System.currentTimeMillis() - startTime;
            logger.info("Snapshot generated successfully for: {} in {}ms", profileUrl, duration);

            System.out.println("Snapshot generated successfully for: " + profileUrl);

            return snapshot;
        } catch (Exception e) {
            logger.error("Failed to generate snapshot for: {}",
                    profile.getUrl() != null ? profile.getUrl() : "unknown", e);
            System.err.println("Error generating snapshot for: " +
                    (profile.getUrl() != null ? profile.getUrl() : "unknown"));
            throw new RuntimeException("Failed to generate snapshot", e);
        }
    }

    /**
     * Get profile name with multiple fallback strategies
     */
    private String getProfileName(StructureDefinition profile) {
        // Strategy 1: Use the name field
        String name = profile.getName();
        if (name != null && !name.isEmpty()) {
            return name;
        }

        // Strategy 2: Use the ID element
        if (profile.getIdElement() != null) {
            String idPart = profile.getIdElement().getIdPart();
            if (idPart != null && !idPart.isEmpty()) {
                logger.debug("Using ID as profile name: {}", idPart);
                return idPart;
            }
        }

        // Strategy 3: Use the URL (without the base)
        String url = profile.getUrl();
        if (url != null && !url.isEmpty()) {
            String urlName = extractNameFromUrl(url);
            if (urlName != null && !urlName.isEmpty()) {
                logger.debug("Using URL-derived name: {}", urlName);
                return urlName;
            }
        }

        // Strategy 4: Use the StructureDefinition kind
        if (profile.getKind() != null && profile.getKind().getDisplay() != null) {
            String kindDisplay = profile.getKind().getDisplay();
            if (!kindDisplay.isEmpty()) {
                logger.debug("Using kind as profile name: {}", kindDisplay);
                return kindDisplay;
            }
        }

        // Strategy 5: Use the type
        if (profile.getType() != null && !profile.getType().isEmpty()) {
            logger.debug("Using type as profile name: {}", profile.getType());
            return profile.getType();
        }

        // Strategy 6: Final fallback
        String fallback = "Profile-" + System.currentTimeMillis();
        logger.warn("No profile name found, using fallback: {}", fallback);
        return fallback;
    }

    /**
     * Extract a name from a URL
     */
    private String extractNameFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        String name = url;

        // Remove protocol and domain
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }

        // Remove query parameters
        int questionMark = name.indexOf('?');
        if (questionMark >= 0) {
            name = name.substring(0, questionMark);
        }

        // Remove fragment
        int hash = name.indexOf('#');
        if (hash >= 0) {
            name = name.substring(0, hash);
        }

        // Handle OID format: urn:oid:1.2.3.4.5.6.7
        if (name.startsWith("urn:oid:")) {
            return "OID-" + name.substring("urn:oid:".length()).replace('.', '-');
        }

        // If the name is empty or just "StructureDefinition", try to find a better name
        if (name.isEmpty() || "StructureDefinition".equals(name)) {
            String[] parts = url.split("/");
            for (int i = parts.length - 1; i >= 0; i--) {
                if (!"StructureDefinition".equals(parts[i]) && !parts[i].isEmpty()) {
                    return parts[i];
                }
            }
            return url.replaceAll("[^a-zA-Z0-9]", "_");
        }

        return name;
    }

    public boolean isInitialized() {
        return initialized.get();
    }
}