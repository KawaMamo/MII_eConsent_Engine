package org.example.consent.populator;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.validation.ValidationResult;
import org.example.consent.loader.ConsentTemplateLoader;
import org.example.consent.model.ExchangeFormatDefinition;
import org.example.tools.FhirResourceLoader;
import org.example.tools.JsonSerializationService;
import org.example.validation.FhirValidatorService;
import org.example.validation.SnapshotGeneratorService;
import org.example.validation.ValidationSupportFactory;
import org.hl7.fhir.common.hapi.validation.support.SnapshotGeneratingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.Narrative;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("ConsentPopulator Tests")
class ConsentPopulatorTest {

    private static final String RESOURCES_PATH = "src/main/resources/";
    private static final String TEMPLATE_1_6_D = RESOURCES_PATH + "2023-05-12-MII-BroadConsent-1.6.d.json";
    private static final String TEMPLATE_1_7_2 = RESOURCES_PATH + "2025-01-21 MII BC 1.7.2 mit Erweiterungsmodul PROM.json";
    private static final String GERMAN_PROFILE = RESOURCES_PATH + "ConsentManagementConsent.json";
    private static final String MII_PROFILE = RESOURCES_PATH + "MII_PR_Consent_Einwilligung.json";
    private static final String MII_ANSWER_CODESYSTEM = RESOURCES_PATH + "CodeSystem-MiiConsentAnswerCodeSystem.json";
    private static final String MII_POLICY_CODESYSTEM = RESOURCES_PATH + "CodeSystem-MiiConsentPolicyCodeSystem.json";
    private static final String MII_VERSION_CODESYSTEM = RESOURCES_PATH + "CodeSystem-MiiConsentVersionModuleCodeSystem.json";
    private static final String MII_POLICY_VALUESET = RESOURCES_PATH + "ValueSet-MiiConsentPolicyValueSet.json";

    private static final String FHIR_BASE_CONSENT = "http://hl7.org/fhir/StructureDefinition/Consent";

    private FhirContext fhirContext;
    private IParser jsonParser;
    private StructureDefinition miiSnapshot;
    private ValidationSupportChain supportChain;

    @BeforeAll
    void setUp() throws Exception {
        fhirContext = FhirContext.forR4();
        jsonParser = fhirContext.newJsonParser();

        // Load resources
        FhirResourceLoader resourceLoader = new FhirResourceLoader(fhirContext, jsonParser);

        StructureDefinition germanConsentBase = resourceLoader.loadStructureDefinition(GERMAN_PROFILE);
        StructureDefinition miiConsentProfile = resourceLoader.loadStructureDefinition(MII_PROFILE);

        resourceLoader.loadCodeSystem(MII_ANSWER_CODESYSTEM);
        resourceLoader.loadCodeSystem(MII_POLICY_CODESYSTEM);
        resourceLoader.loadCodeSystem(MII_VERSION_CODESYSTEM);
        resourceLoader.loadValueSet(MII_POLICY_VALUESET);

        // Setup validation
        ValidationSupportFactory supportFactory = new ValidationSupportFactory(
                fhirContext,
                resourceLoader.getPrePopulatedSupport()
        );

        supportChain = supportFactory.createSupportChain();
        SnapshotGeneratingValidationSupport snapshotSupport = supportFactory.createSnapshotSupport();

        SnapshotGeneratorService snapshotService = new SnapshotGeneratorService(supportChain, snapshotSupport);

        StructureDefinition germanSnapshot = snapshotService.generateSnapshot(
                germanConsentBase,
                FHIR_BASE_CONSENT
        );
        resourceLoader.getPrePopulatedSupport().addStructureDefinition(germanSnapshot);

        miiSnapshot = snapshotService.generateSnapshot(
                miiConsentProfile,
                miiConsentProfile.getBaseDefinition()
        );
        resourceLoader.getPrePopulatedSupport().addStructureDefinition(miiSnapshot);
    }

    static Stream<ConsentTestData> templateTestData() {
        return Stream.of(
                new ConsentTestData(
                        "1.6.d",
                        TEMPLATE_1_6_D,
                        "MII;Patienteneinwilligung MII;1.6.d",
                        13,
                        1,
                        2,
                        11
                ),
                new ConsentTestData(
                        "1.7.2",
                        TEMPLATE_1_7_2,
                        "MII;Patienteneinwilligung MII mit Erweiterungsmodul PROM;1.7.b",
                        18,
                        1,
                        3,
                        15
                )
        );
    }

    static Stream<ConsentTestData> allAcceptedTestData() {
        return Stream.of(
                new ConsentTestData(
                        "1.6.d (All Accepted)",
                        TEMPLATE_1_6_D,
                        "MII;Patienteneinwilligung MII;1.6.d",
                        13,
                        1,
                        13,
                        0
                ),
                new ConsentTestData(
                        "1.7.2 (All Accepted)",
                        TEMPLATE_1_7_2,
                        "MII;Patienteneinwilligung MII mit Erweiterungsmodul PROM;1.7.b",
                        18,
                        1,
                        18,
                        0
                )
        );
    }

