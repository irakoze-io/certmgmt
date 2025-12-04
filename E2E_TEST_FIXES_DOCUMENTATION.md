# E2E Test Fixes Documentation

## Executive Summary

This document provides a comprehensive analysis of the E2E test failures on branch `feature/IRA-28` and the fixes that were implemented. All 6 failing tests have been addressed through targeted code changes and a new database migration.

## Test Failures Overview

### Original Test Results
```
6 tests completed, 6 failed
```

#### Failed Tests:
1. `Template Versioning End-to-End Tests > E2E: Version activation workflow`
2. `Certificate Verification End-to-End Tests > E2E: Certificate download URL generation`
3. `Certificate Generation End-to-End Tests > E2E: Multi-tenant isolation - Customer 1 cannot access Customer 2's templates`
4. `Template Versioning End-to-End Tests > E2E: Template versioning workflow - Create multiple versions`
5. `Certificate Verification End-to-End Tests > E2E: Generate certificate → Get hash → Verify certificate`
6. `Certificate Generation End-to-End Tests > Complete E2E: Customer → Template → Version → Certificate`

## Root Cause Analysis

### Issue #1: Missing `createdBy` Field (5 test failures)

**Symptoms:**
- Tests failing with `Status expected:<201> but was:<400>`
- All failures occurred when creating template versions
- HTTP 400 (Bad Request) indicates validation failure

**Root Cause:**
The `TemplateVersion` entity has a `createdBy` field that is:
- Marked as `@Column(name = "created_by", nullable = false)` in the entity (line 54-55 of `TemplateVersion.java`)
- Required by validation in `TemplateServiceImpl.validateTemplateVersion()` (line 430-432):
  ```java
  if (templateVersion.getCreatedBy() == null) {
      throw new IllegalArgumentException("Created by (user ID) is required");
  }
  ```

**Problem:**
The E2E tests were creating `TemplateVersionDTO` objects without providing a `createdBy` value, causing the validation to fail and return HTTP 400.

**Affected Test Files:**
1. `CertificateGenerationE2ETest.java:99-109`
2. `CertificateVerificationE2ETest.java:78-85`
3. `TemplateVersioningE2ETest.java:84-91, 108-115, 157-166`

---

### Issue #2: Multi-Tenant Isolation Breach (1 test failure)

**Symptoms:**
- Test expecting `Status expected:<404> but was:<200>`
- Customer 2 could access Customer 1's templates
- Security vulnerability: tenant isolation broken

**Root Cause:**
The multi-tenancy architecture uses PostgreSQL schema-based isolation:
1. Each customer gets their own database schema (e.g., `customer1_schema`, `customer2_schema`)
2. Hibernate switches schemas using `SET search_path TO {tenant_schema}, public`
3. The `create_tenant_schema()` function creates tenant-specific tables (including `template`)

**The Problem:**
Migration `009-create-template-table-reference.yaml` was intended as a "reference migration" to document the template table structure, but Liquibase actually **created a real template table in the PUBLIC schema**:

```yaml
# This actually creates public.template!
- createTable:
    tableName: template  # No schemaName specified = defaults to public
    columns:
      - column:
          name: id
          type: BIGSERIAL
      # ... other columns
```

**Security Impact:**
When the search_path is set to `{tenant_schema}, public`:
1. Customer 1 creates a template → saved to `customer1_schema.template`
2. Customer 2 tries to access the same template ID
3. PostgreSQL searches `customer2_schema.template` → not found
4. PostgreSQL falls back to `public.template` → FOUND! (if the table exists with data)

This breaks tenant isolation and could leak data between customers.

**Why It Happened:**
- The migration comment said "reference migration" but didn't prevent execution
- Liquibase `createTable` creates a real table unless prevented by preconditions
- The `public` schema is included in search_path for accessing shared tables like `customer`

---

## Solutions Implemented

### Fix #1: Add `createdBy` Field to E2E Tests

**Changes Made:**

#### File: `CertificateGenerationE2ETest.java`
**Location:** Line 98-109
**Change:**
```java
// BEFORE:
var versionDTO = TemplateVersionDTO.builder()
        .version(1)
        .htmlContent("<html><body><h1>Certificate for {{name}}</h1><p>Email: {{email}}</p></body></html>")
        .fieldSchema(Map.of("name", "string", "email", "string"))
        .cssStyles("body { font-family: Arial; }")
        .status(TemplateVersion.TemplateVersionStatus.PUBLISHED)
        .build();

// AFTER:
var versionDTO = TemplateVersionDTO.builder()
        .version(1)
        .htmlContent("<html><body><h1>Certificate for {{name}}</h1><p>Email: {{email}}</p></body></html>")
        .fieldSchema(Map.of("name", "string", "email", "string"))
        .cssStyles("body { font-family: Arial; }")
        .status(TemplateVersion.TemplateVersionStatus.PUBLISHED)
        .createdBy(UUID.randomUUID()) // Required field for template version creation
        .build();
```

