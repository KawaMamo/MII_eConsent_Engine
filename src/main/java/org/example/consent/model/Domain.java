package org.example.consent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Domain {
    @JsonProperty("name")
    private String name;

    @JsonProperty("finalized")
    private boolean finalized;

    @JsonProperty("label")
    private String label;

    @JsonProperty("comment")
    private String comment;

    @JsonProperty("signerIdType")
    private List<String> signerIdType;

    @JsonProperty("externProperties")
    private String externProperties;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isFinalized() { return finalized; }
    public void setFinalized(boolean finalized) { this.finalized = finalized; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public List<String> getSignerIdType() { return signerIdType; }
    public void setSignerIdType(List<String> signerIdType) { this.signerIdType = signerIdType; }
    public String getExternProperties() { return externProperties; }
    public void setExternProperties(String externProperties) { this.externProperties = externProperties; }
}
