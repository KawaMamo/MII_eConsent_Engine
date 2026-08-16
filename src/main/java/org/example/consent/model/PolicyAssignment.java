package org.example.consent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PolicyAssignment {
    @JsonProperty("policyKey")
    private String policyKey;

    @JsonProperty("comment")
    private String comment;

    @JsonProperty("expirationProperties")
    private String expirationProperties;

    @JsonProperty("externProperties")
    private String externProperties;

    public String getPolicyKey() { return policyKey; }
    public void setPolicyKey(String policyKey) { this.policyKey = policyKey; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public String getExpirationProperties() { return expirationProperties; }
    public void setExpirationProperties(String expirationProperties) { this.expirationProperties = expirationProperties; }
    public String getExternProperties() { return externProperties; }
    public void setExternProperties(String externProperties) { this.externProperties = externProperties; }
}