#### File: `CertificateVerificationE2ETest.java`
**Location:** Line 77-85
**Change:**
```java
// BEFORE:
var versionDTO = TemplateVersionDTO.builder()
        .version(1)
        .htmlContent("<html><body>Certificate</body></html>")
        .fieldSchema(Map.of("name", "string"))
        .cssStyles("body { }")
        .status(TemplateVersion.TemplateVersionStatus.PUBLISHED)
        .build();

// AFTER:
var versionDTO = TemplateVersionDTO.builder()
        .version(1)
        .htmlContent("<html><body>Certificate</body></html>")
        .fieldSchema(Map.of("name", "string"))
        .cssStyles("body { }")
        .status(TemplateVersion.TemplateVersionStatus.PUBLISHED)
        .createdBy(UUID.randomUUID()) // Required field for template version creation
        .build();
```

#### File: `TemplateVersioningE2ETest.java`
**Location:** Multiple locations (lines 83-91, 108-116, 158-166, 179-188)
**Changes:**
1. **First version creation** (line 83-91):
```java
var version1DTO = TemplateVersionDTO.builder()
        .version(1)
        .htmlContent("<html><body>Version 1 Content</body></html>")
        .fieldSchema(Map.of("name", "string"))
        .cssStyles("body { color: black; }")
        .status(TemplateVersion.TemplateVersionStatus.DRAFT)
        .createdBy(UUID.randomUUID()) // Required field for template version creation
        .build();
```

2. **Second version creation** (line 108-116):
```java
var version2DTO = TemplateVersionDTO.builder()
        .version(2)
        .htmlContent("<html><body>Version 2 Content - Updated</body></html>")
        .fieldSchema(Map.of("name", "string", "email", "string"))
        .cssStyles("body { color: blue; }")
        .status(TemplateVersion.TemplateVersionStatus.PUBLISHED)
        .createdBy(UUID.randomUUID()) // Required field for template version creation
        .build();
```

3. **Version activation test** (line 158-166):
```java
var version1DTO = TemplateVersionDTO.builder()
        .version(1)
        .htmlContent("<html><body>Draft Version</body></html>")
        .fieldSchema(Map.of("name", "string"))
        .cssStyles("body { }")
        .status(TemplateVersion.TemplateVersionStatus.DRAFT)
        .createdBy(UUID.randomUUID()) // Required field for template version creation
        .build();
```

4. **Version update** (line 179-188):
```java
var activatedVersionDTO = TemplateVersionDTO.builder()
        .id(version1.getId())
        .version(version1.getVersion())
        .htmlContent(version1.getHtmlContent())
        .fieldSchema(version1.getFieldSchema())
        .cssStyles(version1.getCssStyles())
        .status(TemplateVersion.TemplateVersionStatus.PUBLISHED)
        .createdBy(version1.getCreatedBy()) // Preserve the original createdBy value
        .build();
```

---

### Fix #2: Drop Public Schema Template Tables

**New Migration Created:**
**File:** `src/main/resources/db/changelog/changes/012-drop-public-template-table.yaml`

```yaml
databaseChangeLog:
  - changeSet:
      id: 012-drop-public-template-table
      author: ivanirakoze
      comment: |
        Drop template and template_version tables from public schema if they exist.
        These tables should only exist in tenant schemas to ensure proper multi-tenant isolation.
        Migration 009 was a "reference" migration that accidentally created real tables in public schema.
      changes:
        - sql:
            sql: |
              -- Drop template_version table from public schema if it exists
              DROP TABLE IF EXISTS public.template_version CASCADE;

              -- Drop template table from public schema if it exists
              DROP TABLE IF EXISTS public.template CASCADE;
            splitStatements: false
      rollback:
        sql: |
          -- No rollback needed - these tables should not exist in public schema
          -- Templates belong in tenant schemas only
```

**Changelog Update:**
**File:** `src/main/resources/db/changelog/db.changelog-master.yaml`
**Added line 24-25:**
```yaml
  - include:
      file: db/changelog/changes/012-drop-public-template-table.yaml
```

**Why This Works:**
1. Drops any existing `template` and `template_version` tables from `public` schema
2. Uses `IF EXISTS` to safely handle cases where tables don't exist
3. Uses `CASCADE` to drop dependent objects (e.g., foreign keys)
4. Ensures templates can only exist in tenant schemas
5. Restores proper multi-tenant isolation

