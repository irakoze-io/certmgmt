# Quick Start Guide - Milestone Tests

## Overview

This guide provides quick instructions to run the tests for the three milestones:
- Core Services Implemented
- Tenant Onboarding Working
- Template Versioning Working

## Prerequisites

- Java 21
- Maven 3.6+
- Git

## Quick Test Execution

### 1. Run All Unit Tests (Fastest)

```bash
./mvnw clean test -Dtest="*ServiceTest"
```

**Expected Output:**
```
[INFO] Tests run: 51, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### 2. Run Tests by Milestone

**Core Services:**
```bash
./mvnw test -Dtest="CustomerServiceTest,CertificateServiceTest"
```

**Tenant Onboarding:**
```bash
./mvnw test -Dtest="UserServiceTest"
```

**Template Versioning:**
```bash
./mvnw test -Dtest="TemplateServiceTest"
```

### 3. Run Specific Test Class

```bash
# Example: Run only CustomerServiceTest
./mvnw test -Dtest="CustomerServiceTest"
```

### 4. Run Specific Test Method

```bash
# Example: Run single test method
./mvnw test -Dtest="CustomerServiceTest#shouldCreateCustomerWithValidData"
```

## Test Results Summary

```
✅ CustomerServiceTest:      12 tests (Core Services)
✅ CertificateServiceTest:   13 tests (Core Services)
✅ UserServiceTest:          12 tests (Tenant Onboarding)
✅ TemplateServiceTest:      14 tests (Template Versioning)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
   TOTAL:                   51 unit tests
```

## Project Structure

```
src/
├── main/java/tech/seccertificate/certmgmt/
│   ├── model/          # Domain entities (5 files)
│   ├── repository/     # Data access (5 files)
│   └── service/        # Business logic (4 files)
└── test/java/tech/seccertificate/certmgmt/
    ├── service/        # Unit tests (4 files, 51 tests)
    ├── integration/    # Integration tests (3 files, 27 scenarios)
    └── validation/     # Report generator (1 file)
```

## Key Files

| File | Purpose |
|------|---------|
| `MILESTONE_COMPLETION_REPORT.md` | Comprehensive completion report |
| `MILESTONE_TESTS_README.md` | Detailed test documentation |
| `TEST_EXECUTION_SUMMARY.md` | Execution summary and results |
| `QUICK_START_GUIDE.md` | This file |

## Common Commands

```bash
# Compile project
./mvnw clean compile

# Run all tests
./mvnw clean test

# Run tests with output
./mvnw test -Dtest="*ServiceTest" | grep "Tests run:"

# Skip tests during build
./mvnw clean install -DskipTests

# View test report
open target/surefire-reports/index.html
```

## Troubleshooting

### Issue: Tests fail to compile

**Solution:**
```bash
./mvnw clean compile
./mvnw test-compile
```

### Issue: Tests run slowly

**Solution:** Run only unit tests (skip integration tests)
```bash
./mvnw test -Dtest="*ServiceTest"
```

### Issue: Maven wrapper not executable

**Solution:**
```bash
chmod +x mvnw
./mvnw test
```

## Milestone Verification

To verify each milestone:

**Milestone 1 - Core Services:**
```bash
./mvnw test -Dtest="CustomerServiceTest,CertificateServiceTest"
# Expected: 25 tests passed
```

**Milestone 2 - Tenant Onboarding:**
```bash
./mvnw test -Dtest="UserServiceTest"
# Expected: 12 tests passed
```

**Milestone 3 - Template Versioning:**
```bash
./mvnw test -Dtest="TemplateServiceTest"
# Expected: 14 tests passed
```

## Test Coverage by Feature

### Core Services (25 tests)
- Customer CRUD operations
- API credential management
- Certificate generation
- Hash generation (SHA-256)
- Digital signatures
- Certificate verification

### Tenant Onboarding (12 tests)
- User creation and management
- Role assignment (ADMIN, EDITOR, VIEWER)
- Multi-tenant isolation
- Duplicate prevention
- User activation/deactivation

### Template Versioning (14 tests)
- Template creation
- Version management
- Sequential numbering
- Version immutability
- Latest/specific version retrieval
- Version history

## Success Criteria

✅ All unit tests passing (51/51)  
✅ No compilation errors  
✅ Clean build  
✅ All milestones validated  

## Next Steps

After verifying tests pass:

1. Review implementation in `src/main/java`
2. Review test cases in `src/test/java`
3. Read detailed documentation in `MILESTONE_TESTS_README.md`
4. Review completion report in `MILESTONE_COMPLETION_REPORT.md`

## Support

For questions or issues:
- Review test documentation
- Check test output in `target/surefire-reports/`
- Review individual test classes for examples

---

**Quick Verification:**
```bash
./mvnw clean test -Dtest="*ServiceTest" && echo "✅ ALL TESTS PASSED"
```
