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
 */
public class PeriodCalculator {

    private static final Logger logger = LoggerFactory.getLogger(PeriodCalculator.class);
    private static final Pattern VALIDITY_PERIOD_PATTERN = Pattern.compile("P(\\d+)([YMD])");

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
     */
    public Date calculateEndDate(Date startDate, String validityPeriod) {
        if (startDate == null || validityPeriod == null) {
            throw new IllegalStateException("Validity period not set.");
        }

        Matcher matcher = VALIDITY_PERIOD_PATTERN.matcher(validityPeriod);
        if (!matcher.matches()) {
            throw new IllegalStateException("Invalid VALIDITY_PERIOD format: " + validityPeriod);
        }

        int amount = Integer.parseInt(matcher.group(1));
        String unit = matcher.group(2);

        LocalDateTime start = startDate.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();

        LocalDateTime end;
        switch (unit) {
            case "Y": end = start.plus(amount, ChronoUnit.YEARS); break;
            case "M": end = start.plus(amount, ChronoUnit.MONTHS); break;
            case "D": end = start.plus(amount, ChronoUnit.DAYS); break;
            default: throw new IllegalStateException("Unknown validity period unit: " + unit);
        }

        return Date.from(end.atZone(ZoneId.systemDefault()).toInstant());
    }
}