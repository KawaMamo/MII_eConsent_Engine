package org.example.consent.populator;

import java.util.*;

/**
 * Represents the user's consent request/decision
 * All fields except patientId and organizationId are optional
 *
 * FIXED: Added null safety for all getters and decision lookups
 * FIXED: Added helper methods for status checking
 */
public class ConsentRequest {

    // Required fields
    private String templateKey;
    private String patientId;
    private String organizationId;

    // Optional fields for placeholder replacement (with defaults)
    private String patientName = "Patient/Patientin";
    private String institutionName = "Ihre behandelnde Einrichtung";
    private String organizationName = "Ihre Organisation";

    // Other optional fields
    private Date consentDate;
    private String sourceReference;
    private String mainProvisionType;
    private List<ModuleDecision> moduleDecisions = new ArrayList<>();
    private SignatureData signature;

    // ==========================================
    // Getters and Setters with Null Safety
    // ==========================================

    public String getTemplateKey() {
        return templateKey;
    }

    public void setTemplateKey(String templateKey) {
        this.templateKey = templateKey;
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public String getPatientName() {
        return patientName != null ? patientName : "Patient/Patientin";
    }

    public void setPatientName(String patientName) {
        this.patientName = patientName;
    }

    public String getInstitutionName() {
        return institutionName != null ? institutionName : "Ihre behandelnde Einrichtung";
    }

    public void setInstitutionName(String institutionName) {
        this.institutionName = institutionName;
    }

    public String getOrganizationName() {
        return organizationName != null ? organizationName : "Ihre Organisation";
    }

    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName;
    }

    public Date getConsentDate() {
        return consentDate;
    }

    public void setConsentDate(Date consentDate) {
        this.consentDate = consentDate;
    }

    public String getSourceReference() {
        return sourceReference;
    }

    public void setSourceReference(String sourceReference) {
        this.sourceReference = sourceReference;
    }

    public String getMainProvisionType() {
        return mainProvisionType != null ? mainProvisionType : "deny";
    }

    public void setMainProvisionType(String mainProvisionType) {
        this.mainProvisionType = mainProvisionType;
    }

    public List<ModuleDecision> getModuleDecisions() {
        return moduleDecisions != null ? moduleDecisions : Collections.emptyList();
    }

    public void setModuleDecisions(List<ModuleDecision> moduleDecisions) {
        this.moduleDecisions = moduleDecisions != null ? moduleDecisions : new ArrayList<>();
    }

    public SignatureData getSignature() {
        return signature;
    }

    public void setSignature(SignatureData signature) {
        this.signature = signature;
    }

    /**
     * Add a module decision with null safety
     */
    public void addModuleDecision(ModuleDecision decision) {
        if (decision == null) {
            throw new IllegalArgumentException("ModuleDecision cannot be null");
        }
        if (moduleDecisions == null) {
            moduleDecisions = new ArrayList<>();
        }
        moduleDecisions.add(decision);
    }

    /**
     * Get decision for a specific module with proper null safety
     */
    public ModuleDecision getDecisionForModule(String moduleKey) {
        if (moduleKey == null || moduleKey.isEmpty()) {
            return null;
        }
        if (moduleDecisions == null || moduleDecisions.isEmpty()) {
            return null;
        }
        for (ModuleDecision decision : moduleDecisions) {
            if (decision != null) {
                String decisionKey = decision.getModuleKey();
                if (decisionKey != null && decisionKey.equals(moduleKey)) {
                    return decision;
                }
            }
        }
        return null;
    }

    /**
     * Get all decisions for modules that have a specific status (case-insensitive)
     * FIXED: Case-insensitive status matching
     */
    public List<ModuleDecision> getDecisionsByStatus(String status) {
        if (status == null || moduleDecisions == null || moduleDecisions.isEmpty()) {
            return Collections.emptyList();
        }

        String upperStatus = status.toUpperCase();
        List<ModuleDecision> result = new ArrayList<>();
        for (ModuleDecision decision : moduleDecisions) {
            if (decision != null && decision.getStatus() != null) {
                String decisionStatus = decision.getStatus().toUpperCase();
                if (decisionStatus.equals(upperStatus) ||
                        (upperStatus.equals("ACCEPTED") && decisionStatus.equals("PERMIT")) ||
                        (upperStatus.equals("DECLINED") && decisionStatus.equals("DENY"))) {
                    result.add(decision);
                }
            }
        }
        return result;
    }

    /**
     * Get all accepted module decisions
     */
    public List<ModuleDecision> getAcceptedDecisions() {
        return getDecisionsByStatus("ACCEPTED");
    }

    /**
     * Get all declined module decisions
     */
    public List<ModuleDecision> getDeclinedDecisions() {
        return getDecisionsByStatus("DECLINED");
    }

    /**
     * Check if a module is accepted (case-insensitive)
     */
    public boolean isModuleAccepted(String moduleKey) {
        ModuleDecision decision = getDecisionForModule(moduleKey);
        if (decision == null) {
            return false;
        }
        String status = decision.getStatus();
        return status != null && ("ACCEPTED".equalsIgnoreCase(status) || "PERMIT".equalsIgnoreCase(status));
    }

    /**
     * Check if a module is declined (case-insensitive)
     */
    public boolean isModuleDeclined(String moduleKey) {
        ModuleDecision decision = getDecisionForModule(moduleKey);
        if (decision == null) {
            return false;
        }
        String status = decision.getStatus();
        return status != null && ("DECLINED".equalsIgnoreCase(status) || "DENY".equalsIgnoreCase(status));
    }

    /**
     * Get count of accepted modules
     */
    public long getAcceptedCount() {
        return getAcceptedDecisions().size();
    }

    /**
     * Get count of declined modules
     */
    public long getDeclinedCount() {
        return getDeclinedDecisions().size();
    }

    @Override
    public String toString() {
        return "ConsentRequest{" +
                "templateKey='" + templateKey + '\'' +
                ", patientId='" + patientId + '\'' +
                ", organizationId='" + organizationId + '\'' +
                ", patientName='" + patientName + '\'' +
                ", institutionName='" + institutionName + '\'' +
                ", consentDate=" + consentDate +
                ", moduleDecisions=" + (moduleDecisions != null ? moduleDecisions.size() : 0) +
                '}';
    }
}