package org.example.tools;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hl7.fhir.common.hapi.validation.support.PrePopulatedValidationSupport;
import org.hl7.fhir.r4.model.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class FhirResourceLoader {
    private final FhirContext fhirContext;
    private final IParser jsonParser;
    private final PrePopulatedValidationSupport prePopulatedSupport;

    public FhirResourceLoader(FhirContext fhirContext, IParser jsonParser) {
        this.fhirContext = fhirContext;
        this.jsonParser = jsonParser;
        this.prePopulatedSupport = new PrePopulatedValidationSupport(fhirContext);
    }

    public PrePopulatedValidationSupport getPrePopulatedSupport() {
        return prePopulatedSupport;
    }

    public StructureDefinition loadStructureDefinition(String filePath) throws IOException {
        try (InputStream stream = Files.newInputStream(Path.of(filePath))) {
            StructureDefinition sd = jsonParser.parseResource(StructureDefinition.class, stream);
            prePopulatedSupport.addStructureDefinition(sd);
            System.out.println("Loaded StructureDefinition: " + sd.getUrl());
            return sd;
        }
    }

    public void loadCodeSystem(String filePath) throws IOException {
        try (InputStream stream = Files.newInputStream(Path.of(filePath))) {
            CodeSystem codeSystem = jsonParser.parseResource(CodeSystem.class, stream);
            prePopulatedSupport.addCodeSystem(codeSystem);
            System.out.println("Loaded CodeSystem: " + codeSystem.getUrl());
        }
    }

    public void loadValueSet(String filePath) throws IOException {
        try (InputStream stream = Files.newInputStream(Path.of(filePath))) {
            ValueSet valueSet = jsonParser.parseResource(ValueSet.class, stream);
            prePopulatedSupport.addValueSet(valueSet);
            System.out.println("Loaded ValueSet: " + valueSet.getUrl());
        }
    }
}