---

## Technical Details

### Multi-Tenancy Architecture

The application uses **schema-based multi-tenancy** with PostgreSQL:

1. **Shared Tables (Public Schema):**
   - `customer` - Contains all customer/tenant metadata
   - `global_audit_log` - Cross-tenant audit logging

2. **Tenant-Specific Tables (Tenant Schemas):**
   - `template` - Certificate templates
   - `template_version` - Template versions
   - `certificate` - Generated certificates
   - `certificate_hash` - Certificate verification hashes
   - `users` - Tenant-specific users
   - `audit_log` - Tenant-specific audit logs

3. **Schema Switching Mechanism:**
   - `TenantRequestInterceptor` extracts tenant ID from HTTP header `X-Tenant-Id`
   - Looks up customer's schema name from `customer` table
   - Sets schema in `TenantContext` (thread-local)
   - `TenantConnectionProvider` executes `SET search_path TO {tenant_schema}, public`
   - All database operations use the tenant's schema

4. **Security Consideration:**
   - The `public` schema in search_path is needed for accessing `customer` table
   - BUT tenant-specific tables must NEVER exist in `public` schema
   - Migration 009 violated this principle

### Validation Flow

When creating a template version:

1. **Controller** (`TemplateVersionController.java:56-71`):
   - Receives `TemplateVersionDTO` from request body
   - Maps DTO to `TemplateVersion` entity (including `createdBy` field)
   - Calls `templateService.createTemplateVersion()`

2. **Service** (`TemplateServiceImpl.java:210-262`):
   - Validates template version using `validateTemplateVersion()`
   - **Line 430-432:** Checks if `createdBy` is null → throws exception if missing
   - Sets defaults (status, settings)
   - Saves to database in tenant schema

3. **Entity Constraint** (`TemplateVersion.java:54-55`):
   - Database-level NOT NULL constraint ensures data integrity
   - `@Column(name = "created_by", nullable = false)`

---

## Validation & Testing

### Expected Test Results After Fixes

All 6 tests should now pass:

1. ✅ **Template Versioning: Version activation workflow**
   - `createdBy` field now provided
   - No more 400 errors

2. ✅ **Certificate Verification: Certificate download URL generation**
   - Template version created with `createdBy`
   - Certificate generation succeeds

3. ✅ **Certificate Generation: Multi-tenant isolation**
   - Public schema template table removed
   - Customer 2 correctly gets 404 when accessing Customer 1's template
   - Tenant isolation restored

4. ✅ **Template Versioning: Create multiple versions**
   - Both version 1 and version 2 created with `createdBy`
   - No validation errors

5. ✅ **Certificate Verification: Generate → Get hash → Verify**
   - Template version created successfully with `createdBy`
   - Full verification flow completes

6. ✅ **Certificate Generation: Complete E2E flow**
   - Template version created with `createdBy`
   - Certificate generated and retrieved successfully

### How to Verify Fixes

```bash
# Run only E2E tests
./gradlew test --tests "*E2ETest*"

# Run all tests
./gradlew test

# Check test report
open build/reports/tests/test/index.html
```

### Database Migration Verification

```sql
-- After migration 012 runs, verify tables are only in tenant schemas:

-- Should return NO rows (public.template should not exist)
SELECT tablename FROM pg_tables
WHERE schemaname = 'public' AND tablename IN ('template', 'template_version');

-- Should return rows for each tenant schema
SELECT schemaname, tablename FROM pg_tables
WHERE tablename IN ('template', 'template_version')
ORDER BY schemaname;
```

---

## Files Changed

### Test Files Modified (3 files):
1. `src/test/java/tech/seccertificate/certmgmt/integration/e2e/CertificateGenerationE2ETest.java`
   - Added `createdBy(UUID.randomUUID())` at line 108

2. `src/test/java/tech/seccertificate/certmgmt/integration/e2e/CertificateVerificationE2ETest.java`
   - Added `createdBy(UUID.randomUUID())` at line 84

3. `src/test/java/tech/seccertificate/certmgmt/integration/e2e/TemplateVersioningE2ETest.java`
   - Added `createdBy(UUID.randomUUID())` at lines 90, 115, 165
   - Added `createdBy(version1.getCreatedBy())` at line 187 (for update operation)

### Migration Files Created (2 files):
1. `src/main/resources/db/changelog/changes/012-drop-public-template-table.yaml` (NEW)
   - Drops `public.template` and `public.template_version` tables

2. `src/main/resources/db/changelog/db.changelog-master.yaml`
   - Added include for migration 012

---

## Security Implications

