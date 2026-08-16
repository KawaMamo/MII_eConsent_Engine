package org.example.consent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Represents a Consent Module from the template
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConsentModule {

    @JsonProperty("domainName")
    private String domainName;

    @JsonProperty("name")
    private String name;

    @JsonProperty("label")
    private String label;

    @JsonProperty("version")
    private String version;

    @JsonProperty("text")
    private String text;

    @JsonProperty("title")
    private String title;

    @JsonProperty("shortText")
    private String shortText;

    @JsonProperty("comment")
    private String comment;

    @JsonProperty("externProperties")
    private String externProperties;

    @JsonProperty("finalized")
    private boolean finalized;

    @JsonProperty("policiesAssignedConsentPolicy")
    private List<PolicyAssignment> policiesAssignedConsentPolicy;

    // Getters and setters...
    public String getDomainName() { return domainName; }
    public void setDomainName(String domainName) { this.domainName = domainName; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getShortText() { return shortText; }
    public void setShortText(String shortText) { this.shortText = shortText; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public String getExternProperties() { return externProperties; }
    public void setExternProperties(String externProperties) { this.externProperties = externProperties; }
    public boolean isFinalized() { return finalized; }
    public void setFinalized(boolean finalized) { this.finalized = finalized; }
    public List<PolicyAssignment> getPoliciesAssignedConsentPolicy() { return policiesAssignedConsentPolicy; }
    public void setPoliciesAssignedConsentPolicy(List<PolicyAssignment> policiesAssignedConsentPolicy) {
        this.policiesAssignedConsentPolicy = policiesAssignedConsentPolicy;
    }

    /**
     * Extract FHIR question code from externProperties
     * FIXED: Uses proper property parsing
     */
    public String getFhirQuestionCode() {
        if (externProperties == null) return null;

        String[] props = externProperties.split(";");
        for (String prop : props) {
            if (prop == null) continue;
            String trimmedProp = prop.trim();
            if (trimmedProp.startsWith("fhirQuestionCode=")) {
                return trimmedProp.substring("fhirQuestionCode=".length()).trim();
            }
        }
        return null;
    }
}