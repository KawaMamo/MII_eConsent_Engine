package org.example.consent.populator;

import org.example.consent.model.*;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Service that populates FHIR Consent resources from ExchangeFormatDefinition templates
 * Orchestrates the work of specialized builders and extractors
 * STATELESS DESIGN: Thread-safe, can be reused safely across multiple requests
 */
public class ConsentPopulator {

    private static final Logger logger = LoggerFactory.getLogger(ConsentPopulator.class);

    // Only immutable, shared dependencies
    private final ExchangeFormatDefinition template;
    private final Map<String, ConsentPolicy> policyMap;
    private final Map<String, ConsentModule> moduleMap;
    private final Map<String, ConsentTemplate> templateMap;

    // Delegates
    private final ModuleResolver moduleResolver;
    private final ConfigurationExtractor configExtractor;
    private final NarrativeBuilder narrativeBuilder;
    private final ProvisionBuilder provisionBuilder;
    private final PeriodCalculator periodCalculator;

    public ConsentPopulator(ExchangeFormatDefinition template, StructureDefinition miiProfile) {
        this.template = template;
        this.policyMap = new HashMap<>();
        this.moduleMap = new HashMap<>();
        this.templateMap = new HashMap<>();

        validateInputs();
        buildMaps();

        // Initialize delegates
        this.moduleResolver = new ModuleResolver(policyMap, moduleMap, templateMap);
        this.configExtractor = new ConfigurationExtractor();
        this.periodCalculator = new PeriodCalculator();
        this.narrativeBuilder = new NarrativeBuilder(moduleResolver);
        this.provisionBuilder = new ProvisionBuilder(moduleResolver, periodCalculator);

        logger.info("ConsentPopulator initialized with {} policies, {} modules, {} templates",
                policyMap.size(), moduleMap.size(), templateMap.size());
    }

    /**
     * Populate a Consent resource from the template and user decisions
     */
    public Consent populateConsent(ConsentRequest request, StructureDefinition miiProfile) {
        // Validate request BEFORE accessing templateMap
        validateRequest(request);

        logger.info("Populating consent for patient: {}, template: {}",
                request.getPatientId(), request.getTemplateKey());

        ConsentTemplate consentTemplate = templateMap.get(request.getTemplateKey());
        if (consentTemplate == null) {
            throw new IllegalArgumentException(
                    "Template not found: " + request.getTemplateKey() +
                            ". Available templates: " + templateMap.keySet()
            );
        }

        // Extract configuration from template and profile
        TemplateConfiguration config = configExtractor.extractConfiguration(consentTemplate, miiProfile);

        // Get consent date from request (or default to now)
        Date consentDate = request.getConsentDate() != null ?
                request.getConsentDate() : new Date();
        logger.info("Using consent date: {}", consentDate);

        Consent consent = new Consent();

        // 1. Profile
        consent.getMeta().addProfile(config.profileUrl);
        logger.info("Set profile: {}", config.profileUrl);

        // 2. Narrative
        Narrative narrative = narrativeBuilder.build(consentTemplate, request, config);
        consent.setText(narrative);

        // 3. Status
        consent.setStatus(Consent.ConsentState.ACTIVE);

        // 4. Scope
        consent.setScope(buildScope(config));

        // 5. Categories
        List<CodeableConcept> categories = buildCategories(consentTemplate, config);
        for (CodeableConcept category : categories) {
            consent.addCategory(category);
        }

        // 6. Patient
        consent.setPatient(new Reference(request.getPatientId()));
        logger.info("Set patient: {}", request.getPatientId());

        // 7. DateTime
        consent.setDateTime(consentDate);
        logger.info("Set consent date: {}", consentDate);

        // 8. Organization
        if (request.getOrganizationId() == null || request.getOrganizationId().isEmpty()) {
            throw new IllegalArgumentException("Organization ID is required");
        }
        consent.addOrganization(new Reference(request.getOrganizationId()));
        logger.info("Set organization: {}", request.getOrganizationId());

        // 9. Source Reference
        String sourceRef = extractSourceReference(consentTemplate, request);
        consent.setSource(new Reference(sourceRef));
        logger.info("Set source reference: {}", sourceRef);

        // 10. Policies
        List<Consent.ConsentPolicyComponent> policies = buildPolicies(consentTemplate, config);
        for (Consent.ConsentPolicyComponent policy : policies) {
            consent.addPolicy(policy);
        }

        // 11. Policy Rule
        consent.setPolicyRule(buildPolicyRule(config));

        // 12. Provisions - Pass consent date so period.start matches consent.dateTime
        Consent.ProvisionComponent mainProvision = provisionBuilder.buildProvisions(
                consentTemplate, request, config, consentDate);
        consent.setProvision(mainProvision);

        // 13. Signature (future)
        if (request.getSignature() != null) {
            logger.info("Signature received for later processing");
        }

        long acceptedCount = request.getModuleDecisions() != null ?
                request.getModuleDecisions().stream().filter(d -> "ACCEPTED".equals(d.getStatus())).count() : 0;
        long deniedCount = request.getModuleDecisions() != null ?
                request.getModuleDecisions().stream().filter(d -> "DECLINED".equals(d.getStatus())).count() : 0;

        logger.info("Consent populated successfully with {} accepted and {} denied modules",
                acceptedCount, deniedCount);

        return consent;
    }

