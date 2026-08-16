package org.example.consent.populator;

import java.util.*;

/**
 * Represents the user's consent request/decision
 * All fields except patientId and organizationId are optional
 * FIXED: Added default values for all placeholder fields
 */
public class ConsentRequest {

    // Required fields
    private String templateKey;
    private String patientId;
    private String organizationId;

    // Optional fields for placeholder replacement (with defaults)
    private String patientName = "Patient/Patientin";  // FIXED: Default value
    private String institutionName = "Ihre behandelnde Einrichtung";  // FIXED: Default value
    private String organizationName = "Ihre Organisation";  // FIXED: Default value

    // Other optional fields
    private Date consentDate;
    private String sourceReference;
    private String mainProvisionType;
    private List<ModuleDecision> moduleDecisions = new ArrayList<>();
    private SignatureData signature;

    // Getters and setters...
    public String getTemplateKey() { return templateKey; }
    public void setTemplateKey(String templateKey) { this.templateKey = templateKey; }

    public String getPatientId() { return patientId; }
    public void setPatientId(String patientId) { this.patientId = patientId; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }

    public String getInstitutionName() { return institutionName; }
    public void setInstitutionName(String institutionName) { this.institutionName = institutionName; }

    public String getOrganizationName() { return organizationName; }
    public void setOrganizationName(String organizationName) { this.organizationName = organizationName; }

    public Date getConsentDate() { return consentDate; }
    public void setConsentDate(Date consentDate) { this.consentDate = consentDate; }

    public String getSourceReference() { return sourceReference; }
    public void setSourceReference(String sourceReference) { this.sourceReference = sourceReference; }

    public String getMainProvisionType() { return mainProvisionType; }
    public void setMainProvisionType(String mainProvisionType) { this.mainProvisionType = mainProvisionType; }

    public List<ModuleDecision> getModuleDecisions() { return moduleDecisions; }
    public void setModuleDecisions(List<ModuleDecision> moduleDecisions) { this.moduleDecisions = moduleDecisions; }

    public SignatureData getSignature() { return signature; }
    public void setSignature(SignatureData signature) { this.signature = signature; }

    public void addModuleDecision(ModuleDecision decision) {
        this.moduleDecisions.add(decision);
    }

    public ModuleDecision getDecisionForModule(String moduleKey) {
        if (moduleDecisions != null) {
            for (ModuleDecision decision : moduleDecisions) {
                if (decision.getModuleKey().equals(moduleKey)) {
                    return decision;
                }
            }
        }
        return null;
    }
}