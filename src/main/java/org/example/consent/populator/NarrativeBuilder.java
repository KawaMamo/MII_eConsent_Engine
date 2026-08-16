package org.example.consent.populator;

import org.apache.commons.text.StringEscapeUtils;
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
 */
public class NarrativeBuilder {

    private static final Logger logger = LoggerFactory.getLogger(NarrativeBuilder.class);

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofPattern("dd.MM.yyyy")
            .withZone(ZoneId.systemDefault());

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\[([^\\]]+)\\]");
    private static final Pattern SECTION_PATTERN = Pattern.compile("^(\\d+(?:\\.\\d+)*)\\.");
    private static final Pattern CONDITIONAL_PATTERN = Pattern.compile("\\[falls zutreffend:\\s*([^\\]]*?)\\]");

    // Default fallback values
    private static final String DEFAULT_PATIENT_NAME = "Patient/Patientin";
    private static final String DEFAULT_INSTITUTION_NAME = "Ihre behandelnde Einrichtung";
    private static final String DEFAULT_ORGANIZATION_NAME = "Ihre Organisation";
    private static final String DEFAULT_SECTION = "Abschnitt";

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

        Date consentDate = request.getConsentDate() != null ?
                request.getConsentDate() : new Date();

        Map<String, ModuleDecision> decisionMap = buildDecisionMap(request);

        // Build the HTML content
        String htmlContent = buildHtmlContent(consentTemplate, request, decisionMap, config, consentDate);

        // Clean the HTML
        String finalHtml = cleanHtml(htmlContent);

        // Log for debugging
        logger.debug("Final HTML length: {}", finalHtml.length());
        logger.debug("Final HTML preview: {}", finalHtml.length() > 200 ? finalHtml.substring(0, 200) + "..." : finalHtml);

        // Check for remaining placeholders
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(finalHtml);
        List<String> remaining = new ArrayList<>();
        while (matcher.find()) {
            remaining.add(matcher.group(0));
        }
        if (!remaining.isEmpty()) {
            logger.warn("Unreplaced placeholders: {}", remaining);
        }

        try {
            narrative.setDivAsString(finalHtml);
        } catch (Exception e) {
            logger.error("Failed to set narrative HTML, using fallback", e);
            String fallbackHtml = buildFallbackHtml(consentTemplate, request, config, consentDate);
            narrative.setDivAsString(fallbackHtml);
        }

        return narrative;
    }

    /**
     * Build the HTML content as a string
     */
    private String buildHtmlContent(ConsentTemplate consentTemplate, ConsentRequest request,
                                    Map<String, ModuleDecision> decisionMap,
                                    TemplateConfiguration config, Date consentDate) {
        StringBuilder html = new StringBuilder();
        html.append("<div xmlns=\"http://www.w3.org/1999/xhtml\">");

        // Add header
        if (consentTemplate.getHeader() != null) {
            html.append(replacePlaceholders(consentTemplate.getHeader(), consentTemplate,
                    request, true, config, consentDate));
        }

        // Add title
        if (consentTemplate.getTitle() != null) {
            html.append(replacePlaceholders(consentTemplate.getTitle(), consentTemplate,
                    request, true, config, consentDate));
        }

        // Add modules
        if (consentTemplate.getModulesAssignedConsentModule() != null) {
            List<ModuleAssignment> sortedModules = new ArrayList<>(consentTemplate.getModulesAssignedConsentModule());
            sortedModules.sort(Comparator.comparingInt(ModuleAssignment::getOrderNumber));

            for (ModuleAssignment assignment : sortedModules) {
                ConsentModule module = moduleResolver.getModule(assignment.getModuleKey());
                if (module != null) {
                    ModuleDecision decision = decisionMap.get(assignment.getModuleKey());
                    String status = decision != null ? decision.getStatus() : "DECLINED";
                    boolean isAccepted = "ACCEPTED".equals(status);

                    html.append(buildModuleContent(module, isAccepted, status, consentTemplate,
                            request, config, consentDate));
                }
            }
        }

        html.append("</div>");
        return html.toString();
    }

    /**
     * Build content for a single module
     */
    private String buildModuleContent(ConsentModule module, boolean isAccepted, String status,
                                      ConsentTemplate consentTemplate, ConsentRequest request,
                                      TemplateConfiguration config, Date consentDate) {
        StringBuilder html = new StringBuilder();

        // Add title
        if (module.getTitle() != null) {
            html.append(replacePlaceholders(module.getTitle(), consentTemplate, request,
                    isAccepted, config, consentDate));
        }

        // Add text
        String text = module.getText() != null ?
                replacePlaceholders(module.getText(), consentTemplate, request,
                        isAccepted, config, consentDate) : "";

        // If intro module, just add text
        if (ModuleTypeDetector.isIntroModule(module.getName())) {
            html.append(text);
            return html.toString();
        }

        // For decision modules, wrap with status
        String statusText = isAccepted ? "✓ ICH WILLIGE EIN" : "✗ ICH WILLIGE NICHT EIN";
        String statusColor = isAccepted ? "#4CAF50" : "#f44336";

        html.append("<div style=\"border-left: 4px solid ")
                .append(statusColor)
                .append("; padding-left: 10px; margin: 10px 0;\">");
        html.append("<div style=\"font-weight: bold; color: ")
                .append(statusColor)
                .append(";\">")
                .append(statusText)
                .append("</div>");
        html.append(text);
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

    /**
     * Replace placeholders with actual values
     */
    private String replacePlaceholders(String text, ConsentTemplate consentTemplate,
                                       ConsentRequest request, boolean isAccepted,
                                       TemplateConfiguration config, Date consentDate) {
        if (text == null) return "";

        String result = text;

        // 1. Institution name
        String institutionName = request.getInstitutionName() != null ?
                StringEscapeUtils.escapeHtml4(request.getInstitutionName()) : DEFAULT_INSTITUTION_NAME;
        String escapedInstitution = Matcher.quoteReplacement(institutionName);
        result = result.replaceAll("\\[der/dem Name der behandelnden Einrichtung\\]", escapedInstitution);
        result = result.replaceAll("\\[Name der behandelnden Einrichtung\\]", escapedInstitution);
        result = result.replaceAll("\\[der/dem Name der Einrichtung\\]", escapedInstitution);
        result = result.replaceAll("\\[Name der Einrichtung\\]", escapedInstitution);

        // 2. Conditional placeholders
        result = replaceConditionalPlaceholders(result, isAccepted);

        // 3. Section numbering
        result = replaceSectionNumbering(result, consentTemplate);

        // 4. Date
        String formattedDate = DATE_FORMATTER.format(consentDate.toInstant());
        result = result.replaceAll("\\[Datum der Unterschrift\\]", formattedDate);
        result = result.replaceAll("\\[Datum\\]", formattedDate);

        // 5. Patient name
        String patientName = request.getPatientName() != null ?
                StringEscapeUtils.escapeHtml4(request.getPatientName()) : DEFAULT_PATIENT_NAME;
        String escapedName = Matcher.quoteReplacement(patientName);
        result = result.replaceAll("\\[Name des Patienten\\]", escapedName);
        result = result.replaceAll("\\[Name der Patientin\\]", escapedName);
        result = result.replaceAll("\\[Name der/des Patienten\\]", escapedName);

        // 6. Organization
        String organizationName = request.getOrganizationName() != null ?
                StringEscapeUtils.escapeHtml4(request.getOrganizationName()) : DEFAULT_ORGANIZATION_NAME;
        String escapedOrg = Matcher.quoteReplacement(organizationName);
        result = result.replaceAll("\\[Organisation\\]", escapedOrg);
        result = result.replaceAll("\\[zuständige Stelle\\]", escapedOrg);

        // 7. Validity period
        String validityText = formatValidityPeriod(config.validityPeriod);
        if (validityText == null || validityText.isEmpty()) {
            validityText = "30 Jahre";
        }
        result = result.replaceAll("\\[Gültigkeitsdauer\\]", validityText);
        result = result.replaceAll("\\[Geltungsdauer\\]", validityText);

        // 8. Module count
        if (consentTemplate.getModulesAssignedConsentModule() != null) {
            int totalModules = consentTemplate.getModulesAssignedConsentModule().size();
            result = result.replaceAll("\\[Anzahl der Module\\]", String.valueOf(totalModules));
        }

        // 9. Remove any remaining placeholders
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(result);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String placeholder = matcher.group(0);
            logger.warn("Removing unhandled placeholder: {}", placeholder);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(""));
        }
        matcher.appendTail(sb);
        result = sb.toString();

        return result;
    }

    private String replaceConditionalPlaceholders(String text, boolean isAccepted) {
        if (text == null) return "";

        String result = text;

        // Handle [falls zutreffend: content]
        Matcher matcher = CONDITIONAL_PATTERN.matcher(result);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String content = matcher.group(1).trim();
            String replacement = isAccepted ? content : "";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        result = sb.toString();

        // Handle [falls zutreffend] without colon
        if (isAccepted) {
            result = result.replaceAll("\\[falls zutreffend\\]\\s*", "");
        } else {
            result = result.replaceAll("\\[falls zutreffend\\][^<]*?(?:<[^>]*>)*?", "");
        }

        // Handle "Falls zutreffend:" without brackets
        if (!isAccepted) {
            result = result.replaceAll("Falls zutreffend:[^<]*?(?:<[^>]*>)*?", "");
        }

        return result;
    }

    private String replaceSectionNumbering(String text, ConsentTemplate consentTemplate) {
        if (text == null) return "";

        String result = text;
        result = result.replaceAll("\\[NUMMERIERUNG ANPASSEN\\]", DEFAULT_SECTION);
        return result;
    }

    private String formatValidityPeriod(String period) {
        if (period == null) return null;

        Pattern pattern = Pattern.compile("P(\\d+)([YMD])");
        Matcher matcher = pattern.matcher(period);
        if (!matcher.matches()) {
            if (period.startsWith("P")) {
                return period.replace("P", "");
            }
            return period;
        }

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
     * Clean HTML - ensures valid XHTML
     */
    private String cleanHtml(String html) {
        if (html == null || html.isEmpty()) {
            logger.warn("Empty HTML provided to cleanHtml");
            return "<div xmlns=\"http://www.w3.org/1999/xhtml\"></div>";
        }

        try {
            // Try to use JSoup for cleaning
            Document doc = Jsoup.parse(html, "", Parser.xmlParser());
            doc.outputSettings().syntax(Document.OutputSettings.Syntax.xml);

            String result = doc.body().html();

            // If JSoup returns empty, use the original
            if (result == null || result.isEmpty()) {
                logger.warn("JSoup returned empty, using original HTML");
                return html;
            }

            // Ensure namespace
            if (!result.startsWith("<div xmlns=\"http://www.w3.org/1999/xhtml\"")) {
                result = "<div xmlns=\"http://www.w3.org/1999/xhtml\">" + result + "</div>";
            }

            return result;
        } catch (Exception e) {
            logger.warn("JSoup cleaning failed, using original HTML with basic fixes", e);

            // Basic fixes
            String cleaned = html;
            cleaned = cleaned.replaceAll("<br>", "<br/>");
            cleaned = cleaned.replaceAll("<br\\s+/>", "<br/>");
            cleaned = cleaned.replaceAll("<hr>", "<hr/>");
            cleaned = cleaned.replaceAll("<hr\\s+/>", "<hr/>");
            cleaned = cleaned.replaceAll("<img([^>]*?)>", "<img$1/>");
            cleaned = cleaned.replaceAll("(?i)<b>", "<strong>");
            cleaned = cleaned.replaceAll("(?i)</b>", "</strong>");
            cleaned = cleaned.replaceAll("(?i)<i>", "<em>");
            cleaned = cleaned.replaceAll("(?i)</i>", "</em>");

            if (!cleaned.startsWith("<div xmlns=\"http://www.w3.org/1999/xhtml\"")) {
                cleaned = "<div xmlns=\"http://www.w3.org/1999/xhtml\">" + cleaned + "</div>";
            }

            return cleaned;
        }
    }

    /**
     * Build fallback HTML
     */
    private String buildFallbackHtml(ConsentTemplate consentTemplate, ConsentRequest request,
                                     TemplateConfiguration config, Date consentDate) {
        Date effectiveDate = consentDate != null ? consentDate : new Date();

        String patientName = request.getPatientName() != null ?
                StringEscapeUtils.escapeHtml4(request.getPatientName()) : DEFAULT_PATIENT_NAME;
        String institutionName = request.getInstitutionName() != null ?
                StringEscapeUtils.escapeHtml4(request.getInstitutionName()) : DEFAULT_INSTITUTION_NAME;
        String patientId = request.getPatientId() != null ?
                StringEscapeUtils.escapeHtml4(request.getPatientId()) : "Unbekannt";

        StringBuilder html = new StringBuilder();
        html.append("<div xmlns=\"http://www.w3.org/1999/xhtml\">");
        html.append("<h2>Einwilligungserklärung</h2>");
        html.append("<p><strong>Patient:</strong> ").append(patientName).append("</p>");
        html.append("<p><strong>Patienten-ID:</strong> ").append(patientId).append("</p>");
        html.append("<p><strong>Einrichtung:</strong> ").append(institutionName).append("</p>");
        html.append("<p><strong>Datum:</strong> ").append(DATE_FORMATTER.format(effectiveDate.toInstant())).append("</p>");
        html.append("<p><strong>Template:</strong> ").append(StringEscapeUtils.escapeHtml4(consentTemplate.getName())).append("</p>");
        html.append("<hr/>");

        if (consentTemplate.getModulesAssignedConsentModule() != null) {
            Map<String, ModuleDecision> decisionMap = buildDecisionMap(request);

            List<ModuleAssignment> sortedModules = new ArrayList<>(consentTemplate.getModulesAssignedConsentModule());
            sortedModules.sort(Comparator.comparingInt(ModuleAssignment::getOrderNumber));

            for (ModuleAssignment assignment : sortedModules) {
                ConsentModule module = moduleResolver.getModule(assignment.getModuleKey());
                if (module != null && !ModuleTypeDetector.isIntroModule(module.getName())) {
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
                    html.append("<p><strong>Modul:</strong> ")
                            .append(StringEscapeUtils.escapeHtml4(module.getLabel()))
                            .append("</p>");
                    html.append("</div>");
                }
            }
        }

        html.append("</div>");
        return html.toString();
    }
}