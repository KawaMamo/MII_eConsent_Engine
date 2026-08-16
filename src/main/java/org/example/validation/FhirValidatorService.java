package org.example.validation;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;

/**
 * Service for validating FHIR resources
 * FIXED: Reusable validator instance - initialized once, reused many times
 * FIXED: Uses only available FhirInstanceValidator methods
 */
public class FhirValidatorService {

    private static final Logger logger = LoggerFactory.getLogger(FhirValidatorService.class);

    private final FhirContext fhirContext;
    private final ValidationSupportChain supportChain;
    private FhirValidator validator;
    private boolean initialized = false;

    public FhirValidatorService(FhirContext fhirContext, ValidationSupportChain supportChain) {
        this.fhirContext = fhirContext;
        this.supportChain = supportChain;
    }

    /**
     * Initialize the validator once with full profile validation support
     */
    @PostConstruct
    public void init() {
        if (initialized) {
            return;
        }

        logger.info("Initializing FhirValidator with validation support");

        try {
            // Create FhirInstanceValidator with support chain
            FhirInstanceValidator instanceValidator = new FhirInstanceValidator(fhirContext);
            instanceValidator.setValidationSupport(supportChain);

            // FIXED: Use ONLY methods that exist in the decompiled class

            // Error on unknown profiles - catches undefined profiles
            instanceValidator.setErrorForUnknownProfiles(true);

            // Assume rest references are valid (default is false)
            instanceValidator.setAssumeValidRestReferences(false);

            // No terminology checks (false = perform terminology checks)
            instanceValidator.setNoTerminologyChecks(false);

            // No extensible warnings (false = show extensible warnings)
            instanceValidator.setNoExtensibleWarnings(false);

            // No binding message suppressed (false = show binding messages)
            instanceValidator.setNoBindingMsgSuppressed(false);

            // Allow any extensions (false = restrict extensions)
            instanceValidator.setAnyExtensionsAllowed(false);

            // Set best practice warning level
            // instanceValidator.setBestPracticeWarningLevel(BestPracticeWarningLevel.HINT);
            // Note: BestPracticeWarningLevel enum is from org.hl7.fhir.r5.utils.validation.constants

            logger.info("FhirInstanceValidator configured with: " +
                    "errorForUnknownProfiles=true, assumeValidRestReferences=false, " +
                    "noTerminologyChecks=false, noExtensibleWarnings=false, " +
                    "noBindingMsgSuppressed=false, anyExtensionsAllowed=false");

            this.validator = fhirContext.newValidator();
            this.validator.registerValidatorModule(instanceValidator);

            initialized = true;
            logger.info("FhirValidator initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize FhirValidator", e);
            throw new RuntimeException("Failed to initialize validator", e);
        }
    }

    /**
     * Validate a resource using the reusable validator
     * Performs full profile validation against the MII profile
     */
    public ValidationResult validate(Resource resource) {
        if (!initialized || validator == null) {
            logger.warn("Validator not initialized, initializing now");
            init();
        }

        long startTime = System.currentTimeMillis();

        try {
            ValidationResult result = validator.validateWithResult(resource);

            long duration = System.currentTimeMillis() - startTime;

            if (result.isSuccessful()) {
                logger.debug("Validation completed successfully in {}ms", duration);
            } else {
                long errors = result.getMessages().stream()
                        .filter(m -> m.getSeverity() != null &&
                                m.getSeverity().toString().contains("ERROR"))
                        .count();
                long warnings = result.getMessages().stream()
                        .filter(m -> m.getSeverity() != null &&
                                m.getSeverity().toString().contains("WARNING"))
                        .count();
                logger.warn("Validation completed in {}ms with {} errors and {} warnings",
                        duration, errors, warnings);
            }

            return result;
        } catch (Exception e) {
            logger.error("Validation failed with exception", e);
            throw new RuntimeException("Validation failed", e);
        }
    }

    /**
     * Print validation results using SLF4J
     */
    public void printValidationResults(ValidationResult result) {
        logger.info("=== Validation Result ===");
        logger.info("Is Valid: {}", result.isSuccessful());
        logger.info("Total Messages: {}", result.getMessages().size());

        // Print to console for visibility
        System.out.println("\n=== Validation Result ===");
        System.out.println("Is Valid: " + result.isSuccessful());
        System.out.println("Total Messages: " + result.getMessages().size());

        for (SingleValidationMessage msg : result.getMessages()) {
            String severity = msg.getSeverity() != null ? msg.getSeverity().toString() : "UNKNOWN";
            String location = msg.getLocationString() != null ? msg.getLocationString() : "";
            String message = msg.getMessage() != null ? msg.getMessage() : "";

            if (severity.contains("ERROR")) {
                logger.error("[{}] {} - {}", severity, location, message);
            } else if (severity.contains("WARNING")) {
                logger.warn("[{}] {} - {}", severity, location, message);
            } else {
                logger.info("[{}] {} - {}", severity, location, message);
            }

            System.out.println("[" + severity + "] " + location + " - " + message);
        }
    }

    public boolean isInitialized() {
        return initialized;
    }
}