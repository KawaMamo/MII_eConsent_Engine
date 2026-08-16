package org.example.consent.loader;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.example.consent.model.ExchangeFormatDefinition;
import org.example.consent.model.ConsentTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class ConsentTemplateLoader {

    private static final Logger logger = LoggerFactory.getLogger(ConsentTemplateLoader.class);
    private final ObjectMapper objectMapper;

    public ConsentTemplateLoader() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.objectMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    public ExchangeFormatDefinition loadFromFile(String filePath) throws IOException {
        logger.info("Loading consent template from: {}", filePath);
        File file = new File(filePath);
        return loadFromFile(file);
    }

    public ExchangeFormatDefinition loadFromFile(File file) throws IOException {
        if (!file.exists()) {
            throw new IOException("File not found: " + file.getAbsolutePath());
        }

        String json = new String(Files.readAllBytes(file.toPath()));
        return loadFromJson(json);
    }

    public ExchangeFormatDefinition loadFromJson(String json) throws IOException {
        ExchangeFormatDefinition template = objectMapper.readValue(json, ExchangeFormatDefinition.class);

        // FIXED: Propagate domain externProperties to each template
        if (template.getDomain() != null && template.getTemplatesConsentTemplate() != null) {
            String domainExternProperties = template.getDomain().getExternProperties();
            if (domainExternProperties != null && !domainExternProperties.isEmpty()) {
                for (ConsentTemplate consentTemplate : template.getTemplatesConsentTemplate()) {
                    consentTemplate.setDomainExternProperties(domainExternProperties);
                }
            }
        }

        logger.info("Loaded template: {} (version: {})",
                template.getDomain() != null ? template.getDomain().getName() : "unknown",
                template.getSupportedVersion());

        if (template.getTemplatesConsentTemplate() != null) {
            logger.info("Found {} consent templates", template.getTemplatesConsentTemplate().size());
        }
        if (template.getModulesConsentModule() != null) {
            logger.info("Found {} consent modules", template.getModulesConsentModule().size());
        }
        if (template.getPoliciesConsentPolicy() != null) {
            logger.info("Found {} consent policies", template.getPoliciesConsentPolicy().size());
        }

        return template;
    }
}