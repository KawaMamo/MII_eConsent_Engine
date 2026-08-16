package org.example.consent.populator;

import java.util.Objects;

/**
 * Represents a user's decision for a specific module
 *
 * FIXED: Status normalization to uppercase for consistent matching
 * FIXED: Provision type normalization
 */
public class ModuleDecision {

    private String moduleKey;
    private String moduleName;
    private String status; // "ACCEPTED" or "DECLINED"
    private String provisionType; // "permit" or "deny"
    private String comment;

    // ==========================================
    // Getters and Setters with Null Safety
    // ==========================================

    public String getModuleKey() {
        return moduleKey;
    }

    public void setModuleKey(String moduleKey) {
        if (moduleKey == null || moduleKey.isEmpty()) {
            throw new IllegalArgumentException("Module key cannot be null or empty");
        }
        this.moduleKey = moduleKey;
    }

    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    public String getStatus() {
        return status;
    }

    /**
     * Set status with normalization to uppercase
     * FIXED: Normalizes to uppercase for consistent matching
     */
    public void setStatus(String status) {
        if (status == null) {
            throw new IllegalArgumentException("Status cannot be null");
        }
        // Normalize to uppercase
        String upperStatus = status.toUpperCase();
        // FIXED: Accept both status and provision type values
        if (!"ACCEPTED".equals(upperStatus) && !"DECLINED".equals(upperStatus) &&
                !"PERMIT".equals(upperStatus) && !"DENY".equals(upperStatus)) {
            throw new IllegalArgumentException(
                    "Status must be ACCEPTED, DECLINED, PERMIT, or DENY. Got: " + status
            );
        }
        // Map PERMIT/DENY to ACCEPTED/DECLINED for consistency
        if ("PERMIT".equals(upperStatus)) {
            this.status = "ACCEPTED";
        } else if ("DENY".equals(upperStatus)) {
            this.status = "DECLINED";
        } else {
            this.status = upperStatus;
        }
    }

    public String getProvisionType() {
        return provisionType != null ? provisionType : "deny";
    }

    /**
     * Set provision type with normalization to lowercase
     * FIXED: Normalizes to lowercase for consistent matching
     */
    public void setProvisionType(String provisionType) {
        if (provisionType != null) {
            String lowerType = provisionType.toLowerCase();
            if (!"permit".equals(lowerType) && !"deny".equals(lowerType)) {
                throw new IllegalArgumentException(
                        "Provision type must be 'permit' or 'deny', got: " + provisionType
                );
            }
            this.provisionType = lowerType;
        } else {
            this.provisionType = "deny";
        }
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    // ==========================================
    // Utility Methods
    // ==========================================

    public boolean isAccepted() {
        return "ACCEPTED".equals(status);
    }

    public boolean isDeclined() {
        return "DECLINED".equals(status);
    }

    public boolean isPermit() {
        return "permit".equals(provisionType);
    }

    public boolean isDeny() {
        return "deny".equals(provisionType);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ModuleDecision that = (ModuleDecision) o;
        return Objects.equals(moduleKey, that.moduleKey) &&
                Objects.equals(status, that.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(moduleKey, status);
    }

    @Override
    public String toString() {
        return "ModuleDecision{" +
                "moduleKey='" + moduleKey + '\'' +
                ", moduleName='" + moduleName + '\'' +
                ", status='" + status + '\'' +
                ", provisionType='" + provisionType + '\'' +
                '}';
    }
}