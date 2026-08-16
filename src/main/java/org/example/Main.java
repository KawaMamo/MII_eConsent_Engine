package org.example;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.validation.ValidationResult;
import org.example.consent.loader.ConsentTemplateLoader;
import org.example.consent.model.ExchangeFormatDefinition;
import org.example.consent.populator.ConsentPopulator;
import org.example.consent.populator.ConsentRequest;
import org.example.consent.populator.ModuleDecision;
import org.example.consent.populator.ModuleInfo;
import org.example.tools.FhirResourceLoader;
import org.example.tools.JsonSerializationService;
import org.example.validation.FhirValidatorService;
import org.example.validation.SnapshotGeneratorService;
import org.example.validation.ValidationSupportFactory;
import org.hl7.fhir.common.hapi.validation.support.SnapshotGeneratingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.StructureDefinition;

import java.util.Date;
import java.util.List;

public class Main {

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
            // ==========================================
            // ADMIN PHASE: Load Resources
            // ==========================================
            System.out.println("=== FHIR Consent Management System ===\n");
            System.out.println("--- ADMIN: Loading Resources ---");

            // Load FHIR profiles and terminology resources
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

            // Extract template key with null safety
            String templateKey = extractTemplateKey(template);
            System.out.println("Active Template: " + templateKey);

            // ==========================================
            // ADMIN: Setup Validation Support
            // ==========================================
            System.out.println("\n--- ADMIN: Setting up validation ---");

            ValidationSupportFactory supportFactory = new ValidationSupportFactory(
                    fhirContext,
                    resourceLoader.getPrePopulatedSupport()
            );

            ValidationSupportChain supportChain = supportFactory.createSupportChain();
            SnapshotGeneratingValidationSupport snapshotSupport = supportFactory.createSnapshotSupport();

            // Generate snapshots
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

            // ==========================================
            // USER PHASE: Show available modules
            // ==========================================
            System.out.println("\n--- USER: Available Modules ---");

            ConsentPopulator populator = new ConsentPopulator(template, miiSnapshot);
            List<ModuleInfo> modules = populator.getModulesForTemplate(templateKey);

            System.out.println("Total modules available: " + modules.size());
            for (ModuleInfo module : modules) {
                System.out.println("  " + module.getOrderNumber() + ". " +
                        module.getModuleLabel() + " [" +
                        (module.isMandatory() ? "MANDATORY" : "OPTIONAL") + "]");
            }

            // ==========================================
            // USER: Submit Consent Request
            // ==========================================
            System.out.println("\n--- USER: Submitting Consent ---");

            ConsentRequest request = createConsentRequest(templateKey, modules);

            System.out.println("Consent request created for patient: " + request.getPatientId());
            System.out.println("  Accepted modules: " +
                    request.getModuleDecisions().stream()
                            .filter(d -> "ACCEPTED".equals(d.getStatus()))
                            .count());
            System.out.println("  Declined modules: " +
                    request.getModuleDecisions().stream()
                            .filter(d -> "DECLINED".equals(d.getStatus()))
                            .count());

            // ==========================================
            // SYSTEM: Generate Consent
            // ==========================================
            System.out.println("\n--- SYSTEM: Generating Consent ---");
            Consent consent = populator.populateConsent(request);

            // ==========================================
            // SYSTEM: Validate
            // ==========================================
            System.out.println("\n--- SYSTEM: Validating Consent ---");
            FhirValidatorService validatorService = new FhirValidatorService(fhirContext, supportChain);
            ValidationResult validationResult = validatorService.validate(consent);
            validatorService.printValidationResults(validationResult);

            // ==========================================
            // SYSTEM: Serialize Output
            // ==========================================
            JsonSerializationService serializationService = new JsonSerializationService(jsonParser);
            String jsonPayload = serializationService.serialize(consent);
            serializationService.printJson("Final Consent FHIR JSON", jsonPayload);

            // Print summary
            printSummary(miiSnapshot, template, request, validationResult);

        } catch (Exception e) {
            System.err.println("\n❌ Error during FHIR resource processing: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Extract template key with null safety
     */
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

    /**
     * Create a consent request with sample decisions
     */
    private static ConsentRequest createConsentRequest(String templateKey, List<ModuleInfo> modules) {
        ConsentRequest request = new ConsentRequest();
        request.setTemplateKey(templateKey);
        request.setPatientId("Patient/123456");
        request.setOrganizationId("Organization/hospital-123");
        request.setConsentDate(new Date());
        request.setSourceReference("QuestionnaireResponse/consent-form-2024-01-01");

        // User decisions for each module
        for (ModuleInfo module : modules) {
            ModuleDecision decision = new ModuleDecision();
            decision.setModuleKey(module.getModuleKey());
            decision.setModuleName(module.getModuleName());

            // Accept modules with order number < 3 (0, 1, 2)
            // This is just a sample - in real use, this comes from the user
            if (module.getOrderNumber() < 3) {
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

    /**
     * Print summary of the consent generation
     */
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
    }
}