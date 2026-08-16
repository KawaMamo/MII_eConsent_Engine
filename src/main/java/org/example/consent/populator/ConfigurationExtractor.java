package org.example.consent.populator;

import org.example.consent.model.*;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts configuration from templates and profiles
 * Responsibility: Extract all configuration values from template and profile
 *
 * FIXED: Supports both template-level and domain-level fhirForceProfileConsent
 */
public class ConfigurationExtractor {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationExtractor.class);

    private static final Pattern VALIDITY_PERIOD_PATTERN = Pattern.compile("P(\\d+)([YMD])");
    private static final Pattern CATEGORY_PATH_PATTERN = Pattern.compile("^Consent\\.category(?:\\:[a-zA-Z]+)?$");

    // Default policy rule (FHIR standard)
    private static final String DEFAULT_POLICY_RULE_SYSTEM = "http://terminology.hl7.org/CodeSystem/v3-ActCode";
    private static final String DEFAULT_POLICY_RULE_CODE = "OPTIN";
    private static final String DEFAULT_POLICY_RULE_DISPLAY = "Opt-in";

    // Default scope
    private static final String DEFAULT_SCOPE_SYSTEM = "http://terminology.hl7.org/CodeSystem/consentscope";
    private static final String DEFAULT_SCOPE_CODE = "research";
    private static final String DEFAULT_SCOPE_DISPLAY = "Research";

    // Fallback LOINC values
    private static final String FALLBACK_LOINC_CODE = "57016-8";
    private static final String FALLBACK_LOINC_SYSTEM = "http://loinc.org";
    private static final String FALLBACK_LOINC_DISPLAY = "Privacy consent";

    /**
     * Extract all configuration from template and profile
     * FIXED: Checks both template and domain externProperties for profile URL
     */
    public TemplateConfiguration extractConfiguration(ConsentTemplate consentTemplate, StructureDefinition miiProfile) {
        LoincCategory loinc = extractLoincCategory(miiProfile);
        PolicyRule policyRule = extractPolicyRule(consentTemplate);
        String validityPeriod = extractValidityPeriod(consentTemplate);
        Scope scope = extractScope(consentTemplate);

        // FIXED: Try template externProperties first, then fallback to domain
        String profileUrl = extractProfileUrl(consentTemplate);
        if (profileUrl == null || profileUrl.isEmpty()) {
            throw new IllegalStateException(
                    "Template missing fhirForceProfileConsent in externProperties. " +
                            "Please add fhirForceProfileConsent=... to the template's externProperties or domain's externProperties."
            );
        }

        String consentCategory = consentTemplate.getFhirConsentCategory();
        if (consentCategory == null || consentCategory.isEmpty()) {
            throw new IllegalStateException(
                    "Template missing fhirConsentCategory in externProperties"
            );
        }

        String policyValueSet = consentTemplate.getFhirPolicyValueSet();
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
     * FIXED: Checks both levels
     */
    private String extractProfileUrl(ConsentTemplate consentTemplate) {
        // First try template externProperties
        if (consentTemplate.getExternProperties() != null) {
            String[] props = consentTemplate.getExternProperties().split(";");
            for (String prop : props) {
                if (prop.startsWith("fhirForceProfileConsent=")) {
                    return prop.substring("fhirForceProfileConsent=".length());
                }
            }
        }

        // Fallback: Try to get from domain externProperties (for older templates like 1.6.d)
        if (consentTemplate.getDomainName() != null && consentTemplate.getDomainName().equals("MII")) {
            // The domain externProperties is in the template object
            // We need to check if the template has domain externProperties
            // For 1.6.d, the domain externProperties has the profile URL
            String domainExternProperties = consentTemplate.getDomainExternProperties();
            if (domainExternProperties != null && !domainExternProperties.isEmpty()) {
                String[] props = domainExternProperties.split(";");
                for (String prop : props) {
                    if (prop.startsWith("fhirForceProfileConsent=")) {
                        return prop.substring("fhirForceProfileConsent=".length());
                    }
                }
            }
        }

        return null;
    }

    // ==========================================
    // Other extraction methods
    // ==========================================

    private LoincCategory extractLoincCategory(StructureDefinition miiProfile) {
        if (miiProfile == null) {
            throw new IllegalArgumentException("MII profile cannot be null");
        }

        // Check snapshot first
        if (miiProfile.getSnapshot() != null && miiProfile.getSnapshot().getElement() != null) {
            for (ElementDefinition element : miiProfile.getSnapshot().getElement()) {
                String path = element.getPath();
                if (path != null && isCategoryPath(path)) {
                    LoincCategory result = extractFromElement(element);
                    if (result != null) {
                        logger.info("Extracted LOINC category from profile: {} ({})", result.code, result.system);
                        return result;
                    }
                }
            }
        }

        // Fallback to differential
        if (miiProfile.getDifferential() != null && miiProfile.getDifferential().getElement() != null) {
            for (ElementDefinition element : miiProfile.getDifferential().getElement()) {
                String path = element.getPath();
                if (path != null && isCategoryPath(path)) {
                    LoincCategory result = extractFromElement(element);
                    if (result != null) {
                        logger.info("Extracted LOINC category from differential: {} ({})", result.code, result.system);
                        return result;
                    }
                }
            }
        }

        logger.warn("Could not extract LOINC category from profile, using fallback");
        return new LoincCategory(FALLBACK_LOINC_CODE, FALLBACK_LOINC_SYSTEM, FALLBACK_LOINC_DISPLAY);
    }

    private LoincCategory extractFromElement(ElementDefinition element) {
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

        return null;
    }

    private boolean isCategoryPath(String path) {
        return path != null && CATEGORY_PATH_PATTERN.matcher(path).matches();
    }

    private PolicyRule extractPolicyRule(ConsentTemplate consentTemplate) {
        String system = DEFAULT_POLICY_RULE_SYSTEM;
        String code = DEFAULT_POLICY_RULE_CODE;
        String display = DEFAULT_POLICY_RULE_DISPLAY;

        if (consentTemplate.getExternProperties() != null) {
            String[] props = consentTemplate.getExternProperties().split(";");
            for (String prop : props) {
                if (prop.startsWith("policyRuleSystem=")) {
                    system = prop.substring("policyRuleSystem=".length());
                }
                if (prop.startsWith("policyRuleCode=")) {
                    code = prop.substring("policyRuleCode=".length());
                }
                if (prop.startsWith("policyRuleDisplay=")) {
                    display = prop.substring("policyRuleDisplay=".length());
                }
            }
        }

        logger.info("Using policy rule: {} ({})", code, system);
        return new PolicyRule(system, code, display);
    }

    private String extractValidityPeriod(ConsentTemplate consentTemplate) {
        if (consentTemplate.getExpirationProperties() == null || consentTemplate.getExpirationProperties().isEmpty()) {
            throw new IllegalStateException(
                    "Template missing expirationProperties. Required: VALIDITY_PERIOD=... (e.g., VALIDITY_PERIOD=P30Y)"
            );
        }

        String[] props = consentTemplate.getExpirationProperties().split(";");
        for (String prop : props) {
            if (prop != null && prop.startsWith("VALIDITY_PERIOD=")) {
                String period = prop.substring("VALIDITY_PERIOD=".length());
                Matcher matcher = VALIDITY_PERIOD_PATTERN.matcher(period);
                if (!matcher.matches()) {
                    throw new IllegalStateException(
                            "Invalid VALIDITY_PERIOD format: " + period
                    );
                }
                logger.info("Extracted validity period: {}", period);
                return period;
            }
        }

        throw new IllegalStateException(
                "Template expirationProperties missing VALIDITY_PERIOD."
        );
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
            String[] props = consentTemplate.getExternProperties().split(";");
            for (String prop : props) {
                if (prop.startsWith("scopeSystem=")) {
                    system = prop.substring("scopeSystem=".length());
                }
                if (prop.startsWith("scopeCode=")) {
                    code = prop.substring("scopeCode=".length());
                }
                if (prop.startsWith("scopeDisplay=")) {
                    display = prop.substring("scopeDisplay=".length());
                }
            }
        }

        logger.info("Using scope: {} ({})", code, system);
        return new Scope(system, code, display);
    }

    // ==========================================
    // Inner DTO classes
    // ==========================================

    public static class LoincCategory {
        public final String code, system, display;
        public LoincCategory(String code, String system, String display) {
            this.code = code; this.system = system; this.display = display;
        }
    }

    public static class PolicyRule {
        public final String system, code, display;
        public PolicyRule(String system, String code, String display) {
            this.system = system; this.code = code; this.display = display;
        }
    }

    public static class Scope {
        public final String system, code, display;
        public Scope(String system, String code, String display) {
            this.system = system; this.code = code; this.display = display;
        }
    }
}