package org.example.consent.populator;

import org.example.consent.config.FhirConsentConfig;
import org.example.consent.model.*;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts configuration from templates and profiles
 * Responsibility: Extract all configuration values from template and profile
 *
 * FIXED: Properly trims all key-value tokens from externProperties
 * FIXED: Supports both template-level and domain-level fhirForceProfileConsent
 * FIXED: Full ISO 8601 duration validation support
 * FIXED: Uses externalized configuration for default values
 */
public class ConfigurationExtractor {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationExtractor.class);

    // FIXED: Full ISO 8601 duration validation pattern
    private static final Pattern ISO_8601_DURATION_VALIDATION = Pattern.compile(
            "P(?:(\\d+(?:\\.\\d+)?)Y)?(?:(\\d+(?:\\.\\d+)?)M)?(?:(\\d+(?:\\.\\d+)?)D)?(?:(\\d+(?:\\.\\d+)?)W)?",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern CATEGORY_PATH_PATTERN = Pattern.compile("^Consent\\.category(?:\\:[a-zA-Z]+)?$");

    // Configuration
    private final FhirConsentConfig config;

    // Default policy rule (FHIR standard)
    private static final String DEFAULT_POLICY_RULE_SYSTEM = "http://terminology.hl7.org/CodeSystem/v3-ActCode";
    private static final String DEFAULT_POLICY_RULE_CODE = "OPTIN";
    private static final String DEFAULT_POLICY_RULE_DISPLAY = "Opt-in";

    // Default scope
    private static final String DEFAULT_SCOPE_SYSTEM = "http://terminology.hl7.org/CodeSystem/consentscope";
    private static final String DEFAULT_SCOPE_CODE = "research";
    private static final String DEFAULT_SCOPE_DISPLAY = "Research";

    // Fallback LOINC values (from config or hardcoded)
    private String fallbackLoincCode;
    private String fallbackLoincSystem;
    private String fallbackLoincDisplay;

    public ConfigurationExtractor() {
        this.config = new FhirConsentConfig();
        initializeFallbacks();
    }

    public ConfigurationExtractor(FhirConsentConfig config) {
        this.config = config != null ? config : new FhirConsentConfig();
        initializeFallbacks();
    }

    private void initializeFallbacks() {
        this.fallbackLoincCode = config.getLoincCode();
        this.fallbackLoincSystem = config.getLoincSystem();
        this.fallbackLoincDisplay = config.getLoincDisplay();
    }

    /**
     * Parse externProperties string into a map of key-value pairs
     */
    private Map<String, String> parseExternProperties(String externProperties) {
        Map<String, String> properties = new HashMap<>();

        if (externProperties == null || externProperties.isEmpty()) {
            return properties;
        }

        String[] tokens = externProperties.split(";");
        for (String token : tokens) {
            if (token == null || token.trim().isEmpty()) {
                continue;
            }

            String trimmedToken = token.trim();

            int equalsIndex = trimmedToken.indexOf('=');
            if (equalsIndex > 0) {
                String key = trimmedToken.substring(0, equalsIndex).trim();
                String value = trimmedToken.substring(equalsIndex + 1).trim();
                if (!key.isEmpty()) {
                    properties.put(key, value);
                    logger.debug("Parsed property: {} = {}", key, value);
                }
            } else {
                properties.put(trimmedToken, "true");
                logger.debug("Parsed flag property: {}", trimmedToken);
            }
        }

        return properties;
    }

    /**
     * Get a property value from externProperties with fallback
     */
    private String getProperty(Map<String, String> properties, String key) {
        return properties.get(key);
    }

    /**
     * Get a property value or return default
     */
    private String getProperty(Map<String, String> properties, String key, String defaultValue) {
        String value = properties.get(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Extract all configuration from template and profile
     */
    public TemplateConfiguration extractConfiguration(ConsentTemplate consentTemplate, StructureDefinition miiProfile) {
        LoincCategory loinc = extractLoincCategory(miiProfile);
        PolicyRule policyRule = extractPolicyRule(consentTemplate);
        String validityPeriod = extractValidityPeriod(consentTemplate);
        Scope scope = extractScope(consentTemplate);

        String profileUrl = extractProfileUrl(consentTemplate);
        if (profileUrl == null || profileUrl.isEmpty()) {
            throw new IllegalStateException(
                    "Template missing fhirForceProfileConsent in externProperties. " +
                            "Please add fhirForceProfileConsent=... to the template's externProperties or domain's externProperties."
            );
        }

        String consentCategory = extractConsentCategory(consentTemplate);
        if (consentCategory == null || consentCategory.isEmpty()) {
            throw new IllegalStateException(
                    "Template missing fhirConsentCategory in externProperties"
            );
        }

        String policyValueSet = extractPolicyValueSet(consentTemplate);
        if (policyValueSet == null || policyValueSet.isEmpty()) {
            throw new IllegalStateException(
                    "Template missing fhirPolicyValueSet in externProperties"
            );
        }

        return new TemplateConfiguration(
                loinc.code, loinc.system, loinc.display,
                policyRule.system, policyRule.code, policyRule.display,
                validityPeriod,
                scope.system, scope.code, scope.display,
                profileUrl, consentCategory, policyValueSet,
                consentTemplate
        );
    }

    /**
     * Extract profile URL from template or domain externProperties
     */
    private String extractProfileUrl(ConsentTemplate consentTemplate) {
        if (consentTemplate.getExternProperties() != null) {
            Map<String, String> props = parseExternProperties(consentTemplate.getExternProperties());
            String profileUrl = props.get("fhirForceProfileConsent");
            if (profileUrl != null && !profileUrl.isEmpty()) {
                return profileUrl;
            }
        }

        if (consentTemplate.getDomainName() != null && consentTemplate.getDomainName().equals("MII")) {
            String domainExternProperties = consentTemplate.getDomainExternProperties();
            if (domainExternProperties != null && !domainExternProperties.isEmpty()) {
                Map<String, String> props = parseExternProperties(domainExternProperties);
                String profileUrl = props.get("fhirForceProfileConsent");
                if (profileUrl != null && !profileUrl.isEmpty()) {
                    return profileUrl;
                }
            }
        }

        return null;
    }

    /**
     * Extract consent category from template externProperties
     */
    private String extractConsentCategory(ConsentTemplate consentTemplate) {
        if (consentTemplate.getExternProperties() != null) {
            Map<String, String> props = parseExternProperties(consentTemplate.getExternProperties());
            return props.get("fhirConsentCategory");
        }
        return null;
    }

    /**
     * Extract policy value set from template externProperties
     */
    private String extractPolicyValueSet(ConsentTemplate consentTemplate) {
        if (consentTemplate.getExternProperties() != null) {
            Map<String, String> props = parseExternProperties(consentTemplate.getExternProperties());
            return props.get("fhirPolicyValueSet");
        }
        return null;
    }

    // ==========================================
    // LOINC Category Extraction Methods
    // ==========================================

    private static class LoincCategory {
        final String code, system, display;
        LoincCategory(String code, String system, String display) {
            this.code = code; this.system = system; this.display = display;
        }
    }

    /**
     * Extract LOINC category from MII profile using multiple strategies
     */
    private LoincCategory extractLoincCategory(StructureDefinition miiProfile) {
        if (miiProfile == null) {
            throw new IllegalArgumentException("MII profile cannot be null");
        }

        // Strategy 1: Check for Consent.category:loinc slice specifically
        LoincCategory result = extractFromSlice(miiProfile, "loinc");
        if (result != null) return result;

        // Strategy 2: Check for Consent.category:mii slice
        result = extractFromSlice(miiProfile, "mii");
        if (result != null) return result;

        // Strategy 3: Find any Consent.category with pattern or fixed value
        result = extractFromAnyCategory(miiProfile);
        if (result != null) return result;

        // Strategy 4: Check ValueSet bindings
        result = extractFromValueSetBinding(miiProfile);
        if (result != null) return result;

        // Strategy 5: Fallback to config values
        logger.warn("Could not extract LOINC category from profile, using fallback values");
        return new LoincCategory(fallbackLoincCode, fallbackLoincSystem, fallbackLoincDisplay);
    }

    /**
     * Extract category from a specific slice
     */
    private LoincCategory extractFromSlice(StructureDefinition miiProfile, String sliceName) {
        String targetPath = "Consent.category:" + sliceName;

        // Check snapshot
        if (miiProfile.getSnapshot() != null && miiProfile.getSnapshot().getElement() != null) {
            for (ElementDefinition element : miiProfile.getSnapshot().getElement()) {
                String path = element.getPath();
                if (path != null && path.equals(targetPath)) {
                    LoincCategory result = extractFromElement(element);
                    if (result != null) {
                        logger.info("Extracted LOINC category from slice: {} ({}) [path: {}]",
                                result.code, result.system, path);
                        return result;
                    }
                }
            }
        }

        // Check differential
        if (miiProfile.getDifferential() != null && miiProfile.getDifferential().getElement() != null) {
            for (ElementDefinition element : miiProfile.getDifferential().getElement()) {
                String path = element.getPath();
                if (path != null && path.equals(targetPath)) {
                    LoincCategory result = extractFromElement(element);
                    if (result != null) {
                        logger.info("Extracted LOINC category from differential slice: {} ({}) [path: {}]",
                                result.code, result.system, path);
                        return result;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Extract category from any Consent.category element
     */
    private LoincCategory extractFromAnyCategory(StructureDefinition miiProfile) {
        // Check snapshot
        if (miiProfile.getSnapshot() != null && miiProfile.getSnapshot().getElement() != null) {
            for (ElementDefinition element : miiProfile.getSnapshot().getElement()) {
                String path = element.getPath();
                if (path != null && isCategoryPath(path)) {
                    LoincCategory result = extractFromElement(element);
                    if (result != null) {
                        logger.info("Extracted LOINC category from profile element: {} ({}) [path: {}]",
                                result.code, result.system, path);
                        return result;
                    }
                }
            }
        }

        // Check differential
        if (miiProfile.getDifferential() != null && miiProfile.getDifferential().getElement() != null) {
            for (ElementDefinition element : miiProfile.getDifferential().getElement()) {
                String path = element.getPath();
                if (path != null && isCategoryPath(path)) {
                    LoincCategory result = extractFromElement(element);
                    if (result != null) {
                        logger.info("Extracted LOINC category from differential element: {} ({}) [path: {}]",
                                result.code, result.system, path);
                        return result;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Extract category from an ElementDefinition
     */
    private LoincCategory extractFromElement(ElementDefinition element) {
        // Check pattern
        if (element.getPattern() != null && element.getPattern() instanceof CodeableConcept) {
            CodeableConcept pattern = (CodeableConcept) element.getPattern();
            if (pattern.getCoding() != null && !pattern.getCoding().isEmpty()) {
                Coding coding = pattern.getCoding().get(0);
                if (coding.getSystem() != null && coding.getCode() != null) {
                    String display = coding.getDisplay() != null ? coding.getDisplay() : "Privacy consent";
                    return new LoincCategory(coding.getCode(), coding.getSystem(), display);
                }
            }
        }

        // Check fixed value
        if (element.getFixed() != null && element.getFixed() instanceof CodeableConcept) {
            CodeableConcept fixed = (CodeableConcept) element.getFixed();
            if (fixed.getCoding() != null && !fixed.getCoding().isEmpty()) {
                Coding coding = fixed.getCoding().get(0);
                if (coding.getSystem() != null && coding.getCode() != null) {
                    String display = coding.getDisplay() != null ? coding.getDisplay() : "Privacy consent";
                    return new LoincCategory(coding.getCode(), coding.getSystem(), display);
                }
            }
        }

        // Check example
        if (element.getExample() != null && element.getExample() instanceof CodeableConcept) {
            CodeableConcept example = (CodeableConcept) element.getExample();
            if (example.getCoding() != null && !example.getCoding().isEmpty()) {
                Coding coding = example.getCoding().get(0);
                if (coding.getSystem() != null && coding.getCode() != null) {
                    String display = coding.getDisplay() != null ? coding.getDisplay() : "Privacy consent";
                    return new LoincCategory(coding.getCode(), coding.getSystem(), display);
                }
            }
        }

        return null;
    }

    /**
     * Extract category from ValueSet binding
     */
    private LoincCategory extractFromValueSetBinding(StructureDefinition miiProfile) {
        if (miiProfile.getSnapshot() != null && miiProfile.getSnapshot().getElement() != null) {
            for (ElementDefinition element : miiProfile.getSnapshot().getElement()) {
                String path = element.getPath();
                if (path != null && isCategoryPath(path)) {
                    if (element.getBinding() != null) {
                        String valueSet = element.getBinding().getValueSet();
                        if (valueSet != null && valueSet.contains("loinc")) {
                            logger.info("Found category ValueSet binding: {}, using fallback LOINC", valueSet);
                            return new LoincCategory(fallbackLoincCode, fallbackLoincSystem, fallbackLoincDisplay);
                        }
                    }
                }
            }
        }
        return null;
    }

    private boolean isCategoryPath(String path) {
        return path != null && CATEGORY_PATH_PATTERN.matcher(path).matches();
    }

    // ==========================================
    // Policy Rule Extraction
    // ==========================================

    private static class PolicyRule {
        final String system, code, display;
        PolicyRule(String system, String code, String display) {
            this.system = system; this.code = code; this.display = display;
        }
    }

    private PolicyRule extractPolicyRule(ConsentTemplate consentTemplate) {
        String system = DEFAULT_POLICY_RULE_SYSTEM;
        String code = DEFAULT_POLICY_RULE_CODE;
        String display = DEFAULT_POLICY_RULE_DISPLAY;

        if (consentTemplate.getExternProperties() != null) {
            Map<String, String> props = parseExternProperties(consentTemplate.getExternProperties());
            system = getProperty(props, "policyRuleSystem", system);
            code = getProperty(props, "policyRuleCode", code);
            display = getProperty(props, "policyRuleDisplay", display);
        }

        logger.info("Using policy rule: {} ({})", code, system);
        return new PolicyRule(system, code, display);
    }

    // ==========================================
    // Validity Period Extraction
    // ==========================================

    /**
     * Extract validity period from template expirationProperties
     * FIXED: Supports full ISO 8601 duration format
     */
    private String extractValidityPeriod(ConsentTemplate consentTemplate) {
        if (consentTemplate.getExpirationProperties() == null || consentTemplate.getExpirationProperties().isEmpty()) {
            throw new IllegalStateException(
                    "Template missing expirationProperties. Required: VALIDITY_PERIOD=... (e.g., VALIDITY_PERIOD=P30Y, VALIDITY_PERIOD=P1Y6M)"
            );
        }

        String[] props = consentTemplate.getExpirationProperties().split(";");
        for (String prop : props) {
            if (prop == null) continue;
            String trimmedProp = prop.trim();
            if (trimmedProp.startsWith("VALIDITY_PERIOD=")) {
                String period = trimmedProp.substring("VALIDITY_PERIOD=".length()).trim();

                // FIXED: Validate using full ISO 8601 pattern
                if (!isValidIso8601Duration(period)) {
                    throw new IllegalStateException(
                            "Invalid VALIDITY_PERIOD format: " + period +
                                    ". Expected ISO 8601 duration format like: P30Y, P1Y6M, P2Y3M15D, P5Y, P6M, P1W"
                    );
                }

                logger.info("Extracted validity period: {}", period);
                return period;
            }
        }

        throw new IllegalStateException(
                "Template expirationProperties missing VALIDITY_PERIOD. " +
                        "Example: expirationProperties: \"VALIDITY_PERIOD=P30Y;\""
        );
    }

    /**
     * Validate ISO 8601 duration format
     * FIXED: Supports full ISO 8601 duration format
     */
    private boolean isValidIso8601Duration(String period) {
        if (period == null || period.isEmpty()) {
            return false;
        }

        String normalized = period.toUpperCase();
        if (!normalized.startsWith("P")) {
            return false;
        }

        // Check against full ISO 8601 pattern
        Matcher matcher = ISO_8601_DURATION_VALIDATION.matcher(normalized);
        if (matcher.matches()) {
            // Ensure at least one unit is present
            return matcher.group(1) != null ||
                    matcher.group(2) != null ||
                    matcher.group(3) != null ||
                    matcher.group(4) != null;
        }

        return false;
    }

    // ==========================================
    // Scope Extraction
    // ==========================================

    private static class Scope {
        final String system, code, display;
        Scope(String system, String code, String display) {
            this.system = system; this.code = code; this.display = display;
        }
    }

    private Scope extractScope(ConsentTemplate consentTemplate) {
        String system = DEFAULT_SCOPE_SYSTEM;
        String code = DEFAULT_SCOPE_CODE;
        String display = DEFAULT_SCOPE_DISPLAY;

        if ("REFUSAL".equals(consentTemplate.getType())) {
            code = "treatment";
            display = "Treatment";
        } else if ("REVOCATION".equals(consentTemplate.getType())) {
            code = "research";
            display = "Research (Revocation)";
        }

        if (consentTemplate.getExternProperties() != null) {
            Map<String, String> props = parseExternProperties(consentTemplate.getExternProperties());
            system = getProperty(props, "scopeSystem", system);
            code = getProperty(props, "scopeCode", code);
            display = getProperty(props, "scopeDisplay", display);
        }

        logger.info("Using scope: {} ({})", code, system);
        return new Scope(system, code, display);
    }

    // ==========================================
    // Public getter for config
    // ==========================================

    public FhirConsentConfig getConfig() {
        return config;
    }
}