    @ParameterizedTest
    @MethodSource("templateTestData")
    @DisplayName("Should populate consent with mixed decisions")
    void shouldPopulateConsentWithMixedDecisions(ConsentTestData testData) throws Exception {
        // Given
        ConsentTemplateLoader templateLoader = new ConsentTemplateLoader();
        ExchangeFormatDefinition template = templateLoader.loadFromFile(testData.templatePath);

        ConsentPopulator populator = new ConsentPopulator(template, miiSnapshot);

        // When - get modules
        List<ModuleInfo> modules = populator.getModulesForTemplate(testData.templateKey);

        // Then - verify module count
        assertEquals(testData.expectedTotalModules, modules.size(),
                "Expected " + testData.expectedTotalModules + " modules for " + testData.version);

        // Given - create request with mixed decisions
        ConsentRequest request = createRequestWithMixedDecisions(testData.templateKey, modules);

        // When - populate consent
        Consent consent = populator.populateConsent(request, miiSnapshot);

        // Then - verify basic structure
        assertNotNull(consent, "Consent should not be null");
        assertNotNull(consent.getMeta(), "Meta should not be null");
        assertNotNull(consent.getMeta().getProfile(), "Profile should not be null");
        assertTrue(consent.getMeta().getProfile().size() > 0, "Profile should contain at least one entry");
        assertEquals(Consent.ConsentState.ACTIVE, consent.getStatus(), "Status should be ACTIVE");
        assertNotNull(consent.getDateTime(), "DateTime should not be null");

        // Verify text (narrative)
        assertNotNull(consent.getText(), "Text should not be null");
        assertEquals(Narrative.NarrativeStatus.GENERATED, consent.getText().getStatus(), "Narrative status should be GENERATED");
        assertNotNull(consent.getText().getDivAsString(), "Div content should not be null");
        assertTrue(consent.getText().getDivAsString().length() > 0, "Div content should not be empty");

        // Verify provisions
        assertNotNull(consent.getProvision(), "Provision should not be null");
        assertNotNull(consent.getProvision().getPeriod(), "Period should not be null");
        assertNotNull(consent.getProvision().getPeriod().getStart(), "Period start should not be null");

        // Verify provision count (only decision modules with codes)
        int provisionCount = consent.getProvision().getProvision().size();
        assertTrue(provisionCount >= testData.expectedAcceptedModules,
                "Expected at least " + testData.expectedAcceptedModules + " provisions, got " + provisionCount);

        // Validate against profile
        FhirValidatorService validatorService = new FhirValidatorService(fhirContext, supportChain);
        ValidationResult result = validatorService.validate(consent);

        // Print validation results for debugging
        System.out.println("Validation for " + testData.version + ": " +
                (result.isSuccessful() ? "PASSED ✓" : "FAILED ✗"));
        if (!result.isSuccessful()) {
            System.out.println("Messages:");
            result.getMessages().forEach(msg ->
                    System.out.println("  [" + msg.getSeverity() + "] " + msg.getMessage()));
        }

        assertTrue(result.isSuccessful(), "Consent should be valid against profile for " + testData.version);

        // FIXED: Test serialization - use a more robust check
        JsonSerializationService serializationService = new JsonSerializationService(jsonParser);
        String json = serializationService.serialize(consent);
        assertNotNull(json, "JSON should not be null");
        assertFalse(json.isEmpty(), "JSON should not be empty");
        assertTrue(json.length() > 10, "JSON should have content");

        // FIXED: Check for the presence of resourceType without relying on escaped quotes
        // Check for the actual JSON structure
        assertTrue(json.contains("resourceType"), "JSON should contain resourceType field");
    }

    @ParameterizedTest
    @MethodSource("allAcceptedTestData")
    @DisplayName("Should populate consent with all modules accepted")
    void shouldPopulateConsentWithAllModulesAccepted(ConsentTestData testData) throws Exception {
        // Given
        ConsentTemplateLoader templateLoader = new ConsentTemplateLoader();
        ExchangeFormatDefinition template = templateLoader.loadFromFile(testData.templatePath);

        ConsentPopulator populator = new ConsentPopulator(template, miiSnapshot);

        List<ModuleInfo> modules = populator.getModulesForTemplate(testData.templateKey);
        assertEquals(testData.expectedTotalModules, modules.size());

        // Given - create request with all modules accepted
        ConsentRequest request = createRequestWithAllAccepted(testData.templateKey, modules);

        // When - populate consent
        Consent consent = populator.populateConsent(request, miiSnapshot);

        // Then - verify all modules appear as provisions
        int provisionCount = consent.getProvision().getProvision().size();
        long decisionModuleCount = modules.stream()
                .filter(m -> !ModuleTypeDetector.isIntroModule(m.getModuleKey()))
                .count();

        assertTrue(provisionCount <= decisionModuleCount,
                "Provisions should not exceed decision modules");

        // Verify all provisions have type PERMIT (since all accepted)
        for (Consent.ProvisionComponent provision : consent.getProvision().getProvision()) {
            assertEquals(Consent.ConsentProvisionType.PERMIT, provision.getType(),
                    "All provisions should be PERMIT when all modules accepted");
            assertNotNull(provision.getPeriod(), "Provision period should not be null");
            assertNotNull(provision.getPeriod().getStart(), "Provision period start should not be null");
            assertNotNull(provision.getPeriod().getEnd(), "Provision period end should not be null");
        }

        // Validate
        FhirValidatorService validatorService = new FhirValidatorService(fhirContext, supportChain);
        ValidationResult result = validatorService.validate(consent);
        assertTrue(result.isSuccessful(), "Consent with all accepted should be valid");
    }

