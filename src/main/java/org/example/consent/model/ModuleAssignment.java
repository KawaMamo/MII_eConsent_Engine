package org.example.consent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ModuleAssignment {
    @JsonProperty("moduleKey")
    private String moduleKey;

    @JsonProperty("mandatory")
    private boolean mandatory;

    @JsonProperty("orderNumber")
    private int orderNumber;

    @JsonProperty("displayCheckBoxes")
    private List<String> displayCheckBoxes;

    public String getModuleKey() { return moduleKey; }
    public void setModuleKey(String moduleKey) { this.moduleKey = moduleKey; }
    public boolean isMandatory() { return mandatory; }
    public void setMandatory(boolean mandatory) { this.mandatory = mandatory; }
    public int getOrderNumber() { return orderNumber; }
    public void setOrderNumber(int orderNumber) { this.orderNumber = orderNumber; }
    public List<String> getDisplayCheckBoxes() { return displayCheckBoxes; }
    public void setDisplayCheckBoxes(List<String> displayCheckBoxes) { this.displayCheckBoxes = displayCheckBoxes; }
}
