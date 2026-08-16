package org.example.consent.populator;

/**
 * Centralized utility for detecting module types
 * Responsibility: Determine if a module is an intro module, decision module, etc.
 *
 * Single source of truth for module type detection - eliminates duplication
 */
public class ModuleTypeDetector {

    private static final String[] INTRO_MODULE_PATTERNS = {
            "Intro",
            "Geltungsdauer",
            "Widerrufsrecht",
            "Rekontaktierung_Intro"
    };

    private static final String[] INTRO_MODULE_EXACT_NAMES = {
            "PATDAT_Intro",
            "KKDAT_Intro",
            "BIOMAT_Intro"
    };

    /**
     * Check if a module is an intro module (no decisions, just informational text)
     *
     * @param moduleKey The module key (domain;name;version format)
     * @return true if the module is an intro module
     */
    public static boolean isIntroModule(String moduleKey) {
        if (moduleKey == null) {
            return false;
        }

        // Check exact matches
        for (String exactName : INTRO_MODULE_EXACT_NAMES) {
            if (moduleKey.contains(exactName)) {
                return true;
            }
        }

        // Check patterns
        for (String pattern : INTRO_MODULE_PATTERNS) {
            if (moduleKey.contains(pattern)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if a module is an intro module by name
     *
     * @param moduleName The module name
     * @return true if the module is an intro module
     */
    public static boolean isIntroModuleByName(String moduleName) {
        if (moduleName == null) {
            return false;
        }

        // Check exact matches
        for (String exactName : INTRO_MODULE_EXACT_NAMES) {
            if (moduleName.equals(exactName)) {
                return true;
            }
        }

        // Check patterns
        for (String pattern : INTRO_MODULE_PATTERNS) {
            if (moduleName.contains(pattern)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get all intro module patterns for debugging/logging
     */
    public static String[] getIntroModulePatterns() {
        return INTRO_MODULE_PATTERNS.clone();
    }

    /**
     * Get all intro module exact names for debugging/logging
     */
    public static String[] getIntroModuleExactNames() {
        return INTRO_MODULE_EXACT_NAMES.clone();
    }
}