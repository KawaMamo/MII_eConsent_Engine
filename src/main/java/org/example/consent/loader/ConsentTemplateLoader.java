package org.example.consent.loader;


import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.example.consent.model.ExchangeFormatDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Loader for ExchangeFormatDefinition (Consent Template)
 */
public class ConsentTemplateLoader {

    private static final Logger logger = LoggerFactory.getLogger(ConsentTemplateLoader.class);
    private final ObjectMapper objectMapper;

    public ConsentTemplateLoader() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.objectMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Load template from file path
     */
    public ExchangeFormatDefinition loadFromFile(String filePath) throws IOException {
        logger.info("Loading consent template from: {}", filePath);
        File file = new File(filePath);
        return loadFromFile(file);
    }

    /**
     * Load template from file
     */
    public ExchangeFormatDefinition loadFromFile(File file) throws IOException {
        if (!file.exists()) {
            throw new IOException("File not found: " + file.getAbsolutePath());
        }

        String json = new String(Files.readAllBytes(file.toPath()));
        return loadFromJson(json);
    }

    /**
     * Load template from JSON string
     */
    public ExchangeFormatDefinition loadFromJson(String json) throws IOException {
        ExchangeFormatDefinition template = objectMapper.readValue(json, ExchangeFormatDefinition.class);
        logger.info("Loaded template: {} (version: {})",
                template.getDomain() != null ? template.getDomain().getName() : "unknown",
                template.getSupportedVersion());

        // Log template statistics
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

    /**
     * Load template from resources
     */
    public ExchangeFormatDefinition loadFromResources(String resourcePath) throws IOException {
        logger.info("Loading consent template from resources: {}", resourcePath);

        // Try to load from classpath
        ClassLoader classLoader = getClass().getClassLoader();
        java.net.URL resource = classLoader.getResource(resourcePath);

        if (resource == null) {
            throw new IOException("Resource not found: " + resourcePath);
        }

        File file = new File(resource.getFile());
        return loadFromFile(file);
    }

    /**
     * Get all template files from a directory
     */
    public List<File> findTemplateFiles(String directory) throws IOException {
        List<File> templateFiles = new ArrayList<>();
        Path dirPath = Paths.get(directory);

        if (!Files.exists(dirPath)) {
            throw new IOException("Directory not found: " + directory);
        }

        Files.walk(dirPath)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".json"))
                .forEach(p -> templateFiles.add(p.toFile()));

        logger.info("Found {} template files in directory: {}", templateFiles.size(), directory);
        return templateFiles;
    }
}
