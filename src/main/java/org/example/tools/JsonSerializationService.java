package org.example.tools;

import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for serializing FHIR resources to JSON
 */
public class JsonSerializationService {

    private static final Logger logger = LoggerFactory.getLogger(JsonSerializationService.class);
    private final IParser jsonParser;

    public JsonSerializationService(IParser jsonParser) {
        this.jsonParser = jsonParser;
    }

    public String serialize(IBaseResource resource) {
        if (resource == null) {
            logger.warn("Attempted to serialize null resource");
            return "{}";
        }
        try {
            String json = jsonParser.setPrettyPrint(true).encodeResourceToString(resource);
            logger.debug("Serialized JSON length: {}", json != null ? json.length() : 0);
            return json;
        } catch (Exception e) {
            logger.error("Failed to serialize resource: {}", e.getMessage(), e);
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    public void printJson(String title, String json) {
        if (json == null || json.isEmpty()) {
            System.out.println("\n=== " + title + " ===");
            System.out.println("(Empty JSON)");
            return;
        }
        System.out.println("\n=== " + title + " ===");
        System.out.println(json);
    }
}