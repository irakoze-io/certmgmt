# Certificate Management API

A production-ready, multi-tenant SaaS platform for PDF certificate generation, distribution, and verification. Built with Spring Boot 4.0.0 and Java 25.

[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Proprietary-red.svg)]()

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Architecture](#architecture)
- [Technology Stack](#technology-stack)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Installation](#installation)
  - [Running the Application](#running-the-application)
- [API Documentation](#api-documentation)
- [Multi-Tenancy](#multi-tenancy)
- [Certificate Generation](#certificate-generation)
- [Testing](#testing)
- [Configuration](#configuration)
- [Project Structure](#project-structure)
- [Security](#security)
- [Contributing](#contributing)
- [License](#license)

## Overview

The Certificate Management API is a comprehensive solution for organizations that need to generate, distribute, and verify digital certificates at scale. Whether you're issuing completion certificates, awards, diplomas, or any other type of credential, this platform provides:

- **Multi-tenant architecture** with complete data isolation
- **Template management** with versioning support
- **Asynchronous PDF generation** for scalability
- **Cryptographic verification** using SHA-256 hashing
- **Secure storage** with time-limited access URLs
- **Comprehensive audit logging** for compliance

### Problem Domain

Organizations often struggle with:
- Generating professional PDF certificates at scale
- Managing multiple certificate templates with different designs
- Ensuring data isolation for different customers/departments
- Verifying certificate authenticity
- Providing secure, temporary access to generated certificates

This platform solves all these challenges with a modern, cloud-ready architecture.

## Features

### Core Features

- **Customer Management**
  - Multi-tenant onboarding with automatic schema provisioning
  - Customer status management (ACTIVE, TRIAL, SUSPENDED, CANCELLED)
  - Usage limits (max users, max certificates per month)
  - Domain-based organization

- **Template Management**
  - CRUD operations for certificate templates
  - Template versioning with DRAFT, PUBLISHED, and ARCHIVED states
  - HTML + CSS template support with Thymeleaf syntax
  - Flexible field schema definition (JSONB)
  - Template code-based lookup for API integrations

- **Certificate Generation**
  - **Synchronous mode**: Immediate PDF generation and return
  - **Asynchronous mode**: Queue-based processing for high-volume operations
  - Dynamic data injection (recipient information, metadata)
  - Template variable replacement
  - Certificate status tracking (PENDING → PROCESSING → ISSUED/FAILED)
  - Automatic certificate numbering

- **Certificate Operations**
  - List certificates with advanced filtering
  - Retrieve by ID or certificate number
  - Update certificate metadata
  - Revoke certificates
  - Generate time-limited download URLs
  - Public verification endpoint (no authentication required)

- **Security & Compliance**
  - SHA-256 hash-based certificate verification
  - Pre-signed URLs with configurable expiration (default: 60 minutes)
  - Comprehensive audit logging (global and tenant-specific)
  - Schema-based tenant isolation
  - User role management (ADMIN, EDITOR, VIEWER, API_CLIENT)

## Architecture

### Multi-Tenancy Model

This application uses **Level 2 Schema-Based Multi-Tenancy**:

```
┌─────────────────────────────────────────────────────────────┐
│                    PostgreSQL Database                      │
├─────────────────────────────────────────────────────────────┤
│  PUBLIC SCHEMA                                              │
│  ├── customer (tenant registry)                             │
│  └── global_audit_log                                       │
├─────────────────────────────────────────────────────────────┤
│  TENANT SCHEMA: customer_abc                                │
│  ├── users                                                  │
│  ├── template                                               │
│  ├── template_version                                       │
│  ├── certificate                                            │
│  ├── certificate_hash                                       │
│  └── audit_log                                              │
├─────────────────────────────────────────────────────────────┤
│  TENANT SCHEMA: customer_xyz                                │
│  ├── users                                                  │
│  ├── template                                               │
│  └── ...                                                    │
└─────────────────────────────────────────────────────────────┘
```

**Tenant Resolution**: Automatic via HTTP headers:
- `X-Tenant-Id`: Customer ID
- `X-Tenant-Schema`: Direct schema name

### System Architecture

```
┌──────────────┐      ┌──────────────┐      ┌──────────────┐
│              │      │              │      │              │
│   REST API   │─────▶│  RabbitMQ    │─────▶│   Workers    │
│  (Sync/Async)│      │   Queue      │      │  (2-5 pods)  │
│              │      │              │      │              │
└──────┬───────┘      └──────────────┘      └──────┬───────┘
       │                                            │
       │                                            │
       ▼                                            ▼
┌──────────────┐                           ┌──────────────┐
│              │                           │              │
│  PostgreSQL  │                           │    MinIO     │
│ (Multi-Schema)│                          │  (S3-compat) │
│              │                           │              │
└──────────────┘                           └──────────────┘
```

### Certificate Generation Flow

**Asynchronous Flow**:
```
Client Request
    │
    ├─▶ Validate tenant & template
    ├─▶ Create certificate record (status: PENDING)
    ├─▶ Publish message to RabbitMQ
    └─▶ Return certificate ID immediately
         │
         ▼
    RabbitMQ Queue
         │
         ▼
    Worker picks up message
         │
         ├─▶ Update status: PROCESSING
         ├─▶ Generate PDF from template
         ├─▶ Calculate SHA-256 hash
         ├─▶ Upload to MinIO
         ├─▶ Update status: ISSUED
         └─▶ Store storage path & hash
```

## Technology Stack

### Core Framework
- **Java 25** - Latest LTS JDK
- **Spring Boot 4.0.0** - Application framework
- **Gradle** - Build automation

### Spring Ecosystem
- Spring Boot Starter Web (REST API)
- Spring Boot Starter Data JPA (ORM)
- Spring Boot Starter Security (Authentication/Authorization)
- Spring Boot Starter AMQP (RabbitMQ)
- Spring Boot Starter Actuator (Health monitoring)
- Spring Integration (AMQP, HTTP, JDBC, JPA)
- Spring Boot Starter Validation

### Database & Persistence
- **PostgreSQL** - Primary relational database
- **Hibernate 6.x** - JPA implementation with multi-tenancy
- **Liquibase** - Database migration management
- **Spring Data JPA** - Data access abstraction

### PDF Generation
- **OpenHTMLtoPDF** - HTML to PDF conversion
- **Thymeleaf** - Template engine
- **Apache Batik** - SVG support in PDFs

### Storage & Messaging
- **MinIO** - S3-compatible object storage (self-hosted)
- **RabbitMQ** - Message queue for async processing
- **Spring AMQP** - RabbitMQ integration

### API & Documentation
- **SpringDoc OpenAPI 2.8.14** - OpenAPI 3.0 specification
- **Scalar UI** - Modern API documentation interface

### Development & Testing
- **Lombok** - Boilerplate code reduction
- **Spring Boot DevTools** - Hot reload
- **JUnit 5** - Testing framework
- **Testcontainers** - Integration testing with real services
- **MockMVC** - REST API testing
- **Awaitility** - Async testing utilities

## Getting Started

### Prerequisites

- **Java 25** (JDK)
- **Gradle 8.x** (or use the wrapper)
- **Docker & Docker Compose** (for infrastructure)
- **Git**

### Installation

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd certmgmt
   ```

2. **Start infrastructure services**
   ```bash
   docker-compose up -d
   ```

   This starts:
   - PostgreSQL (port 5432)
   - RabbitMQ (port 5672)
   - MinIO (API: 9000, Console: 9001)

3. **Verify services are running**
   ```bash
   docker-compose ps
   ```

4. **Create MinIO bucket** (first time only)
   - Navigate to http://localhost:9001
   - Login: `minioadmin` / `minioadmin`
   - Create bucket named `certificates`

### Running the Application

**Option 1: Using Gradle (Development)**
```bash
./gradlew bootRun
```

**Option 2: Using Gradle with Tests**
```bash
./gradlew bootRunWithTests
```

**Option 3: Build and run JAR**
```bash
./gradlew build
java -jar build/libs/certmgmt-0.0.1.jar
```

**Option 4: Skip tests for faster startup**
```bash
./gradlew bootRun -x test
```

The application will start on **http://localhost:8080**

### Verify Installation

1. **Health Check**
   ```bash
   curl http://localhost:8080/actuator/health
   ```

2. **API Documentation**
   Open http://localhost:8080/scalar in your browser

3. **Create your first customer**
   ```bash
   curl -X POST http://localhost:8080/api/customers \
     -H "Content-Type: application/json" \
     -d '{
       "name": "Acme Corporation",
       "tenantSchema": "acme_corp",
       "domain": "acme.com",
       "maxUsers": 50,
       "maxCertificatesPerMonth": 1000
     }'
   ```

## API Documentation

### Interactive Documentation

- **Scalar UI**: http://localhost:8080/scalar (Recommended)
- **OpenAPI JSON**: http://localhost:8080/v3/api-docs
- **OpenAPI YAML**: http://localhost:8080/v3/api-docs.yaml

### Key Endpoints

#### Customer Management
```
POST   /api/customers              Create customer (tenant onboarding)
GET    /api/customers              List all customers
GET    /api/customers/{id}         Get customer details
```

#### Template Management
```
POST   /api/templates              Create template
GET    /api/templates              List templates
GET    /api/templates/{id}         Get template by ID
GET    /api/templates/code/{code}  Get template by code
PUT    /api/templates/{id}         Update template
DELETE /api/templates/{id}         Delete template
```

#### Template Versions
```
POST   /api/template-versions      Create new version
GET    /api/template-versions      List versions
GET    /api/template-versions/{id} Get version details
PUT    /api/template-versions/{id} Update version
DELETE /api/template-versions/{id} Delete version
```

#### Certificates
```
POST   /api/certificates                      Generate certificate
GET    /api/certificates                      List certificates (with filters)
GET    /api/certificates/{id}                 Get certificate details
GET    /api/certificates/number/{number}      Get by certificate number
PUT    /api/certificates/{id}                 Update certificate
DELETE /api/certificates/{id}                 Delete certificate
POST   /api/certificates/{id}/revoke          Revoke certificate
GET    /api/certificates/{id}/download-url    Get pre-signed download URL
GET    /api/certificates/verify/{hash}        Verify certificate (public)
```

### Request Headers

All tenant-specific endpoints require one of:
- `X-Tenant-Id: <customer_id>`
- `X-Tenant-Schema: <schema_name>`

### Response Format

All API responses follow this structure:
```json
{
  "success": true,
  "message": "Operation completed successfully",
  "data": { ... },
  "details": null
}
```

Error responses:
```json
{
  "success": false,
  "message": "Error description",
  "data": null,
  "details": "Detailed error information"
}
```

## Multi-Tenancy

### Tenant Schema Isolation

Each customer gets a dedicated PostgreSQL schema, ensuring:
- **Data isolation**: No cross-tenant data leakage
- **Security**: Schema-level access control
- **Compliance**: Audit trails per tenant
- **Scalability**: Independent schema evolution

### Tenant Onboarding Flow

1. **Create Customer**
   ```bash
   POST /api/customers
   {
     "name": "Customer Name",
     "tenantSchema": "customer_schema",
     "domain": "customer.com",
     "maxUsers": 100,
     "maxCertificatesPerMonth": 5000
   }
   ```

2. **Automatic Schema Creation**
   - Application creates schema: `customer_schema`
   - Liquibase runs migrations
   - Tables created: users, template, template_version, certificate, etc.

3. **Start Using APIs**
   - Include `X-Tenant-Schema: customer_schema` in all requests
   - All data automatically isolated to this schema

### Tenant Context Propagation

The system automatically:
- Extracts tenant information from request headers
- Validates tenant schema exists
- Switches Hibernate connection to correct schema
- Propagates context through async operations (RabbitMQ)

## Certificate Generation

### Template Structure

Templates use HTML + CSS with Thymeleaf syntax:

```html
<!DOCTYPE html>
<html>
<head>
    <style>
        body { font-family: Arial, sans-serif; }
        .certificate { border: 5px solid #gold; padding: 50px; }
        .recipient { font-size: 32px; color: #333; }
    </style>
</head>
<body>
    <div class="certificate">
        <h1>Certificate of Completion</h1>
        <p class="recipient">Awarded to: <strong th:text="${recipientName}"></strong></p>
        <p th:text="${courseTitle}"></p>
        <p>Date: <span th:text="${completionDate}"></span></p>
    </div>
</body>
</html>
```

### Generating Certificates

**Synchronous Generation** (immediate PDF):
```bash
POST /api/certificates
X-Tenant-Schema: acme_corp
Content-Type: application/json

{
  "customerId": 1,
  "templateVersionId": 5,
  "recipientData": {
    "recipientName": "John Doe",
    "courseTitle": "Advanced Java Programming",
    "completionDate": "2025-12-06"
  },
  "metadata": {
    "courseId": "JAVA-301",
    "instructor": "Jane Smith"
  },
  "expiresAt": "2026-12-06T00:00:00"
}
```

**Asynchronous Generation** (for bulk operations):
- Same request format
- Returns immediately with certificate ID
- Worker processes in background
- Poll status or use webhooks (future feature)

### Certificate Verification

**Public Verification Endpoint**:
```bash
GET /api/certificates/verify/{hash}
```

Returns:
```json
{
  "success": true,
  "message": "Certificate verified successfully",
  "data": {
    "certificateNumber": "CERT-2025-001234",
    "issuedAt": "2025-12-06T10:30:00",
    "status": "ISSUED",
    "recipientData": { ... }
  }
}
```

### Downloading Certificates

```bash
GET /api/certificates/{id}/download-url
X-Tenant-Schema: acme_corp
```

Returns a pre-signed URL valid for 60 minutes:
```json
{
  "success": true,
  "data": {
    "downloadUrl": "http://localhost:9000/certificates/acme_corp/2025/12/cert-123.pdf?X-Amz-...",
    "expiresAt": "2025-12-06T11:30:00"
  }
}
```

## Testing

### Running Tests

**All tests**:
```bash
./gradlew test
```

**Integration tests only**:
```bash
./gradlew test --tests '*IntegrationTest'
```

**E2E tests**:
```bash
./gradlew test --tests '*E2ETest'
```

**With detailed output**:
```bash
./gradlew test --info
```

### Test Infrastructure

Tests use **Testcontainers** for real service integration:
- PostgreSQL container
- RabbitMQ container
- MinIO container

No mocking of infrastructure - tests run against real services.

### Test Categories

1. **Unit Tests**: Service logic, validation, utilities
2. **Integration Tests**: Database operations, repository layer
3. **E2E Tests**: Full workflows (create template → generate certificate → verify)
4. **Controller Tests**: REST API endpoints with MockMVC

### Test Coverage

- 103 Java files (79 main + 24 test)
- Integration tests for all major workflows
- E2E tests for certificate generation pipeline
- Repository tests with Testcontainers

## Configuration

### Environment Variables

Create a `.env` file or export:

```bash
# Database
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/certmanagement
SPRING_DATASOURCE_USERNAME=certmgmt_admin
SPRING_DATASOURCE_PASSWORD=runPQSQLN0w

# RabbitMQ
SPRING_RABBITMQ_HOST=localhost
SPRING_RABBITMQ_PORT=5672
SPRING_RABBITMQ_USERNAME=myuser
SPRING_RABBITMQ_PASSWORD=secret

# MinIO
STORAGE_MINIO_ENDPOINT=http://localhost:9000
STORAGE_MINIO_ACCESS_KEY=minioadmin
STORAGE_MINIO_SECRET_KEY=minioadmin
STORAGE_MINIO_BUCKET_NAME=certificates
STORAGE_MINIO_SIGNED_URL_EXPIRATION_MINUTES=60
```

### Application Profiles

- **Default**: Development with local services
- **Test**: Test profile with Testcontainers
- **Production**: Production settings (create `application-prod.properties`)

### Key Configuration Properties

See `src/main/resources/application.properties` for:
- Database connection pooling
- Hibernate multi-tenancy settings
- Liquibase changelog location
- RabbitMQ retry policies
- MinIO bucket configuration
- Actuator endpoints
- OpenAPI settings

## Project Structure

```
certmgmt/
├── src/
│   ├── main/
│   │   ├── java/tech/seccertificate/certmgmt/
│   │   │   ├── config/              # Configuration classes
│   │   │   │   ├── MultiTenantConfig.java
│   │   │   │   ├── SecurityConfig.java
│   │   │   │   ├── RabbitMQConfig.java
│   │   │   │   ├── StorageConfig.java
│   │   │   │   └── OpenApiConfig.java
│   │   │   ├── controller/          # REST controllers
│   │   │   │   ├── CustomerController.java
│   │   │   │   ├── TemplateController.java
│   │   │   │   ├── TemplateVersionController.java
│   │   │   │   └── CertificateController.java
│   │   │   ├── dto/                 # Data Transfer Objects
│   │   │   │   ├── certificate/
│   │   │   │   ├── customer/
│   │   │   │   ├── template/
│   │   │   │   └── message/
│   │   │   ├── entity/              # JPA entities
│   │   │   │   ├── Customer.java
│   │   │   │   ├── User.java
│   │   │   │   ├── Template.java
│   │   │   │   ├── TemplateVersion.java
│   │   │   │   ├── Certificate.java
│   │   │   │   └── CertificateHash.java
│   │   │   ├── exception/           # Custom exceptions
│   │   │   ├── repository/          # Data access layer
│   │   │   └── service/             # Business logic
│   │   │       ├── CustomerService.java
│   │   │       ├── TemplateService.java
│   │   │       ├── CertificateService.java
│   │   │       ├── PdfGenerationService.java
│   │   │       └── StorageService.java
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── db/changelog/        # Liquibase migrations
│   │       └── templates/           # Thymeleaf templates
│   └── test/
│       ├── java/tech/seccertificate/certmgmt/
│       │   ├── integration/         # Integration tests
│       │   ├── e2e/                 # End-to-end tests
│       │   └── service/             # Unit tests
│       └── resources/
│           └── application-test.properties
├── build.gradle                     # Build configuration
├── compose.yaml                     # Docker Compose for local dev
└── README.md                        # This file
```

## Security

### Current Security Features

1. **Multi-Tenancy Isolation**
   - Schema-based data separation
   - Automatic tenant validation
   - Context propagation

2. **Certificate Security**
   - SHA-256 cryptographic hashing
   - Hash-based verification
   - Tamper detection

3. **Storage Security**
   - Pre-signed URLs with expiration
   - Time-limited access (default: 60 minutes)
   - Bucket-level isolation

4. **Audit Logging**
   - Global audit logs (cross-tenant)
   - Tenant-specific audit logs
   - Action tracking (CREATE, UPDATE, DELETE)

### Planned Security Enhancements

- OAuth2 Authorization Server integration
- Keycloak user management
- Role-based access control (RBAC)
- API key authentication
- Rate limiting
- CORS configuration

### Security Best Practices

- Never commit credentials to version control
- Use environment variables for sensitive data
- Rotate MinIO and database credentials regularly
- Enable SSL/TLS in production
- Configure firewall rules for database and RabbitMQ
- Regular security audits and updates

## Infrastructure Services

### PostgreSQL

**Access**:
- Host: localhost:5432
- Database: certmanagement
- User: certmgmt_admin
- Password: runPQSQLN0w

**Schema Management**:
- Public schema: customer table, global audit
- Tenant schemas: Dynamically created per customer
- Migrations: Managed by Liquibase

### RabbitMQ

**Access**:
- Host: localhost:5672
- Management UI: http://localhost:15672 (if enabled)
- User: myuser
- Password: secret

**Queue Configuration**:
- Exchange: `certificate.exchange` (topic)
- Queue: `certificate.generation.queue`
- DLQ: `certificate.generation.dlq`
- Routing Key: `certificate.generate`
- TTL: 1 hour
- Prefetch: 10 messages
- Concurrent consumers: 2-5

### MinIO

**Access**:
- API: http://localhost:9000
- Console: http://localhost:9001
- Username: minioadmin
- Password: minioadmin

**Bucket Structure**:
```
certificates/
├── acme_corp/
│   └── 2025/
│       └── 12/
│           ├── cert-001.pdf
│           └── cert-002.pdf
└── other_tenant/
    └── 2025/
        └── ...
```

## Troubleshooting

### Common Issues

**1. Application fails to start - Database connection error**
```
Solution: Ensure PostgreSQL is running
docker-compose up -d postgres
```

**2. Certificate generation fails - MinIO connection error**
```
Solution: Verify MinIO is running and bucket exists
- Check: http://localhost:9001
- Create bucket: certificates
```

**3. Async certificate generation not working**
```
Solution: Check RabbitMQ is running
docker-compose up -d rabbitmq
```

**4. Tests failing - Testcontainers**
```
Solution: Ensure Docker is running
docker ps
```

**5. Multi-tenancy schema errors**
```
Solution: Verify tenant schema exists
SELECT schema_name FROM information_schema.schemata;
```

### Logging

Enable debug logging:
```properties
logging.level.tech.seccertificate.certmgmt=DEBUG
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.springframework.amqp=DEBUG
```

## Performance Considerations

### Scalability

- **Async Processing**: Use async mode for bulk operations
- **Worker Scaling**: Scale RabbitMQ consumers (2-5 concurrent)
- **Database**: Connection pooling configured
- **MinIO**: Supports distributed deployment
- **Caching**: Consider adding Redis for template caching

### Optimization Tips

1. Use async generation for > 10 certificates
2. Batch certificate creation requests
3. Cache frequently used templates
4. Monitor queue depth and scale workers
5. Use database indexes for common queries
6. Implement pagination for list endpoints

## Monitoring

### Actuator Endpoints

- Health: http://localhost:8080/actuator/health
- Info: http://localhost:8080/actuator/info
- Metrics: http://localhost:8080/actuator/metrics

### Health Checks

The `/actuator/health` endpoint checks:
- Database connectivity
- RabbitMQ connectivity
- Disk space
- Application status

### Production Monitoring

Recommended integrations:
- Prometheus for metrics
- Grafana for dashboards
- ELK stack for log aggregation
- Sentry for error tracking

## Deployment

### Building for Production

```bash
./gradlew clean build
```

Creates JAR: `build/libs/certmgmt-0.0.1.jar`

### Docker Deployment

```dockerfile
FROM eclipse-temurin:25-jdk-alpine
VOLUME /tmp
COPY build/libs/certmgmt-0.0.1.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
```

### Environment Configuration

Production checklist:
- [ ] Configure production database
- [ ] Set secure passwords
- [ ] Enable SSL/TLS
- [ ] Configure CORS
- [ ] Set up monitoring
- [ ] Configure backup strategy
- [ ] Review security settings
- [ ] Set up CI/CD pipeline

## Database Migrations

### Liquibase

Migrations located in: `src/main/resources/db/changelog/changes/`

**Run migrations manually**:
```bash
./gradlew update
```

**Rollback last changeset**:
```bash
./gradlew rollbackCount -PliquibaseCommandValue=1
```

**View migration status**:
```bash
./gradlew status
```

### Creating New Migrations

1. Create file: `src/main/resources/db/changelog/changes/V{number}_description.yaml`
2. Add to master: `db.changelog-master.yaml`
3. Test locally
4. Commit to version control

## Contributing

### Development Workflow

1. Create feature branch
2. Make changes
3. Run tests: `./gradlew test`
4. Build: `./gradlew build`
5. Create pull request

### Code Style

- Java 25 language features
- Lombok for boilerplate reduction
- Spring Boot conventions
- RESTful API design
- Comprehensive JavaDoc

### Testing Requirements

- Unit tests for service logic
- Integration tests for database operations
- E2E tests for critical workflows
- Minimum 80% code coverage

## License

Proprietary - All rights reserved

## Support

For issues and questions:
- Email: support@seccertificate.tech
- Documentation: http://localhost:8080/scalar
- Issue Tracker: [Internal]

## Changelog

### Version 1.0.0 (Current)

- Multi-tenant architecture with schema isolation
- Template management with versioning
- Synchronous and asynchronous certificate generation
- PDF generation from HTML templates
- MinIO storage integration
- RabbitMQ async processing
- SHA-256 certificate verification
- Comprehensive REST API
- Testcontainers integration testing
- OpenAPI documentation with Scalar UI

## Roadmap

### Planned Features

- [ ] OAuth2 authentication and authorization
- [ ] Webhook notifications for certificate events
- [ ] Bulk certificate generation API
- [ ] Template preview functionality
- [ ] Certificate analytics dashboard
- [ ] Email delivery integration
- [ ] QR code generation for certificates
- [ ] Certificate expiration notifications
- [ ] Advanced template editor UI
- [ ] Multi-language support
- [ ] Certificate revocation list (CRL)
- [ ] Blockchain-based verification option

---

**Built with Spring Boot 4.0.0 and Java 25**

For detailed API documentation, visit http://localhost:8080/scalar after starting the application.
