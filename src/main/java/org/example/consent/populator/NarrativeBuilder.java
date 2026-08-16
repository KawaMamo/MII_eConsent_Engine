package org.example.consent.populator;

import org.example.consent.model.*;
import org.hl7.fhir.r4.model.Narrative;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the human-readable narrative (text.div) for the Consent resource
 * Responsibility: Generate HTML narrative with placeholder replacement and decision status
 */
public class NarrativeBuilder {

    private static final Logger logger = LoggerFactory.getLogger(NarrativeBuilder.class);

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofPattern("dd.MM.yyyy")
            .withZone(ZoneId.systemDefault());

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\[([^\\]]+)\\]");
    private static final Pattern SECTION_PATTERN = Pattern.compile("^(\\d+(?:\\.\\d+)*)\\.");
    private static final Pattern CONDITIONAL_PATTERN = Pattern.compile("\\[falls zutreffend:\\s*([^\\]]*?)\\]");

    private final ModuleResolver moduleResolver;

    public NarrativeBuilder(ModuleResolver moduleResolver) {
        this.moduleResolver = moduleResolver;
    }

    /**
     * Build dynamic narrative with placeholder replacement and decision status
     */
    public Narrative build(ConsentTemplate consentTemplate, ConsentRequest request,
                           TemplateConfiguration config) {
        Narrative narrative = new Narrative();
        narrative.setStatus(Narrative.NarrativeStatus.GENERATED);

        Map<String, ModuleDecision> decisionMap = buildDecisionMap(request);

        StringBuilder html = new StringBuilder();
        html.append("<div xmlns=\"http://www.w3.org/1999/xhtml\">");

        // Add header with placeholder replacement
        if (consentTemplate.getHeader() != null) {
            String header = replacePlaceholders(consentTemplate.getHeader(), consentTemplate,
                    request, true, config);
            html.append(header);
        }

        // Add title with placeholder replacement
        if (consentTemplate.getTitle() != null) {
            String title = replacePlaceholders(consentTemplate.getTitle(), consentTemplate,
                    request, true, config);
            html.append(title);
        }

        // Add module content
        if (consentTemplate.getModulesAssignedConsentModule() != null) {
            List<ModuleAssignment> sortedModules = new ArrayList<>(consentTemplate.getModulesAssignedConsentModule());
            sortedModules.sort(Comparator.comparingInt(ModuleAssignment::getOrderNumber));

            for (ModuleAssignment assignment : sortedModules) {
                ConsentModule module = moduleResolver.getModule(assignment.getModuleKey());
                if (module != null) {
                    ModuleDecision decision = decisionMap.get(assignment.getModuleKey());
                    String status = decision != null ? decision.getStatus() : "DECLINED";
                    boolean isAccepted = "ACCEPTED".equals(status);

                    String moduleHtml = buildModuleHtml(module, isAccepted, status, consentTemplate, request, config);
                    html.append(moduleHtml);
                }
            }
        }

        html.append("</div>");

        // Clean the HTML - CRITICAL: ensure we have content
        String rawHtml = html.toString();
        logger.debug("Raw HTML length: {}", rawHtml.length());

        String finalHtml = cleanHtml(rawHtml);
        logger.debug("Cleaned HTML length: {}", finalHtml.length());
        logger.debug("Cleaned HTML preview: {}", finalHtml.substring(0, Math.min(200, finalHtml.length())));

        // Check for remaining placeholders
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(finalHtml);
        if (matcher.find()) {
            logger.warn("WARNING: Unreplaced placeholders remain in narrative");
            while (matcher.find()) {
                logger.warn("  Unreplaced: {}", matcher.group(0));
            }
        }

        try {
            narrative.setDivAsString(finalHtml);
        } catch (Exception e) {
            logger.error("Failed to set narrative HTML, creating fallback", e);
            String fallbackHtml = buildFallbackNarrative(consentTemplate, request, config);
            narrative.setDivAsString(fallbackHtml);
        }

        return narrative;
    }

    /**
     * Build HTML for a single module with decision status
     */
    private String buildModuleHtml(ConsentModule module, boolean isAccepted, String status,
                                   ConsentTemplate consentTemplate, ConsentRequest request,
                                   TemplateConfiguration config) {
        StringBuilder html = new StringBuilder();

        // Add module title
        if (module.getTitle() != null) {
            String title = replacePlaceholders(module.getTitle(), consentTemplate, request, isAccepted, config);
            html.append(title);
        }

        // Add module text
        String originalText = module.getText() != null ?
                replacePlaceholders(module.getText(), consentTemplate, request, isAccepted, config) : "";

        // For intro modules, just add the text without decision status
        if (isIntroModule(module.getName())) {
            html.append(originalText);
            return html.toString();
        }

        // For decision modules, wrap with status indicator
        String statusText = isAccepted ? "✓ ICH WILLIGE EIN" : "✗ ICH WILLIGE NICHT EIN";
        String statusColor = isAccepted ? "#4CAF50" : "#f44336";

        html.append("<div class=\"module-decision\" style=\"border-left: 4px solid ")
                .append(statusColor)
                .append("; padding-left: 10px; margin: 10px 0;\">");
        html.append("<div style=\"font-weight: bold; color: ")
                .append(statusColor)
                .append(";\">")
                .append(statusText)
                .append("</div>");
        html.append(originalText);
        html.append("</div>");

        return html.toString();
    }

    private Map<String, ModuleDecision> buildDecisionMap(ConsentRequest request) {
        Map<String, ModuleDecision> decisionMap = new HashMap<>();
        if (request.getModuleDecisions() != null) {
            for (ModuleDecision decision : request.getModuleDecisions()) {
                decisionMap.put(decision.getModuleKey(), decision);
            }
        }
        return decisionMap;
    }

    private String replacePlaceholders(String text, ConsentTemplate consentTemplate,
                                       ConsentRequest request, boolean isAccepted,
                                       TemplateConfiguration config) {
        if (text == null) return "";

        String result = text;

        // 1. Replace institution name placeholders
        String institutionName = request.getInstitutionName() != null ?
                request.getInstitutionName() : "Ihre behandelnde Einrichtung";
        String escapedInstitution = Matcher.quoteReplacement(institutionName);
        result = result.replaceAll("\\[der/dem Name der behandelnden Einrichtung\\]", escapedInstitution);
        result = result.replaceAll("\\[Name der behandelnden Einrichtung\\]", escapedInstitution);
        result = result.replaceAll("\\[der/dem Name der Einrichtung\\]", escapedInstitution);
        result = result.replaceAll("\\[Name der Einrichtung\\]", escapedInstitution);

        // 2. Replace conditional placeholders
        result = replaceConditionalPlaceholders(result, isAccepted);

        // 3. Replace section numbering placeholders
        result = replaceSectionNumbering(result, consentTemplate);

        // 4. Replace date placeholders
        Date consentDate = request.getConsentDate() != null ? request.getConsentDate() : new Date();
        String formattedDate = DATE_FORMATTER.format(consentDate.toInstant());
        result = result.replaceAll("\\[Datum der Unterschrift\\]", formattedDate);
        result = result.replaceAll("\\[Datum\\]", formattedDate);

        // 5. Replace patient name placeholder
        if (request.getPatientName() != null) {
            String escapedName = Matcher.quoteReplacement(request.getPatientName());
            result = result.replaceAll("\\[Name des Patienten\\]", escapedName);
            result = result.replaceAll("\\[Name der Patientin\\]", escapedName);
            result = result.replaceAll("\\[Name der/des Patienten\\]", escapedName);
        }

        // 6. Replace organization placeholders
        String organizationName = request.getOrganizationName() != null ?
                request.getOrganizationName() : "Ihre Organisation";
        String escapedOrg = Matcher.quoteReplacement(organizationName);
        result = result.replaceAll("\\[Organisation\\]", escapedOrg);
        result = result.replaceAll("\\[zuständige Stelle\\]", escapedOrg);

        // 7. Replace validity period placeholders
        String validityText = formatValidityPeriod(config.validityPeriod);
        result = result.replaceAll("\\[Gültigkeitsdauer\\]", validityText);
        result = result.replaceAll("\\[Geltungsdauer\\]", validityText);

        // 8. Replace policy count placeholders
        if (consentTemplate.getModulesAssignedConsentModule() != null) {
            int totalModules = consentTemplate.getModulesAssignedConsentModule().size();
            result = result.replaceAll("\\[Anzahl der Module\\]", String.valueOf(totalModules));
        }

        return result;
    }

    private String replaceConditionalPlaceholders(String text, boolean isAccepted) {
        if (text == null) return "";

        String result = text;
        Matcher matcher = CONDITIONAL_PATTERN.matcher(result);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String content = matcher.group(1).trim();
            String replacement = isAccepted ? content : "";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        result = sb.toString();

        result = result.replaceAll("\\[falls zutreffend\\]", isAccepted ? "" : "");

        if (!isAccepted) {
            result = result.replaceAll("Falls zutreffend:[^<]*?(?:<[^>]*>)*?", "");
        }

        return result;
    }

    private String replaceSectionNumbering(String text, ConsentTemplate consentTemplate) {
        if (text == null) return "";

        String result = text;
        Map<String, String> sectionNumbers = new HashMap<>();

        if (consentTemplate.getModulesAssignedConsentModule() != null) {
            for (ModuleAssignment assignment : consentTemplate.getModulesAssignedConsentModule()) {
                ConsentModule module = moduleResolver.getModule(assignment.getModuleKey());
                if (module != null) {
                    String title = module.getTitle();
                    if (title != null) {
                        Matcher m = SECTION_PATTERN.matcher(title.trim());
                        if (m.find()) {
                            sectionNumbers.put(module.getName(), m.group(1));
                        }
                    }
                }
            }
        }

        result = result.replaceAll("\\[NUMMERIERUNG ANPASSEN\\]", "Abschnitt");
        return result;
    }

    private String formatValidityPeriod(String period) {
        if (period == null) return "";
        Pattern pattern = Pattern.compile("P(\\d+)([YMD])");
        Matcher matcher = pattern.matcher(period);
        if (!matcher.matches()) return period;

        int amount = Integer.parseInt(matcher.group(1));
        String unit = matcher.group(2);

        switch (unit) {
            case "Y": return amount + " Jahr" + (amount > 1 ? "e" : "");
            case "M": return amount + " Monat" + (amount > 1 ? "e" : "");
            case "D": return amount + " Tag" + (amount > 1 ? "e" : "");
            default: return period;
        }
    }

    /**
     * Clean HTML - ensures XHTML compliance
     */
    private String cleanHtml(String html) {
        if (html == null) return "";
        String cleaned = html;

        // Fix common HTML issues
        cleaned = cleaned.replaceAll("<br>", "<br/>");
        cleaned = cleaned.replaceAll("<br\\s+/>", "<br/>");
        cleaned = cleaned.replaceAll("<hr>", "<hr/>");
        cleaned = cleaned.replaceAll("<hr\\s+/>", "<hr/>");
        cleaned = cleaned.replaceAll("<img([^>]*?)>", "<img$1/>");
        cleaned = cleaned.replaceAll("style=\"\"", "");
        cleaned = cleaned.replaceAll("&(?![a-zA-Z]+;)", "&amp;");
        cleaned = cleaned.replaceAll("(?i)</?font[^>]*>", "");
        cleaned = cleaned.replaceAll("(?i)<b>", "<strong>");
        cleaned = cleaned.replaceAll("(?i)</b>", "</strong>");
        cleaned = cleaned.replaceAll("(?i)<i>", "<em>");
        cleaned = cleaned.replaceAll("(?i)</i>", "</em>");

        return cleaned;
    }

    /**
     * Build fallback narrative when HTML generation fails
     */
    private String buildFallbackNarrative(ConsentTemplate consentTemplate, ConsentRequest request,
                                          TemplateConfiguration config) {
        StringBuilder html = new StringBuilder();
        html.append("<div xmlns=\"http://www.w3.org/1999/xhtml\">");
        html.append("<h2>Einwilligungserklärung</h2>");
        html.append("<p><strong>Patient:</strong> ").append(request.getPatientId()).append("</p>");
        html.append("<p><strong>Datum:</strong> ").append(DATE_FORMATTER.format(new Date().toInstant())).append("</p>");
        html.append("<p><strong>Template:</strong> ").append(consentTemplate.getName()).append("</p>");
        html.append("<hr/>");

        if (consentTemplate.getModulesAssignedConsentModule() != null) {
            Map<String, ModuleDecision> decisionMap = buildDecisionMap(request);

            List<ModuleAssignment> sortedModules = new ArrayList<>(consentTemplate.getModulesAssignedConsentModule());
            sortedModules.sort(Comparator.comparingInt(ModuleAssignment::getOrderNumber));

            for (ModuleAssignment assignment : sortedModules) {
                ConsentModule module = moduleResolver.getModule(assignment.getModuleKey());
                if (module != null && !isIntroModule(module.getName())) {
                    ModuleDecision decision = decisionMap.get(assignment.getModuleKey());
                    boolean isAccepted = decision != null && "ACCEPTED".equals(decision.getStatus());
                    String statusText = isAccepted ? "✓ EINWILLIGUNG ERTEILT" : "✗ EINWILLIGUNG VERWEIGERT";
                    String statusColor = isAccepted ? "#4CAF50" : "#f44336";

                    html.append("<div style=\"border-left: 4px solid ")
                            .append(statusColor)
                            .append("; padding-left: 10px; margin: 10px 0;\">");
                    html.append("<div style=\"font-weight: bold; color: ")
                            .append(statusColor)
                            .append(";\">")
                            .append(statusText)
                            .append("</div>");
                    html.append("<p><strong>Modul:</strong> ").append(module.getLabel()).append("</p>");
                    html.append("</div>");
                }
            }
        }

        html.append("</div>");
        return html.toString();
    }

    private boolean isIntroModule(String moduleName) {
        if (moduleName == null) return false;
        return moduleName.contains("Intro") ||
                moduleName.contains("Geltungsdauer") ||
                moduleName.contains("Widerrufsrecht") ||
                moduleName.contains("Rekontaktierung_Intro") ||
                moduleName.equals("PATDAT_Intro") ||
                moduleName.equals("KKDAT_Intro") ||
                moduleName.equals("BIOMAT_Intro");
    }
}