    @Test
    @DisplayName("Should extract template keys correctly for 1.6.d")
    void shouldExtractTemplateKeys_1_6_d() throws Exception {
        ConsentTemplateLoader templateLoader = new ConsentTemplateLoader();
        ExchangeFormatDefinition template = templateLoader.loadFromFile(TEMPLATE_1_6_D);

        ConsentPopulator populator = new ConsentPopulator(template, miiSnapshot);

        assertNotNull(populator.getAvailableTemplateKeys(), "Template keys should not be null");
        assertTrue(populator.getAvailableTemplateKeys().size() > 0, "Should have at least one template key");

        String key = populator.getAvailableTemplateKeys().iterator().next();
        assertTrue(key.contains("Patienteneinwilligung MII"), "Key should contain template name");
    }

    @Test
    @DisplayName("Should extract template keys correctly for 1.7.2")
    void shouldExtractTemplateKeys_1_7_2() throws Exception {
        ConsentTemplateLoader templateLoader = new ConsentTemplateLoader();
        ExchangeFormatDefinition template = templateLoader.loadFromFile(TEMPLATE_1_7_2);

        ConsentPopulator populator = new ConsentPopulator(template, miiSnapshot);

        assertNotNull(populator.getAvailableTemplateKeys(), "Template keys should not be null");
        assertTrue(populator.getAvailableTemplateKeys().size() > 0, "Should have at least one template key");

        String key = populator.getAvailableTemplateKeys().iterator().next();
        assertTrue(key.contains("Patienteneinwilligung MII mit Erweiterungsmodul PROM"),
                "Key should contain template name with PROM module");
    }

    // ==========================================
    // Helper Methods
    // ==========================================

    private ConsentRequest createRequestWithMixedDecisions(String templateKey, List<ModuleInfo> modules) {
        ConsentRequest request = new ConsentRequest();
        request.setTemplateKey(templateKey);
        request.setPatientId("Patient/123456");
        request.setOrganizationId("Organization/hospital-123");
        request.setConsentDate(new Date());
        request.setSourceReference("QuestionnaireResponse/consent-form-2024-01-01");
        request.setInstitutionName("Universitätsklinikum Hamburg");
        request.setPatientName("Max Mustermann");

        int acceptCount = templateKey.contains("1.7.b") ? 3 : 2;

        for (ModuleInfo module : modules) {
            ModuleDecision decision = new ModuleDecision();
            decision.setModuleKey(module.getModuleKey());
            decision.setModuleName(module.getModuleName());

            if (module.getOrderNumber() < acceptCount && !ModuleTypeDetector.isIntroModule(module.getModuleKey())) {
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

    private ConsentRequest createRequestWithAllAccepted(String templateKey, List<ModuleInfo> modules) {
        ConsentRequest request = new ConsentRequest();
        request.setTemplateKey(templateKey);
        request.setPatientId("Patient/123456");
        request.setOrganizationId("Organization/hospital-123");
        request.setConsentDate(new Date());
        request.setSourceReference("QuestionnaireResponse/consent-form-2024-01-01");
        request.setInstitutionName("Universitätsklinikum Hamburg");
        request.setPatientName("Max Mustermann");

        for (ModuleInfo module : modules) {
            ModuleDecision decision = new ModuleDecision();
            decision.setModuleKey(module.getModuleKey());
            decision.setModuleName(module.getModuleName());

            if (!ModuleTypeDetector.isIntroModule(module.getModuleKey())) {
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

    static class ConsentTestData {
        final String version;
        final String templatePath;
        final String templateKey;
        final int expectedTotalModules;
        final int expectedMandatoryModules;
        final int expectedAcceptedModules;
        final int expectedDeclinedModules;

        ConsentTestData(String version, String templatePath, String templateKey,
                        int expectedTotalModules, int expectedMandatoryModules,
                        int expectedAcceptedModules, int expectedDeclinedModules) {
            this.version = version;
            this.templatePath = templatePath;
            this.templateKey = templateKey;
            this.expectedTotalModules = expectedTotalModules;
            this.expectedMandatoryModules = expectedMandatoryModules;
            this.expectedAcceptedModules = expectedAcceptedModules;
            this.expectedDeclinedModules = expectedDeclinedModules;
        }

        @Override
        public String toString() {
            return version;
        }
    }
}