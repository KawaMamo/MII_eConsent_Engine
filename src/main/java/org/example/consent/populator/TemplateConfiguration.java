package org.example.consent.populator;

import org.example.consent.model.ConsentTemplate;

/**
 * Immutable configuration holder for a specific template
 * All values are extracted from template and profile at request time
 */
public class TemplateConfiguration {

    public final String loincCategoryCode;
    public final String loincCategorySystem;
    public final String loincCategoryDisplay;
    public final String policyRuleSystem;
    public final String policyRuleCode;
    public final String policyRuleDisplay;
    public final String validityPeriod;
    public final String scopeSystem;
    public final String scopeCode;
    public final String scopeDisplay;
    public final String profileUrl;
    public final String consentCategory;
    public final String policyValueSet;
    public final ConsentTemplate consentTemplate;

    public TemplateConfiguration(
            String loincCategoryCode, String loincCategorySystem, String loincCategoryDisplay,
            String policyRuleSystem, String policyRuleCode, String policyRuleDisplay,
            String validityPeriod,
            String scopeSystem, String scopeCode, String scopeDisplay,
            String profileUrl, String consentCategory, String policyValueSet,
            ConsentTemplate consentTemplate) {
        this.loincCategoryCode = loincCategoryCode;
        this.loincCategorySystem = loincCategorySystem;
        this.loincCategoryDisplay = loincCategoryDisplay;
        this.policyRuleSystem = policyRuleSystem;
        this.policyRuleCode = policyRuleCode;
        this.policyRuleDisplay = policyRuleDisplay;
        this.validityPeriod = validityPeriod;
        this.scopeSystem = scopeSystem;
        this.scopeCode = scopeCode;
        this.scopeDisplay = scopeDisplay;
        this.profileUrl = profileUrl;
        this.consentCategory = consentCategory;
        this.policyValueSet = policyValueSet;
        this.consentTemplate = consentTemplate;
    }
}