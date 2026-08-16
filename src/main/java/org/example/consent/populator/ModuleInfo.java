package org.example.consent.populator;

public class ModuleInfo {
    private String moduleKey;
    private String moduleName;
    private String moduleLabel;
    private boolean mandatory;
    private int orderNumber;
    private String defaultStatus;
    private String displayText;
    private String moduleType;

    // Getters and setters
    public String getModuleKey() { return moduleKey; }
    public void setModuleKey(String moduleKey) { this.moduleKey = moduleKey; }

    public String getModuleName() { return moduleName; }
    public void setModuleName(String moduleName) { this.moduleName = moduleName; }

    public String getModuleLabel() { return moduleLabel; }
    public void setModuleLabel(String moduleLabel) { this.moduleLabel = moduleLabel; }

    public boolean isMandatory() { return mandatory; }
    public void setMandatory(boolean mandatory) { this.mandatory = mandatory; }

    public int getOrderNumber() { return orderNumber; }
    public void setOrderNumber(int orderNumber) { this.orderNumber = orderNumber; }

    public String getDefaultStatus() { return defaultStatus; }
    public void setDefaultStatus(String defaultStatus) { this.defaultStatus = defaultStatus; }

    public String getDisplayText() { return displayText; }
    public void setDisplayText(String displayText) { this.displayText = displayText; }

    public String getModuleType() { return moduleType; }
    public void setModuleType(String moduleType) { this.moduleType = moduleType; }
}
