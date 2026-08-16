package org.example.validation;


import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.hl7.fhir.r4.model.*;


public class FhirValidatorService {
    private final FhirContext fhirContext;
    private final ValidationSupportChain supportChain;

    public FhirValidatorService(FhirContext fhirContext, ValidationSupportChain supportChain) {
        this.fhirContext = fhirContext;
        this.supportChain = supportChain;
    }

    public ValidationResult validate(Resource resource) {
        FhirInstanceValidator instanceValidator = new FhirInstanceValidator(fhirContext);
        instanceValidator.setValidationSupport(supportChain);

        FhirValidator validator = fhirContext.newValidator();
        validator.registerValidatorModule(instanceValidator);

        return validator.validateWithResult(resource);
    }

    public void printValidationResults(ValidationResult result) {
        System.out.println("\n=== Validation Result ===");
        System.out.println("Is Valid: " + result.isSuccessful());
        System.out.println("Total Messages: " + result.getMessages().size());

        for (SingleValidationMessage msg : result.getMessages()) {
            System.out.println("[" + msg.getSeverity() + "] " +
                    msg.getLocationString() + " - " +
                    msg.getMessage());
        }
    }
}
