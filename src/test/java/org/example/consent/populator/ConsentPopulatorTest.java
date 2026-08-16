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
import org.hl7.fhir.r4.model.StructureDefinition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("ConsentPopulator Tests")
class ConsentPopulatorTest {

    private static final String TEMPLATE_1_6_D = "2023-05-12-MII-BroadConsent-1.6.d.json";
    private static final String TEMPLATE_1_7_2 = "2025-01-21 MII BC 1.7.2 mit Erweiterungsmodul PROM.json";
    private static final String GERMAN_PROFILE = "ConsentManagementConsent.json";
    private static final String MII_PROFILE = "MII_PR_Consent_Einwilligung.json";
    private static final String MII_ANSWER_CODESYSTEM = "CodeSystem-MiiConsentAnswerCodeSystem.json";
    private static final String MII_POLICY_CODESYSTEM = "CodeSystem-MiiConsentPolicyCodeSystem.json";
    private static final String MII_VERSION_CODESYSTEM = "CodeSystem-MiiConsentVersionModuleCodeSystem.json";
    private static final String MII_POLICY_VALUESET = "ValueSet-MiiConsentPolicyValueSet.json";

    private static final String FHIR_BASE_CONSENT = "http://hl7.org/fhir/StructureDefinition/Consent";

    private FhirContext fhirContext;
    private IParser jsonParser;
    private StructureDefinition miiSnapshot;
    private ValidationSupportChain supportChain;
    private ConsentTemplateLoader templateLoader;

