package org.example.structureDefinition;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

public class ProfileVersionExtractorTest {

    private final ProfileVersionExtractor extractor = new ProfileVersionExtractor();

    @Test
    void shouldExtractFhirVersionFourFromMII_Consent_1_0_9(){
        File consent = new File("src/test/resources/MII_PR_Consent_Einwilligung.json");
        String fhirVersion = extractor.getFhirVersion(consent);
        assertEquals("4", fhirVersion);
    }

    @Test
    void shouldExtractFhirVersionFiveFromTOUConsent(){
        File consent = new File("src/test/resources/Profile-Consent.json");
        String fhirVersion = extractor.getFhirVersion(consent);
        assertEquals("5", fhirVersion);
    }

    @Test
    void shouldReturnAnException_whenNoFhirVersionExists(){
        File consent = new File("src/test/resources/mii-exa-kardio-herzinsuffizienz-unbekannt.json");
        assertThrows(RuntimeException.class, ()-> extractor.getFhirVersion(consent));
    }
}
