# FHIR Consent Management System

[![Java](https://img.shields.io/badge/Java-11%2B-blue.svg)](https://adoptopenjdk.net/)
[![FHIR](https://img.shields.io/badge/FHIR-R4-brightgreen.svg)](https://hl7.org/fhir/R4/)
[![HAPI FHIR](https://img.shields.io/badge/HAPI_FHIR-8.10.1-orange.svg)](https://hapifhir.io/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Build Status](https://img.shields.io/badge/build-passing-brightgreen.svg)]()
[![Tests](https://img.shields.io/badge/tests-16%20passing-brightgreen.svg)]()

---

## 📖 Table of Contents

- [Overview](#-overview)
- [Features](#-features)
- [Architecture](#-architecture)
- [Prerequisites](#-prerequisites)
- [Installation](#-installation)
- [Project Structure](#-project-structure)
- [Core Components](#-core-components)
- [Workflow](#-workflow)
- [Usage Guide](#-usage-guide)
- [API Reference](#-api-reference)
- [Configuration](#-configuration)
- [Validation](#-validation)
- [Testing](#-testing)
- [Troubleshooting](#-troubleshooting)
- [Contributing](#-contributing)
- [License](#-license)

---

## 🎯 Overview

The **FHIR Consent Management System** is a robust Java-based solution for generating, validating, and serializing FHIR R4 Consent resources compliant with the German **Medical Informatics Initiative (MII)** specifications.

It transforms complex consent templates (`ExchangeFormatDefinition`) into fully validated FHIR resources with:
- ✅ Proper nested provisions
- ✅ Dynamic narrative generation
- ✅ Automatic policy resolution
- ✅ Period end date calculation
- ✅ Multi-version template support (1.6.d & 1.7.2)

### Why This Matters

In German healthcare, patient consent for research data usage must be:
- **Standardized** (FHIR R4 compliant)
- **Validatable** programmatically
- **Legally sound** with complete human-readable narrative
- **Interoperable** across healthcare systems

This system ensures all these requirements are met through automated, template-driven consent generation.

---

## ✨ Features

### Core Capabilities

| Feature | Description |
|---------|-------------|
| **Template-Driven Generation** | Parse ExchangeFormatDefinition templates and generate FHIR Consent resources |
| **Dynamic Module Processing** | Process consent modules with per-module accept/decline decisions |
| **Nested Provisions** | Automatically build nested provisions with proper permit/deny types |
| **Policy Resolution** | Automatically resolve policy codes from template mappings |
| **Period End Calculation** | Calculate consent validity periods from template (supports full ISO 8601) |
| **Dynamic Narrative** | Generate human-readable HTML narrative matching structured data |
| **Placeholder Replacement** | Replace template placeholders with actual values |
| **Profile Validation** | Validate generated resources against MII and German Consent profiles |
| **No Hardcoded Values** | Everything extracted from templates and profiles |
| **Multi-Version Support** | Supports FHIR R4 and R5 (R4 currently implemented) |
| **Full ISO 8601 Support** | Handles complex duration formats (P1Y6M, P2Y3M15D, etc.) |

### Technical Features

- ✅ **SOLID Principles** - Clean, maintainable, testable architecture
- ✅ **Builder Pattern** - Fluent API for building consent resources
- ✅ **Factory Pattern** - Version-agnostic adapter factory
- ✅ **Validation Support** - Comprehensive validation with custom CodeSystems and ValueSets
- ✅ **JSON Serialization** - Pretty-printed FHIR JSON output
- ✅ **Stateless Design** - Thread-safe for concurrent usage
- ✅ **Comprehensive Testing** - 16+ unit tests with edge case coverage
- ✅ **HTML Sanitization** - JSoup-based XHTML cleaning

---

## 🏗️ Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    FHIR Consent Management System                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                   ADMIN PHASE                                       │    │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────┐  │    │
│  │  │  Load FHIR   │  │  Load Code-  │  │  Load Consent            │  │    │
│  │  │  Profiles    │  │  Systems &   │  │  Template                │  │    │
│  │  │              │  │  ValueSets   │  │                          │  │    │
│  │  └──────────────┘  └──────────────┘  └──────────────────────────┘  │    │
│  │         │                 │                  │                     │    │
│  │         └─────────────────┼──────────────────┘                     │    │
│  │                           ▼                                         │    │
│  │              ┌──────────────────────┐                               │    │
│  │              │  Generate Snapshots  │                               │    │
│  │              └──────────────────────┘                               │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                              │                                              │
│                              ▼                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                    USER PHASE                                       │    │
│  │  ┌─────────────────────────────────────────────────────────────┐    │    │
│  │  │  ConsentRequest: Patient ID, Organization ID,               │    │    │
│  │  │  Module Decisions (ACCEPTED/DECLINED), Signature            │    │    │
│  │  └─────────────────────────────────────────────────────────────┘    │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                              │                                              │
│                              ▼                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                   SYSTEM PHASE                                      │    │
│  │  ┌──────────────────────────────────────────────────────────────┐   │    │
│  │  │  ConsentPopulator: Build Consent Resource                    │   │    │
│  │  │  - Set profile from template                                 │   │    │
│  │  │  - Generate dynamic narrative                                │   │    │
│  │  │  - Build nested provisions with permit/deny                  │   │    │
│  │  │  - Calculate period end dates                                │   │    │
│  │  │  - Resolve policies from template                            │   │    │
│  │  └──────────────────────────────────────────────────────────────┘   │    │
│  │                           ▼                                         │    │
│  │  ┌──────────────────────────────────────────────────────────────┐   │    │
│  │  │  FhirValidatorService: Validate against profiles             │   │    │
│  │  └──────────────────────────────────────────────────────────────┘   │    │
│  │                           ▼                                         │    │
│  │  ┌──────────────────────────────────────────────────────────────┐   │    │
│  │  │  JsonSerializationService: Output FHIR JSON                  │   │    │
│  │  └──────────────────────────────────────────────────────────────┘   │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Component Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Main Application                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌────────────────────────┐  ┌────────────────────────┐                   │
│  │   FhirResourceLoader   │  │   ConsentTemplateLoader│                   │
│  │  - Load profiles       │  │  - Parse template JSON │                   │
│  │  - Load CodeSystems    │  │  - Build model objects │                   │
│  │  - Load ValueSets      │  │                       │                   │
│  └────────────────────────┘  └────────────────────────┘                   │
│              │                          │                                  │
│              └──────────────┬───────────┘                                  │
│                             ▼                                              │
│              ┌────────────────────────────┐                                │
│              │  ValidationSupportFactory  │                                │
│              │  - Build support chain     │                                │
│              │  - Create snapshot support │                                │
│              └────────────────────────────┘                                │
│                             │                                              │
│                             ▼                                              │
│              ┌────────────────────────────┐                                │
│              │  SnapshotGeneratorService  │                                │
│              │  - Generate snapshots      │                                │
│              │  - Merge base + diff       │                                │
│              └────────────────────────────┘                                │
│                             │                                              │
│                             ▼                                              │
│              ┌────────────────────────────┐                                │
│              │     ConsentPopulator       │                                │
│              │  - Build Consent resource  │                                │
│              │  - Dynamic narrative       │                                │
│              │  - Nested provisions       │                                │
│              │  - Placeholder replacement │                                │
│              └────────────────────────────┘                                │
│                             │                                              │
│              ┌──────────────┴──────────────┐                               │
│              ▼                             ▼                               │
│  ┌────────────────────────┐  ┌────────────────────────┐                   │
│  │  FhirValidatorService  │  │JsonSerializationService│                   │
│  │  - Validate resource   │  │  - Serialize to JSON   │                   │
│  │  - Check profiles      │  │  - Pretty print        │                   │
│  └────────────────────────┘  └────────────────────────┘                   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 📋 Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| **Java** | JDK 11 or higher | Required for running the application |
| **Maven** | 3.6+ | For building and dependency management |
| **FHIR Version** | R4 (4.0.1) | Currently supported |
| **HAPI FHIR** | 8.10.1 | Core FHIR library |
| **Jackson** | 2.22.1 | JSON parsing |

---

## 🚀 Installation

### 1. Clone the Repository

```bash
git clone https://github.com/yourusername/fhir-consent-management.git
cd fhir-consent-management
```

### 2. Build with Maven

```bash
mvn clean compile
```

### 3. Prepare Resource Files

Place the following files in `src/main/resources/`:

```bash
src/main/resources/
├── ConsentManagementConsent.json              # German Consent base profile
├── MII_PR_Consent_Einwilligung.json           # MII Consent profile
├── CodeSystem-MiiConsentAnswerCodeSystem.json # Answer codes
├── CodeSystem-MiiConsentPolicyCodeSystem.json # Policy codes
├── CodeSystem-MiiConsentVersionModuleCodeSystem.json # Version modules
├── ValueSet-MiiConsentPolicyValueSet.json     # Policy value set
├── 2023-05-12-MII-BroadConsent-1.6.d.json    # Template 1.6.d
└── 2025-01-21 MII BC 1.7.2 mit Erweiterungsmodul PROM.json # Template 1.7.2
```

### 4. Run the Application

```bash
mvn exec:java -Dexec.mainClass="org.example.Main"
```

---

## 📁 Project Structure

```
fhir-consent-management/
├── src/
│   └── main/
│       ├── java/
│       │   └── org/
│       │       └── example/
│       │           ├── Main.java                          # Application entry point
│       │           ├── consent/
│       │           │   ├── loader/
│       │           │   │   └── ConsentTemplateLoader.java # Template loader
│       │           │   ├── model/
│       │           │   │   ├── ExchangeFormatDefinition.java
│       │           │   │   ├── ConsentPolicy.java
│       │           │   │   ├── ConsentModule.java
│       │           │   │   └── ConsentTemplate.java
│       │           │   └── populator/
│       │           │       ├── ConsentPopulator.java     # Main orchestrator
│       │           │       ├── TemplateConfiguration.java
│       │           │       ├── ConfigurationExtractor.java
│       │           │       ├── NarrativeBuilder.java
│       │           │       ├── ProvisionBuilder.java
│       │           │       ├── PeriodCalculator.java
│       │           │       ├── ModuleResolver.java
│       │           │       ├── ModuleTypeDetector.java
│       │           │       ├── ConsentRequest.java
│       │           │       ├── ModuleDecision.java
│       │           │       ├── ModuleInfo.java
│       │           │       └── SignatureData.java
│       │           ├── tools/
│       │           │   ├── FhirResourceLoader.java      # FHIR resource loader
│       │           │   └── JsonSerializationService.java # JSON serializer
│       │           └── validation/
│       │               ├── FhirValidatorService.java     # Validator service
│       │               ├── SnapshotGeneratorService.java # Snapshot generator
│       │               └── ValidationSupportFactory.java # Support chain factory
│       └── resources/
│           ├── ConsentManagementConsent.json
│           ├── MII_PR_Consent_Einwilligung.json
│           ├── CodeSystem-MiiConsentAnswerCodeSystem.json
│           ├── CodeSystem-MiiConsentPolicyCodeSystem.json
│           ├── CodeSystem-MiiConsentVersionModuleCodeSystem.json
│           ├── ValueSet-MiiConsentPolicyValueSet.json
│           ├── 2023-05-12-MII-BroadConsent-1.6.d.json
│           └── 2025-01-21 MII BC 1.7.2 mit Erweiterungsmodul PROM.json
├── src/
│   └── test/
│       └── java/
│           └── org/
│               └── example/
│                   ├── ConsentPopulatorTest.java
│                   └── ProfileVersionExtractorTest.java
├── pom.xml
└── README.md
```

---

## 🔧 Core Components

### 1. ConsentPopulator

The heart of the system - builds FHIR Consent resources from templates.

```java
ConsentPopulator populator = new ConsentPopulator(template);
Consent consent = populator.populateConsent(request, miiSnapshot);
```

**Key Responsibilities:**
- Extracts configuration from template and profile
- Builds dynamic narrative with placeholder replacement
- Creates nested provisions with permit/deny types
- Calculates period end dates (full ISO 8601 support)
- Resolves policies from template mappings

### 2. ConsentRequest

Represents the user's consent decisions.

```java
ConsentRequest request = new ConsentRequest();
request.setPatientId("Patient/123456");
request.setOrganizationId("Organization/hospital-123");
request.setInstitutionName("Universitätsklinikum Hamburg");
request.setPatientName("Max Mustermann");

for (ModuleInfo module : modules) {
    ModuleDecision decision = new ModuleDecision();
    decision.setModuleKey(module.getModuleKey());
    decision.setStatus("ACCEPTED");  // or "DECLINED"
    request.addModuleDecision(decision);
}
```

### 3. FhirResourceLoader

Loads FHIR resources (profiles, CodeSystems, ValueSets) from files or classpath.

```java
FhirResourceLoader loader = new FhirResourceLoader(fhirContext, jsonParser);
StructureDefinition profile = loader.loadStructureDefinition("profile.json");
loader.loadCodeSystem("codesystem.json");
loader.loadValueSet("valueset.json");
```

### 4. ValidationSupportFactory

Builds the validation support chain with optimized ordering.

```java
ValidationSupportFactory factory = new ValidationSupportFactory(fhirContext, prePopulated);
ValidationSupportChain chain = factory.createSupportChain();
SnapshotGeneratingValidationSupport snapshotSupport = factory.createSnapshotSupport();
```

### 5. NarrativeBuilder

Builds the human-readable narrative with placeholder replacement and decision status.

```java
NarrativeBuilder builder = new NarrativeBuilder(moduleResolver);
Narrative narrative = builder.build(consentTemplate, request, config);
```

### 6. ProvisionBuilder

Builds main and nested provisions with proper permit/deny types.

```java
ProvisionBuilder builder = new ProvisionBuilder(moduleResolver, periodCalculator);
Consent.ProvisionComponent provisions = builder.buildProvisions(
    consentTemplate, request, config, consentDate);
```

### 7. PeriodCalculator

Parses ISO 8601 duration formats and calculates end dates.

```java
PeriodCalculator calculator = new PeriodCalculator();
Period period = calculator.createPeriod(startDate, "P30Y");
// Supports: P30Y, P1Y6M, P2Y3M15D, P5Y, P6M, P1W, etc.
```

---

## 🔄 Workflow

### Complete Process Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           WORKFLOW OVERVIEW                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  ADMIN PHASE                                                        │    │
│  │                                                                     │    │
│  │  1. Load FHIR Profiles (German Consent, MII Consent)              │    │
│  │  2. Load CodeSystems (Answer, Policy, Version Module)              │    │
│  │  3. Load ValueSet (MII Policy)                                     │    │
│  │  4. Load Consent Template (ExchangeFormatDefinition)               │    │
│  │  5. Generate Snapshots (merge base + differential)                │    │
│  │  6. Build validation support chain                                 │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                    │                                       │
│                                    ▼                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  USER PHASE                                                         │    │
│  │                                                                     │    │
│  │  1. View available modules and their requirements                  │    │
│  │  2. Submit consent request:                                        │    │
│  │     - Patient ID                                                   │    │
│  │     - Organization ID                                              │    │
│  │     - Patient Name (for placeholder replacement)                   │    │
│  │     - Institution Name (for placeholder replacement)               │    │
│  │     - Module decisions (ACCEPTED/DECLINED)                        │    │
│  │     - Signature (optional, image)                                 │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                    │                                       │
│                                    ▼                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  SYSTEM PHASE                                                       │    │
│  │                                                                     │    │
│  │  1. Populate Consent resource from template + decisions            │    │
│  │     - Set profile from template                                    │    │
│  │     - Generate dynamic narrative with placeholder replacement      │    │
│  │     - Build nested provisions with permit/deny types              │    │
│  │     - Calculate period end from VALIDITY_PERIOD                   │    │
│  │     - Resolve policies from template mappings                     │    │
│  │                                                                     │    │
│  │  2. Validate against MII profile                                   │    │
│  │     - Structural validation                                        │    │
│  │     - Cardinality checks                                           │    │
│  │     - ValueSet checks                                              │    │
│  │     - Profile constraints                                          │    │
│  │                                                                     │    │
│  │  3. Generate FHIR JSON                                             │    │
│  │  4. Store/Return consent                                           │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 📝 Usage Guide

### Example: Basic Consent Generation

```java
package org.example;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.validation.ValidationResult;
import org.example.consent.loader.ConsentTemplateLoader;
import org.example.consent.model.ExchangeFormatDefinition;
import org.example.consent.populator.*;
import org.example.tools.FhirResourceLoader;
import org.example.tools.JsonSerializationService;
import org.example.validation.*;
import org.hl7.fhir.common.hapi.validation.support.*;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.StructureDefinition;

import java.util.Date;
import java.util.List;

public class ConsentGenerator {
    
    private static final String RESOURCES_PATH = "src/main/resources/";
    
    public static void main(String[] args) {
        try {
            // 1. Initialize FHIR context
            FhirContext fhirContext = FhirContext.forR4();
            IParser jsonParser = fhirContext.newJsonParser();
            
            // 2. Load resources
            FhirResourceLoader resourceLoader = new FhirResourceLoader(fhirContext, jsonParser);
            
            StructureDefinition germanProfile = resourceLoader.loadStructureDefinition(
                RESOURCES_PATH + "ConsentManagementConsent.json");
            StructureDefinition miiProfile = resourceLoader.loadStructureDefinition(
                RESOURCES_PATH + "MII_PR_Consent_Einwilligung.json");
            
            resourceLoader.loadCodeSystem(RESOURCES_PATH + "CodeSystem-MiiConsentAnswerCodeSystem.json");
            resourceLoader.loadCodeSystem(RESOURCES_PATH + "CodeSystem-MiiConsentPolicyCodeSystem.json");
            resourceLoader.loadCodeSystem(RESOURCES_PATH + "CodeSystem-MiiConsentVersionModuleCodeSystem.json");
            resourceLoader.loadValueSet(RESOURCES_PATH + "ValueSet-MiiConsentPolicyValueSet.json");
            
            // 3. Load template
            ConsentTemplateLoader templateLoader = new ConsentTemplateLoader();
            ExchangeFormatDefinition template = templateLoader.loadFromFile(
                RESOURCES_PATH + "2025-01-21 MII BC 1.7.2 mit Erweiterungsmodul PROM.json");
            
            String templateKey = template.getTemplatesConsentTemplate().get(0).getDomainName() + ";" +
                template.getTemplatesConsentTemplate().get(0).getName() + ";" +
                template.getTemplatesConsentTemplate().get(0).getVersion();
            
            // 4. Setup validation
            ValidationSupportFactory supportFactory = new ValidationSupportFactory(
                fhirContext, resourceLoader.getPrePopulatedSupport());
            ValidationSupportChain supportChain = supportFactory.createSupportChain();
            SnapshotGeneratingValidationSupport snapshotSupport = supportFactory.createSnapshotSupport();
            
            // 5. Generate snapshots
            SnapshotGeneratorService snapshotService = new SnapshotGeneratorService(supportChain, snapshotSupport);
            StructureDefinition germanSnapshot = snapshotService.generateSnapshot(
                germanProfile, "http://hl7.org/fhir/StructureDefinition/Consent");
            resourceLoader.getPrePopulatedSupport().addStructureDefinition(germanSnapshot);
            
            StructureDefinition miiSnapshot = snapshotService.generateSnapshot(
                miiProfile, miiProfile.getBaseDefinition());
            resourceLoader.getPrePopulatedSupport().addStructureDefinition(miiSnapshot);
            
            // 6. Get modules and create request
            ConsentPopulator populator = new ConsentPopulator(template);
            List<ModuleInfo> modules = populator.getModulesForTemplate(templateKey);
            
            ConsentRequest request = new ConsentRequest();
            request.setTemplateKey(templateKey);
            request.setPatientId("Patient/123456");
            request.setOrganizationId("Organization/hospital-123");
            request.setInstitutionName("Universitätsklinikum Hamburg");
            request.setPatientName("Max Mustermann");
            request.setConsentDate(new Date());
            
            // Accept first 3 modules, decline the rest
            for (ModuleInfo module : modules) {
                ModuleDecision decision = new ModuleDecision();
                decision.setModuleKey(module.getModuleKey());
                decision.setModuleName(module.getModuleName());
                if (module.getOrderNumber() < 3) {
                    decision.setStatus("ACCEPTED");
                    decision.setProvisionType("permit");
                } else {
                    decision.setStatus("DECLINED");
                    decision.setProvisionType("deny");
                }
                request.addModuleDecision(decision);
            }
            
            // 7. Generate consent
            Consent consent = populator.populateConsent(request, miiSnapshot);
            
            // 8. Validate
            FhirValidatorService validatorService = new FhirValidatorService(fhirContext, supportChain);
            validatorService.init();
            ValidationResult result = validatorService.validate(consent);
            validatorService.printValidationResults(result);
            
            // 9. Output JSON
            JsonSerializationService serializationService = new JsonSerializationService(jsonParser);
            String json = serializationService.serialize(consent);
            serializationService.printJson("Final Consent FHIR JSON", json);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

### Example: Placeholder Replacement

The system automatically replaces placeholders in the narrative:

| Template Text | Replaced With |
|---------------|---------------|
| `[Name der behandelnden Einrichtung]` | `request.getInstitutionName()` |
| `[Name des Patienten]` | `request.getPatientName()` |
| `[Datum der Unterschrift]` | `request.getConsentDate()` |
| `[zuständige Stelle]` | `request.getOrganizationName()` |
| `[Gültigkeitsdauer]` | Template `VALIDITY_PERIOD` (e.g., "30 Jahre") |
| `[falls zutreffend: ...]` | Removed if declined, kept if accepted |
| `[NUMMERIERUNG ANPASSEN]` | Section number or "Abschnitt" |

---

## 📚 API Reference

### ConsentPopulator

#### Constructor

```java
public ConsentPopulator(ExchangeFormatDefinition template)
```

**Parameters:**
- `template` - The consent template (ExchangeFormatDefinition)

#### Methods

```java
public Consent populateConsent(ConsentRequest request, StructureDefinition miiProfile)
```
Populates a FHIR Consent resource from the template and user decisions.

```java
public List<ModuleInfo> getModulesForTemplate(String templateKey)
```
Returns all modules from a template with their default status.

```java
public Set<String> getAvailableTemplateKeys()
```
Returns all available template keys (immutable view).

```java
public List<String> getAvailableTemplateNames()
```
Returns all available template names with labels.

### ConsentRequest

#### Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `templateKey` | String | Yes | Template identifier |
| `patientId` | String | Yes | Patient reference |
| `organizationId` | String | Yes | Organization reference |
| `patientName` | String | No | Patient name (for narrative) |
| `institutionName` | String | No | Institution name (for narrative) |
| `organizationName` | String | No | Organization name (for narrative) |
| `consentDate` | Date | No | Consent date (defaults to now) |
| `sourceReference` | String | No | Source reference |
| `mainProvisionType` | String | No | Main provision type (permit/deny) |
| `moduleDecisions` | List | Yes | Module decisions |
| `signature` | SignatureData | No | Signature data |

### ModuleDecision

| Field | Type | Description |
|-------|------|-------------|
| `moduleKey` | String | Module identifier |
| `moduleName` | String | Module name |
| `status` | String | ACCEPTED or DECLINED |
| `provisionType` | String | permit or deny |
| `comment` | String | Optional comment |

### ModuleInfo

| Field | Type | Description |
|-------|------|-------------|
| `moduleKey` | String | Module identifier |
| `moduleName` | String | Module name |
| `moduleLabel` | String | Display label |
| `mandatory` | boolean | Whether module is mandatory |
| `orderNumber` | int | Display order |
| `defaultStatus` | String | Default status (ACCEPTED/DECLINED) |

### PeriodCalculator

```java
public Period createPeriod(Date startDate, String validityPeriod)
```
Creates a Period with start and calculated end date.

**Supported Formats:**
- `P30Y` - 30 years
- `P1Y6M` - 1 year and 6 months
- `P2Y3M15D` - 2 years, 3 months, 15 days
- `P5Y` - 5 years
- `P6M` - 6 months
- `P1W` - 1 week
- `P2W3D` - 2 weeks and 3 days

---

## ⚙️ Configuration

### Maven Dependencies

```xml
<dependencies>
    <!-- HAPI FHIR Core -->
    <dependency>
        <groupId>ca.uhn.hapi.fhir</groupId>
        <artifactId>hapi-fhir-base</artifactId>
        <version>8.10.1</version>
    </dependency>
    
    <!-- HAPI FHIR R4 -->
    <dependency>
        <groupId>ca.uhn.hapi.fhir</groupId>
        <artifactId>hapi-fhir-structures-r4</artifactId>
        <version>8.10.1</version>
    </dependency>
    
    <!-- HAPI FHIR Validation -->
    <dependency>
        <groupId>ca.uhn.hapi.fhir</groupId>
        <artifactId>hapi-fhir-validation</artifactId>
        <version>8.10.1</version>
    </dependency>
    
    <!-- HAPI FHIR Validation Resources R4 -->
    <dependency>
        <groupId>ca.uhn.hapi.fhir</groupId>
        <artifactId>hapi-fhir-validation-resources-r4</artifactId>
        <version>8.10.1</version>
    </dependency>
    
    <!-- HTML Escaping -->
    <dependency>
        <groupId>org.apache.commons</groupId>
        <artifactId>commons-text</artifactId>
        <version>1.10.0</version>
    </dependency>
    
    <!-- JSoup for HTML cleaning -->
    <dependency>
        <groupId>org.jsoup</groupId>
        <artifactId>jsoup</artifactId>
        <version>1.17.2</version>
    </dependency>
    
    <!-- Jackson for JSON -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
        <version>2.22.1</version>
    </dependency>
</dependencies>
```

### Template Configuration

The consent template (`ExchangeFormatDefinition`) must contain:

#### Domain Configuration
```properties
externProperties: "scopeSystem=http://terminology.hl7.org/CodeSystem/consentscope;scopeCode=research;scopeDisplay=Research"
```

#### Template Configuration
```properties
externProperties: "fhirForceProfileConsent=https://www.medizininformatik-initiative.de/fhir/modul-consent/StructureDefinition/mii-pr-consent-einwilligung;fhirConsentCategory=2.16.840.1.113883.3.1937.777.24.2.184;fhirPolicyValueSet=urn:oid:2.16.840.1.113883.3.1937.777.24.5.3;"
expirationProperties: "VALIDITY_PERIOD=P30Y;"
```

#### Module Configuration
```properties
externProperties: "fhirQuestionCode=2.16.840.1.113883.3.1937.777.24.2.1567"
```

#### Policy Configuration
```properties
externProperties: "fhirPolicyCode=2.16.840.1.113883.3.1937.777.24.5.3.5"
```

---

## ✅ Validation

### Validation Results

```
=== Validation Result ===
Is Valid: true
Total Messages: 3
[WARNING] Consent.category[0] - CodeSystem is unknown: http://loinc.org
[WARNING] Consent.category[1] - Not in value set 'Consent Category Codes'
[WARNING] Consent.policyRule - Not in value set 'Consent PolicyRule Codes'
```

### Expected Warnings

The following warnings are **expected** and **acceptable**:

| Warning | Reason |
|---------|--------|
| LOINC CodeSystem unknown | Validator can't resolve http://loinc.org locally |
| MII category not in standard value set | MII-specific category code |
| PolicyRule not in standard value set | OPTIN is valid but not in the specific value set |

These warnings do NOT affect the validity of the consent resource.

---

## 🧪 Testing

### Run Tests

```bash
mvn test
```

### Test Coverage

```
[INFO] Tests run: 16, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### Test Categories

| Category | Tests | Description |
|----------|-------|-------------|
| **Core Tests** | 6 | Mixed decisions, all accepted, template selection |
| **Edge Cases** | 4 | Missing fields, null handling |
| **XHTML Security** | 2 | Special characters, injection prevention |
| **Date Consistency** | 1 | consent.dateTime vs period.start |
| **Narrative Fallback** | 1 | Decision preservation |
| **Policy Code Fallback** | 1 | Null policy code handling |
| **Profile Extraction** | 3 | FHIR version detection |

---

## 🔧 Troubleshooting

### Common Issues

| Issue | Error | Solution |
|-------|-------|----------|
| **Unknown base definition** | `HAPI-0705: Unknown base definition` | Load German Consent profile and generate snapshot first |
| **Profile not found** | `Profile reference has not been checked` | Generate snapshot for MII profile and add to support |
| **CodeSystem unknown** | `CodeSystem is unknown and can't be validated` | Load the CodeSystem file using `loadCodeSystem()` |
| **ValueSet not found** | `ValueSet not found` | Load the ValueSet file using `loadValueSet()` |
| **Invalid code** | `value provided was not found in the value set` | Add InMemoryTerminologyServerValidationSupport to chain |
| **File not found** | `NoSuchFileException` | Check file path and ensure files are in resources |
| **Narrative div empty** | `Narrative.div: minimum required = 1` | Check template text fields, ensure HTML is well-formed |
| **Special characters error** | `unable to parse character reference` | Ensure user content is HTML-escaped |

### Debug Mode

Enable debug logging:

```properties
# In src/main/resources/log4j.properties
log4j.rootLogger=DEBUG, console
log4j.logger.org.hl7.fhir=DEBUG
log4j.logger.ca.uhn.fhir=DEBUG
```

---

## 🤝 Contributing

1. **Fork the repository**
2. **Create a feature branch** (`git checkout -b feature/amazing-feature`)
3. **Commit your changes** (`git commit -m 'Add amazing feature'`)
4. **Push to the branch** (`git push origin feature/amazing-feature`)
5. **Open a Pull Request**

### Coding Standards

- Follow SOLID principles
- Write unit tests for new features
- Document public APIs with JavaDoc
- Use meaningful variable names
- No hardcoded values - extract from templates

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 🙏 Acknowledgments

- **HAPI FHIR** - Excellent FHIR library for Java
- **HL7 International** - FHIR specification
- **MII (Medizininformatik-Initiative)** - German Medical Informatics Initiative
- **Firely** - Simplifier.net for FHIR resources
- **AG Einwilligungsmanagement** - German Consent Management working group

---

## 📞 Contact

For questions or support, please contact:
- **Email**: your.email@example.com
- **GitHub Issues**: [Create an issue](https://github.com/yourusername/fhir-consent-management/issues)

---

## 📊 Sample Consent Output

```json
{
  "resourceType": "Consent",
  "meta": {
    "profile": ["https://www.medizininformatik-initiative.de/fhir/modul-consent/StructureDefinition/mii-pr-consent-einwilligung"]
  },
  "text": {
    "status": "generated",
    "div": "<div xmlns=\"http://www.w3.org/1999/xhtml\">\n  <h2>Einwilligungserklärung</h2>\n  <p>Patient: Max Mustermann</p>\n  <p>Datum: 16.08.2026</p>\n  <div style=\"border-left: 4px solid #4CAF50; padding-left: 10px;\">\n    <div style=\"font-weight: bold; color: #4CAF50;\">✓ ICH WILLIGE EIN</div>\n    <p>Ich willige ein in die Erhebung, Verarbeitung...</p>\n  </div>\n</div>"
  },
  "status": "active",
  "scope": {
    "coding": [{"system": "http://terminology.hl7.org/CodeSystem/consentscope", "code": "research"}]
  },
  "category": [
    {"coding": [{"system": "http://loinc.org", "code": "57016-8"}]},
    {"coding": [{"system": "https://www.medizininformatik-initiative.de/fhir/modul-consent/CodeSystem/mii-cs-consent-version-modules", "code": "2.16.840.1.113883.3.1937.777.24.2.184"}]}
  ],
  "patient": {"reference": "Patient/123456"},
  "dateTime": "2026-08-16T16:31:39+02:00",
  "organization": [{"reference": "Organization/hospital-123"}],
  "provision": {
    "type": "deny",
    "period": {
      "start": "2026-08-16T16:31:39+02:00",
      "end": "2056-08-16T16:31:39+02:00"
    },
    "provision": [
      {
        "type": "permit",
        "period": {
          "start": "2026-08-16T16:31:39+02:00",
          "end": "2056-08-16T16:31:39+02:00"
        },
        "code": [
          {"coding": [{"system": "urn:oid:2.16.840.1.113883.3.1937.777.24.5.3", "code": "2.16.840.1.113883.3.1937.777.24.5.3.5", "display": "IDAT_bereitstellen_EU_DSGVO_NIVEAU"}]}
        ]
      }
    ]
  }
}
```

---
