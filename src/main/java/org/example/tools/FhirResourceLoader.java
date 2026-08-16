package org.example.tools;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.common.hapi.validation.support.PrePopulatedValidationSupport;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.hl7.fhir.r4.model.ValueSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

/**
 * Loads FHIR resources from files or classpath
 * FIXED: Added classpath loading for tests and CI/CD
 */
public class FhirResourceLoader {

    private static final Logger logger = LoggerFactory.getLogger(FhirResourceLoader.class);

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

    // ==========================================
    // File-based loading (for main application)
    // ==========================================

    public StructureDefinition loadStructureDefinition(String filePath) throws IOException {
        try (InputStream stream = java.nio.file.Files.newInputStream(java.nio.file.Path.of(filePath))) {
            return parseAndAddStructureDefinition(stream, filePath);
        }
    }

    public void loadCodeSystem(String filePath) throws IOException {
        try (InputStream stream = java.nio.file.Files.newInputStream(java.nio.file.Path.of(filePath))) {
            parseAndAddCodeSystem(stream, filePath);
        }
    }

    public void loadValueSet(String filePath) throws IOException {
        try (InputStream stream = java.nio.file.Files.newInputStream(java.nio.file.Path.of(filePath))) {
            parseAndAddValueSet(stream, filePath);
        }
    }

    // ==========================================
    // Classpath-based loading (for tests/CI/CD)
    // ==========================================

    public StructureDefinition loadStructureDefinitionFromClasspath(String resourcePath) throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IOException("Resource not found on classpath: " + resourcePath);
            }
            return parseAndAddStructureDefinition(stream, resourcePath);
        }
    }

    public void loadCodeSystemFromClasspath(String resourcePath) throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IOException("Resource not found on classpath: " + resourcePath);
            }
            parseAndAddCodeSystem(stream, resourcePath);
        }
    }

    public void loadValueSetFromClasspath(String resourcePath) throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IOException("Resource not found on classpath: " + resourcePath);
            }
            parseAndAddValueSet(stream, resourcePath);
        }
    }

    // ==========================================
    // Private parsing methods
    // ==========================================

    private StructureDefinition parseAndAddStructureDefinition(InputStream stream, String source) throws IOException {
        StructureDefinition sd = jsonParser.parseResource(StructureDefinition.class, stream);
        prePopulatedSupport.addStructureDefinition(sd);
        logger.info("Loaded StructureDefinition: {} from {}", sd.getUrl(), source);
        return sd;
    }

    private void parseAndAddCodeSystem(InputStream stream, String source) throws IOException {
        CodeSystem cs = jsonParser.parseResource(CodeSystem.class, stream);
        prePopulatedSupport.addCodeSystem(cs);
        logger.info("Loaded CodeSystem: {} from {}", cs.getUrl(), source);
    }

    private void parseAndAddValueSet(InputStream stream, String source) throws IOException {
        ValueSet vs = jsonParser.parseResource(ValueSet.class, stream);
        prePopulatedSupport.addValueSet(vs);
        logger.info("Loaded ValueSet: {} from {}", vs.getUrl(), source);
    }
}