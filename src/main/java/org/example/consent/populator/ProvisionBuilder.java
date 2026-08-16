package org.example.consent.populator;

import org.example.consent.model.*;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.Period;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Builds the provisions (structured consent decisions) for the Consent resource
 * Responsibility: Build main and nested provisions with proper permit/deny types
 */
public class ProvisionBuilder {

    private static final Logger logger = LoggerFactory.getLogger(ProvisionBuilder.class);

    private final ModuleResolver moduleResolver;
    private final PeriodCalculator periodCalculator;

    public ProvisionBuilder(ModuleResolver moduleResolver, PeriodCalculator periodCalculator) {
        this.moduleResolver = moduleResolver;
        this.periodCalculator = periodCalculator;
    }

    /**
     * Build all provisions (main + nested) from template and decisions
     */
    public Consent.ProvisionComponent buildProvisions(ConsentTemplate consentTemplate,
                                                      ConsentRequest request,
                                                      TemplateConfiguration config) {
        Consent.ProvisionComponent mainProvision = new Consent.ProvisionComponent();

        String provisionType = getMainProvisionType(request);
        Consent.ConsentProvisionType type = "permit".equalsIgnoreCase(provisionType) ?
                Consent.ConsentProvisionType.PERMIT : Consent.ConsentProvisionType.DENY;
        mainProvision.setType(type);
        logger.info("Main provision type: {}", type);

        Date startDate = new Date();
        Period mainPeriod = periodCalculator.createPeriod(startDate, config.validityPeriod);
        mainProvision.setPeriod(mainPeriod);
        logger.info("Main provision period: {} to {}", mainPeriod.getStart(), mainPeriod.getEnd());

        if (consentTemplate.getModulesAssignedConsentModule() != null) {
            List<Consent.ProvisionComponent> nestedProvisions = new ArrayList<>();

            List<ModuleAssignment> sortedModules = new ArrayList<>(consentTemplate.getModulesAssignedConsentModule());
            sortedModules.sort(Comparator.comparingInt(ModuleAssignment::getOrderNumber));

            Map<String, ModuleDecision> decisionMap = buildDecisionMap(request);

            for (ModuleAssignment assignment : sortedModules) {
                if (isIntroModuleKey(assignment.getModuleKey())) {
                    continue;
                }

                ConsentModule module = moduleResolver.getModule(assignment.getModuleKey());
                if (module != null) {
                    ModuleDecision decision = decisionMap.get(assignment.getModuleKey());
                    Consent.ProvisionComponent nestedProvision = buildNestedProvision(
                            module, assignment, consentTemplate, decision, startDate, config);

                    if (nestedProvision != null) {
                        nestedProvisions.add(nestedProvision);
                        String status = decision != null ? decision.getStatus() : "DECLINED";
                        int codeCount = nestedProvision.getCode() != null ? nestedProvision.getCode().size() : 0;
                        logger.debug("Added nested provision for module: {} (status: {}, codes: {})",
                                module.getName(), status, codeCount);
                    }
                }
            }

            for (Consent.ProvisionComponent nested : nestedProvisions) {
                mainProvision.addProvision(nested);
            }

            logger.info("Built {} nested provisions", nestedProvisions.size());
        }

        return mainProvision;
    }

    private Consent.ProvisionComponent buildNestedProvision(ConsentModule module, ModuleAssignment assignment,
                                                            ConsentTemplate consentTemplate,
                                                            ModuleDecision decision,
                                                            Date startDate,
                                                            TemplateConfiguration config) {
        Consent.ProvisionComponent provision = new Consent.ProvisionComponent();

        String provisionType = "deny";
        if (decision != null) {
            if ("ACCEPTED".equalsIgnoreCase(decision.getStatus())) {
                provisionType = "permit";
            } else if ("DECLINED".equalsIgnoreCase(decision.getStatus())) {
                provisionType = "deny";
            } else if (decision.getProvisionType() != null) {
                provisionType = decision.getProvisionType();
            }
        }

        Consent.ConsentProvisionType type = "permit".equalsIgnoreCase(provisionType) ?
                Consent.ConsentProvisionType.PERMIT : Consent.ConsentProvisionType.DENY;
        provision.setType(type);

        String status = decision != null ? decision.getStatus() : "DECLINED";
        logger.debug("Nested provision type: {} (status: {})", type, status);

        Period nestedPeriod = periodCalculator.createPeriod(startDate, config.validityPeriod);
        provision.setPeriod(nestedPeriod);

        String policySystem = config.policyValueSet;
        if (policySystem == null) {
            policySystem = "urn:oid:2.16.840.1.113883.3.1937.777.24.5.3";
        }

        List<PolicyAssignment> policyAssignments = module.getPoliciesAssignedConsentPolicy();
        if (policyAssignments != null) {
            for (PolicyAssignment policyAssignment : policyAssignments) {
                String policyKey = policyAssignment.getPolicyKey();
                ConsentPolicy policy = moduleResolver.getPolicy(policyKey);
                if (policy != null) {
                    String fhirCode = policy.getFhirPolicyCode();
                    if (fhirCode != null) {
                        org.hl7.fhir.r4.model.CodeableConcept code = new org.hl7.fhir.r4.model.CodeableConcept();
                        code.addCoding()
                                .setSystem(policySystem)
                                .setCode(fhirCode)
                                .setDisplay(policy.getLabel());
                        provision.addCode(code);
                    } else {
                        logger.warn("Policy {} has no FHIR code, but provision type {} preserves decision",
                                policyKey, type);
                    }
                } else {
                    logger.warn("Policy not found: {}", policyKey);
                }
            }
        }

        return provision;
    }

    private Map<String, ModuleDecision> buildDecisionMap(ConsentRequest request) {
        Map<String, ModuleDecision> decisionMap = new HashMap<>();
        if (request.getModuleDecisions() != null) {
            for (ModuleDecision decision : request.getModuleDecisions()) {
                decisionMap.put(decision.getModuleKey(), decision);
            }
        }
        return decisionMap;
    }

    private String getMainProvisionType(ConsentRequest request) {
        return request.getMainProvisionType() != null ? request.getMainProvisionType() : "deny";
    }

    private boolean isIntroModuleKey(String moduleKey) {
        if (moduleKey == null) return false;
        return moduleKey.contains("Intro") ||
                moduleKey.contains("Geltungsdauer") ||
                moduleKey.contains("Widerrufsrecht") ||
                moduleKey.contains("Rekontaktierung_Intro");
    }
}