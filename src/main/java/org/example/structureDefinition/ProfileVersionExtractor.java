package org.example.structureDefinition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

public class ProfileVersionExtractor {

    private final ObjectMapper mapper = new ObjectMapper();

    public String getFhirVersion(File fhirResource){
        try {
            JsonNode structureDefinitionJson = mapper.readTree(fhirResource);
            String fhirVersion = structureDefinitionJson.path("fhirVersion").asText();
            if(Objects.nonNull(fhirVersion)){
                if(fhirVersion.startsWith("4"))
                    return "4";
                if (fhirVersion.startsWith("5"))
                    return "5";
                if(fhirVersion.startsWith("6"))
                    throw new RuntimeException("So version 6 is out there call Development team to add support to this version");
                else throw new RuntimeException("No FHIR Version was detected or the Version is higher than 6");
            }else throw new RuntimeException("No FHIR Version was detected");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
