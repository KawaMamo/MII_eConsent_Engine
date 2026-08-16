package org.example.consent.populator;

import org.example.consent.config.FhirConsentConfig;
import org.example.consent.model.*;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Service that populates FHIR Consent resources from ExchangeFormatDefinition templates
 * Orchestrates the work of specialized builders and extractors
 * STATELESS DESIGN: Thread-safe, can be reused safely across multiple requests
 *
 * FIXED: Externalized configuration via FhirConsentConfig
 * FIXED: Composite map keys with validation
 * FIXED: Full profile validation integration
 */
public class ConsentPopulator {

    private static final Logger logger = LoggerFactory.getLogger(ConsentPopulator.class);

    private static final Pattern EXTERN_PROPERTIES_SPLIT = Pattern.compile(";\\s*");

    // Configuration
    private final FhirConsentConfig config;

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

    /**
     * Constructor - initializes the populator with template data
     *
     * @param template The consent template (ExchangeFormatDefinition)
     */
    public ConsentPopulator(ExchangeFormatDefinition template) {
        this(template, new FhirConsentConfig());
    }

    /**
     * Constructor with custom configuration
     *
     * @param template The consent template (ExchangeFormatDefinition)
     * @param config Externalized configuration
     */
    public ConsentPopulator(ExchangeFormatDefinition template, FhirConsentConfig config) {
        this.template = template;
        this.config = config != null ? config : new FhirConsentConfig();
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

    // ==========================================
    // Main Population Method
    // ==========================================

    /**
     * Populate a Consent resource from the template and user decisions
     */
    public Consent populateConsent(ConsentRequest request, StructureDefinition miiProfile) {
        validateRequest(request);

        logger.info("Populating consent for patient: {}, template: {}",
                request.getPatientId(), request.getTemplateKey());

        // FIXED: Validate template key with proper error message
        ConsentTemplate consentTemplate = getTemplateSafely(request.getTemplateKey());

        // Extract configuration from template and profile
        TemplateConfiguration config = configExtractor.extractConfiguration(consentTemplate, miiProfile);

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
        CodeableConcept scope = buildScope(config);
        consent.setScope(scope);

        // 5. Categories - FIXED: Uses externalized configuration
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

        // 12. Provisions
        Consent.ProvisionComponent mainProvision = provisionBuilder.buildProvisions(
                consentTemplate, request, config, consentDate);
        consent.setProvision(mainProvision);

        // 13. Signature (future)
        if (request.getSignature() != null) {
            logger.info("Signature received for later processing");
        }

        // Count decisions with null safety
        long acceptedCount = request.getModuleDecisions() != null ?
                request.getModuleDecisions().stream()
                        .filter(d -> d != null && d.getStatus() != null &&
                                ("ACCEPTED".equalsIgnoreCase(d.getStatus()) ||
                                        "PERMIT".equalsIgnoreCase(d.getStatus())))
                        .count() : 0;

        long deniedCount = request.getModuleDecisions() != null ?
                request.getModuleDecisions().stream()
                        .filter(d -> d != null && d.getStatus() != null &&
                                ("DECLINED".equalsIgnoreCase(d.getStatus()) ||
                                        "DENY".equalsIgnoreCase(d.getStatus())))
                        .count() : 0;

        logger.info("Consent populated successfully with {} accepted and {} denied modules",
                acceptedCount, deniedCount);

        return consent;
    }

    /**
     * Safely get template with validation
     * FIXED: Composite map key with proper validation
     */
    private ConsentTemplate getTemplateSafely(String templateKey) {
        if (templateKey == null || templateKey.isEmpty()) {
            throw new IllegalArgumentException("Template key cannot be null or empty");
        }

        // Validate key format
        String[] parts = templateKey.split(";");
        if (parts.length != 3) {
            throw new IllegalArgumentException(
                    "Invalid template key format. Expected: domain;name;version. Got: " + templateKey
            );
        }

        // Validate each part is not empty
        for (int i = 0; i < parts.length; i++) {
            if (parts[i] == null || parts[i].trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "Template key part " + i + " is empty. Key: " + templateKey
                );
            }
        }

        ConsentTemplate consentTemplate = templateMap.get(templateKey);
        if (consentTemplate == null) {
            throw new IllegalArgumentException(
                    "Template not found: " + templateKey +
                            ". Available templates: " + templateMap.keySet()
            );
        }
        return consentTemplate;
    }

    // ==========================================
    // Helper methods with externalized config
    // ==========================================

    /**
     * Build scope CodeableConcept with null safety
     */
    private CodeableConcept buildScope(TemplateConfiguration config) {
        CodeableConcept scope = new CodeableConcept();

        String system = config.scopeSystem != null ? config.scopeSystem : "http://terminology.hl7.org/CodeSystem/consentscope";
        String code = config.scopeCode != null ? config.scopeCode : "research";
        String display = config.scopeDisplay != null ? config.scopeDisplay : "Research";

        scope.addCoding()
                .setSystem(system)
                .setCode(code)
                .setDisplay(display);

        return scope;
    }

