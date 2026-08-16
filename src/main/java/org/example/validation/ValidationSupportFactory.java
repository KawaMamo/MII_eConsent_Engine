package org.example.validation;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.PrePopulatedValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.SnapshotGeneratingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;

/**
 * Factory for creating validation support chains
 * FIXED:
 * - prePopulatedSupport placed FIRST for custom profile precedence
 * - Single SnapshotGeneratingValidationSupport instance shared
 * - Maintains backward compatibility with existing tests
 */
public class ValidationSupportFactory {

    private static final Logger logger = LoggerFactory.getLogger(ValidationSupportFactory.class);

    private final FhirContext fhirContext;
    private final PrePopulatedValidationSupport prePopulatedSupport;

    // FIXED: Single instance, reused
    private SnapshotGeneratingValidationSupport snapshotSupport;
    private InMemoryTerminologyServerValidationSupport terminologySupport;
    private DefaultProfileValidationSupport defaultSupport;
    private ValidationSupportChain supportChain;

    public ValidationSupportFactory(FhirContext fhirContext, PrePopulatedValidationSupport prePopulatedSupport) {
        this.fhirContext = fhirContext;
        this.prePopulatedSupport = prePopulatedSupport;
    }

    /**
     * Initialize all support components once
     */
    @PostConstruct
    public void init() {
        logger.info("Initializing ValidationSupportFactory");

        // Create single instances
        this.snapshotSupport = new SnapshotGeneratingValidationSupport(fhirContext);
        this.terminologySupport = new InMemoryTerminologyServerValidationSupport(fhirContext);
        this.defaultSupport = new DefaultProfileValidationSupport(fhirContext);

        // FIXED: prePopulatedSupport FIRST for custom profile precedence
        this.supportChain = new ValidationSupportChain(
                prePopulatedSupport,        // CUSTOM profiles FIRST (highest priority)
                terminologySupport,         // Terminology validation
                defaultSupport,             // Base FHIR definitions (fallback)
                snapshotSupport             // Snapshot generation
        );

        logger.info("ValidationSupportFactory initialized with custom profiles first");
    }

    /**
     * Create support chain - maintained for backward compatibility
     * Delegates to the initialized chain
     */
    public ValidationSupportChain createSupportChain() {
        if (supportChain == null) {
            logger.warn("Support chain not initialized, initializing now");
            init();
        }
        return supportChain;
    }

    /**
     * Create snapshot support - maintained for backward compatibility
     * Returns the single shared instance
     */
    public SnapshotGeneratingValidationSupport createSnapshotSupport() {
        if (snapshotSupport == null) {
            init();
        }
        return snapshotSupport;
    }

    /**
     * Get the support chain (initialized once)
     */
    public ValidationSupportChain getSupportChain() {
        if (supportChain == null) {
            logger.warn("Support chain not initialized, initializing now");
            init();
        }
        return supportChain;
    }

    /**
     * Get the snapshot support (single instance)
     */
    public SnapshotGeneratingValidationSupport getSnapshotSupport() {
        if (snapshotSupport == null) {
            init();
        }
        return snapshotSupport;
    }

    /**
     * Rebuild the support chain if needed (e.g., after adding new resources)
     */
    public void rebuildSupportChain() {
        logger.info("Rebuilding support chain");
        init();
    }
}