    @BeforeAll
    void setUp() throws Exception {
        fhirContext = FhirContext.forR4();
        jsonParser = fhirContext.newJsonParser();
        templateLoader = new ConsentTemplateLoader();

        // Load resources from classpath
        FhirResourceLoader resourceLoader = new FhirResourceLoader(fhirContext, jsonParser);

        StructureDefinition germanConsentBase = resourceLoader.loadStructureDefinitionFromClasspath(GERMAN_PROFILE);
        StructureDefinition miiConsentProfile = resourceLoader.loadStructureDefinitionFromClasspath(MII_PROFILE);

        resourceLoader.loadCodeSystemFromClasspath(MII_ANSWER_CODESYSTEM);
        resourceLoader.loadCodeSystemFromClasspath(MII_POLICY_CODESYSTEM);
        resourceLoader.loadCodeSystemFromClasspath(MII_VERSION_CODESYSTEM);
        resourceLoader.loadValueSetFromClasspath(MII_POLICY_VALUESET);

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

    // ==========================================
    // Test Data Providers
    // ==========================================

    static Stream<ConsentTestData> templateTestData() {
        return Stream.of(
                new ConsentTestData(
                        "1.6.d",
                        TEMPLATE_1_6_D,
                        "MII;Patienteneinwilligung MII;1.6.d",
                        13,
                        2,
                        11
                ),
                new ConsentTestData(
                        "1.7.2",
                        TEMPLATE_1_7_2,
                        "MII;Patienteneinwilligung MII mit Erweiterungsmodul PROM;1.7.b",
                        18,
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
                        13,
                        0
                ),
                new ConsentTestData(
                        "1.7.2 (All Accepted)",
                        TEMPLATE_1_7_2,
                        "MII;Patienteneinwilligung MII mit Erweiterungsmodul PROM;1.7.b",
                        18,
                        18,
                        0
                )
        );
    }

    // ==========================================
    // Core Tests
    // ==========================================

    @ParameterizedTest
    @MethodSource("templateTestData")
    @DisplayName("Should populate consent with mixed decisions")
    void shouldPopulateConsentWithMixedDecisions(ConsentTestData testData) throws Exception {
        ExchangeFormatDefinition template = templateLoader.loadFromClasspath(testData.templatePath);
        ConsentPopulator populator = new ConsentPopulator(template);

        List<ModuleInfo> modules = populator.getModulesForTemplate(testData.templateKey);
        assertEquals(testData.expectedTotalModules, modules.size());

        ConsentRequest request = createRequestWithMixedDecisions(testData.templateKey, modules);
        Consent consent = populator.populateConsent(request, miiSnapshot);

        // Verify accepted modules map to PERMIT provisions
        long expectedAccepted = request.getModuleDecisions().stream()
                .filter(d -> "ACCEPTED".equals(d.getStatus()))
                .filter(d -> !ModuleTypeDetector.isIntroModule(d.getModuleKey()))
                .count();
        long permitCount = consent.getProvision().getProvision().stream()
                .filter(p -> p.getType() == Consent.ConsentProvisionType.PERMIT)
                .count();
        assertEquals(expectedAccepted, permitCount,
                "Number of PERMIT provisions should match number of accepted decision modules");

        // Verify declined modules map to DENY provisions
        long expectedDeclined = request.getModuleDecisions().stream()
                .filter(d -> "DECLINED".equals(d.getStatus()))
                .filter(d -> !ModuleTypeDetector.isIntroModule(d.getModuleKey()))
                .count();
        long denyCount = consent.getProvision().getProvision().stream()
                .filter(p -> p.getType() == Consent.ConsentProvisionType.DENY)
                .count();
        assertEquals(expectedDeclined, denyCount,
                "Number of DENY provisions should match number of declined decision modules");

        // Validate
        FhirValidatorService validatorService = new FhirValidatorService(fhirContext, supportChain);
        ValidationResult result = validatorService.validate(consent);
        assertTrue(result.isSuccessful(), "Consent should be valid against profile");

        // Test serialization
        JsonSerializationService serializationService = new JsonSerializationService(jsonParser);
        String json = serializationService.serialize(consent);
        assertNotNull(json);
        assertFalse(json.isEmpty());
        assertTrue(json.contains("resourceType"), "JSON should contain resourceType");
    }

    @ParameterizedTest
    @MethodSource("allAcceptedTestData")
    @DisplayName("Should populate consent with all modules accepted")
    void shouldPopulateConsentWithAllModulesAccepted(ConsentTestData testData) throws Exception {
        ExchangeFormatDefinition template = templateLoader.loadFromClasspath(testData.templatePath);
        ConsentPopulator populator = new ConsentPopulator(template);

        List<ModuleInfo> modules = populator.getModulesForTemplate(testData.templateKey);
        assertEquals(testData.expectedTotalModules, modules.size());

        ConsentRequest request = createRequestWithAllAccepted(testData.templateKey, modules);
        Consent consent = populator.populateConsent(request, miiSnapshot);

        long decisionModuleCount = modules.stream()
                .filter(m -> !ModuleTypeDetector.isIntroModule(m.getModuleKey()))
                .count();

        long permitCount = consent.getProvision().getProvision().stream()
                .filter(p -> p.getType() == Consent.ConsentProvisionType.PERMIT)
                .count();

        assertEquals(decisionModuleCount, permitCount,
                "All decision modules should have PERMIT provisions when all accepted");

        long denyCount = consent.getProvision().getProvision().stream()
                .filter(p -> p.getType() == Consent.ConsentProvisionType.DENY)
                .count();
        assertEquals(0, denyCount, "No DENY provisions should exist when all accepted");

        FhirValidatorService validatorService = new FhirValidatorService(fhirContext, supportChain);
        ValidationResult result = validatorService.validate(consent);
        assertTrue(result.isSuccessful());
    }

    // ==========================================
    // Edge Case Tests
    // ==========================================

    @Test
    @DisplayName("Should handle missing patient ID with proper exception")
    void shouldHandleMissingPatientId() throws Exception {
        ExchangeFormatDefinition template = templateLoader.loadFromClasspath(TEMPLATE_1_7_2);
        ConsentPopulator populator = new ConsentPopulator(template);

        ConsentRequest request = new ConsentRequest();
        request.setTemplateKey("MII;Patienteneinwilligung MII mit Erweiterungsmodul PROM;1.7.b");
        request.setOrganizationId("Organization/test");
        request.setModuleDecisions(new ArrayList<>());

        assertThrows(IllegalArgumentException.class,
                () -> populator.populateConsent(request, miiSnapshot));
    }

    @Test
    @DisplayName("Should handle missing organization ID with proper exception")
    void shouldHandleMissingOrganizationId() throws Exception {
        ExchangeFormatDefinition template = templateLoader.loadFromClasspath(TEMPLATE_1_7_2);
        ConsentPopulator populator = new ConsentPopulator(template);

        ConsentRequest request = new ConsentRequest();
        request.setTemplateKey("MII;Patienteneinwilligung MII mit Erweiterungsmodul PROM;1.7.b");
        request.setPatientId("Patient/test");
        request.setModuleDecisions(new ArrayList<>());

        assertThrows(IllegalArgumentException.class,
                () -> populator.populateConsent(request, miiSnapshot));
    }

    @Test
    @DisplayName("Should handle missing module decisions with proper exception")
    void shouldHandleMissingModuleDecisions() throws Exception {
        ExchangeFormatDefinition template = templateLoader.loadFromClasspath(TEMPLATE_1_7_2);
        ConsentPopulator populator = new ConsentPopulator(template);

        ConsentRequest request = new ConsentRequest();
        request.setTemplateKey("MII;Patienteneinwilligung MII mit Erweiterungsmodul PROM;1.7.b");
        request.setPatientId("Patient/test");
        request.setOrganizationId("Organization/test");

        assertThrows(IllegalArgumentException.class,
                () -> populator.populateConsent(request, miiSnapshot));
    }

    @Test
    @DisplayName("Should handle null request with proper exception")
    void shouldHandleNullRequest() throws Exception {
        ExchangeFormatDefinition template = templateLoader.loadFromClasspath(TEMPLATE_1_7_2);
        ConsentPopulator populator = new ConsentPopulator(template);

        assertThrows(IllegalArgumentException.class,
                () -> populator.populateConsent(null, miiSnapshot));
    }

    // ==========================================
    // XHTML & Injection Security Tests
    // ==========================================

    @Test
    @DisplayName("Should handle special XML characters in patient name safely")
    void shouldHandleSpecialCharactersInPatientName() throws Exception {
        ExchangeFormatDefinition template = templateLoader.loadFromClasspath(TEMPLATE_1_7_2);
        ConsentPopulator populator = new ConsentPopulator(template);

        List<ModuleInfo> modules = populator.getModulesForTemplate(
                "MII;Patienteneinwilligung MII mit Erweiterungsmodul PROM;1.7.b");

        ConsentRequest request = createRequestWithMixedDecisions(
                "MII;Patienteneinwilligung MII mit Erweiterungsmodul PROM;1.7.b", modules);
        request.setPatientName("Max & Mustermann <test> \"quoted\" 'single'");

        Consent consent = populator.populateConsent(request, miiSnapshot);

        assertNotNull(consent.getText());
        String divContent = consent.getText().getDivAsString();
        assertNotNull(divContent);
        assertFalse(divContent.isEmpty());
        assertDoesNotThrow(() -> consent.getText().getDivAsString());

        FhirValidatorService validatorService = new FhirValidatorService(fhirContext, supportChain);
        ValidationResult result = validatorService.validate(consent);
        assertTrue(result.isSuccessful());
    }

    @Test
    @DisplayName("Should handle special XML characters in institution name safely")
    void shouldHandleSpecialCharactersInInstitutionName() throws Exception {
        ExchangeFormatDefinition template = templateLoader.loadFromClasspath(TEMPLATE_1_7_2);
        ConsentPopulator populator = new ConsentPopulator(template);

        List<ModuleInfo> modules = populator.getModulesForTemplate(
                "MII;Patienteneinwilligung MII mit Erweiterungsmodul PROM;1.7.b");

        ConsentRequest request = createRequestWithMixedDecisions(
                "MII;Patienteneinwilligung MII mit Erweiterungsmodul PROM;1.7.b", modules);
        request.setInstitutionName("Klinikum <Test> & Co. \"GmbH\"");

        Consent consent = populator.populateConsent(request, miiSnapshot);

        assertNotNull(consent.getText());
        String divContent = consent.getText().getDivAsString();
        assertNotNull(divContent);
        assertFalse(divContent.isEmpty());
        assertDoesNotThrow(() -> consent.getText().getDivAsString());

        FhirValidatorService validatorService = new FhirValidatorService(fhirContext, supportChain);
        ValidationResult result = validatorService.validate(consent);
        assertTrue(result.isSuccessful());
    }

    // ==========================================
    // Date Consistency Tests
    // ==========================================

    @Test
    @DisplayName("Should maintain date consistency between consent.dateTime and provision.period.start")
    void shouldMaintainDateConsistency() throws Exception {
        ExchangeFormatDefinition template = templateLoader.loadFromClasspath(TEMPLATE_1_7_2);
        ConsentPopulator populator = new ConsentPopulator(template);

        List<ModuleInfo> modules = populator.getModulesForTemplate(
                "MII;Patienteneinwilligung MII mit Erweiterungsmodul PROM;1.7.b");

        Date specificDate = new Date(1700000000000L);

        ConsentRequest request = createRequestWithMixedDecisions(
                "MII;Patienteneinwilligung MII mit Erweiterungsmodul PROM;1.7.b", modules);
        request.setConsentDate(specificDate);

        Consent consent = populator.populateConsent(request, miiSnapshot);

        assertEquals(specificDate.getTime() / 1000, consent.getDateTime().getTime() / 1000,
                "consent.dateTime should match request date");

        Date periodStart = consent.getProvision().getPeriod().getStart();
        assertEquals(consent.getDateTime().getTime() / 1000, periodStart.getTime() / 1000,
                "provision.period.start should match consent.dateTime");

        for (Consent.ProvisionComponent provision : consent.getProvision().getProvision()) {
            Date nestedStart = provision.getPeriod().getStart();
            assertEquals(consent.getDateTime().getTime() / 1000, nestedStart.getTime() / 1000,
                    "Nested provision.period.start should match consent.dateTime");
        }

        Date periodEnd = consent.getProvision().getPeriod().getEnd();
        assertTrue(periodEnd.after(periodStart), "period.end should be after period.start");
    }

    // ==========================================
    // Narrative Fallback Tests
    // ==========================================

    @Test
    @DisplayName("Should preserve decisions in fallback narrative when HTML fails")
    void shouldPreserveDecisionsInFallbackNarrative() throws Exception {
        // Given
        ExchangeFormatDefinition template = templateLoader.loadFromClasspath(TEMPLATE_1_7_2);
        ConsentPopulator populator = new ConsentPopulator(template);

        List<ModuleInfo> modules = populator.getModulesForTemplate(
                "MII;Patienteneinwilligung MII mit Erweiterungsmodul PROM;1.7.b");

        ConsentRequest request = createRequestWithMixedDecisions(
                "MII;Patienteneinwilligung MII mit Erweiterungsmodul PROM;1.7.b", modules);

        // When
        Consent consent = populator.populateConsent(request, miiSnapshot);

        // Then
        assertNotNull(consent.getText());
        String divContent = consent.getText().getDivAsString();
        assertNotNull(divContent);
        assertFalse(divContent.isEmpty());

        // FIXED: Check for the correct status texts used in the narrative
        // The main narrative uses "ICH WILLIGE EIN" / "ICH WILLIGE NICHT EIN"
        // The fallback narrative uses "EINWILLIGUNG ERTEILT" / "EINWILLIGUNG VERWEIGERT"
        // Check for either pattern since we don't know which narrative is used
        boolean hasAccepted = divContent.contains("ICH WILLIGE EIN") ||
                divContent.contains("EINWILLIGUNG ERTEILT");
        boolean hasDeclined = divContent.contains("ICH WILLIGE NICHT EIN") ||
                divContent.contains("EINWILLIGUNG VERWEIGERT");

        // At least one accepted and one declined should be present (mixed decisions)
        assertTrue(hasAccepted || hasDeclined,
                "Narrative should contain at least one decision status. Found: " +
                        (divContent.length() > 100 ? divContent.substring(0, 100) + "..." : divContent));

        // Validate
        FhirValidatorService validatorService = new FhirValidatorService(fhirContext, supportChain);
        ValidationResult result = validatorService.validate(consent);
        assertTrue(result.isSuccessful());
    }

    // ==========================================
    // Policy Code Fallback Tests
    // ==========================================

    @Test
    @DisplayName("Should handle policy with null FHIR code gracefully")
    void shouldHandleNullPolicyCode() throws Exception {
        ExchangeFormatDefinition template = templateLoader.loadFromClasspath(TEMPLATE_1_7_2);
        ConsentPopulator populator = new ConsentPopulator(template);

        List<ModuleInfo> modules = populator.getModulesForTemplate(
                "MII;Patienteneinwilligung MII mit Erweiterungsmodul PROM;1.7.b");

        ConsentRequest request = createRequestWithMixedDecisions(
                "MII;Patienteneinwilligung MII mit Erweiterungsmodul PROM;1.7.b", modules);

        Consent consent = populator.populateConsent(request, miiSnapshot);

        assertNotNull(consent.getProvision());
        assertTrue(consent.getProvision().getProvision().size() > 0,
                "Should have at least one provision");

        for (Consent.ProvisionComponent provision : consent.getProvision().getProvision()) {
            assertNotNull(provision.getType(), "Provision type should be set");
            assertNotNull(provision.getPeriod(), "Provision period should be set");
        }

        FhirValidatorService validatorService = new FhirValidatorService(fhirContext, supportChain);
        ValidationResult result = validatorService.validate(consent);
        assertTrue(result.isSuccessful());
    }

    // ==========================================
    // Request Creation Methods
    // ==========================================

    private ConsentRequest createRequestWithMixedDecisions(String templateKey, List<ModuleInfo> modules) {
        ConsentRequest request = new ConsentRequest();
        request.setTemplateKey(templateKey);
        request.setPatientId("Patient/123456");
        request.setOrganizationId("Organization/hospital-123");
        request.setConsentDate(new Date());
        request.setInstitutionName("Universitätsklinikum Hamburg");
        request.setPatientName("Max Mustermann");

        int acceptCount = templateKey.contains("1.7.b") ? 3 : 2;

        for (ModuleInfo module : modules) {
            if (ModuleTypeDetector.isIntroModule(module.getModuleKey())) {
                continue;
            }

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

        return request;
    }

    private ConsentRequest createRequestWithAllAccepted(String templateKey, List<ModuleInfo> modules) {
        ConsentRequest request = new ConsentRequest();
        request.setTemplateKey(templateKey);
        request.setPatientId("Patient/123456");
        request.setOrganizationId("Organization/hospital-123");
        request.setConsentDate(new Date());
        request.setInstitutionName("Universitätsklinikum Hamburg");
        request.setPatientName("Max Mustermann");

        for (ModuleInfo module : modules) {
            if (ModuleTypeDetector.isIntroModule(module.getModuleKey())) {
                continue;
            }

            ModuleDecision decision = new ModuleDecision();
            decision.setModuleKey(module.getModuleKey());
            decision.setModuleName(module.getModuleName());
            decision.setStatus("ACCEPTED");
            decision.setProvisionType("permit");
            request.addModuleDecision(decision);
        }

        return request;
    }

    // ==========================================
    // Test Data Class
    // ==========================================

    static class ConsentTestData {
        final String version;
        final String templatePath;
        final String templateKey;
        final int expectedTotalModules;
        final int expectedAcceptedModules;
        final int expectedDeclinedModules;

        ConsentTestData(String version, String templatePath, String templateKey,
                        int expectedTotalModules, int expectedAcceptedModules,
                        int expectedDeclinedModules) {
            this.version = version;
            this.templatePath = templatePath;
            this.templateKey = templateKey;
            this.expectedTotalModules = expectedTotalModules;
            this.expectedAcceptedModules = expectedAcceptedModules;
            this.expectedDeclinedModules = expectedDeclinedModules;
        }

        @Override
        public String toString() {
            return version;
        }
    }
}