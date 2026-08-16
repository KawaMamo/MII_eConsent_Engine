package org.example.consent.populator;

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
 * FIXED: Untrimmed externProperties tokens - now using Pattern.split with trim
 * FIXED: Loose status matching - using equalsIgnoreCase
 * FIXED: Null safety for status checks
 * FIXED: Incomplete scope initialization with null checks
 */
public class ConsentPopulator {

    private static final Logger logger = LoggerFactory.getLogger(ConsentPopulator.class);

    // FIXED: Pattern for splitting externProperties with optional whitespace
    private static final Pattern EXTERN_PROPERTIES_SPLIT = Pattern.compile(";\\s*");

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

    // ==========================================
    // Main Population Method
    // ==========================================

    /**
     * Populate a Consent resource from the template and user decisions
     *
     * @param request The user's consent request with decisions
     * @param miiProfile The MII Consent profile (StructureDefinition)
     * @return Populated Consent resource
     * @throws IllegalArgumentException if request is null or missing required fields
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

        // 4. Scope - FIXED: Null checks applied
        CodeableConcept scope = buildScope(config);
        consent.setScope(scope);

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

        // FIXED: Null-safe status counting with equalsIgnoreCase
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

    // ==========================================
    // Helper methods
    // ==========================================

    /**
     * Build scope CodeableConcept with null safety
     * FIXED: Null checks on config scope values
     */
    private CodeableConcept buildScope(TemplateConfiguration config) {
        CodeableConcept scope = new CodeableConcept();

        // FIXED: Null checks to avoid empty FHIR codings
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
     * FIXED: Uses EXTERN_PROPERTIES_SPLIT for proper trimming
     */
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

        // FIXED: Use EXTERN_PROPERTIES_SPLIT for proper trimming
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

    /**
     * Extract source reference from template or request
     * FIXED: Uses EXTERN_PROPERTIES_SPLIT for proper trimming
     */
    private String extractSourceReference(ConsentTemplate consentTemplate, ConsentRequest request) {
        if (request.getSourceReference() != null && !request.getSourceReference().isEmpty()) {
            return request.getSourceReference();
        }

        // FIXED: Use EXTERN_PROPERTIES_SPLIT for proper trimming
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

    /**
     * Get all available template keys (immutable view)
     */
    public Set<String> getAvailableTemplateKeys() {
        return Collections.unmodifiableSet(templateMap.keySet());
    }

    /**
     * Get all available template names
     */
    public List<String> getAvailableTemplateNames() {
        return templateMap.values().stream()
                .map(t -> t.getDomainName() + " - " + t.getName() + " (" + t.getVersionLabel() + ")")
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Get all modules from a template with their default status
     */
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

    /**
     * Validate that the request has all required fields
     * FIXED: Added null safety for module decisions
     */
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

        // FIXED: Validate each decision has required fields
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
            // FIXED: Validate status values (case-insensitive)
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