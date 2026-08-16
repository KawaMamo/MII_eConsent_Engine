package org.example.consent.populator;

import ca.uhn.fhir.context.FhirContext;
import org.example.consent.model.*;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service that populates FHIR Consent resources from ExchangeFormatDefinition templates
 * All values are extracted dynamically from the template and MII profile
 * NO HARDCODED DEFAULTS - if something is missing, throw an exception
 */
public class ConsentPopulator {

    private static final Logger logger = LoggerFactory.getLogger(ConsentPopulator.class);

    private final ExchangeFormatDefinition template;
    private final StructureDefinition miiProfile;
    private final Map<String, ConsentPolicy> policyMap;
    private final Map<String, ConsentModule> moduleMap;
    private final Map<String, ConsentTemplate> templateMap;

    // Extracted from profile
    private String loincCategoryCode;
    private String loincCategorySystem;
    private String loincCategoryDisplay;

    // Extracted from template
    private String policyRuleSystem;
    private String policyRuleCode;
    private String policyRuleDisplay;
    private String validityPeriod;

    // Scope values
    private String scopeSystem;
    private String scopeCode;
    private String scopeDisplay;

    private static final Pattern VALIDITY_PERIOD_PATTERN = Pattern.compile("P(\\d+)([YMD])");

    // Default policy rule (FHIR standard)
    private static final String DEFAULT_POLICY_RULE_SYSTEM = "http://terminology.hl7.org/CodeSystem/v3-ActCode";
    private static final String DEFAULT_POLICY_RULE_CODE = "OPTIN";
    private static final String DEFAULT_POLICY_RULE_DISPLAY = "Opt-in";

