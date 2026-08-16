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

import java.io.File;
import java.util.Date;
import java.util.List;

/**
 * Simple test runner for comparing both template versions
 */
public class TestRunner {

    private static final String RESOURCES_PATH = "src/main/resources/";
    private static final String TEMPLATE_1_6_D = RESOURCES_PATH + "2023-05-12-MII-BroadConsent-1.6.d.json";
    private static final String TEMPLATE_1_7_2 = RESOURCES_PATH + "2025-01-21 MII BC 1.7.2 mit Erweiterungsmodul PROM.json";

    public static void main(String[] args) {
        try {
            System.out.println("=== ConsentPopulator Test Runner ===\n");

            // Test 1.6.d
            System.out.println("--- Testing Template 1.6.d ---");
            testTemplate(TEMPLATE_1_6_D, "MII;Patienteneinwilligung MII;1.6.d");

            // Test 1.7.2
            System.out.println("\n--- Testing Template 1.7.2 ---");
            testTemplate(TEMPLATE_1_7_2, "MII;Patienteneinwilligung MII mit Erweiterungsmodul PROM;1.7.b");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void testTemplate(String templatePath, String templateKey) throws Exception {
        FhirContext fhirContext = FhirContext.forR4();
        IParser jsonParser = fhirContext.newJsonParser();

        // Load resources
        FhirResourceLoader resourceLoader = new FhirResourceLoader(fhirContext, jsonParser);

        StructureDefinition germanConsentBase = resourceLoader.loadStructureDefinition(
                RESOURCES_PATH + "ConsentManagementConsent.json");
        StructureDefinition miiConsentProfile = resourceLoader.loadStructureDefinition(
                RESOURCES_PATH + "MII_PR_Consent_Einwilligung.json");

        resourceLoader.loadCodeSystem(RESOURCES_PATH + "CodeSystem-MiiConsentAnswerCodeSystem.json");
        resourceLoader.loadCodeSystem(RESOURCES_PATH + "CodeSystem-MiiConsentPolicyCodeSystem.json");
        resourceLoader.loadCodeSystem(RESOURCES_PATH + "CodeSystem-MiiConsentVersionModuleCodeSystem.json");
        resourceLoader.loadValueSet(RESOURCES_PATH + "ValueSet-MiiConsentPolicyValueSet.json");

        // Setup validation
        ValidationSupportFactory supportFactory = new ValidationSupportFactory(
                fhirContext, resourceLoader.getPrePopulatedSupport());
        ValidationSupportChain supportChain = supportFactory.createSupportChain();
        SnapshotGeneratingValidationSupport snapshotSupport = supportFactory.createSnapshotSupport();

        SnapshotGeneratorService snapshotService = new SnapshotGeneratorService(supportChain, snapshotSupport);

        StructureDefinition germanSnapshot = snapshotService.generateSnapshot(
                germanConsentBase, "http://hl7.org/fhir/StructureDefinition/Consent");
        resourceLoader.getPrePopulatedSupport().addStructureDefinition(germanSnapshot);

        StructureDefinition miiSnapshot = snapshotService.generateSnapshot(
                miiConsentProfile, miiConsentProfile.getBaseDefinition());
        resourceLoader.getPrePopulatedSupport().addStructureDefinition(miiSnapshot);

        // Load template
        ConsentTemplateLoader templateLoader = new ConsentTemplateLoader();
        ExchangeFormatDefinition template = templateLoader.loadFromFile(templatePath);

        ConsentPopulator populator = new ConsentPopulator(template, miiSnapshot);

        // Get modules
        List<ModuleInfo> modules = populator.getModulesForTemplate(templateKey);
        System.out.println("Total modules: " + modules.size());
        System.out.println("  Mandatory: " + modules.stream().filter(ModuleInfo::isMandatory).count());
        System.out.println("  Optional: " + modules.stream().filter(m -> !m.isMandatory()).count());

        // Create request with mixed decisions
        ConsentRequest request = new ConsentRequest();
        request.setTemplateKey(templateKey);
        request.setPatientId("Patient/123456");
        request.setOrganizationId("Organization/hospital-123");
        request.setConsentDate(new Date());
        request.setInstitutionName("Universitätsklinikum Hamburg");
        request.setPatientName("Max Mustermann");

        int acceptCount = templateKey.contains("1.7.b") ? 3 : 2;

        for (ModuleInfo module : modules) {
            ModuleDecision decision = new ModuleDecision();
            decision.setModuleKey(module.getModuleKey());
            decision.setModuleName(module.getModuleName());

            if (module.getOrderNumber() < acceptCount) {
                decision.setStatus("ACCEPTED");
                decision.setProvisionType("permit");
            } else {
                decision.setStatus("DECLINED");
                decision.setProvisionType("deny");
            }
            request.addModuleDecision(decision);
        }

        System.out.println("Accepted: " + request.getModuleDecisions().stream()
                .filter(d -> "ACCEPTED".equals(d.getStatus())).count());
        System.out.println("Declined: " + request.getModuleDecisions().stream()
                .filter(d -> "DECLINED".equals(d.getStatus())).count());

        // Generate consent
        Consent consent = populator.populateConsent(request, miiSnapshot);

        // Validate
        FhirValidatorService validatorService = new FhirValidatorService(fhirContext, supportChain);
        ValidationResult result = validatorService.validate(consent);
        validatorService.printValidationResults(result);

        // Output JSON
        JsonSerializationService serializationService = new JsonSerializationService(jsonParser);
        String json = serializationService.serialize(consent);

        // Save to file
        String outputPath = "target/" + templateKey.replaceAll("[; ]", "_") + ".json";
        java.nio.file.Files.write(
                java.nio.file.Paths.get(outputPath),
                json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        System.out.println("Output saved to: " + outputPath);

        System.out.println("Validation: " + (result.isSuccessful() ? "PASSED ✓" : "FAILED ✗"));
        System.out.println("Provisions: " + consent.getProvision().getProvision().size());
    }
}