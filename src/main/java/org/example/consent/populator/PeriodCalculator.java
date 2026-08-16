package org.example.consent.populator;

import org.hl7.fhir.r4.model.Period;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Calculates period end dates based on validity period strings
 * Responsibility: Parse VALIDITY_PERIOD and calculate end dates
 *
 * FIXED: Supports full ISO 8601 duration format (P[n]Y[n]M[n]D, P[n]W, etc.)
 * FIXED: Supports both uppercase and lowercase 'p' prefix
 */
public class PeriodCalculator {

    private static final Logger logger = LoggerFactory.getLogger(PeriodCalculator.class);

    // FIXED: Full ISO 8601 duration pattern supporting multiple units
    // Supports: P[n]Y[n]M[n]D, P[n]W, and combinations
    // Examples: P30Y, P1Y6M, P2Y3M15D, P5Y, P6M, P1W
    private static final Pattern ISO_8601_DURATION_PATTERN = Pattern.compile(
            "P(?:(\\d+(?:\\.\\d+)?)Y)?(?:(\\d+(?:\\.\\d+)?)M)?(?:(\\d+(?:\\.\\d+)?)D)?(?:(\\d+(?:\\.\\d+)?)W)?",
            Pattern.CASE_INSENSITIVE
    );

    // Alternative pattern for simpler durations (backward compatibility)
    private static final Pattern SIMPLE_DURATION_PATTERN = Pattern.compile("P(\\d+)([YMDW])", Pattern.CASE_INSENSITIVE);

    /**
     * Create a period with start and calculated end date
     */
    public Period createPeriod(Date startDate, String validityPeriod) {
        Period period = new Period();
        period.setStart(startDate);

        Date endDate = calculateEndDate(startDate, validityPeriod);
        period.setEnd(endDate);
        logger.debug("Period: {} to {}", startDate, endDate);

        return period;
    }

    /**
     * Calculate end date from start date and validity period
     * FIXED: Supports full ISO 8601 duration format
     */
    public Date calculateEndDate(Date startDate, String validityPeriod) {
        if (startDate == null || validityPeriod == null) {
            throw new IllegalStateException("Validity period not set.");
        }

        if (validityPeriod.isEmpty()) {
            throw new IllegalStateException("Validity period cannot be empty.");
        }

        // Normalize: ensure uppercase P
        String normalizedPeriod = validityPeriod.toUpperCase();
        if (!normalizedPeriod.startsWith("P")) {
            throw new IllegalStateException("Invalid VALIDITY_PERIOD format: must start with 'P'. Got: " + validityPeriod);
        }

        LocalDateTime start = startDate.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();

        LocalDateTime end = parseDurationAndAdd(start, normalizedPeriod);

        return Date.from(end.atZone(ZoneId.systemDefault()).toInstant());
    }

