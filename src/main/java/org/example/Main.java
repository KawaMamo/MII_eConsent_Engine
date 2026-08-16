package org.example;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.validation.ValidationResult;
import org.example.consent.loader.ConsentTemplateLoader;
import org.example.consent.model.ExchangeFormatDefinition;
import org.example.consent.populator.*;
import org.example.tools.FhirResourceLoader;
import org.example.tools.JsonSerializationService;
import org.example.validation.FhirValidatorService;
import org.example.validation.SnapshotGeneratorService;
import org.example.validation.ValidationSupportFactory;
import org.hl7.fhir.common.hapi.validation.support.SnapshotGeneratingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private static final String RESOURCES_PATH = "src/main/resources/";

    private static final class Constants {
        static final String GERMAN_CONSENT_PROFILE = RESOURCES_PATH + "ConsentManagementConsent.json";
        static final String MII_CONSENT_PROFILE = RESOURCES_PATH + "MII_PR_Consent_Einwilligung.json";
        static final String MII_ANSWER_CODESYSTEM = RESOURCES_PATH + "CodeSystem-MiiConsentAnswerCodeSystem.json";
        static final String MII_POLICY_CODESYSTEM = RESOURCES_PATH + "CodeSystem-MiiConsentPolicyCodeSystem.json";
        static final String MII_VERSION_CODESYSTEM = RESOURCES_PATH + "CodeSystem-MiiConsentVersionModuleCodeSystem.json";
        static final String MII_POLICY_VALUESET = RESOURCES_PATH + "ValueSet-MiiConsentPolicyValueSet.json";
        static final String GICS_CONSENT_TEMPLATE = RESOURCES_PATH + "2025-01-21 MII BC 1.7.2 mit Erweiterungsmodul PROM.json";

        static final String FHIR_BASE_CONSENT = "http://hl7.org/fhir/StructureDefinition/Consent";
    }

    public static void main(String[] args) {
        FhirContext fhirContext = FhirContext.forR4();
        IParser jsonParser = fhirContext.newJsonParser();

        try {
            System.out.println("=== FHIR Consent Management System ===\n");
            System.out.println("--- ADMIN: Loading Resources ---");
            logger.info("=== FHIR Consent Management System ===\n");
            logger.info("--- ADMIN: Loading Resources ---");

            // ==========================================
            // ADMIN: Load Resources
            // ==========================================
            FhirResourceLoader resourceLoader = new FhirResourceLoader(fhirContext, jsonParser);

            StructureDefinition germanConsentBase = resourceLoader.loadStructureDefinition(Constants.GERMAN_CONSENT_PROFILE);
            StructureDefinition miiConsentProfile = resourceLoader.loadStructureDefinition(Constants.MII_CONSENT_PROFILE);

            resourceLoader.loadCodeSystem(Constants.MII_ANSWER_CODESYSTEM);
            resourceLoader.loadCodeSystem(Constants.MII_POLICY_CODESYSTEM);
            resourceLoader.loadCodeSystem(Constants.MII_VERSION_CODESYSTEM);
            resourceLoader.loadValueSet(Constants.MII_POLICY_VALUESET);

            // Load consent template
            ConsentTemplateLoader templateLoader = new ConsentTemplateLoader();
            ExchangeFormatDefinition template = templateLoader.loadFromFile(Constants.GICS_CONSENT_TEMPLATE);

            String templateKey = extractTemplateKey(template);
            System.out.println("Active Template: " + templateKey);
            logger.info("Active Template: {}", templateKey);

            // ==========================================
            // ADMIN: Setup Validation (Optimized)
            // ==========================================
            System.out.println("\n--- ADMIN: Setting up validation ---");
            logger.info("\n--- ADMIN: Setting up validation ---");

            // Create factory - it initializes all components once
            ValidationSupportFactory supportFactory = new ValidationSupportFactory(
                    fhirContext,
                    resourceLoader.getPrePopulatedSupport()
            );

            // Initialize the factory
            supportFactory.init();

            // Get the shared instances
            ValidationSupportChain supportChain = supportFactory.getSupportChain();
            SnapshotGeneratingValidationSupport snapshotSupport = supportFactory.getSnapshotSupport();

            // Generate snapshots using optimized service
            SnapshotGeneratorService snapshotService = new SnapshotGeneratorService(
                    supportChain,
                    snapshotSupport
            );

            StructureDefinition germanSnapshot = snapshotService.generateSnapshot(
                    germanConsentBase,
                    Constants.FHIR_BASE_CONSENT
            );
            resourceLoader.getPrePopulatedSupport().addStructureDefinition(germanSnapshot);

            StructureDefinition miiSnapshot = snapshotService.generateSnapshot(
                    miiConsentProfile,
                    miiConsentProfile.getBaseDefinition()
            );
            resourceLoader.getPrePopulatedSupport().addStructureDefinition(miiSnapshot);

            System.out.println("Profile snapshots generated successfully");
            logger.info("Profile snapshots generated successfully");

            // ==========================================
            // USER: Show Modules
            // ==========================================
            System.out.println("\n--- USER: Available Modules ---");
            logger.info("\n--- USER: Available Modules ---");

            ConsentPopulator populator = new ConsentPopulator(template, miiSnapshot);
            List<ModuleInfo> modules = populator.getModulesForTemplate(templateKey);

            System.out.println("Total modules available: " + modules.size());
            logger.info("Total modules available: {}", modules.size());
            for (ModuleInfo module : modules) {
                String status = module.isMandatory() ? "MANDATORY" : "OPTIONAL";
                System.out.println("  " + module.getOrderNumber() + ". " + module.getModuleLabel() + " [" + status + "]");
                logger.info("  {}: {} [{}]", module.getOrderNumber(), module.getModuleLabel(), status);
            }

            // ==========================================
            // USER: Submit Consent Request
            // ==========================================
            System.out.println("\n--- USER: Submitting Consent ---");
            logger.info("\n--- USER: Submitting Consent ---");

            ConsentRequest request = createConsentRequest(templateKey, modules);

            long acceptedCount = request.getModuleDecisions().stream()
                    .filter(d -> "ACCEPTED".equals(d.getStatus()))
                    .count();
            long declinedCount = request.getModuleDecisions().stream()
                    .filter(d -> "DECLINED".equals(d.getStatus()))
                    .count();

            System.out.println("Consent request created for patient: " + request.getPatientId());
            System.out.println("  Accepted modules: " + acceptedCount);
            System.out.println("  Declined modules: " + declinedCount);
            logger.info("Consent request created for patient: {}", request.getPatientId());
            logger.info("  Accepted modules: {}", acceptedCount);
            logger.info("  Declined modules: {}", declinedCount);

            // ==========================================
            // SYSTEM: Generate Consent
            // ==========================================
            System.out.println("\n--- SYSTEM: Generating Consent ---");
            logger.info("\n--- SYSTEM: Generating Consent ---");

            Consent consent = populator.populateConsent(request, miiSnapshot);
            System.out.println("Consent generated successfully");
            logger.info("Consent generated successfully");

            // ==========================================
            // SYSTEM: Validate (using optimized validator)
            // ==========================================
            System.out.println("\n--- SYSTEM: Validating Consent ---");
            logger.info("\n--- SYSTEM: Validating Consent ---");

            // Create validator service - it will initialize the validator once
            FhirValidatorService validatorService = new FhirValidatorService(fhirContext, supportChain);
            validatorService.init(); // Initialize the reusable validator

            ValidationResult validationResult = validatorService.validate(consent);
            validatorService.printValidationResults(validationResult);

            // ==========================================
            // SYSTEM: Output JSON
            // ==========================================
            JsonSerializationService serializationService = new JsonSerializationService(jsonParser);
            String jsonPayload = serializationService.serialize(consent);
            serializationService.printJson("Final Consent FHIR JSON", jsonPayload);

            // Print summary
            printSummary(miiSnapshot, template, request, validationResult);

        } catch (Exception e) {
            System.err.println("\n❌ Error during FHIR resource processing: " + e.getMessage());
            logger.error("Error during FHIR resource processing", e);
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static String extractTemplateKey(ExchangeFormatDefinition template) {
        if (template == null) {
            throw new IllegalArgumentException("Template is null");
        }
        if (template.getTemplatesConsentTemplate() == null || template.getTemplatesConsentTemplate().isEmpty()) {
            throw new IllegalArgumentException("No consent templates found in the file");
        }

        return template.getTemplatesConsentTemplate().get(0).getDomainName() + ";" +
                template.getTemplatesConsentTemplate().get(0).getName() + ";" +
                template.getTemplatesConsentTemplate().get(0).getVersion();
    }

    private static ConsentRequest createConsentRequest(String templateKey, List<ModuleInfo> modules) {
        ConsentRequest request = new ConsentRequest();
        request.setTemplateKey(templateKey);
        request.setPatientId("Patient/123456");
        request.setOrganizationId("Organization/hospital-123");
        request.setConsentDate(new Date());
        request.setSourceReference("QuestionnaireResponse/consent-form-2024-01-01");
        request.setInstitutionName("Universitätsklinikum Hamburg");
        request.setPatientName("Max Mustermann");

        for (ModuleInfo module : modules) {
            if (ModuleTypeDetector.isIntroModule(module.getModuleKey())) {
                continue;
            }

            ModuleDecision decision = new ModuleDecision();
            decision.setModuleKey(module.getModuleKey());
            decision.setModuleName(module.getModuleName());

            // Accept first 3 non-intro modules, decline the rest
            long acceptedCount = request.getModuleDecisions().stream()
                    .filter(d -> "ACCEPTED".equals(d.getStatus()))
                    .count();

            if (acceptedCount < 3) {
                decision.setStatus("ACCEPTED");
                decision.setProvisionType("permit");
            } else {
                decision.setStatus("DECLINED");
                decision.setProvisionType("deny");
            }
            request.addModuleDecision(decision);
        }

        return request;
    }

    private static void printSummary(StructureDefinition miiSnapshot, ExchangeFormatDefinition template,
                                     ConsentRequest request, ValidationResult validationResult) {
        System.out.println("\n=== Summary ===");
        System.out.println("Domain: " + template.getDomain().getName());
        System.out.println("Supported Version: " + template.getSupportedVersion());
        System.out.println("Profile URL: " + miiSnapshot.getUrl());
        System.out.println("Profile Version: " + miiSnapshot.getVersion());
        System.out.println("Patient: " + request.getPatientId());
        System.out.println("Organization: " + request.getOrganizationId());
        System.out.println("Consent Date: " + request.getConsentDate());
        System.out.println("Validation: " + (validationResult.isSuccessful() ? "PASSED ✓" : "FAILED ✗"));
        System.out.println("Messages: " + validationResult.getMessages().size());

        logger.info("\n=== Summary ===");
        logger.info("Domain: {}", template.getDomain().getName());
        logger.info("Supported Version: {}", template.getSupportedVersion());
        logger.info("Profile URL: {}", miiSnapshot.getUrl());
        logger.info("Profile Version: {}", miiSnapshot.getVersion());
        logger.info("Patient: {}", request.getPatientId());
        logger.info("Organization: {}", request.getOrganizationId());
        logger.info("Consent Date: {}", request.getConsentDate());
        logger.info("Validation: {}", validationResult.isSuccessful() ? "PASSED ✓" : "FAILED ✗");
        logger.info("Messages: {}", validationResult.getMessages().size());
    }
}