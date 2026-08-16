package org.example.consent;


import org.hl7.fhir.r4.model.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ConsentBuilder {
    private final Consent consent;
    private String profileUrl;

    public ConsentBuilder() {
        this.consent = new Consent();
    }

    public ConsentBuilder withProfile(String profileUrl) {
        this.profileUrl = profileUrl;
        consent.getMeta().addProfile(profileUrl);
        return this;
    }

    public ConsentBuilder withNarrative(String narrativeText) {
        Narrative narrative = new Narrative();
        narrative.setStatus(Narrative.NarrativeStatus.GENERATED);
        narrative.setDivAsString("<div xmlns=\"http://www.w3.org/1999/xhtml\">" + narrativeText + "</div>");
        consent.setText(narrative);
        return this;
    }

    public ConsentBuilder withStatus(Consent.ConsentState status) {
        consent.setStatus(status);
        return this;
    }

    public ConsentBuilder withScope(String system, String code, String display) {
        CodeableConcept scope = new CodeableConcept();
        scope.addCoding().setSystem(system).setCode(code).setDisplay(display);
        consent.setScope(scope);
        return this;
    }

    public ConsentBuilder addCategory(String system, String code, String display) {
        CodeableConcept category = new CodeableConcept();
        category.addCoding().setSystem(system).setCode(code).setDisplay(display);
        consent.addCategory(category);
        return this;
    }

    public ConsentBuilder withPatient(String patientReference) {
        consent.setPatient(new Reference(patientReference));
        return this;
    }

    public ConsentBuilder withDateTime(Date dateTime) {
        consent.setDateTime(dateTime);
        return this;
    }

    public ConsentBuilder addOrganization(String organizationReference) {
        consent.addOrganization(new Reference(organizationReference));
        return this;
    }

    public ConsentBuilder withSource(String sourceReference) {
        consent.setSource(new Reference(sourceReference));
        return this;
    }

    public ConsentBuilder addPolicy(String policyUri) {
        Consent.ConsentPolicyComponent policy = new Consent.ConsentPolicyComponent();
        policy.setUri(policyUri);
        consent.addPolicy(policy);
        return this;
    }

    public ConsentBuilder withPolicyRule(String system, String code, String display) {
        CodeableConcept policyRule = new CodeableConcept();
        policyRule.addCoding().setSystem(system).setCode(code).setDisplay(display);
        consent.setPolicyRule(policyRule);
        return this;
    }

    public ConsentBuilder withProvision(Consent.ProvisionComponent provision) {
        consent.setProvision(provision);
        return this;
    }

    public Consent build() {
        return consent;
    }

    /**
     * Creates a main provision with nested provisions for MII Consent
     * This properly creates the nested structure required by the profile
     */
    public static Consent.ProvisionComponent createMiiConsentProvision(Date startDate,
                                                                       Date endDate,
                                                                       List<Consent.ProvisionComponent> nestedProvisions) {
        Consent.ProvisionComponent mainProvision = new Consent.ProvisionComponent();
        mainProvision.setType(Consent.ConsentProvisionType.PERMIT);

        // Add period for main provision (required by German Consent profile)
        Period mainPeriod = new Period();
        mainPeriod.setStart(startDate);
        if (endDate != null) {
            mainPeriod.setEnd(endDate);
        }
        mainProvision.setPeriod(mainPeriod);

        // Add nested provisions (required by MII profile)
        if (nestedProvisions != null && !nestedProvisions.isEmpty()) {
            for (Consent.ProvisionComponent nested : nestedProvisions) {
                mainProvision.addProvision(nested);
            }
        }

        return mainProvision;
    }

    /**
     * Creates a nested provision for a specific MII policy module
     */
    public static Consent.ProvisionComponent createNestedProvision(String type,
                                                                   Date startDate,
                                                                   Date endDate,
                                                                   String codeSystem,
                                                                   String code,
                                                                   String display) {
        Consent.ProvisionComponent provision = new Consent.ProvisionComponent();
        provision.setType(Consent.ConsentProvisionType.PERMIT);

        // Add period for nested provision (required by MII profile)
        Period period = new Period();
        period.setStart(startDate);
        if (endDate != null) {
            period.setEnd(endDate);
        }
        provision.setPeriod(period);

        // Add code for the module (required by MII profile)
        if (codeSystem != null && code != null) {
            CodeableConcept provisionCode = new CodeableConcept();
            provisionCode.addCoding()
                    .setSystem(codeSystem)
                    .setCode(code)
                    .setDisplay(display);
            provision.addCode(provisionCode);
        }

        return provision;
    }

    /**
     * Creates multiple nested provisions for different MII policy modules
     */
    public static List<Consent.ProvisionComponent> createMultipleNestedProvisions(Date startDate,
                                                                                  Date endDate,
                                                                                  List<PolicyModule> modules) {
        List<Consent.ProvisionComponent> provisions = new ArrayList<>();
        for (PolicyModule module : modules) {
            Consent.ProvisionComponent provision = createNestedProvision(
                    "permit",
                    startDate,
                    endDate,
                    module.getCodeSystem(),
                    module.getCode(),
                    module.getDisplay()
            );
            provisions.add(provision);
        }
        return provisions;
    }
}
