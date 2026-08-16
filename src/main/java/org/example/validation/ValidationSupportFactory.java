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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Factory for creating validation support chains
 * FIXED: Thread-safe with read-write locks
 * FIXED: prePopulatedSupport placed FIRST for custom profile precedence
 * FIXED: Single SnapshotGeneratingValidationSupport instance shared
 */
public class ValidationSupportFactory {

    private static final Logger logger = LoggerFactory.getLogger(ValidationSupportFactory.class);

    private final FhirContext fhirContext;
    private final PrePopulatedValidationSupport prePopulatedSupport;

    // ReentrantReadWriteLock for thread-safe reads and writes
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    // Single instances - protected by the lock
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
     * Thread-safe with double-check locking
     */
    @PostConstruct
    public void init() {
        if (initialized.get()) {
            return;
        }

        rwLock.writeLock().lock();
        try {
            // Double-check
            if (initialized.get()) {
                return;
            }

            logger.info("Initializing ValidationSupportFactory");

            // Create single instances
            this.snapshotSupport = new SnapshotGeneratingValidationSupport(fhirContext);
            this.terminologySupport = new InMemoryTerminologyServerValidationSupport(fhirContext);
            this.defaultSupport = new DefaultProfileValidationSupport(fhirContext);

            // prePopulatedSupport FIRST for custom profile precedence
            this.supportChain = new ValidationSupportChain(
                    prePopulatedSupport,        // CUSTOM profiles FIRST
                    terminologySupport,         // Terminology validation
                    defaultSupport,             // Base FHIR definitions
                    snapshotSupport             // Snapshot generation
            );

            initialized.set(true);
            logger.info("ValidationSupportFactory initialized successfully");
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Get the support chain
     * Thread-safe: read lock ensures consistent state
     */
    public ValidationSupportChain createSupportChain() {
        if (!initialized.get()) {
            init();
        }

        rwLock.readLock().lock();
        try {
            return supportChain;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Get the snapshot support
     * Thread-safe: read lock ensures consistent state
     */
    public SnapshotGeneratingValidationSupport createSnapshotSupport() {
        if (!initialized.get()) {
            init();
        }

        rwLock.readLock().lock();
        try {
            return snapshotSupport;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Get the support chain (same as createSupportChain)
     */
    public ValidationSupportChain getSupportChain() {
        return createSupportChain();
    }

    /**
     * Get the snapshot support (same as createSnapshotSupport)
     */
    public SnapshotGeneratingValidationSupport getSnapshotSupport() {
        return createSnapshotSupport();
    }

    /**
     * Rebuild the support chain
     * FIXED: Thread-safe with write lock
     */
    public void rebuildSupportChain() {
        rwLock.writeLock().lock();
        try {
            logger.info("Rebuilding support chain");

            // Reset initialized flag
            initialized.set(false);

            // Re-initialize
            init();

            logger.info("Support chain rebuilt successfully");
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public boolean isInitialized() {
        return initialized.get();
    }
}