    // ==========================================
    // Helper methods
    // ==========================================

    private CodeableConcept buildScope(TemplateConfiguration config) {
        CodeableConcept scope = new CodeableConcept();
        scope.addCoding()
                .setSystem(config.scopeSystem)
                .setCode(config.scopeCode)
                .setDisplay(config.scopeDisplay);
        return scope;
    }

    private List<CodeableConcept> buildCategories(ConsentTemplate consentTemplate, TemplateConfiguration config) {
        List<CodeableConcept> categories = new ArrayList<>();

        // LOINC category
        CodeableConcept loincCategory = new CodeableConcept();
        loincCategory.addCoding()
                .setSystem(config.loincCategorySystem)
                .setCode(config.loincCategoryCode)
                .setDisplay(config.loincCategoryDisplay);
        categories.add(loincCategory);
        logger.debug("Added LOINC category: {} ({})", config.loincCategoryCode, config.loincCategorySystem);

        // MII category
        CodeableConcept miiCategory = new CodeableConcept();
        String miiCategorySystem = "https://www.medizininformatik-initiative.de/fhir/modul-consent/CodeSystem/mii-cs-consent-version-modules";
        String miiCategoryDisplay = "MII Broad Consent";

        if (consentTemplate.getExternProperties() != null) {
            String[] props = consentTemplate.getExternProperties().split(";");
            for (String prop : props) {
                if (prop.startsWith("fhirConsentCategorySystem=")) {
                    miiCategorySystem = prop.substring("fhirConsentCategorySystem=".length());
                }
                if (prop.startsWith("fhirConsentCategoryDisplay=")) {
                    miiCategoryDisplay = prop.substring("fhirConsentCategoryDisplay=".length());
                }
            }
        }

        miiCategory.addCoding()
                .setSystem(miiCategorySystem)
                .setCode(config.consentCategory)
                .setDisplay(miiCategoryDisplay);
        categories.add(miiCategory);
        logger.info("Added MII category: {}", config.consentCategory);

        return categories;
    }

    private List<Consent.ConsentPolicyComponent> buildPolicies(ConsentTemplate consentTemplate, TemplateConfiguration config) {
        List<Consent.ConsentPolicyComponent> policies = new ArrayList<>();

        Consent.ConsentPolicyComponent policy = new Consent.ConsentPolicyComponent();
        policy.setUri(config.policyValueSet);
        policies.add(policy);
        logger.info("Added policy URI: {}", config.policyValueSet);

        return policies;
    }

    private CodeableConcept buildPolicyRule(TemplateConfiguration config) {
        CodeableConcept policyRule = new CodeableConcept();
        policyRule.addCoding()
                .setSystem(config.policyRuleSystem)
                .setCode(config.policyRuleCode)
                .setDisplay(config.policyRuleDisplay);
        return policyRule;
    }

    private String extractSourceReference(ConsentTemplate consentTemplate, ConsentRequest request) {
        if (request.getSourceReference() != null && !request.getSourceReference().isEmpty()) {
            return request.getSourceReference();
        }

        if (consentTemplate.getExternProperties() != null) {
            String[] props = consentTemplate.getExternProperties().split(";");
            for (String prop : props) {
                if (prop.startsWith("fhirSourceReference=")) {
                    return prop.substring("fhirSourceReference=".length());
                }
            }
        }

        return "QuestionnaireResponse/" + consentTemplate.getName().replaceAll("\\s+", "-");
    }

    // ==========================================
    // Public Methods
    // ==========================================

    public Set<String> getAvailableTemplateKeys() {
        return Collections.unmodifiableSet(templateMap.keySet());
    }

