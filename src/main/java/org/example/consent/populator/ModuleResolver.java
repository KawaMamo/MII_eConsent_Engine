package org.example.consent.populator;

import org.example.consent.model.*;

import java.util.Map;

/**
 * Resolves policies and modules from template maps
 * Responsibility: Provide lookup access to policies and modules
 */
public class ModuleResolver {

    private final Map<String, ConsentPolicy> policyMap;
    private final Map<String, ConsentModule> moduleMap;
    private final Map<String, ConsentTemplate> templateMap;

    public ModuleResolver(Map<String, ConsentPolicy> policyMap,
                          Map<String, ConsentModule> moduleMap,
                          Map<String, ConsentTemplate> templateMap) {
        this.policyMap = policyMap;
        this.moduleMap = moduleMap;
        this.templateMap = templateMap;
    }

    public ConsentPolicy getPolicy(String policyKey) {
        return policyMap.get(policyKey);
    }

    public ConsentModule getModule(String moduleKey) {
        return moduleMap.get(moduleKey);
    }

    public ConsentTemplate getTemplate(String templateKey) {
        return templateMap.get(templateKey);
    }
}