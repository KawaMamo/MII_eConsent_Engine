package org.example.consent.config;

import java.util.HashMap;
import java.util.Map;

/**
 * Externalized configuration for FHIR Consent Management System
 * FIXED: Moves hardcoded values to external configuration
 */
public class FhirConsentConfig {

    // Default MII CodeSystem URIs (can be overridden via config)
    private static final String DEFAULT_MII_VERSION_MODULE_SYSTEM =
            "https://www.medizininformatik-initiative.de/fhir/modul-consent/CodeSystem/mii-cs-consent-version-modules";

    private static final String DEFAULT_MII_POLICY_OID_SYSTEM =
            "urn:oid:2.16.840.1.113883.3.1937.777.24.5.3";

    private static final String DEFAULT_LOINC_SYSTEM =
            "http://loinc.org";

    private static final String DEFAULT_LOINC_CODE =
            "57016-8";

    private static final String DEFAULT_LOINC_DISPLAY =
            "Privacy consent";

    private static final String DEFAULT_MII_CATEGORY_DISPLAY =
            "MII Broad Consent";

    // Configuration map for flexible overrides
    private final Map<String, String> configMap;

    public FhirConsentConfig() {
        this.configMap = new HashMap<>();
        initializeDefaults();
    }

    public FhirConsentConfig(Map<String, String> overrides) {
        this.configMap = new HashMap<>();
        initializeDefaults();
        if (overrides != null) {
            configMap.putAll(overrides);
        }
    }

    private void initializeDefaults() {
        configMap.put("mii.version.module.system", DEFAULT_MII_VERSION_MODULE_SYSTEM);
        configMap.put("mii.policy.oid.system", DEFAULT_MII_POLICY_OID_SYSTEM);
        configMap.put("loinc.system", DEFAULT_LOINC_SYSTEM);
        configMap.put("loinc.code", DEFAULT_LOINC_CODE);
        configMap.put("loinc.display", DEFAULT_LOINC_DISPLAY);
        configMap.put("mii.category.display", DEFAULT_MII_CATEGORY_DISPLAY);
    }

    public String getMiiVersionModuleSystem() {
        return configMap.get("mii.version.module.system");
    }

    public String getMiiPolicyOidSystem() {
        return configMap.get("mii.policy.oid.system");
    }

    public String getLoincSystem() {
        return configMap.get("loinc.system");
    }

    public String getLoincCode() {
        return configMap.get("loinc.code");
    }

    public String getLoincDisplay() {
        return configMap.get("loinc.display");
    }

    public String getMiiCategoryDisplay() {
        return configMap.get("mii.category.display");
    }

    public String get(String key) {
        return configMap.get(key);
    }

    public String get(String key, String defaultValue) {
        return configMap.getOrDefault(key, defaultValue);
    }

    public Map<String, String> getConfigMap() {
        return new HashMap<>(configMap);
    }
}