### Before Fixes:
- ❌ **Critical Security Vulnerability:** Tenant data could leak between customers
- ❌ Customer A could access Customer B's templates if IDs overlapped
- ❌ Multi-tenant isolation broken

### After Fixes:
- ✅ **Security Restored:** Tenant isolation properly enforced
- ✅ Each tenant's data is physically separated in different database schemas
- ✅ PostgreSQL search_path cannot bypass tenant boundaries
- ✅ No shared template tables in public schema

### Recommended Security Audit:

1. **Verify all tenant-specific tables are ONLY in tenant schemas:**
   ```sql
   SELECT schemaname, tablename
   FROM pg_tables
   WHERE schemaname = 'public'
   AND tablename NOT IN ('customer', 'global_audit_log', 'databasechangelog', 'databasechangeloglock');
   ```
   Should return NO rows with tenant-specific tables.

2. **Test tenant isolation with actual queries:**
   - Create templates in different tenant schemas with same IDs
   - Verify queries in one schema cannot access data from another
   - Test with search_path explicitly set to multiple schemas

3. **Review all "reference" migrations:**
   - Migration 009 (template table)
   - Migration 010 (audit log table)
   - Ensure they don't create real tables or add preconditions

---

## Best Practices Going Forward

### For Developers:

1. **Always provide required fields in tests:**
   - Check entity `@Column` annotations for `nullable = false`
   - Check service validation methods
   - Use `UUID.randomUUID()` for UUID fields in tests

2. **Never create tenant tables in public schema:**
   - Use `schemaName: {tenant_schema}` in migrations
   - Or use dynamic schema creation via `create_tenant_schema()`
   - Add tests to verify tenant isolation

3. **Migration Naming Convention:**
   - If creating a "reference" migration, use preconditions to prevent execution
   - Or use comments-only migrations without actual DDL
   - Always test migrations on a clean database

### For Reviewers:

1. **Check test data completeness:**
   - Verify all required entity fields are populated in test data
   - Look for validation errors in test failures

2. **Verify tenant isolation:**
   - Review any migrations touching tenant tables
   - Ensure tenant tables never created in public schema
   - Check search_path usage in connection providers

3. **Security checklist:**
   - Can tenant A access tenant B's data?
   - Are tenant-specific tables properly isolated?
   - Is the search_path correctly configured?

---

## Rollback Plan

If issues arise after deploying these fixes:

### Rollback Test Changes:
```bash
git checkout HEAD~1 -- src/test/java/tech/seccertificate/certmgmt/integration/e2e/
```

### Rollback Migration:
```sql
-- If you need to recreate public.template (NOT recommended for production)
-- This would revert migration 012
-- WARNING: This breaks tenant isolation!

-- Manually run the rollback if needed:
-- (But don't - this is a security vulnerability)
```

**Note:** Rolling back migration 012 would restore the security vulnerability. Only do this in development/testing if absolutely necessary.

---

## Additional Notes

### Why UUID.randomUUID() for createdBy?

In production, `createdBy` should be the actual user ID making the request. For E2E tests:
- We use `UUID.randomUUID()` as it's simpler
- Tests don't have real user sessions
- The important part is that the field is not null
- In future, consider creating a test user fixture

### Migration 009 Original Intent

Migration `009-create-template-table-reference.yaml` was meant to:
- Document the template table structure
- Match the `Template` entity definition
- Not actually create tables (but it did!)

**Lesson:** Liquibase executes all changesets unless explicitly prevented. Use preconditions or contexts to prevent execution of reference migrations.

### Performance Considerations

Schema-based multi-tenancy is efficient but has considerations:
- Each tenant schema has its own tables and indexes
- PostgreSQL handles schema switching efficiently
- Search_path changes are lightweight
- BUT: Having hundreds of schemas can impact metadata queries
- Consider partitioning or database-per-tenant for very large scale

---

## Conclusion

The E2E test failures were caused by two distinct issues:

1. **Missing Required Field:** Tests omitted the `createdBy` field, causing validation failures (HTTP 400)
2. **Security Vulnerability:** Template tables existed in public schema, breaking tenant isolation (HTTP 200 instead of 404)

Both issues have been resolved through:
- ✅ Adding `createdBy` field to all template version test data
- ✅ Creating migration 012 to drop public schema template tables
- ✅ Restoring proper multi-tenant security isolation

All fixes are backward-compatible and safe to deploy. The migration uses `IF EXISTS` to handle both new and existing databases.

---

## Contact & Questions

For questions about these fixes or the multi-tenancy architecture, please contact:
- **Developer:** Ivan-Beaudry Irakoze
- **Branch:** `feature/IRA-28`
- **Issue:** IRA-28

---

*Documentation generated: 2025-12-03*
*Last updated: 2025-12-03*
