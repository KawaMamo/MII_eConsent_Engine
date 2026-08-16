package org.example.consent.populator;

public class ModuleDecision {
    private String moduleKey;
    private String moduleName;
    private String status; // "ACCEPTED" or "DECLINED" - default: "DECLINED"
    private String provisionType; // "permit" or "deny" - default: "deny"
    private String comment;

    // Getters and setters
    public String getModuleKey() { return moduleKey; }
    public void setModuleKey(String moduleKey) { this.moduleKey = moduleKey; }

    public String getModuleName() { return moduleName; }
    public void setModuleName(String moduleName) { this.moduleName = moduleName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getProvisionType() { return provisionType; }
    public void setProvisionType(String provisionType) { this.provisionType = provisionType; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