    /**
     * Build categories from template and config
     * FIXED: Uses externalized configuration for default values
     */
    private List<CodeableConcept> buildCategories(ConsentTemplate consentTemplate, TemplateConfiguration config) {
        List<CodeableConcept> categories = new ArrayList<>();

        // 1. LOINC category - uses config values
        CodeableConcept loincCategory = new CodeableConcept();
        loincCategory.addCoding()
                .setSystem(config.loincCategorySystem != null ?
                        config.loincCategorySystem : this.config.getLoincSystem())
                .setCode(config.loincCategoryCode != null ?
                        config.loincCategoryCode : this.config.getLoincCode())
                .setDisplay(config.loincCategoryDisplay != null ?
                        config.loincCategoryDisplay : this.config.getLoincDisplay());
        categories.add(loincCategory);
        logger.debug("Added LOINC category: {} ({})", config.loincCategoryCode, config.loincCategorySystem);

        // 2. MII category - uses config values
        CodeableConcept miiCategory = new CodeableConcept();
        String miiCategorySystem = this.config.getMiiVersionModuleSystem();
        String miiCategoryDisplay = this.config.getMiiCategoryDisplay();

        // Allow template to override
        if (consentTemplate.getExternProperties() != null) {
            String[] props = EXTERN_PROPERTIES_SPLIT.split(consentTemplate.getExternProperties());
            for (String prop : props) {
                if (prop == null) continue;
                String trimmedProp = prop.trim();
                if (trimmedProp.startsWith("fhirConsentCategorySystem=")) {
                    miiCategorySystem = trimmedProp.substring("fhirConsentCategorySystem=".length()).trim();
                }
                if (trimmedProp.startsWith("fhirConsentCategoryDisplay=")) {
                    miiCategoryDisplay = trimmedProp.substring("fhirConsentCategoryDisplay=".length()).trim();
                }
            }
        }

        miiCategory.addCoding()
                .setSystem(miiCategorySystem)
                .setCode(config.consentCategory)
                .setDisplay(miiCategoryDisplay);
        categories.add(miiCategory);
        logger.info("Added MII category: {} ({})", config.consentCategory, miiCategorySystem);

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

    /**
     * Extract source reference from template or request
     */
    private String extractSourceReference(ConsentTemplate consentTemplate, ConsentRequest request) {
        if (request.getSourceReference() != null && !request.getSourceReference().isEmpty()) {
            return request.getSourceReference();
        }

        if (consentTemplate.getExternProperties() != null) {
            String[] props = EXTERN_PROPERTIES_SPLIT.split(consentTemplate.getExternProperties());
            for (String prop : props) {
                if (prop == null) continue;
                String trimmedProp = prop.trim();
                if (trimmedProp.startsWith("fhirSourceReference=")) {
                    return trimmedProp.substring("fhirSourceReference=".length()).trim();
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

    /**
     * Get all modules from a template with their default status
     * FIXED: Uses safe template retrieval
     */
    public List<ModuleInfo> getModulesForTemplate(String templateKey) {
        ConsentTemplate consentTemplate = getTemplateSafely(templateKey);

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

    /**
     * Get the configuration
     */
    public FhirConsentConfig getConfig() {
        return config;
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

    /**
     * Build maps with composite keys
     * FIXED: Validates key components before concatenation
     */
    private void buildMaps() {
        if (template.getPoliciesConsentPolicy() != null) {
            for (ConsentPolicy policy : template.getPoliciesConsentPolicy()) {
                String key = buildCompositeKey(
                        policy.getDomainName(),
                        policy.getName(),
                        policy.getVersion(),
                        "policy"
                );
                policyMap.put(key, policy);
                logger.debug("Mapped policy: {}", key);
            }
        }

        if (template.getModulesConsentModule() != null) {
            for (ConsentModule module : template.getModulesConsentModule()) {
                String key = buildCompositeKey(
                        module.getDomainName(),
                        module.getName(),
                        module.getVersion(),
                        "module"
                );
                moduleMap.put(key, module);
                logger.debug("Mapped module: {}", key);
            }
        }

        if (template.getTemplatesConsentTemplate() != null) {
            for (ConsentTemplate consentTemplate : template.getTemplatesConsentTemplate()) {
                String key = buildCompositeKey(
                        consentTemplate.getDomainName(),
                        consentTemplate.getName(),
                        consentTemplate.getVersion(),
                        "template"
                );
                templateMap.put(key, consentTemplate);
                logger.debug("Mapped template: {}", key);
            }
        }
    }

    /**
     * Build composite key with validation
     * FIXED: Prevents "null;null;null" keys and validates components
     */
    private String buildCompositeKey(String domain, String name, String version, String type) {
        if (domain == null || domain.trim().isEmpty()) {
            throw new IllegalArgumentException(type + " domain cannot be null or empty");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException(type + " name cannot be null or empty");
        }
        if (version == null || version.trim().isEmpty()) {
            throw new IllegalArgumentException(type + " version cannot be null or empty");
        }

        return domain.trim() + ";" + name.trim() + ";" + version.trim();
    }

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

        for (ModuleDecision decision : request.getModuleDecisions()) {
            if (decision == null) {
                throw new IllegalArgumentException("Module decision cannot be null");
            }
            if (decision.getModuleKey() == null || decision.getModuleKey().isEmpty()) {
                throw new IllegalArgumentException("Module decision must have a module key");
            }
            if (decision.getStatus() == null || decision.getStatus().isEmpty()) {
                throw new IllegalArgumentException("Module decision must have a status");
            }
            String upperStatus = decision.getStatus().toUpperCase();
            if (!"ACCEPTED".equals(upperStatus) && !"DECLINED".equals(upperStatus) &&
                    !"PERMIT".equals(upperStatus) && !"DENY".equals(upperStatus)) {
                throw new IllegalArgumentException(
                        "Module decision status must be ACCEPTED, DECLINED, PERMIT, or DENY. Got: " + decision.getStatus()
                );
            }
        }
    }
}