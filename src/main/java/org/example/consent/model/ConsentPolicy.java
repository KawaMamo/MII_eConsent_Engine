package org.example.consent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ConsentPolicy {
    @JsonProperty("domainName")
    private String domainName;

    @JsonProperty("name")
    private String name;

    @JsonProperty("label")
    private String label;

    @JsonProperty("version")
    private String version;

    @JsonProperty("comment")
    private String comment;

    @JsonProperty("externProperties")
    private String externProperties;

    @JsonProperty("finalized")
    private boolean finalized;

    // Getters and setters
    public String getDomainName() { return domainName; }
    public void setDomainName(String domainName) { this.domainName = domainName; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public String getExternProperties() { return externProperties; }
    public void setExternProperties(String externProperties) { this.externProperties = externProperties; }
    public boolean isFinalized() { return finalized; }
    public void setFinalized(boolean finalized) { this.finalized = finalized; }

    /**
     * Extract FHIR policy code from externProperties
     */
    public String getFhirPolicyCode() {
        if (externProperties == null) return null;
        String[] props = externProperties.split(";");
        for (String prop : props) {
            if (prop.startsWith("fhirPolicyCode=")) {
                return prop.substring("fhirPolicyCode=".length());
            }
        }
        return null;
    }
}
