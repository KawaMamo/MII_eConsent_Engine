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
 * FIXED: Accepts Resource directly for better compatibility
 */
public class FhirValidatorService {

    private static final Logger logger = LoggerFactory.getLogger(FhirValidatorService.class);

    private final FhirContext fhirContext;
    private final ValidationSupportChain supportChain;
    private FhirValidator validator;  // Reusable instance
    private boolean initialized = false;

    public FhirValidatorService(FhirContext fhirContext, ValidationSupportChain supportChain) {
        this.fhirContext = fhirContext;
        this.supportChain = supportChain;
    }

    /**
     * Initialize the validator once - called after construction or when support chain is ready
     */
    @PostConstruct
    public void init() {
        if (initialized) {
            return;
        }

        logger.info("Initializing FhirValidator with support chain");

        try {
            // Create validator once and reuse it
            FhirInstanceValidator instanceValidator = new FhirInstanceValidator(fhirContext);
            instanceValidator.setValidationSupport(supportChain);

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
     * FIXED: Accepts Resource (which extends IBaseResource) directly
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
            logger.debug("Validation completed in {}ms, result: {}", duration, result.isSuccessful());

            return result;
        } catch (Exception e) {
            logger.error("Validation failed with exception", e);
            throw new RuntimeException("Validation failed", e);
        }
    }

    /**
     * Print validation results using SLF4J instead of System.out
     * Also outputs to console for visibility
     */
    public void printValidationResults(ValidationResult result) {
        // Log to SLF4J
        logger.info("=== Validation Result ===");
        logger.info("Is Valid: {}", result.isSuccessful());
        logger.info("Total Messages: {}", result.getMessages().size());

        // Also print to console for immediate visibility
        System.out.println("\n=== Validation Result ===");
        System.out.println("Is Valid: " + result.isSuccessful());
        System.out.println("Total Messages: " + result.getMessages().size());

        for (SingleValidationMessage msg : result.getMessages()) {
            String severity = msg.getSeverity() != null ? msg.getSeverity().toString() : "UNKNOWN";
            String location = msg.getLocationString() != null ? msg.getLocationString() : "";
            String message = msg.getMessage() != null ? msg.getMessage() : "";

            // Log to SLF4J with appropriate level
            if (severity.contains("ERROR")) {
                logger.error("[{}] {} - {}", severity, location, message);
            } else if (severity.contains("WARNING")) {
                logger.warn("[{}] {} - {}", severity, location, message);
            } else {
                logger.info("[{}] {} - {}", severity, location, message);
            }

            // Print to console
            System.out.println("[" + severity + "] " + location + " - " + message);
        }
    }

    /**
     * Check if validator is initialized
     */
    public boolean isInitialized() {
        return initialized;
    }
}