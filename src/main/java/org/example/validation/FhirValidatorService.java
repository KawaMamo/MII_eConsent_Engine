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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Service for validating FHIR resources
 * FIXED: Thread-safe with double-check locking
 * FIXED: Correct enum comparison for severity using getCode()
 */
public class FhirValidatorService {

    private static final Logger logger = LoggerFactory.getLogger(FhirValidatorService.class);

    private final FhirContext fhirContext;
    private final ValidationSupportChain supportChain;
    private final ReentrantLock initLock = new ReentrantLock();
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    private FhirValidator validator;

    public FhirValidatorService(FhirContext fhirContext, ValidationSupportChain supportChain) {
        this.fhirContext = fhirContext;
        this.supportChain = supportChain;
    }

    /**
     * Initialize the validator once with full profile validation support
     * Thread-safe with double-check locking
     */
    @PostConstruct
    public void init() {
        // Fast path: already initialized
        if (initialized.get()) {
            return;
        }

        // Lock to prevent concurrent initialization
        initLock.lock();
        try {
            // Double-check: another thread might have initialized while we waited
            if (initialized.get()) {
                return;
            }

            logger.info("Initializing FhirValidator with validation support");

            try {
                // Create FhirInstanceValidator with support chain
                FhirInstanceValidator instanceValidator = new FhirInstanceValidator(fhirContext);
                instanceValidator.setValidationSupport(supportChain);

                // Configure validation settings
                instanceValidator.setErrorForUnknownProfiles(true);
                instanceValidator.setAssumeValidRestReferences(false);
                instanceValidator.setNoTerminologyChecks(false);
                instanceValidator.setNoExtensibleWarnings(false);
                instanceValidator.setNoBindingMsgSuppressed(false);
                instanceValidator.setAnyExtensionsAllowed(false);

                this.validator = fhirContext.newValidator();
                this.validator.registerValidatorModule(instanceValidator);

                // Mark as initialized (atomic operation)
                initialized.set(true);

                logger.info("FhirValidator initialized successfully");
            } catch (Exception e) {
                logger.error("Failed to initialize FhirValidator", e);
                throw new RuntimeException("Failed to initialize validator", e);
            }
        } finally {
            initLock.unlock();
        }
    }

    /**
     * Validate a resource using the reusable validator
     * Thread-safe with double-check locking
     */
    public ValidationResult validate(Resource resource) {
        // Lazy initialization with double-check locking
        if (!initialized.get()) {
            init();
        }

        // Guard against null if initialization failed
        if (validator == null) {
            throw new IllegalStateException("Validator not initialized properly");
        }

        long startTime = System.currentTimeMillis();

        try {
            ValidationResult result = validator.validateWithResult(resource);

            long duration = System.currentTimeMillis() - startTime;

            if (result.isSuccessful()) {
                logger.debug("Validation completed successfully in {}ms", duration);
            } else {
                // FIXED: Use getCode() for severity comparison
                long errors = result.getMessages().stream()
                        .filter(m -> m.getSeverity() != null &&
                                "ERROR".equals(m.getSeverity().getCode()))
                        .count();
                long warnings = result.getMessages().stream()
                        .filter(m -> m.getSeverity() != null &&
                                "WARNING".equals(m.getSeverity().getCode()))
                        .count();
                long infos = result.getMessages().stream()
                        .filter(m -> m.getSeverity() != null &&
                                "INFO".equals(m.getSeverity().getCode()))
                        .count();
                logger.warn("Validation completed in {}ms with {} errors, {} warnings, {} infos",
                        duration, errors, warnings, infos);
            }

            return result;
        } catch (Exception e) {
            logger.error("Validation failed with exception", e);
            throw new RuntimeException("Validation failed", e);
        }
    }

    /**
     * Print validation results using SLF4J
     * FIXED: Correct enum comparison using getCode()
     */
    public void printValidationResults(ValidationResult result) {
        logger.info("=== Validation Result ===");
        logger.info("Is Valid: {}", result.isSuccessful());
        logger.info("Total Messages: {}", result.getMessages().size());

        // Print to console for visibility
        System.out.println("\n=== Validation Result ===");
        System.out.println("Is Valid: " + result.isSuccessful());
        System.out.println("Total Messages: " + result.getMessages().size());

        // FIXED: Use getCode() for severity comparison
        for (SingleValidationMessage msg : result.getMessages()) {
            String severity;
            if (msg.getSeverity() == null) {
                severity = "UNKNOWN";
            } else {
                severity = msg.getSeverity().toString();
            }
            String location = msg.getLocationString() != null ? msg.getLocationString() : "";
            String message = msg.getMessage() != null ? msg.getMessage() : "";

            // FIXED: Direct enum comparison using getCode()
            if (msg.getSeverity() != null) {
                String severityCode = msg.getSeverity().getCode();
                if ("ERROR".equals(severityCode)) {
                    logger.error("[{}] {} - {}", severity, location, message);
                    System.out.println("[" + severity + "] " + location + " - " + message);
                } else if ("WARNING".equals(severityCode)) {
                    logger.warn("[{}] {} - {}", severity, location, message);
                    System.out.println("[" + severity + "] " + location + " - " + message);
                } else {
                    logger.info("[{}] {} - {}", severity, location, message);
                    System.out.println("[" + severity + "] " + location + " - " + message);
                }
            } else {
                logger.info("[{}] {} - {}", severity, location, message);
                System.out.println("[" + severity + "] " + location + " - " + message);
            }
        }
    }

    public boolean isInitialized() {
        return initialized.get();
    }
}