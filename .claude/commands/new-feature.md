---
description: Scaffold a new feature slice with Controller, Service, Repository, DTOs, and test stubs.
---

Read CLAUDE.md for architecture rules (package-by-feature, controller→service→repository, DTOs at boundary).

Feature name: $ARGUMENTS

If no feature name provided, ask: "What is the feature name? (e.g., 'jobs', 'tailoring', 'scoring')"

Steps:

1. Determine the feature package name from $ARGUMENTS (lowercase, no spaces).
   Example: "job ingestion" → `jobs`, "Resume Tailoring" → `tailoring`

2. Determine the class name prefix (PascalCase). Example: `jobs` → `Job`, `tailoring` → `Tailoring`

3. Create the following files (only create Repository if data access is needed — ask if unclear):

   **Main source files:**

   `src/main/java/com/careercopilot/{feature}/{Name}Controller.java`
   - Annotated with `@RestController`, `@RequestMapping("/api/v1/{feature}")`, `@RequiredArgsConstructor`
   - Injects `{Name}Service`
   - Stub methods for each expected endpoint (return 501 Not Implemented initially)
   - Jakarta validation annotations on request DTOs (`@Valid`)

   `src/main/java/com/careercopilot/{feature}/{Name}Service.java`
   - Annotated with `@Service`, `@RequiredArgsConstructor`, `@Transactional`
   - Injects `{Name}Repository` (if data access) and any shared services
   - Stub methods matching controller expectations

   `src/main/java/com/careercopilot/{feature}/{Name}Repository.java` (if data access)
   - Extends `JpaRepository<{Name}Entity, UUID>`
   - Annotated with `@Repository`
   - Empty initially — add custom query methods as needed

   `src/main/java/com/careercopilot/{feature}/{Name}Entity.java` (if data access)
   - JPA entity with `@Entity`, `@Table(name = "{feature_plural}")`
   - Fields: `id` (UUID, `@Id`, `@GeneratedValue`), `createdAt`, `updatedAt` (`@CreationTimestamp`, `@UpdateTimestamp`)
   - No business logic in entity

   `src/main/java/com/careercopilot/{feature}/dto/` directory with:
   - `{Name}RequestDto.java` — incoming payload record with validation annotations
   - `{Name}ResponseDto.java` — outgoing payload record

   **Test files:**

   `src/test/java/com/careercopilot/{feature}/{Name}ControllerTest.java`
   - `@WebMvcTest({Name}Controller.class)`
   - `@MockBean {Name}Service`
   - Stub test methods: one per expected endpoint
   - Test happy path + 401 unauthenticated for each protected endpoint

   `src/test/java/com/careercopilot/{feature}/{Name}ServiceTest.java`
   - Plain JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`)
   - `@Mock {Name}Repository`
   - Stub test methods for each service method

   `src/test/java/com/careercopilot/{feature}/{Name}IntegrationTest.java`
   - `@SpringBootTest(webEnvironment = RANDOM_PORT)`
   - Uses `@Testcontainers` with PostgreSQL container
   - Full stack test: HTTP request → controller → service → DB
   - At minimum: one happy-path test per endpoint

4. After creating files, print a summary:
   "Created feature slice '{feature}':
   - Controller: src/main/java/com/careercopilot/{feature}/{Name}Controller.java
   - Service: src/main/java/com/careercopilot/{feature}/{Name}Service.java
   - Repository: src/main/java/com/careercopilot/{feature}/{Name}Repository.java
   - DTOs: src/main/java/com/careercopilot/{feature}/dto/
   - Tests: src/test/java/com/careercopilot/{feature}/

   Next: implement the stub methods, then run /test"
