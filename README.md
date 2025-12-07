# Certificate Management System

Multi-tenant SaaS platform for PDF certificate generation, distribution, and verification. Full-stack solution with Angular frontend and Spring Boot backend.

[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Angular](https://img.shields.io/badge/Angular-20+-red.svg)](https://angular.io/)

## Overview

Enterprise certificate management system supporting:
- Multi-tenant isolation with schema-based data separation
- Template versioning and dynamic form generation
- Synchronous and asynchronous PDF generation (HTML → PDF)
- Certificate simulation and preview
- Cryptographic verification with signed hashes and QR codes
- Secure certificate download with signed URLs
- Audit logging and compliance tracking

## Features

### Backend (Spring Boot)
- Customer onboarding with automatic schema provisioning
- Template CRUD with versioning (DRAFT, PUBLISHED, ARCHIVED)
- Certificate generation (sync/async via RabbitMQ)
- PDF rendering worker (HTML → PDF using OpenHTMLtoPDF/Thymeleaf)
- S3/MinIO storage with signed URLs
- Certificate verification (hash validation + signature)
- Audit logging (global and tenant-specific)
- Rate limiting and RBAC (admin/editor/viewer/api-client)

### Frontend (Angular)
- JWT-based authentication
- Template creation and editing with dynamic form generator (JSON schema)
- Template versioning UI
- Certificate simulation preview (inline PDF)
- Certificate generation form
- Certificate list with filters
- Certificate viewer and download
- QR code scanner (optional)

### Infrastructure
- PostgreSQL with schema-based multi-tenancy
- Redis for caching
- RabbitMQ for async PDF generation
- MinIO/S3 for certificate storage
- Docker Compose for local development

## Architecture

### Multi-Tenancy

Schema-based isolation (Level 2):
- Public schema: customer registry, global audit logs
- Tenant schemas: users, templates, template_versions, certificates, certificate_hashes, audit_logs
- Tenant resolution via HTTP headers: `X-Tenant-Id` or `X-Tenant-Schema`

### System Components

```
Angular Frontend → Spring Boot API → RabbitMQ → PDF Workers
                              ↓
                    PostgreSQL (Multi-Schema)
                              ↓
                         MinIO/S3 Storage
                              ↓
                          Redis Cache
```

### Certificate Generation

**Synchronous**: Immediate PDF generation and return  
**Asynchronous**: Queue message → Worker processes → Status updates (PENDING → PROCESSING → ISSUED/FAILED)

## Technology Stack

**Backend**
- Java 25 + Spring Boot 4.0.0
- Spring Security (JWT/OAuth2)
- Spring Data JPA + Flyway
- PostgreSQL (multi-tenant schemas)
- RabbitMQ (Spring AMQP)
- MinIO SDK (S3-compatible storage)
- Redis
- OpenHTMLtoPDF + Thymeleaf/Freemarker
- SpringDoc OpenAPI

**Frontend**
- Angular 20+ (standalone components)
- JWT authentication
- Dynamic form generation

**Infrastructure**
- Docker Compose
- PostgreSQL, RabbitMQ, Redis, MinIO
- Keycloak (OAuth2/Spring Authorization Server)

## Getting Started

### Prerequisites
- Java 25, Gradle 8.x, Docker & Docker Compose

### Quick Start

1. Start infrastructure:
   ```bash
   docker-compose up -d
   ```

2. Create MinIO bucket `certificates` at http://localhost:9001 (minioadmin/minioadmin)

3. Run backend:
   ```bash
   ./gradlew bootRun
   ```

4. Verify:
   - Health: http://localhost:8080/actuator/health
   - API docs: http://localhost:8080/scalar

## API Documentation

**Interactive**: http://localhost:8080/scalar  
**OpenAPI**: http://localhost:8080/v3/api-docs

### Key Endpoints

- `POST /api/customers` - Create customer (tenant onboarding)
- `POST /api/templates` - Create template
- `GET /api/templates` - List templates
- `POST /api/template-versions` - Create template version
- `POST /api/certificates` - Generate certificate (sync/async)
- `GET /api/certificates` - List certificates (with filters)
- `GET /api/certificates/{id}/download-url` - Get signed download URL
- `GET /api/certificates/verify/{hash}` - Verify certificate (public)

**Tenant headers**: `X-Tenant-Id` or `X-Tenant-Schema`

## Multi-Tenancy

Schema-based isolation per customer:
- Automatic schema creation on customer onboarding
- Flyway migrations run per tenant schema
- Tenant resolution via HTTP headers
- Context propagation through async operations (RabbitMQ)

## Certificate Generation

**Templates**: HTML + CSS with Thymeleaf/Freemarker syntax, JSON schema for dynamic fields

**Generation modes**:
- Synchronous: Immediate PDF return
- Asynchronous: Queue-based processing via RabbitMQ

**Verification**: Public endpoint `/api/certificates/verify/{hash}` validates SHA-256 hash

**Download**: Pre-signed URLs (default 60 minutes expiration) from MinIO/S3

## Testing

```bash
./gradlew test                    # All tests
./gradlew test --tests '*IntegrationTest'  # Integration only
./gradlew test --tests '*E2ETest'          # E2E only
```

**Test Infrastructure**: Testcontainers (PostgreSQL, RabbitMQ, MinIO)  
**Coverage**: Unit, integration, E2E, and controller tests

## Configuration

Environment variables:
- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`
- `SPRING_RABBITMQ_HOST`, `SPRING_RABBITMQ_PORT`, `SPRING_RABBITMQ_USERNAME`, `SPRING_RABBITMQ_PASSWORD`
- `STORAGE_MINIO_ENDPOINT`, `STORAGE_MINIO_ACCESS_KEY`, `STORAGE_MINIO_SECRET_KEY`, `STORAGE_MINIO_BUCKET_NAME`

Profiles: `default` (dev), `test` (Testcontainers), `prod`  
See `application.properties` for full configuration.

## Project Structure

```
certmgmt/
├── src/main/java/.../certmgmt/
│   ├── config/          # Multi-tenancy, security, RabbitMQ, storage
│   ├── controller/      # REST endpoints
│   ├── dto/             # Data transfer objects
│   ├── entity/          # JPA entities
│   ├── repository/      # Data access layer
│   └── service/         # Business logic
├── src/main/resources/
│   ├── db/changelog/    # Flyway migrations
│   └── templates/       # Thymeleaf templates
└── compose.yaml         # Docker Compose services
```

## Security

- Schema-based tenant isolation
- SHA-256 hash verification with tamper detection
- Pre-signed URLs with expiration (default: 60 minutes)
- Audit logging (global and tenant-specific)
- OAuth2/JWT authentication (Keycloak/Spring Authorization Server)
- RBAC: admin, editor, viewer, api-client roles
- Rate limiting and CORS configuration

## Infrastructure

**PostgreSQL** (localhost:5432)
- Database: certmanagement
- Public schema: customer registry, global audit
- Tenant schemas: auto-created per customer
- Migrations: Flyway

**RabbitMQ** (localhost:5672)
- Exchange: `certificate.exchange` (topic)
- Queue: `certificate.generation.queue`
- DLQ: `certificate.generation.dlq`

**MinIO** (API: 9000, Console: 9001)
- Bucket: `certificates`
- Path structure: `{tenant}/{year}/{month}/cert-{id}.pdf`

**Redis** (caching)
**Keycloak** (OAuth2/authentication)

## Troubleshooting

- Database connection: `docker-compose up -d postgres`
- MinIO: Verify bucket exists at http://localhost:9001
- RabbitMQ: `docker-compose up -d rabbitmq`
- Tests: Ensure Docker is running for Testcontainers
- Schema errors: Verify tenant schema exists in PostgreSQL

Debug logging: `logging.level.tech.seccertificate.certmgmt=DEBUG`

## Performance

- Async processing for bulk operations (≥1000 certificates/min)
- Scale RabbitMQ workers (2-5 concurrent consumers)
- Redis caching for templates
- Database connection pooling
- Pagination for list endpoints

## Monitoring

Actuator endpoints: `/actuator/health`, `/actuator/metrics`  
Production: Prometheus, Grafana, ELK stack, Sentry

## Deployment

Build: `./gradlew clean build`  
Docker: Use `eclipse-temurin:25-jdk-alpine` base image  
Production: Configure database, SSL/TLS, CORS, monitoring, backups

## Database Migrations

Flyway migrations in `src/main/resources/db/changelog/`  
Run: `./gradlew flywayMigrate`  
Status: `./gradlew flywayInfo`
