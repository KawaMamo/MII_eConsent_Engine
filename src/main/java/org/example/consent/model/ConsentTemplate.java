package org.example.consent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;


@JsonIgnoreProperties(ignoreUnknown = true)
public class ConsentTemplate {
    @JsonProperty("domainName")
    private String domainName;

    @JsonProperty("Name")
    private String name;

    @JsonProperty("Label")
    private String label;

    @JsonProperty("version")
    private String version;

    @JsonProperty("versionLabel")
    private String versionLabel;

    @JsonProperty("type")
    private String type;

    @JsonProperty("title")
    private String title;

    @JsonProperty("header")
    private String header;

    @JsonProperty("comment")
    private String comment;

    @JsonProperty("expirationProperties")
    private String expirationProperties;

    @JsonProperty("externProperties")
    private String externProperties;

    @JsonProperty("finalized")
    private boolean finalized;

    @JsonProperty("modulesAssignedConsentModule")
    private List<ModuleAssignment> modulesAssignedConsentModule;

    // Getters and setters
    public String getDomainName() { return domainName; }
    public void setDomainName(String domainName) { this.domainName = domainName; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getVersionLabel() { return versionLabel; }
    public void setVersionLabel(String versionLabel) { this.versionLabel = versionLabel; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getHeader() { return header; }
    public void setHeader(String header) { this.header = header; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public String getExpirationProperties() { return expirationProperties; }
    public void setExpirationProperties(String expirationProperties) { this.expirationProperties = expirationProperties; }
    public String getExternProperties() { return externProperties; }
    public void setExternProperties(String externProperties) { this.externProperties = externProperties; }
    public boolean isFinalized() { return finalized; }
    public void setFinalized(boolean finalized) { this.finalized = finalized; }
    public List<ModuleAssignment> getModulesAssignedConsentModule() { return modulesAssignedConsentModule; }
    public void setModulesAssignedConsentModule(List<ModuleAssignment> modulesAssignedConsentModule) {
        this.modulesAssignedConsentModule = modulesAssignedConsentModule;
    }

    /**
     * Extract FHIR profile URL from externProperties
     */
    public String getFhirProfileUrl() {
        if (externProperties == null) return null;
        String[] props = externProperties.split(";");
        for (String prop : props) {
            if (prop.startsWith("fhirForceProfileConsent=")) {
                return prop.substring("fhirForceProfileConsent=".length());
            }
        }
        return null;
    }

    /**
     * Extract FHIR Consent Category from externProperties
     */
    public String getFhirConsentCategory() {
        if (externProperties == null) return null;
        String[] props = externProperties.split(";");
        for (String prop : props) {
            if (prop.startsWith("fhirConsentCategory=")) {
                return prop.substring("fhirConsentCategory=".length());
            }
        }
        return null;
    }

    /**
     * Extract FHIR Policy ValueSet from externProperties
     */
    public String getFhirPolicyValueSet() {
        if (externProperties == null) return null;
        String[] props = externProperties.split(";");
        for (String prop : props) {
            if (prop.startsWith("fhirPolicyValueSet=")) {
                return prop.substring("fhirPolicyValueSet=".length());
            }
        }
        return null;
    }

    /**
     * Extract FHIR Answer CodeSystem from externProperties
     */
    public String getFhirAnswerCodeSystem() {
        if (externProperties == null) return null;
        String[] props = externProperties.split(";");
        for (String prop : props) {
            if (prop.startsWith("fhirAnswerCodeSystem=")) {
                return prop.substring("fhirAnswerCodeSystem=".length());
            }
        }
        return null;
    }

    /**
     * Get accepted answer code (YES)
     */
    public String getFhirAnswerCodeYes() {
        if (externProperties == null) return null;
        String[] props = externProperties.split(";");
        for (String prop : props) {
            if (prop.startsWith("fhirAnswerCodeYes=")) {
                return prop.substring("fhirAnswerCodeYes=".length());
            }
        }
        return null;
    }
}
