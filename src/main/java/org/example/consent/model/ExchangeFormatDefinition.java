
package org.example.consent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;


@JsonIgnoreProperties(ignoreUnknown = true)
public class ExchangeFormatDefinition {

    @JsonProperty("resourceType")
    private String resourceType;

    @JsonProperty("meta")
    private Meta meta;

    @JsonProperty("supportedVersion")
    private String supportedVersion;

    @JsonProperty("domain")
    private Domain domain;

    @JsonProperty("policiesConsentPolicy")
    private List<ConsentPolicy> policiesConsentPolicy;

    @JsonProperty("modulesConsentModule")
    private List<ConsentModule> modulesConsentModule;

    @JsonProperty("templatesConsentTemplate")
    private List<ConsentTemplate> templatesConsentTemplate;

    // Getters and setters
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public Meta getMeta() { return meta; }
    public void setMeta(Meta meta) { this.meta = meta; }
    public String getSupportedVersion() { return supportedVersion; }
    public void setSupportedVersion(String supportedVersion) { this.supportedVersion = supportedVersion; }
    public Domain getDomain() { return domain; }
    public void setDomain(Domain domain) { this.domain = domain; }
    public List<ConsentPolicy> getPoliciesConsentPolicy() { return policiesConsentPolicy; }
    public void setPoliciesConsentPolicy(List<ConsentPolicy> policiesConsentPolicy) { this.policiesConsentPolicy = policiesConsentPolicy; }
    public List<ConsentModule> getModulesConsentModule() { return modulesConsentModule; }
    public void setModulesConsentModule(List<ConsentModule> modulesConsentModule) { this.modulesConsentModule = modulesConsentModule; }
    public List<ConsentTemplate> getTemplatesConsentTemplate() { return templatesConsentTemplate; }
    public void setTemplatesConsentTemplate(List<ConsentTemplate> templatesConsentTemplate) { this.templatesConsentTemplate = templatesConsentTemplate; }
}

@JsonIgnoreProperties(ignoreUnknown = true)
class Meta {
    @JsonProperty("profile")
    private List<String> profile;

    public List<String> getProfile() { return profile; }
    public void setProfile(List<String> profile) { this.profile = profile; }
}