    /**
     * Parse ISO 8601 duration and add to the start date
     * FIXED: Handles full ISO 8601 duration format
     */
    private LocalDateTime parseDurationAndAdd(LocalDateTime start, String duration) {
        LocalDateTime result = start;

        // First try full ISO 8601 pattern
        Matcher matcher = ISO_8601_DURATION_PATTERN.matcher(duration);
        if (matcher.matches()) {
            boolean hasUnits = false;

            // Extract years
            String yearsStr = matcher.group(1);
            if (yearsStr != null && !yearsStr.isEmpty()) {
                double years = Double.parseDouble(yearsStr);
                result = result.plus((long) years, ChronoUnit.YEARS);
                hasUnits = true;
                logger.debug("Added {} years", years);
            }

            // Extract months
            String monthsStr = matcher.group(2);
            if (monthsStr != null && !monthsStr.isEmpty()) {
                double months = Double.parseDouble(monthsStr);
                result = result.plus((long) months, ChronoUnit.MONTHS);
                hasUnits = true;
                logger.debug("Added {} months", months);
            }

            // Extract days
            String daysStr = matcher.group(3);
            if (daysStr != null && !daysStr.isEmpty()) {
                double days = Double.parseDouble(daysStr);
                result = result.plus((long) days, ChronoUnit.DAYS);
                hasUnits = true;
                logger.debug("Added {} days", days);
            }

            // Extract weeks
            String weeksStr = matcher.group(4);
            if (weeksStr != null && !weeksStr.isEmpty()) {
                double weeks = Double.parseDouble(weeksStr);
                result = result.plus((long) weeks, ChronoUnit.WEEKS);
                hasUnits = true;
                logger.debug("Added {} weeks", weeks);
            }

            if (hasUnits) {
                return result;
            }
        }

        // Fallback to simple pattern (backward compatibility)
        Matcher simpleMatcher = SIMPLE_DURATION_PATTERN.matcher(duration);
        if (simpleMatcher.matches()) {
            int amount = Integer.parseInt(simpleMatcher.group(1));
            String unit = simpleMatcher.group(2);

            switch (unit) {
                case "Y":
                    result = result.plus(amount, ChronoUnit.YEARS);
                    logger.debug("Added {} years", amount);
                    return result;
                case "M":
                    result = result.plus(amount, ChronoUnit.MONTHS);
                    logger.debug("Added {} months", amount);
                    return result;
                case "D":
                    result = result.plus(amount, ChronoUnit.DAYS);
                    logger.debug("Added {} days", amount);
                    return result;
                case "W":
                    result = result.plus(amount, ChronoUnit.WEEKS);
                    logger.debug("Added {} weeks", amount);
                    return result;
                default:
                    // Unknown unit
            }
        }

        // If we get here, we couldn't parse the duration
        logger.warn("Could not parse VALIDITY_PERIOD: {}, using default 30 years", duration);
        return start.plus(30, ChronoUnit.YEARS);
    }

    /**
     * Parse and format a validity period for human-readable display
     */
    public String formatValidityPeriod(String period) {
        if (period == null) return null;

        String normalized = period.toUpperCase();

        // Try full ISO 8601 pattern
        Matcher matcher = ISO_8601_DURATION_PATTERN.matcher(normalized);
        if (matcher.matches()) {
            StringBuilder result = new StringBuilder();

            String years = matcher.group(1);
            if (years != null && !years.isEmpty()) {
                int y = (int) Double.parseDouble(years);
                result.append(y).append(" Jahr").append(y > 1 ? "e" : "");
            }

            String months = matcher.group(2);
            if (months != null && !months.isEmpty()) {
                if (result.length() > 0) result.append(", ");
                int m = (int) Double.parseDouble(months);
                result.append(m).append(" Monat").append(m > 1 ? "e" : "");
            }

            String days = matcher.group(3);
            if (days != null && !days.isEmpty()) {
                if (result.length() > 0) result.append(", ");
                int d = (int) Double.parseDouble(days);
                result.append(d).append(" Tag").append(d > 1 ? "e" : "");
            }

            String weeks = matcher.group(4);
            if (weeks != null && !weeks.isEmpty()) {
                if (result.length() > 0) result.append(", ");
                int w = (int) Double.parseDouble(weeks);
                result.append(w).append(" Woche").append(w > 1 ? "n" : "");
            }

            return result.length() > 0 ? result.toString() : period;
        }

        // Try simple pattern
        Matcher simpleMatcher = SIMPLE_DURATION_PATTERN.matcher(normalized);
        if (simpleMatcher.matches()) {
            int amount = Integer.parseInt(simpleMatcher.group(1));
            String unit = simpleMatcher.group(2);

            switch (unit) {
                case "Y": return amount + " Jahr" + (amount > 1 ? "e" : "");
                case "M": return amount + " Monat" + (amount > 1 ? "e" : "");
                case "D": return amount + " Tag" + (amount > 1 ? "e" : "");
                case "W": return amount + " Woche" + (amount > 1 ? "n" : "");
                default: return period;
            }
        }

        return period;
    }
}