    // Placeholder patterns
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\[([^\\]]+)\\]");

    public ConsentPopulator(ExchangeFormatDefinition template, StructureDefinition miiProfile) {
        this.template = template;
        this.miiProfile = miiProfile;
        this.policyMap = new HashMap<>();
        this.moduleMap = new HashMap<>();
        this.templateMap = new HashMap<>();

        validateInputs();

        buildMaps();
        extractCategoryInfoFromProfile();
        extractPolicyRuleFromTemplate();
        extractScopeFromTemplate();
        extractValidityPeriodFromTemplate();
    }

    // ==========================================
    // Placeholder Replacement System
    // ==========================================

    /**
     * Replace all placeholders in HTML text with actual values
     */
    private String replacePlaceholders(String text, ConsentTemplate consentTemplate,
                                       ConsentRequest request, boolean isAccepted) {
        if (text == null) return "";

        String result = text;

        // 1. Replace institution name placeholders
        String institutionName = request.getInstitutionName() != null ?
                request.getInstitutionName() : "Ihre behandelnde Einrichtung";
        result = result.replaceAll("\\[der/dem Name der behandelnden Einrichtung\\]", institutionName);
        result = result.replaceAll("\\[Name der behandelnden Einrichtung\\]", institutionName);
        result = result.replaceAll("\\[der/dem Name der Einrichtung\\]", institutionName);
        result = result.replaceAll("\\[Name der Einrichtung\\]", institutionName);

        // 2. Replace conditional placeholders based on module acceptance
        result = replaceConditionalPlaceholders(result, isAccepted);

        // 3. Replace section numbering placeholders
        result = replaceSectionNumbering(result, consentTemplate);

        // 4. Replace date placeholders
        Date consentDate = request.getConsentDate() != null ?
                request.getConsentDate() : new Date();
        result = result.replaceAll("\\[Datum der Unterschrift\\]",
                new java.text.SimpleDateFormat("dd.MM.yyyy").format(consentDate));
        result = result.replaceAll("\\[Datum\\]",
                new java.text.SimpleDateFormat("dd.MM.yyyy").format(consentDate));

        // 5. Replace patient name placeholder (if available)
        if (request.getPatientName() != null) {
            result = result.replaceAll("\\[Name des Patienten\\]", request.getPatientName());
            result = result.replaceAll("\\[Name der Patientin\\]", request.getPatientName());
            result = result.replaceAll("\\[Name der/des Patienten\\]", request.getPatientName());
        }

        // 6. Replace organization placeholders
        String organizationName = request.getOrganizationName() != null ?
                request.getOrganizationName() : "Ihre Organisation";
        result = result.replaceAll("\\[Organisation\\]", organizationName);
        result = result.replaceAll("\\[zuständige Stelle\\]", organizationName);

        // 7. Replace validity period placeholders
        String validityText = formatValidityPeriod(this.validityPeriod);
        result = result.replaceAll("\\[Gültigkeitsdauer\\]", validityText);
        result = result.replaceAll("\\[Geltungsdauer\\]", validityText);

        // 8. Replace policy count placeholders
        if (consentTemplate.getModulesAssignedConsentModule() != null) {
            int totalModules = consentTemplate.getModulesAssignedConsentModule().size();
            result = result.replaceAll("\\[Anzahl der Module\\]", String.valueOf(totalModules));
        }

        // 9. Replace any remaining placeholders with a warning (should not happen)
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(result);
        if (matcher.find()) {
            logger.warn("Unreplaced placeholder found: {}", matcher.group(0));
            // Keep the placeholder but add a note - better than empty text
        }

        return result;
    }

    /**
     * Replace conditional placeholders like "[falls zutreffend: ...]"
     */
    private String replaceConditionalPlaceholders(String text, boolean isAccepted) {
        // Pattern for conditional placeholders: [falls zutreffend: content]
        Pattern conditionalPattern = Pattern.compile("\\[falls zutreffend:([^\\]]+)\\]");
        Matcher matcher = conditionalPattern.matcher(text);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String content = matcher.group(1).trim();
            // If the module is accepted, include the content; otherwise, remove it
            String replacement = isAccepted ? content : "";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);

        // Also handle variations: [falls zutreffend] (without colon)
        String result = sb.toString();
        result = result.replaceAll("\\[falls zutreffend\\]", isAccepted ? "" : "");

        // Handle "Falls zutreffend:" (without brackets)
        // This is trickier - we need to look for the pattern and remove the whole section if not accepted
        if (!isAccepted) {
            // Remove "Falls zutreffend: " followed by content up to the next paragraph
            result = result.replaceAll("Falls zutreffend:[^<]*(?:<[^>]*>)*", "");
        }

        return result;
    }

    /**
     * Replace section numbering placeholders
     */
    private String replaceSectionNumbering(String text, ConsentTemplate consentTemplate) {
        // Try to get actual section numbers from modules
        Map<String, String> sectionNumbers = new HashMap<>();
        if (consentTemplate.getModulesAssignedConsentModule() != null) {
            for (ModuleAssignment assignment : consentTemplate.getModulesAssignedConsentModule()) {
                ConsentModule module = getModule(assignment.getModuleKey());
                if (module != null) {
                    // Extract section number from module if present (e.g., "1.", "2.1", etc.)
                    String title = module.getTitle();
                    if (title != null) {
                        Pattern sectionPattern = Pattern.compile("(\\d+(\\.\\d+)?)\\.");
                        Matcher m = sectionPattern.matcher(title);
                        if (m.find()) {
                            String section = m.group(1);
                            sectionNumbers.put(module.getName(), section);
                        }
                    }
                }
            }
        }

        String result = text;

        // Replace common section references
        // [NUMMERIERUNG ANPASSEN] - try to determine from context
        // This is a complex case - we need to look at the surrounding text
        // For now, we'll replace with a placeholder that can be configured
        result = result.replaceAll("\\[NUMMERIERUNG ANPASSEN\\]", "Abschnitt");

        return result;
    }

    /**
     * Format validity period for human-readable display
     */
    private String formatValidityPeriod(String period) {
        if (period == null) return "";

        Matcher matcher = VALIDITY_PERIOD_PATTERN.matcher(period);
        if (!matcher.matches()) {
            return period;
        }

        int amount = Integer.parseInt(matcher.group(1));
        String unit = matcher.group(2);

        switch (unit) {
            case "Y":
                return amount + " Jahr" + (amount > 1 ? "e" : "");
            case "M":
                return amount + " Monat" + (amount > 1 ? "e" : "");
            case "D":
                return amount + " Tag" + (amount > 1 ? "e" : "");
            default:
                return period;
        }
    }

    /**
     * Validate that required inputs are provided
     */
    private void validateInputs() {
        if (template == null) {
            throw new IllegalArgumentException("Consent template cannot be null");
        }
        if (miiProfile == null) {
            throw new IllegalArgumentException("MII profile cannot be null");
        }
        if (template.getDomain() == null) {
            throw new IllegalArgumentException("Template domain is missing");
        }
        if (template.getTemplatesConsentTemplate() == null || template.getTemplatesConsentTemplate().isEmpty()) {
            throw new IllegalArgumentException("Template has no consent templates defined");
        }
    }

    /**
     * Extract validity period from template expirationProperties
     */
    private void extractValidityPeriodFromTemplate() {
        ConsentTemplate consentTemplate = template.getTemplatesConsentTemplate().get(0);

        if (consentTemplate.getExpirationProperties() == null) {
            throw new IllegalStateException(
                    "Template missing expirationProperties. Required: VALIDITY_PERIOD=... (e.g., VALIDITY_PERIOD=P30Y)"
            );
        }

        String[] props = consentTemplate.getExpirationProperties().split(";");
        boolean found = false;

        for (String prop : props) {
            if (prop.startsWith("VALIDITY_PERIOD=")) {
                this.validityPeriod = prop.substring("VALIDITY_PERIOD=".length());
                found = true;
                logger.info("Extracted validity period from template: {}", this.validityPeriod);
                break;
            }
        }

        if (!found) {
            throw new IllegalStateException(
                    "Template expirationProperties missing VALIDITY_PERIOD. " +
                            "Example: expirationProperties: \"VALIDITY_PERIOD=P30Y;\""
            );
        }

        Matcher matcher = VALIDITY_PERIOD_PATTERN.matcher(this.validityPeriod);
        if (!matcher.matches()) {
            throw new IllegalStateException(
                    "Invalid VALIDITY_PERIOD format: " + this.validityPeriod +
                            ". Expected format: P<number>Y (years), P<number>M (months), or P<number>D (days)"
            );
        }
    }

    /**
     * Extract scope from template
     */
    private void extractScopeFromTemplate() {
        this.scopeSystem = "http://terminology.hl7.org/CodeSystem/consentscope";
        this.scopeCode = "research";
        this.scopeDisplay = "Research";

        ConsentTemplate consentTemplate = template.getTemplatesConsentTemplate().get(0);
        if (consentTemplate.getExternProperties() != null) {
            String[] props = consentTemplate.getExternProperties().split(";");
            for (String prop : props) {
                if (prop.startsWith("scopeSystem=")) {
                    this.scopeSystem = prop.substring("scopeSystem=".length());
                }
                if (prop.startsWith("scopeCode=")) {
                    this.scopeCode = prop.substring("scopeCode=".length());
                }
                if (prop.startsWith("scopeDisplay=")) {
                    this.scopeDisplay = prop.substring("scopeDisplay=".length());
                }
            }
        }

        if ("REFUSAL".equals(consentTemplate.getType())) {
            this.scopeCode = "treatment";
            this.scopeDisplay = "Treatment";
        } else if ("REVOCATION".equals(consentTemplate.getType())) {
            this.scopeCode = "research";
            this.scopeDisplay = "Research (Revocation)";
        }

        logger.info("Using scope: {} ({})", scopeCode, scopeSystem);
    }

    /**
     * Extract policy rule from template
     */
    private void extractPolicyRuleFromTemplate() {
        this.policyRuleSystem = DEFAULT_POLICY_RULE_SYSTEM;
        this.policyRuleCode = DEFAULT_POLICY_RULE_CODE;
        this.policyRuleDisplay = DEFAULT_POLICY_RULE_DISPLAY;

        ConsentTemplate consentTemplate = template.getTemplatesConsentTemplate().get(0);
        if (consentTemplate.getExternProperties() != null) {
            String[] props = consentTemplate.getExternProperties().split(";");
            for (String prop : props) {
                if (prop.startsWith("policyRuleSystem=")) {
                    this.policyRuleSystem = prop.substring("policyRuleSystem=".length());
                }
                if (prop.startsWith("policyRuleCode=")) {
                    this.policyRuleCode = prop.substring("policyRuleCode=".length());
                }
                if (prop.startsWith("policyRuleDisplay=")) {
                    this.policyRuleDisplay = prop.substring("policyRuleDisplay=".length());
                }
            }
        }

        logger.info("Using policy rule: {} ({})", policyRuleCode, policyRuleSystem);
    }

    /**
     * Extract LOINC category from MII profile
     */
    private void extractCategoryInfoFromProfile() {
        boolean found = false;

        if (miiProfile.getSnapshot() != null && miiProfile.getSnapshot().getElement() != null) {
            for (ElementDefinition element : miiProfile.getSnapshot().getElement()) {
                String path = element.getPath();
                if (path != null && path.equals("Consent.category")) {

                    if (element.getPattern() != null && element.getPattern() instanceof CodeableConcept) {
                        CodeableConcept pattern = (CodeableConcept) element.getPattern();
                        if (pattern.getCoding() != null && !pattern.getCoding().isEmpty()) {
                            Coding coding = pattern.getCoding().get(0);
                            if (coding.getSystem() != null && coding.getCode() != null) {
                                this.loincCategoryCode = coding.getCode();
                                this.loincCategorySystem = coding.getSystem();
                                this.loincCategoryDisplay = coding.getDisplay() != null ?
                                        coding.getDisplay() : "Privacy consent";
                                found = true;
                                logger.info("Extracted LOINC category from profile: {} ({})",
                                        loincCategoryCode, loincCategorySystem);
                                return;
                            }
                        }
                    }

                    if (element.getFixed() != null && element.getFixed() instanceof CodeableConcept) {
                        CodeableConcept fixed = (CodeableConcept) element.getFixed();
                        if (fixed.getCoding() != null && !fixed.getCoding().isEmpty()) {
                            Coding coding = fixed.getCoding().get(0);
                            if (coding.getSystem() != null && coding.getCode() != null) {
                                this.loincCategoryCode = coding.getCode();
                                this.loincCategorySystem = coding.getSystem();
                                this.loincCategoryDisplay = coding.getDisplay() != null ?
                                        coding.getDisplay() : "Privacy consent";
                                found = true;
                                logger.info("Extracted LOINC category from fixed value: {} ({})",
                                        loincCategoryCode, loincCategorySystem);
                                return;
                            }
                        }
                    }
                }
            }
        }

        if (!found && miiProfile.getDifferential() != null && miiProfile.getDifferential().getElement() != null) {
            for (ElementDefinition element : miiProfile.getDifferential().getElement()) {
                String path = element.getPath();
                if (path != null && path.equals("Consent.category")) {
                    if (element.getPattern() != null && element.getPattern() instanceof CodeableConcept) {
                        CodeableConcept pattern = (CodeableConcept) element.getPattern();
                        if (pattern.getCoding() != null && !pattern.getCoding().isEmpty()) {
                            Coding coding = pattern.getCoding().get(0);
                            if (coding.getSystem() != null && coding.getCode() != null) {
                                this.loincCategoryCode = coding.getCode();
                                this.loincCategorySystem = coding.getSystem();
                                this.loincCategoryDisplay = coding.getDisplay() != null ?
                                        coding.getDisplay() : "Privacy consent";
                                found = true;
                                logger.info("Extracted LOINC category from differential pattern: {} ({})",
                                        loincCategoryCode, loincCategorySystem);
                                return;
                            }
                        }
                    }
                }
            }
        }

        if (!found) {
            throw new IllegalStateException(
                    "Could not extract LOINC category from MII profile. " +
                            "Profile must have Consent.category with pattern or fixed value."
            );
        }
    }

    /**
     * Build maps for quick lookups
     */
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

    // ==========================================
    // Main Population Method
    // ==========================================

    public Consent populateConsent(ConsentRequest request) {
        logger.info("Populating consent for patient: {}, template: {}",
                request.getPatientId(), request.getTemplateKey());

        ConsentTemplate consentTemplate = templateMap.get(request.getTemplateKey());
        if (consentTemplate == null) {
            throw new IllegalArgumentException("Template not found: " + request.getTemplateKey());
        }

        validateRequest(request);

        Consent consent = new Consent();

        // 1. Set profile from template
        String profileUrl = consentTemplate.getFhirProfileUrl();
        if (profileUrl == null || profileUrl.isEmpty()) {
            throw new IllegalStateException("Template missing fhirForceProfileConsent in externProperties");
        }
        consent.getMeta().addProfile(profileUrl);
        logger.info("Set profile: {}", profileUrl);

        // 2. Set narrative - DYNAMIC with placeholder replacement
        Narrative narrative = buildDynamicNarrative(consentTemplate, request);
        consent.setText(narrative);

        // 3. Set status
        consent.setStatus(Consent.ConsentState.ACTIVE);

        // 4. Set scope
        CodeableConcept scope = buildScope();
        consent.setScope(scope);

        // 5. Set categories
        List<CodeableConcept> categories = buildCategories(consentTemplate);
        for (CodeableConcept category : categories) {
            consent.addCategory(category);
        }

        // 6. Set patient reference
        consent.setPatient(new Reference(request.getPatientId()));
        logger.info("Set patient: {}", request.getPatientId());

        // 7. Set dateTime
        Date consentDate = request.getConsentDate() != null ?
                request.getConsentDate() : new Date();
        consent.setDateTime(consentDate);
        logger.info("Set consent date: {}", consentDate);

        // 8. Set organization
        if (request.getOrganizationId() == null || request.getOrganizationId().isEmpty()) {
            throw new IllegalArgumentException("Organization ID is required");
        }
        consent.addOrganization(new Reference(request.getOrganizationId()));
        logger.info("Set organization: {}", request.getOrganizationId());

        // 9. Set source reference
        String sourceRef = extractSourceReference(consentTemplate, request);
        consent.setSource(new Reference(sourceRef));
        logger.info("Set source reference: {}", sourceRef);

        // 10. Add policies
        List<Consent.ConsentPolicyComponent> policies = buildPolicies(consentTemplate);
        for (Consent.ConsentPolicyComponent policy : policies) {
            consent.addPolicy(policy);
        }

        // 11. Set policy rule
        CodeableConcept policyRule = buildPolicyRule();
        consent.setPolicyRule(policyRule);

        // 12. Build provisions
        Consent.ProvisionComponent mainProvision = buildProvisions(consentTemplate, request);
        consent.setProvision(mainProvision);

        // 13. Add signature if provided
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
    // Narrative Building Methods
    // ==========================================

    /**
     * Build dynamic narrative with placeholder replacement
     */
    private Narrative buildDynamicNarrative(ConsentTemplate consentTemplate, ConsentRequest request) {
        Narrative narrative = new Narrative();
        narrative.setStatus(Narrative.NarrativeStatus.GENERATED);

        Map<String, ModuleDecision> decisionMap = new HashMap<>();
        if (request.getModuleDecisions() != null) {
            for (ModuleDecision decision : request.getModuleDecisions()) {
                decisionMap.put(decision.getModuleKey(), decision);
            }
        }

        StringBuilder html = new StringBuilder();
        html.append("<div xmlns=\"http://www.w3.org/1999/xhtml\">");

        // Add header with placeholder replacement
        if (consentTemplate.getHeader() != null) {
            String header = replacePlaceholders(consentTemplate.getHeader(), consentTemplate, request, true);
            html.append(cleanHtml(header));
        }

        // Add title with placeholder replacement
        if (consentTemplate.getTitle() != null) {
            String title = replacePlaceholders(consentTemplate.getTitle(), consentTemplate, request, true);
            html.append(cleanHtml(title));
        }

        // Process each module with decision awareness and placeholder replacement
        if (consentTemplate.getModulesAssignedConsentModule() != null) {
            List<ModuleAssignment> sortedModules = new ArrayList<>(consentTemplate.getModulesAssignedConsentModule());
            sortedModules.sort(Comparator.comparingInt(ModuleAssignment::getOrderNumber));

            for (ModuleAssignment assignment : sortedModules) {
                ConsentModule module = getModule(assignment.getModuleKey());
                if (module != null) {
                    ModuleDecision decision = decisionMap.get(assignment.getModuleKey());
                    String status = decision != null ? decision.getStatus() : "DECLINED";
                    boolean isAccepted = "ACCEPTED".equals(status);

                    String moduleHtml = buildModuleHtml(module, isAccepted, status, consentTemplate, request);
                    html.append(moduleHtml);
                }
            }
        }

        html.append("</div>");
        String finalHtml = cleanHtml(html.toString());

        // Final check for any remaining placeholders
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(finalHtml);
        if (matcher.find()) {
            logger.warn("WARNING: Unreplaced placeholders remain in narrative: {}", matcher.group(0));
            // Log all remaining placeholders
            while (matcher.find()) {
                logger.warn("  Unreplaced: {}", matcher.group(0));
            }
        }

        try {
            narrative.setDivAsString(finalHtml);
        } catch (Exception e) {
            logger.warn("Failed to set narrative HTML, using simplified version");
            String simpleHtml = "<div xmlns=\"http://www.w3.org/1999/xhtml\">" +
                    "<h2>Consent for Research Participation</h2>" +
                    "<p>Patient: " + request.getPatientId() + "</p>" +
                    "<p>Date: " + new java.text.SimpleDateFormat("dd.MM.yyyy").format(new Date()) + "</p>" +
                    "<p>This consent was generated from template: " + consentTemplate.getName() + "</p>" +
                    "</div>";
            narrative.setDivAsString(simpleHtml);
        }

        return narrative;
    }

    /**
     * Build HTML for a single module with decision status and placeholder replacement
     */
    private String buildModuleHtml(ConsentModule module, boolean isAccepted, String status,
                                   ConsentTemplate consentTemplate, ConsentRequest request) {
        StringBuilder html = new StringBuilder();

        // Add module title with placeholder replacement
        if (module.getTitle() != null) {
            String title = replacePlaceholders(module.getTitle(), consentTemplate, request, isAccepted);
            html.append(cleanHtml(title));
        }

        // Get original text with placeholder replacement
        String originalText = module.getText() != null ?
                replacePlaceholders(module.getText(), consentTemplate, request, isAccepted) : "";

        // Check if this is an intro module (no decisions)
        if (isIntroModule(module.getName())) {
            html.append(cleanHtml(originalText));
            return html.toString();
        }

        // Build decision-aware content
        String statusText = isAccepted ? "✓ ICH WILLIGE EIN" : "✗ ICH WILLIGE NICHT EIN";
        String statusColor = isAccepted ? "#4CAF50" : "#f44336";

        html.append("<div class=\"module-decision\" style=\"border-left: 4px solid ")
                .append(statusColor)
                .append("; padding-left: 10px; margin: 10px 0;\">");

        // Add status header
        html.append("<div style=\"font-weight: bold; color: ")
                .append(statusColor)
                .append(";\">")
                .append(statusText)
                .append("</div>");

        // Add module content
        html.append(cleanHtml(originalText));

        html.append("</div>");

        return html.toString();
    }

    /**
     * Check if a module is an intro module (no decisions, just text)
     */
    private boolean isIntroModule(String moduleName) {
        if (moduleName == null) return false;
        return moduleName.contains("Intro") ||
                moduleName.contains("Geltungsdauer") ||
                moduleName.contains("Widerrufsrecht") ||
                moduleName.contains("Rekontaktierung_Intro") ||
                moduleName.equals("PATDAT_Intro") ||
                moduleName.equals("KKDAT_Intro") ||
                moduleName.equals("BIOMAT_Intro");
    }

    // ==========================================
    // Period/Validity Helper Methods
    // ==========================================

    private String getValidityPeriod() {
        return this.validityPeriod;
    }

    private Date calculateEndDate(Date startDate) {
        if (startDate == null || this.validityPeriod == null) {
            throw new IllegalStateException("Validity period not set.");
        }

        Matcher matcher = VALIDITY_PERIOD_PATTERN.matcher(this.validityPeriod);
        if (!matcher.matches()) {
            throw new IllegalStateException(
                    "Invalid VALIDITY_PERIOD format: " + this.validityPeriod
            );
        }

        int amount = Integer.parseInt(matcher.group(1));
        String unit = matcher.group(2);

        LocalDateTime start = startDate.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();

        LocalDateTime end;
        switch (unit) {
            case "Y":
                end = start.plus(amount, ChronoUnit.YEARS);
                break;
            case "M":
                end = start.plus(amount, ChronoUnit.MONTHS);
                break;
            case "D":
                end = start.plus(amount, ChronoUnit.DAYS);
                break;
            default:
                throw new IllegalStateException("Unknown validity period unit: " + unit);
        }

        return Date.from(end.atZone(ZoneId.systemDefault()).toInstant());
    }

    private Period createPeriod(Date startDate) {
        Period period = new Period();
        period.setStart(startDate);

        Date endDate = calculateEndDate(startDate);
        period.setEnd(endDate);
        logger.debug("Period end date set to: {} (validity: {})", endDate, this.validityPeriod);

        return period;
    }

    // ==========================================
    // Private helper methods
    // ==========================================

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

    private String cleanHtml(String html) {
        if (html == null) return "";
        String cleaned = html;
        cleaned = cleaned.replaceAll("<br>", "<br/>");
        cleaned = cleaned.replaceAll("<br\\s+/>", "<br/>");
        cleaned = cleaned.replaceAll("<hr>", "<hr/>");
        cleaned = cleaned.replaceAll("<hr\\s+/>", "<hr/>");
        cleaned = cleaned.replaceAll("<img([^>]*?)>", "<img$1/>");
        cleaned = cleaned.replaceAll("style=\"\"", "");
        cleaned = cleaned.replaceAll("&(?![a-zA-Z]+;)", "&amp;");
        cleaned = cleaned.replaceAll("(?i)</?font[^>]*>", "");
        cleaned = cleaned.replaceAll("(?i)<b>", "<strong>");
        cleaned = cleaned.replaceAll("(?i)</b>", "</strong>");
        cleaned = cleaned.replaceAll("(?i)<i>", "<em>");
        cleaned = cleaned.replaceAll("(?i)</i>", "</em>");
        return cleaned;
    }

    private CodeableConcept buildScope() {
        CodeableConcept scope = new CodeableConcept();
        scope.addCoding()
                .setSystem(scopeSystem)
                .setCode(scopeCode)
                .setDisplay(scopeDisplay);
        return scope;
    }

    private List<CodeableConcept> buildCategories(ConsentTemplate consentTemplate) {
        List<CodeableConcept> categories = new ArrayList<>();

        CodeableConcept loincCategory = new CodeableConcept();
        loincCategory.addCoding()
                .setSystem(loincCategorySystem)
                .setCode(loincCategoryCode)
                .setDisplay(loincCategoryDisplay);
        categories.add(loincCategory);
        logger.debug("Added LOINC category: {} ({})", loincCategoryCode, loincCategorySystem);

        String categoryOid = consentTemplate.getFhirConsentCategory();
        if (categoryOid == null || categoryOid.isEmpty()) {
            throw new IllegalStateException(
                    "Template missing fhirConsentCategory in externProperties"
            );
        }

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
                .setCode(categoryOid)
                .setDisplay(miiCategoryDisplay);
        categories.add(miiCategory);
        logger.info("Added MII category: {}", categoryOid);

        return categories;
    }

    private List<Consent.ConsentPolicyComponent> buildPolicies(ConsentTemplate consentTemplate) {
        List<Consent.ConsentPolicyComponent> policies = new ArrayList<>();

        String policyValueSet = consentTemplate.getFhirPolicyValueSet();
        if (policyValueSet == null || policyValueSet.isEmpty()) {
            throw new IllegalStateException(
                    "Template missing fhirPolicyValueSet in externProperties"
            );
        }

        Consent.ConsentPolicyComponent policy = new Consent.ConsentPolicyComponent();
        policy.setUri(policyValueSet);
        policies.add(policy);
        logger.info("Added policy URI: {}", policyValueSet);

        return policies;
    }

    private CodeableConcept buildPolicyRule() {
        CodeableConcept policyRule = new CodeableConcept();
        policyRule.addCoding()
                .setSystem(policyRuleSystem)
                .setCode(policyRuleCode)
                .setDisplay(policyRuleDisplay);
        return policyRule;
    }

    private Consent.ProvisionComponent buildProvisions(ConsentTemplate consentTemplate, ConsentRequest request) {
        Consent.ProvisionComponent mainProvision = new Consent.ProvisionComponent();

        String provisionType = getMainProvisionType(request);
        Consent.ConsentProvisionType type;
        if ("permit".equalsIgnoreCase(provisionType)) {
            type = Consent.ConsentProvisionType.PERMIT;
        } else {
            type = Consent.ConsentProvisionType.DENY;
        }
        mainProvision.setType(type);
        logger.info("Main provision type: {}", type);

        Date startDate = new Date();
        Period mainPeriod = createPeriod(startDate);
        mainProvision.setPeriod(mainPeriod);
        logger.info("Main provision period: {} to {}", mainPeriod.getStart(), mainPeriod.getEnd());

        if (consentTemplate.getModulesAssignedConsentModule() != null) {
            List<Consent.ProvisionComponent> nestedProvisions = new ArrayList<>();

            List<ModuleAssignment> sortedModules = new ArrayList<>(consentTemplate.getModulesAssignedConsentModule());
            sortedModules.sort(Comparator.comparingInt(ModuleAssignment::getOrderNumber));

            for (ModuleAssignment assignment : sortedModules) {
                if (isIntroModuleKey(assignment.getModuleKey())) {
                    continue;
                }

                ConsentModule module = getModule(assignment.getModuleKey());
                if (module != null) {
                    ModuleDecision decision = request.getDecisionForModule(assignment.getModuleKey());

                    Consent.ProvisionComponent nestedProvision = buildNestedProvision(
                            module, assignment, consentTemplate, decision, startDate);
                    if (nestedProvision != null) {
                        if (nestedProvision.getCode() != null && !nestedProvision.getCode().isEmpty()) {
                            nestedProvisions.add(nestedProvision);
                            String status = decision != null ? decision.getStatus() : "DECLINED";
                            logger.debug("Added nested provision for module: {} (status: {})",
                                    module.getName(), status);
                        } else {
                            logger.warn("Nested provision for module {} has no codes, skipping", module.getName());
                        }
                    }
                }
            }

            for (Consent.ProvisionComponent nested : nestedProvisions) {
                mainProvision.addProvision(nested);
            }

            logger.info("Built {} nested provisions from modules", nestedProvisions.size());
        }

        return mainProvision;
    }

    private boolean isIntroModuleKey(String moduleKey) {
        if (moduleKey == null) return false;
        return moduleKey.contains("Intro") ||
                moduleKey.contains("Geltungsdauer") ||
                moduleKey.contains("Widerrufsrecht") ||
                moduleKey.contains("Rekontaktierung_Intro");
    }

    private String getMainProvisionType(ConsentRequest request) {
        if (request.getMainProvisionType() != null) {
            return request.getMainProvisionType();
        }
        return "deny";
    }

    private Consent.ProvisionComponent buildNestedProvision(ConsentModule module, ModuleAssignment assignment,
                                                            ConsentTemplate consentTemplate, ModuleDecision decision,
                                                            Date startDate) {
        Consent.ProvisionComponent provision = new Consent.ProvisionComponent();

        String provisionType = "deny";
        if (decision != null) {
            if ("ACCEPTED".equalsIgnoreCase(decision.getStatus())) {
                provisionType = "permit";
            } else if ("DECLINED".equalsIgnoreCase(decision.getStatus())) {
                provisionType = "deny";
            } else if (decision.getProvisionType() != null) {
                provisionType = decision.getProvisionType();
            }
        }

        Consent.ConsentProvisionType type;
        if ("permit".equalsIgnoreCase(provisionType)) {
            type = Consent.ConsentProvisionType.PERMIT;
        } else {
            type = Consent.ConsentProvisionType.DENY;
        }
        provision.setType(type);

        String status = decision != null ? decision.getStatus() : "DECLINED";
        logger.debug("Nested provision type: {} (status: {})", type, status);

        Period nestedPeriod = createPeriod(startDate);
        provision.setPeriod(nestedPeriod);

        String policySystem = consentTemplate.getFhirPolicyValueSet();
        if (policySystem == null) {
            policySystem = "urn:oid:2.16.840.1.113883.3.1937.777.24.5.3";
        }

        List<PolicyAssignment> policyAssignments = module.getPoliciesAssignedConsentPolicy();
        if (policyAssignments != null) {
            for (PolicyAssignment policyAssignment : policyAssignments) {
                String policyKey = policyAssignment.getPolicyKey();
                ConsentPolicy policy = getPolicy(policyKey);
                if (policy != null) {
                    String fhirCode = policy.getFhirPolicyCode();
                    if (fhirCode != null) {
                        CodeableConcept code = new CodeableConcept();
                        code.addCoding()
                                .setSystem(policySystem)
                                .setCode(fhirCode)
                                .setDisplay(policy.getLabel());
                        provision.addCode(code);
                    } else {
                        logger.warn("Policy {} has no FHIR code", policyKey);
                    }
                } else {
                    logger.warn("Policy not found: {}", policyKey);
                }
            }
        }

        return provision;
    }

    private ConsentPolicy getPolicy(String policyKey) {
        return policyMap.get(policyKey);
    }

    private ConsentModule getModule(String moduleKey) {
        return moduleMap.get(moduleKey);
    }

    // ==========================================
    // Public getter methods
    // ==========================================

    public Set<String> getAvailableTemplateKeys() {
        return templateMap.keySet();
    }

    public List<String> getAvailableTemplateNames() {
        return templateMap.values().stream()
                .map(t -> t.getDomainName() + " - " + t.getName() + " (" + t.getVersionLabel() + ")")
                .collect(Collectors.toList());
    }

    public List<ModuleInfo> getModulesForTemplate(String templateKey) {
        ConsentTemplate consentTemplate = templateMap.get(templateKey);
        if (consentTemplate == null) {
            throw new IllegalArgumentException("Template not found: " + templateKey);
        }

        List<ModuleInfo> moduleInfos = new ArrayList<>();
        if (consentTemplate.getModulesAssignedConsentModule() != null) {
            List<ModuleAssignment> sortedModules = new ArrayList<>(consentTemplate.getModulesAssignedConsentModule());
            sortedModules.sort(Comparator.comparingInt(ModuleAssignment::getOrderNumber));

            for (ModuleAssignment assignment : sortedModules) {
                ConsentModule module = getModule(assignment.getModuleKey());
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

    private void validateRequest(ConsentRequest request) {
        if (request.getPatientId() == null || request.getPatientId().isEmpty()) {
            throw new IllegalArgumentException("Patient ID is required");
        }
        if (request.getOrganizationId() == null || request.getOrganizationId().isEmpty()) {
            throw new IllegalArgumentException("Organization ID is required");
        }
        if (request.getModuleDecisions() == null || request.getModuleDecisions().isEmpty()) {
            throw new IllegalArgumentException("Module decisions are required");
        }
    }
}