    public List<String> getAvailableTemplateNames() {
        return templateMap.values().stream()
                .map(t -> t.getDomainName() + " - " + t.getName() + " (" + t.getVersionLabel() + ")")
                .collect(java.util.stream.Collectors.toList());
    }

    public List<ModuleInfo> getModulesForTemplate(String templateKey) {
        if (templateKey == null || templateKey.isEmpty()) {
            throw new IllegalArgumentException("Template key cannot be null or empty");
        }

        ConsentTemplate consentTemplate = templateMap.get(templateKey);
        if (consentTemplate == null) {
            throw new IllegalArgumentException(
                    "Template not found: " + templateKey +
                            ". Available templates: " + templateMap.keySet()
            );
        }

        List<ModuleInfo> moduleInfos = new ArrayList<>();
        if (consentTemplate.getModulesAssignedConsentModule() != null) {
            List<ModuleAssignment> sortedModules = new ArrayList<>(consentTemplate.getModulesAssignedConsentModule());
            sortedModules.sort(Comparator.comparingInt(ModuleAssignment::getOrderNumber));

            for (ModuleAssignment assignment : sortedModules) {
                ConsentModule module = moduleResolver.getModule(assignment.getModuleKey());
                if (module != null) {
                    ModuleInfo info = new ModuleInfo();
                    info.setModuleKey(assignment.getModuleKey());
                    info.setModuleName(module.getName());
                    info.setModuleLabel(module.getLabel());
                    info.setMandatory(assignment.isMandatory());
                    info.setOrderNumber(assignment.getOrderNumber());
                    info.setDefaultStatus(assignment.isMandatory() ? "ACCEPTED" : "DECLINED");
                    moduleInfos.add(info);
                }
            }
        }
        return moduleInfos;
    }

    // ==========================================
    // Private helpers
    // ==========================================

    private void validateInputs() {
        if (template == null) {
            throw new IllegalArgumentException("Consent template cannot be null");
        }
        if (template.getDomain() == null) {
            throw new IllegalArgumentException("Template domain is missing");
        }
        if (template.getTemplatesConsentTemplate() == null || template.getTemplatesConsentTemplate().isEmpty()) {
            throw new IllegalArgumentException("Template has no consent templates defined");
        }
    }

    private void buildMaps() {
        if (template.getPoliciesConsentPolicy() != null) {
            for (ConsentPolicy policy : template.getPoliciesConsentPolicy()) {
                String key = policy.getDomainName() + ";" + policy.getName() + ";" + policy.getVersion();
                policyMap.put(key, policy);
                logger.debug("Mapped policy: {}", key);
            }
        }

        if (template.getModulesConsentModule() != null) {
            for (ConsentModule module : template.getModulesConsentModule()) {
                String key = module.getDomainName() + ";" + module.getName() + ";" + module.getVersion();
                moduleMap.put(key, module);
                logger.debug("Mapped module: {}", key);
            }
        }

        if (template.getTemplatesConsentTemplate() != null) {
            for (ConsentTemplate consentTemplate : template.getTemplatesConsentTemplate()) {
                String key = consentTemplate.getDomainName() + ";" + consentTemplate.getName() + ";" + consentTemplate.getVersion();
                templateMap.put(key, consentTemplate);
                logger.debug("Mapped template: {}", key);
            }
        }
    }

    // Update the validateRequest method to handle null decisions properly
    private void validateRequest(ConsentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Consent request cannot be null");
        }
        if (request.getTemplateKey() == null || request.getTemplateKey().isEmpty()) {
            throw new IllegalArgumentException("Template key is required");
        }
        if (request.getPatientId() == null || request.getPatientId().isEmpty()) {
            throw new IllegalArgumentException("Patient ID is required");
        }
        if (request.getOrganizationId() == null || request.getOrganizationId().isEmpty()) {
            throw new IllegalArgumentException("Organization ID is required");
        }
        if (request.getModuleDecisions() == null || request.getModuleDecisions().isEmpty()) {
            throw new IllegalArgumentException("Module decisions are required");
        }

        // Validate each decision has a module key
        for (ModuleDecision decision : request.getModuleDecisions()) {
            if (decision == null) {
                throw new IllegalArgumentException("Module decision cannot be null");
            }
            if (decision.getModuleKey() == null || decision.getModuleKey().isEmpty()) {
                throw new IllegalArgumentException("Module decision must have a module key");
            }
            if (decision.getStatus() == null) {
                throw new IllegalArgumentException("Module decision must have a status");
            }
        }
    }

}