package org.example.tools;


import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.r4.model.*;


public class JsonSerializationService {
    private final IParser jsonParser;

    public JsonSerializationService(IParser jsonParser) {
        this.jsonParser = jsonParser;
    }

    public String serialize(Resource resource) {
        return jsonParser.setPrettyPrint(true).encodeResourceToString(resource);
    }

    public void printJson(String title, String json) {
        System.out.println("\n=== " + title + " ===");
        System.out.println(json);
    }
}
