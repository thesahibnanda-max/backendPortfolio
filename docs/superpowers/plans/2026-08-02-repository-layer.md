# Repository Layer (LLM Portfolio Application) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a PostgreSQL + jOOQ repository layer (`UserRepository`, `ChatRepository`) for the LLM portfolio backend, per the BRD, with idempotent schema initialization and no exposure of SQL details outside the repository package.

**Architecture:** `schema.sql` is the single source of truth for the database shape. It is used twice: (1) at **build time**, jOOQ's `DDLDatabase` code generator reads it to produce type-safe `Tables`/`Record` classes (no live DB needed to build) and (2) at **runtime**, a `SchemaInitializer` `ApplicationRunner` executes the same script against the real Postgres instance via jOOQ's parser (`CREATE TABLE/INDEX IF NOT EXISTS`, so it's safe to re-run). Repositories are interfaces in `repository/`, implemented by `Jooq*Repository` classes in `repository/jooq/` that depend only on `DSLContext` and the generated code — nothing outside the `repository` package ever sees jOOQ types or SQL.

**Tech Stack:** Java 25, Spring Boot 4.0.0 (`spring-boot-starter-jooq`), PostgreSQL (`org.postgresql:postgresql`), jOOQ 3.19.28 (version confirmed via `mvn help:evaluate -Dexpression=jooq.version` against this project's parent POM), Testcontainers 2.0.2 (managed by the Spring Boot BOM, confirmed via the same method) for integration tests, `com.github.f4b6a3:ulid-creator:5.2.3` for ULID generation.

## Global Constraints

- Repository layer only — no REST controllers, no auth logic, no LLM integration, no service layer (per BRD Section 1).
- No raw SQL, string concatenation, or manual `PreparedStatement`/JDBC outside `schema.sql` and the `SchemaInitializer` that executes it (BRD Section 14).
- No table is ever dropped automatically; schema init is idempotent and repeatable (BRD Section 4).
- Passwords: never plaintext, never logged, never returned in a way that resembles logging (BRD Section 18). Repositories accept a pre-hashed password string — hashing itself is out of scope (BRD Section 6 notes).
- Constructor injection only; no field injection (BRD Section 24).
- Service layer (future) depends only on `UserRepository`/`ChatRepository` interfaces, never the `Jooq*` implementations (BRD Section 13, Section 20 DIP).
- Messages are sorted by timestamp ascending both before persistence and before returning, unconditionally (BRD Section 9) — implemented once in `Message.sortedByTimestamp` and reused everywhere, never duplicated.
- Chat titles are stored as opaque strings; the repository never generates them (BRD Section 10).
- `application.yml` database properties are environment-overridable with the exact defaults from BRD Section 5 (`DB_HOST:localhost`, `DB_PORT:5432`, `DB_NAME:defaultDB`, `DB_USERNAME:postgres`, `DB_PASSWORD:postgres`).
- Docker is available in this environment (verified: `docker version` → 27.0.3) so Testcontainers-backed integration tests are used for repository verification, matching "Testable" (BRD Section 24).

## Documented Assumptions (spec is ambiguous or silent on these)

1. **`findChats(username)` ordering** — BRD Section 3.2 says "chatTitle or createdAt depending on implementation." This plan orders by `created_at DESC` (newest chat first), the typical UX for a chat list sidebar.
2. **ULID storage type** — Postgres has no built-in `ULID` column type. `chat_id` is stored as `VARCHAR(26)` (the canonical Crockford Base32 ULID string length), which is the standard practical mapping.
3. **`Message.timestamp` serializes with a trailing `Z`** — BRD Section 8 explicitly types it `Instant` (UTC instant), while the Section 7 JSON example omits the `Z` suffix. This plan follows the explicit type (Section 8) over the possibly-simplified example; `Instant` serializes as ISO-8601 with `Z`, e.g. `"2026-08-02T12:00:00Z"`.
4. **`UserEntity`/`ChatEntity` timestamp fields use `LocalDateTime`** (matching the `TIMESTAMP` — not `TIMESTAMPTZ` — column type in BRD Section 6), distinct from `Message.timestamp` which is `Instant` per explicit requirement.
5. **Entity types**: `Role` is an enum; `Message` and `UserEntity` are Java `record`s (naturally immutable, 2-3 fields each); `ChatEntity` uses Lombok `@Value @Builder` (6 fields — BRD Section 12 explicitly allows a builder "if object construction becomes large," and this matches the existing codebase's Lombok convention seen in `GroqCallRequest`).

## File Structure

```
pom.xml                                                          # + jOOQ, Postgres, ULID, Testcontainers deps + codegen plugin
src/main/resources/db/schema.sql                                 # DDL: single source of truth for codegen AND runtime init
src/main/resources/application.yml                                # + spring.datasource.*, spring.jooq.sql-dialect
src/main/java/net/sahibnanda/portfolio/
  utils/StringUtils.java                                          # + generateUlid(), isValidUlid()
  utils/JsonUtils.java                                             # + JavaTimeModule registration (needed for Instant)
  entity/Role.java                                                 # enum USER, ASSISTANT
  entity/Message.java                                              # record + sortedByTimestamp()
  entity/UserEntity.java                                           # record
  entity/ChatEntity.java                                           # Lombok @Value @Builder
  exception/RepositoryException.java                               # abstract base, extends RuntimeException
  exception/DuplicateUsernameException.java
  exception/UserNotFoundException.java
  exception/ChatNotFoundException.java
  exception/DatabaseOperationException.java
  repository/UserRepository.java                                   # interface
  repository/ChatRepository.java                                   # interface
  repository/init/SchemaInitializer.java                           # ApplicationRunner, executes schema.sql idempotently
  repository/jooq/JooqUserRepository.java                          # implements UserRepository
  repository/jooq/JooqChatRepository.java                          # implements ChatRepository
target/generated-sources/jooq/net/sahibnanda/portfolio/jooq/...    # generated by jOOQ codegen at build time (gitignored via target/)
src/test/java/net/sahibnanda/portfolio/
  utils/StringUtilsTest.java                                       # ULID unit tests (no container)
  utils/JsonUtilsTest.java                                         # Instant round-trip unit test (no container)
  repository/AbstractRepositoryIntegrationTest.java                # shared Testcontainers Postgres base class
  repository/init/SchemaInitializerTest.java
  repository/jooq/JooqUserRepositoryTest.java
  repository/jooq/JooqChatRepositoryTest.java
```

---

### Task 1: Add dependencies and jOOQ code-generation plugin

**Files:**
- Modify: `pom.xml`

**Interfaces:**
- Produces: `spring-boot-starter-jooq` autoconfigured `DSLContext` bean (consumed by Task 8-10); generated classes under package `net.sahibnanda.portfolio.jooq` (consumed by Task 8-10); ULID generation via `com.github.f4b6a3.ulid.UlidCreator`/`Ulid` (consumed by Task 5).

- [ ] **Step 1: Add runtime dependencies**

Add inside `<dependencies>` in `pom.xml`, after the existing `okhttp` dependency and before `spring-boot-starter-test`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jooq</artifactId>
</dependency>

<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>

<dependency>
    <groupId>com.fasterxml.jackson.datatype</groupId>
    <artifactId>jackson-datatype-jsr310</artifactId>
</dependency>

<dependency>
    <groupId>com.github.f4b6a3</groupId>
    <artifactId>ulid-creator</artifactId>
    <version>5.2.3</version>
</dependency>
```

- [ ] **Step 2: Add test-scoped Testcontainers dependencies**

Add after `spring-boot-starter-test`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>

<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>

<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

None of these need explicit `<version>` — they're managed by the `spring-boot-starter-parent:4.0.0` BOM (confirmed: `spring.jooq.version` resolves to `3.19.28`, `testcontainers.version` resolves to `2.0.2` in this project).

- [ ] **Step 3: Add the jOOQ code-generation plugin**

Add inside `<build><plugins>` in `pom.xml`, after the `spotless-maven-plugin` block:

```xml
<plugin>
    <groupId>org.jooq</groupId>
    <artifactId>jooq-codegen-maven</artifactId>
    <version>3.19.28</version>
    <executions>
        <execution>
            <id>generate-jooq-sources</id>
            <phase>generate-sources</phase>
            <goals>
                <goal>generate</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <generator>
            <database>
                <name>org.jooq.meta.extensions.ddl.DDLDatabase</name>
                <properties>
                    <property>
                        <key>scripts</key>
                        <value>src/main/resources/db/schema.sql</value>
                    </property>
                    <property>
                        <key>sort</key>
                        <value>semantic</value>
                    </property>
                    <property>
                        <key>defaultNameCase</key>
                        <value>lower</value>
                    </property>
                </properties>
                <inputSchema>public</inputSchema>
            </database>
            <target>
                <packageName>net.sahibnanda.portfolio.jooq</packageName>
                <directory>target/generated-sources/jooq</directory>
            </target>
        </generator>
    </configuration>
    <dependencies>
        <dependency>
            <groupId>org.jooq</groupId>
            <artifactId>jooq-meta-extensions</artifactId>
            <version>3.19.28</version>
        </dependency>
    </dependencies>
</plugin>
```

This reads `src/main/resources/db/schema.sql` (created in Task 2) at build time — no live database needed for codegen. The plugin automatically registers `target/generated-sources/jooq` as a compile source root.

- [ ] **Step 4: Verify the POM is valid**

Run: `mvn -q validate`
Expected: no output, exit code 0. (Codegen itself can't run yet — `schema.sql` doesn't exist until Task 2 — this step only confirms the XML/plugin config is well-formed.)

- [ ] **Step 5: Commit**

```bash
git add pom.xml
git commit -m "build: add jOOQ, PostgreSQL, ULID, and Testcontainers dependencies"
```

---

### Task 2: Database schema (DDL) and configuration

**Files:**
- Create: `src/main/resources/db/schema.sql`
- Modify: `src/main/resources/application.yml`

**Interfaces:**
- Produces: `db/schema.sql` classpath resource (consumed by Task 1's codegen plugin and Task 6's `SchemaInitializer`); `spring.datasource.*` / `spring.jooq.sql-dialect` properties (consumed by Spring Boot's jOOQ autoconfiguration).

- [ ] **Step 1: Write the schema DDL**

Create `src/main/resources/db/schema.sql`:

```sql
CREATE TABLE IF NOT EXISTS users (
    username       VARCHAR(255) PRIMARY KEY,
    password_hash  VARCHAR(255) NOT NULL,
    created_at     TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS chats (
    chat_id     VARCHAR(26) PRIMARY KEY,
    username    VARCHAR(255) NOT NULL,
    chat_title  VARCHAR(255) NOT NULL,
    messages    JSONB NOT NULL,
    created_at  TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP NOT NULL,
    CONSTRAINT fk_chats_username FOREIGN KEY (username) REFERENCES users (username) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_chats_username_created_at ON chats (username, created_at);
```

`chat_id` is `VARCHAR(26)` — the fixed length of a Crockford Base32-encoded ULID (see Documented Assumption 2). `users.username` and `chats.chat_id` already have implicit unique indexes via their `PRIMARY KEY` constraints, satisfying the "index on username / chat_id" requirement (BRD Section 17); `idx_chats_username_created_at` covers both plain username lookups (as its leftmost column) and the `findChats` ordering query.

- [ ] **Step 2: Add datasource and jOOQ configuration to application.yml**

Edit `src/main/resources/application.yml` — add a `datasource` and `jooq` block under the existing `spring:` key:

```yaml
spring:
  application:
    name: backend-portfolio
  threads:
    virtual:
      enabled: true
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:defaultDB}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
  jooq:
    sql-dialect: POSTGRES
server:
  port: 8080

groq:
  base-url: ${GROQ_BASE_URL:https://api.groq.com/openai/v1}
  api-keys: ${GROQ_API_KEYS:sample-api-key-1,sample-api-key-2}
```

- [ ] **Step 3: Verify jOOQ code generation runs against the new schema**

Run: `mvn -q generate-sources`
Expected: exit code 0, and `target/generated-sources/jooq/net/sahibnanda/portfolio/jooq/Tables.java` exists, along with `tables/Users.java`, `tables/Chats.java`, `tables/records/UsersRecord.java`, `tables/records/ChatsRecord.java`.

Run: `ls target/generated-sources/jooq/net/sahibnanda/portfolio/jooq/tables/`
Expected: `Chats.java`, `Users.java`, `records/` directory present.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/db/schema.sql src/main/resources/application.yml
git commit -m "feat: add database schema DDL and datasource configuration"
```

---

### Task 3: Testcontainers integration test base

**Files:**
- Create: `src/test/java/net/sahibnanda/portfolio/repository/AbstractRepositoryIntegrationTest.java`

**Interfaces:**
- Consumes: nothing project-specific yet — pure Testcontainers + Spring Boot test setup.
- Produces: `AbstractRepositoryIntegrationTest` (extended by Task 6, 8, 9's test classes), which boots a real Spring context against a real Postgres container (so `SchemaInitializer` runs for real) and exposes a clean-slate `dslContext` field before every test.

- [ ] **Step 1: Write the base test class**

Create `src/test/java/net/sahibnanda/portfolio/repository/AbstractRepositoryIntegrationTest.java`:

```java
package net.sahibnanda.portfolio.repository;

import net.sahibnanda.portfolio.jooq.Tables;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
public abstract class AbstractRepositoryIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired protected DSLContext dslContext;

  @BeforeEach
  void cleanDatabase() {
    dslContext.deleteFrom(Tables.CHATS).execute();
    dslContext.deleteFrom(Tables.USERS).execute();
  }
}
```

`@ServiceConnection` auto-wires `spring.datasource.*` to point at the container — no `@DynamicPropertySource` needed. `SchemaInitializer` (Task 6) runs automatically as part of Spring context startup, so tables already exist by the time `cleanDatabase()` runs. Child tables are deleted before parent tables to satisfy the `chats.username` foreign key.

- [ ] **Step 2: Verify it compiles**

Run: `mvn -q test-compile`
Expected: exit code 0. (This class has no `@Test` methods itself, so nothing to run yet — Task 6 is the first concrete subclass.)

- [ ] **Step 3: Commit**

```bash
git add src/test/java/net/sahibnanda/portfolio/repository/AbstractRepositoryIntegrationTest.java
git commit -m "test: add shared Testcontainers Postgres integration test base"
```

---

### Task 4: ULID utilities and Instant-safe JSON utilities

**Files:**
- Modify: `src/main/java/net/sahibnanda/portfolio/utils/StringUtils.java`
- Modify: `src/main/java/net/sahibnanda/portfolio/utils/JsonUtils.java`
- Test: `src/test/java/net/sahibnanda/portfolio/utils/StringUtilsTest.java`
- Test: `src/test/java/net/sahibnanda/portfolio/utils/JsonUtilsTest.java`

**Interfaces:**
- Produces: `StringUtils.generateUlid(): String`, `StringUtils.isValidUlid(String): boolean` (consumed by Task 5's `Message` timestamp validation is unrelated; consumed by Task 10's `JooqChatRepository.create`); `JsonUtils` now correctly round-trips `java.time.Instant` (consumed by Task 5's `Message` JSONB serialization in Task 10).

- [ ] **Step 1: Write failing tests for StringUtils ULID methods**

Create `src/test/java/net/sahibnanda/portfolio/utils/StringUtilsTest.java`:

```java
package net.sahibnanda.portfolio.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StringUtilsTest {

  @Test
  void generateUlidProducesTwentySixCharacterUppercaseValue() {
    String ulid = StringUtils.generateUlid();

    assertThat(ulid).hasSize(26);
    assertThat(StringUtils.isValidUlid(ulid)).isTrue();
  }

  @Test
  void generateUlidProducesUniqueValues() {
    String first = StringUtils.generateUlid();
    String second = StringUtils.generateUlid();

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void isValidUlidRejectsNullEmptyAndMalformedInput() {
    assertThat(StringUtils.isValidUlid(null)).isFalse();
    assertThat(StringUtils.isValidUlid("")).isFalse();
    assertThat(StringUtils.isValidUlid("not-a-ulid")).isFalse();
  }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q test -Dtest=StringUtilsTest`
Expected: FAIL — compile error, `generateUlid`/`isValidUlid` do not exist on `StringUtils`.

- [ ] **Step 3: Implement the ULID methods**

Edit `src/main/java/net/sahibnanda/portfolio/utils/StringUtils.java`:

```java
package net.sahibnanda.portfolio.utils;

import com.github.f4b6a3.ulid.Ulid;
import com.github.f4b6a3.ulid.UlidCreator;
import lombok.experimental.UtilityClass;

@UtilityClass
public class StringUtils {

  public boolean isEmpty(String str) {
    return str == null || str.trim().isEmpty();
  }

  public boolean equalsIgnoreCase(String a, String b) {
    if (a == null && b == null) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    return a.equalsIgnoreCase(b);
  }

  public String generateUlid() {
    return UlidCreator.getUlid().toString();
  }

  public boolean isValidUlid(String value) {
    if (isEmpty(value)) {
      return false;
    }
    try {
      Ulid.from(value);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q test -Dtest=StringUtilsTest`
Expected: PASS, 3 tests green.

- [ ] **Step 5: Write a failing test for Instant round-tripping in JsonUtils**

Create `src/test/java/net/sahibnanda/portfolio/utils/JsonUtilsTest.java`:

```java
package net.sahibnanda.portfolio.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class JsonUtilsTest {

  private record InstantHolder(Instant timestamp) {}

  @Test
  void roundTripsInstantAsIso8601() {
    Instant original = Instant.parse("2026-08-02T12:00:00Z");

    String json = JsonUtils.toJson(new InstantHolder(original));
    InstantHolder deserialized = JsonUtils.fromJson(json, InstantHolder.class);

    assertThat(json).contains("2026-08-02T12:00:00Z");
    assertThat(deserialized.timestamp()).isEqualTo(original);
  }
}
```

- [ ] **Step 6: Run test to verify it fails**

Run: `mvn -q test -Dtest=JsonUtilsTest`
Expected: FAIL — `com.fasterxml.jackson.databind.exc.InvalidDefinitionException` (no serializer/deserializer for `java.time.Instant`, `JavaTimeModule` not registered).

- [ ] **Step 7: Register JavaTimeModule in JsonUtils**

Edit `src/main/java/net/sahibnanda/portfolio/utils/JsonUtils.java`:

```java
package net.sahibnanda.portfolio.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.UncheckedIOException;
import lombok.experimental.UtilityClass;

@UtilityClass
public class JsonUtils {

  private final ObjectMapper objectMapper =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  public <T> T fromJson(String json, Class<T> clazz) {
    try {
      return objectMapper.readValue(json, clazz);
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException("Failed to deserialize JSON", e);
    }
  }

  public <T> T fromJson(String json, TypeReference<T> typeReference) {
    try {
      return objectMapper.readValue(json, typeReference);
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException("Failed to deserialize JSON", e);
    }
  }

  public String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException("Failed to serialize object", e);
    }
  }
}
```

- [ ] **Step 8: Run tests to verify they pass**

Run: `mvn -q test -Dtest=StringUtilsTest,JsonUtilsTest`
Expected: PASS, 4 tests green.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/net/sahibnanda/portfolio/utils/StringUtils.java \
        src/main/java/net/sahibnanda/portfolio/utils/JsonUtils.java \
        src/test/java/net/sahibnanda/portfolio/utils/StringUtilsTest.java \
        src/test/java/net/sahibnanda/portfolio/utils/JsonUtilsTest.java
git commit -m "feat: add ULID generation/validation and fix Instant JSON serialization"
```

---

### Task 5: Domain exceptions

**Files:**
- Create: `src/main/java/net/sahibnanda/portfolio/exception/RepositoryException.java`
- Create: `src/main/java/net/sahibnanda/portfolio/exception/DuplicateUsernameException.java`
- Create: `src/main/java/net/sahibnanda/portfolio/exception/UserNotFoundException.java`
- Create: `src/main/java/net/sahibnanda/portfolio/exception/ChatNotFoundException.java`
- Create: `src/main/java/net/sahibnanda/portfolio/exception/DatabaseOperationException.java`

**Interfaces:**
- Produces: four concrete unchecked exception types (consumed by Task 9's `JooqUserRepository` and Task 10's `JooqChatRepository`).

This is pure scaffolding (no logic to test beyond construction), following the existing `GroqCallException` two-constructor pattern already in this codebase.

- [ ] **Step 1: Create the base exception**

Create `src/main/java/net/sahibnanda/portfolio/exception/RepositoryException.java`:

```java
package net.sahibnanda.portfolio.exception;

public abstract class RepositoryException extends RuntimeException {

  protected RepositoryException(String message) {
    super(message);
  }

  protected RepositoryException(String message, Throwable cause) {
    super(message, cause);
  }
}
```

- [ ] **Step 2: Create the four concrete exceptions**

Create `src/main/java/net/sahibnanda/portfolio/exception/DuplicateUsernameException.java`:

```java
package net.sahibnanda.portfolio.exception;

public class DuplicateUsernameException extends RepositoryException {

  public DuplicateUsernameException(String username) {
    super("Username already exists: " + username);
  }
}
```

Create `src/main/java/net/sahibnanda/portfolio/exception/UserNotFoundException.java`:

```java
package net.sahibnanda.portfolio.exception;

public class UserNotFoundException extends RepositoryException {

  public UserNotFoundException(String username) {
    super("User not found: " + username);
  }
}
```

Create `src/main/java/net/sahibnanda/portfolio/exception/ChatNotFoundException.java`:

```java
package net.sahibnanda.portfolio.exception;

public class ChatNotFoundException extends RepositoryException {

  public ChatNotFoundException(String chatId) {
    super("Chat not found: " + chatId);
  }
}
```

Create `src/main/java/net/sahibnanda/portfolio/exception/DatabaseOperationException.java`:

```java
package net.sahibnanda.portfolio.exception;

public class DatabaseOperationException extends RepositoryException {

  public DatabaseOperationException(String message, Throwable cause) {
    super(message, cause);
  }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `mvn -q compile`
Expected: exit code 0.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/net/sahibnanda/portfolio/exception/RepositoryException.java \
        src/main/java/net/sahibnanda/portfolio/exception/DuplicateUsernameException.java \
        src/main/java/net/sahibnanda/portfolio/exception/UserNotFoundException.java \
        src/main/java/net/sahibnanda/portfolio/exception/ChatNotFoundException.java \
        src/main/java/net/sahibnanda/portfolio/exception/DatabaseOperationException.java
git commit -m "feat: add repository domain exceptions"
```

---

### Task 6: Entities (Role, Message, UserEntity, ChatEntity)

**Files:**
- Create: `src/main/java/net/sahibnanda/portfolio/entity/Role.java`
- Create: `src/main/java/net/sahibnanda/portfolio/entity/Message.java`
- Create: `src/main/java/net/sahibnanda/portfolio/entity/UserEntity.java`
- Create: `src/main/java/net/sahibnanda/portfolio/entity/ChatEntity.java`
- Test: `src/test/java/net/sahibnanda/portfolio/entity/MessageTest.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `Role` enum (`USER`, `ASSISTANT`); `Message(Role, String, Instant)` record with `Message.sortedByTimestamp(List<Message>): List<Message>` (consumed by Task 10's `JooqChatRepository`); `UserEntity(String, String, LocalDateTime)` record (consumed by Task 9); `ChatEntity` builder with `chatId/username/chatTitle/messages/createdAt/updatedAt` (consumed by Task 10).

- [ ] **Step 1: Write a failing test for Message ordering and null validation**

Create `src/test/java/net/sahibnanda/portfolio/entity/MessageTest.java`:

```java
package net.sahibnanda.portfolio.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MessageTest {

  @Test
  void sortedByTimestampOrdersAscendingRegardlessOfInputOrder() {
    Message earliest = new Message(Role.USER, "hi", Instant.parse("2026-08-02T12:00:00Z"));
    Message middle = new Message(Role.ASSISTANT, "hello", Instant.parse("2026-08-02T12:00:05Z"));
    Message latest = new Message(Role.USER, "bye", Instant.parse("2026-08-02T12:00:10Z"));

    List<Message> sorted = Message.sortedByTimestamp(List.of(latest, earliest, middle));

    assertThat(sorted).containsExactly(earliest, middle, latest);
  }

  @Test
  void constructorRejectsNullTimestamp() {
    assertThatThrownBy(() -> new Message(Role.USER, "hi", null))
        .isInstanceOf(NullPointerException.class);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=MessageTest`
Expected: FAIL — `Role` and `Message` classes do not exist yet.

- [ ] **Step 3: Implement Role**

Create `src/main/java/net/sahibnanda/portfolio/entity/Role.java`:

```java
package net.sahibnanda.portfolio.entity;

public enum Role {
  USER,
  ASSISTANT
}
```

- [ ] **Step 4: Implement Message**

Create `src/main/java/net/sahibnanda/portfolio/entity/Message.java`:

```java
package net.sahibnanda.portfolio.entity;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record Message(Role role, String message, Instant timestamp) {

  public Message {
    Objects.requireNonNull(role, "role must not be null");
    Objects.requireNonNull(message, "message must not be null");
    Objects.requireNonNull(timestamp, "timestamp must not be null");
  }

  public static List<Message> sortedByTimestamp(List<Message> messages) {
    Objects.requireNonNull(messages, "messages must not be null");
    return messages.stream().sorted(Comparator.comparing(Message::timestamp)).toList();
  }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -q test -Dtest=MessageTest`
Expected: PASS, 2 tests green.

- [ ] **Step 6: Implement UserEntity**

Create `src/main/java/net/sahibnanda/portfolio/entity/UserEntity.java`:

```java
package net.sahibnanda.portfolio.entity;

import java.time.LocalDateTime;
import java.util.Objects;

public record UserEntity(String username, String passwordHash, LocalDateTime createdAt) {

  public UserEntity {
    Objects.requireNonNull(username, "username must not be null");
    Objects.requireNonNull(passwordHash, "passwordHash must not be null");
    Objects.requireNonNull(createdAt, "createdAt must not be null");
  }
}
```

- [ ] **Step 7: Implement ChatEntity**

Create `src/main/java/net/sahibnanda/portfolio/entity/ChatEntity.java`:

```java
package net.sahibnanda.portfolio.entity;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ChatEntity {
  String chatId;
  String username;
  String chatTitle;
  List<Message> messages;
  LocalDateTime createdAt;
  LocalDateTime updatedAt;
}
```

- [ ] **Step 8: Verify everything compiles and tests pass**

Run: `mvn -q test -Dtest=MessageTest`
Expected: PASS, 2 tests green.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/net/sahibnanda/portfolio/entity/ src/test/java/net/sahibnanda/portfolio/entity/
git commit -m "feat: add Role, Message, UserEntity, and ChatEntity domain entities"
```

---

### Task 7: Schema initializer

**Files:**
- Create: `src/main/java/net/sahibnanda/portfolio/repository/init/SchemaInitializer.java`
- Test: `src/test/java/net/sahibnanda/portfolio/repository/init/SchemaInitializerTest.java`

**Interfaces:**
- Consumes: `DSLContext` (from `spring-boot-starter-jooq` autoconfiguration, Task 1); `db/schema.sql` classpath resource (Task 2).
- Produces: `users`/`chats` tables and indexes present in the database at application startup, before any repository is used. `AbstractRepositoryIntegrationTest` (Task 3) subclasses implicitly depend on this running successfully.

- [ ] **Step 1: Write a failing test asserting the schema initializes and re-runs safely**

Create `src/test/java/net/sahibnanda/portfolio/repository/init/SchemaInitializerTest.java`:

```java
package net.sahibnanda.portfolio.repository.init;

import static org.assertj.core.api.Assertions.assertThatCode;

import net.sahibnanda.portfolio.jooq.Tables;
import net.sahibnanda.portfolio.repository.AbstractRepositoryIntegrationTest;
import org.junit.jupiter.api.Test;

class SchemaInitializerTest extends AbstractRepositoryIntegrationTest {

  @org.springframework.beans.factory.annotation.Autowired private SchemaInitializer schemaInitializer;

  @Test
  void tablesExistAfterContextStartup() {
    Integer userCount = dslContext.fetchCount(Tables.USERS);
    Integer chatCount = dslContext.fetchCount(Tables.CHATS);

    org.assertj.core.api.Assertions.assertThat(userCount).isZero();
    org.assertj.core.api.Assertions.assertThat(chatCount).isZero();
  }

  @Test
  void runningInitializerAgainIsIdempotent() {
    assertThatCode(() -> schemaInitializer.run(null)).doesNotThrowAnyException();
    assertThatCode(() -> schemaInitializer.run(null)).doesNotThrowAnyException();
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=SchemaInitializerTest`
Expected: FAIL — `SchemaInitializer` class does not exist, and (separately) the Spring context would fail to start since no schema exists yet without it.

- [ ] **Step 3: Implement SchemaInitializer**

Create `src/main/java/net/sahibnanda/portfolio/repository/init/SchemaInitializer.java`:

```java
package net.sahibnanda.portfolio.repository.init;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.jooq.DSLContext;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

@Component
public class SchemaInitializer implements ApplicationRunner {

  private static final String SCHEMA_SCRIPT_LOCATION = "db/schema.sql";

  private final DSLContext dslContext;

  public SchemaInitializer(DSLContext dslContext) {
    this.dslContext = dslContext;
  }

  @Override
  public void run(ApplicationArguments args) {
    dslContext.parser().parse(readSchemaScript()).executeBatch();
  }

  private String readSchemaScript() {
    Resource resource = new ClassPathResource(SCHEMA_SCRIPT_LOCATION);
    try (InputStream inputStream = resource.getInputStream()) {
      return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Failed to read schema script: " + SCHEMA_SCRIPT_LOCATION, e);
    }
  }
}
```

`dslContext.parser()` parses the multi-statement script (respecting the Postgres dialect already configured on the autoconfigured `DSLContext`) and `executeBatch()` runs each `CREATE TABLE/INDEX IF NOT EXISTS` statement — safe to call on every startup, and safe to call twice in a test.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=SchemaInitializerTest`
Expected: PASS, 2 tests green. (First run pulls the `postgres:16-alpine` Testcontainers image if not already cached — may take longer the first time.)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/net/sahibnanda/portfolio/repository/init/SchemaInitializer.java \
        src/test/java/net/sahibnanda/portfolio/repository/init/SchemaInitializerTest.java
git commit -m "feat: add idempotent schema initializer that runs on startup"
```

---

### Task 8: UserRepository

**Files:**
- Create: `src/main/java/net/sahibnanda/portfolio/repository/UserRepository.java`
- Create: `src/main/java/net/sahibnanda/portfolio/repository/jooq/JooqUserRepository.java`
- Test: `src/test/java/net/sahibnanda/portfolio/repository/jooq/JooqUserRepositoryTest.java`

**Interfaces:**
- Consumes: `DSLContext` (Task 1), `Tables.USERS`/`UsersRecord` (Task 1 codegen), `UserEntity` (Task 6), `DuplicateUsernameException`/`UserNotFoundException`/`DatabaseOperationException` (Task 5).
- Produces: `UserRepository` interface with `create(String, String): UserEntity`, `findByUsername(String): Optional<UserEntity>`, `exists(String): boolean`, `delete(String): void`, `updatePassword(String, String): void` — this is the only contract a future service layer may depend on.

- [ ] **Step 1: Write the interface**

Create `src/main/java/net/sahibnanda/portfolio/repository/UserRepository.java`:

```java
package net.sahibnanda.portfolio.repository;

import java.util.Optional;
import net.sahibnanda.portfolio.entity.UserEntity;

public interface UserRepository {

  UserEntity create(String username, String hashedPassword);

  Optional<UserEntity> findByUsername(String username);

  boolean exists(String username);

  void delete(String username);

  void updatePassword(String username, String hashedPassword);
}
```

- [ ] **Step 2: Write failing integration tests for every method**

Create `src/test/java/net/sahibnanda/portfolio/repository/jooq/JooqUserRepositoryTest.java`:

```java
package net.sahibnanda.portfolio.repository.jooq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import net.sahibnanda.portfolio.entity.UserEntity;
import net.sahibnanda.portfolio.exception.DuplicateUsernameException;
import net.sahibnanda.portfolio.exception.UserNotFoundException;
import net.sahibnanda.portfolio.repository.AbstractRepositoryIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JooqUserRepositoryTest extends AbstractRepositoryIntegrationTest {

  @Autowired private JooqUserRepository userRepository;

  @Test
  void createPersistsUserWithCreationTimestamp() {
    UserEntity created = userRepository.create("alice", "hashed-pw");

    assertThat(created.username()).isEqualTo("alice");
    assertThat(created.passwordHash()).isEqualTo("hashed-pw");
    assertThat(created.createdAt()).isNotNull();
  }

  @Test
  void createRejectsDuplicateUsername() {
    userRepository.create("alice", "hashed-pw");

    assertThatThrownBy(() -> userRepository.create("alice", "other-hash"))
        .isInstanceOf(DuplicateUsernameException.class);
  }

  @Test
  void findByUsernameReturnsEmptyWhenAbsent() {
    Optional<UserEntity> found = userRepository.findByUsername("missing");

    assertThat(found).isEmpty();
  }

  @Test
  void findByUsernameReturnsUserWhenPresent() {
    userRepository.create("alice", "hashed-pw");

    Optional<UserEntity> found = userRepository.findByUsername("alice");

    assertThat(found).isPresent();
    assertThat(found.get().username()).isEqualTo("alice");
  }

  @Test
  void existsReflectsPresence() {
    assertThat(userRepository.exists("alice")).isFalse();

    userRepository.create("alice", "hashed-pw");

    assertThat(userRepository.exists("alice")).isTrue();
  }

  @Test
  void deleteRemovesUser() {
    userRepository.create("alice", "hashed-pw");

    userRepository.delete("alice");

    assertThat(userRepository.exists("alice")).isFalse();
  }

  @Test
  void deleteThrowsWhenUserMissing() {
    assertThatThrownBy(() -> userRepository.delete("missing"))
        .isInstanceOf(UserNotFoundException.class);
  }

  @Test
  void updatePasswordChangesHash() {
    userRepository.create("alice", "old-hash");

    userRepository.updatePassword("alice", "new-hash");

    assertThat(userRepository.findByUsername("alice").orElseThrow().passwordHash())
        .isEqualTo("new-hash");
  }

  @Test
  void updatePasswordThrowsWhenUserMissing() {
    assertThatThrownBy(() -> userRepository.updatePassword("missing", "new-hash"))
        .isInstanceOf(UserNotFoundException.class);
  }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn -q test -Dtest=JooqUserRepositoryTest`
Expected: FAIL — `JooqUserRepository` does not exist.

- [ ] **Step 4: Implement JooqUserRepository**

Create `src/main/java/net/sahibnanda/portfolio/repository/jooq/JooqUserRepository.java`:

```java
package net.sahibnanda.portfolio.repository.jooq;

import java.time.LocalDateTime;
import java.util.Optional;
import net.sahibnanda.portfolio.entity.UserEntity;
import net.sahibnanda.portfolio.exception.DatabaseOperationException;
import net.sahibnanda.portfolio.exception.DuplicateUsernameException;
import net.sahibnanda.portfolio.exception.UserNotFoundException;
import net.sahibnanda.portfolio.jooq.Tables;
import net.sahibnanda.portfolio.jooq.tables.records.UsersRecord;
import net.sahibnanda.portfolio.repository.UserRepository;
import org.jooq.DSLContext;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class JooqUserRepository implements UserRepository {

  private final DSLContext dslContext;

  public JooqUserRepository(DSLContext dslContext) {
    this.dslContext = dslContext;
  }

  @Override
  public UserEntity create(String username, String hashedPassword) {
    LocalDateTime createdAt = LocalDateTime.now();
    try {
      dslContext
          .insertInto(Tables.USERS)
          .set(Tables.USERS.USERNAME, username)
          .set(Tables.USERS.PASSWORD_HASH, hashedPassword)
          .set(Tables.USERS.CREATED_AT, createdAt)
          .execute();
    } catch (DuplicateKeyException e) {
      throw new DuplicateUsernameException(username);
    } catch (DataAccessException e) {
      throw new DatabaseOperationException("Failed to create user: " + username, e);
    }
    return new UserEntity(username, hashedPassword, createdAt);
  }

  @Override
  public Optional<UserEntity> findByUsername(String username) {
    try {
      return dslContext
          .selectFrom(Tables.USERS)
          .where(Tables.USERS.USERNAME.eq(username))
          .fetchOptional(this::toEntity);
    } catch (DataAccessException e) {
      throw new DatabaseOperationException("Failed to find user: " + username, e);
    }
  }

  @Override
  public boolean exists(String username) {
    try {
      return dslContext.fetchExists(
          dslContext.selectFrom(Tables.USERS).where(Tables.USERS.USERNAME.eq(username)));
    } catch (DataAccessException e) {
      throw new DatabaseOperationException("Failed to check user existence: " + username, e);
    }
  }

  @Override
  public void delete(String username) {
    int deleted;
    try {
      deleted =
          dslContext.deleteFrom(Tables.USERS).where(Tables.USERS.USERNAME.eq(username)).execute();
    } catch (DataAccessException e) {
      throw new DatabaseOperationException("Failed to delete user: " + username, e);
    }
    if (deleted == 0) {
      throw new UserNotFoundException(username);
    }
  }

  @Override
  public void updatePassword(String username, String hashedPassword) {
    int updated;
    try {
      updated =
          dslContext
              .update(Tables.USERS)
              .set(Tables.USERS.PASSWORD_HASH, hashedPassword)
              .where(Tables.USERS.USERNAME.eq(username))
              .execute();
    } catch (DataAccessException e) {
      throw new DatabaseOperationException("Failed to update password for user: " + username, e);
    }
    if (updated == 0) {
      throw new UserNotFoundException(username);
    }
  }

  private UserEntity toEntity(UsersRecord record) {
    return new UserEntity(record.getUsername(), record.getPasswordHash(), record.getCreatedAt());
  }
}
```

Unique-constraint violations on `username` are translated by Spring Boot's jOOQ autoconfiguration into `DuplicateKeyException` before reaching this code (Spring Boot registers an `ExecuteListener` that maps SQL exceptions to its `DataAccessException` hierarchy whenever both `spring-boot-starter-jooq` and JDBC exception translation are on the classpath, which they are here).

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn -q test -Dtest=JooqUserRepositoryTest`
Expected: PASS, 9 tests green.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/net/sahibnanda/portfolio/repository/UserRepository.java \
        src/main/java/net/sahibnanda/portfolio/repository/jooq/JooqUserRepository.java \
        src/test/java/net/sahibnanda/portfolio/repository/jooq/JooqUserRepositoryTest.java
git commit -m "feat: add UserRepository and its jOOQ implementation"
```

---

### Task 9: ChatRepository

**Files:**
- Create: `src/main/java/net/sahibnanda/portfolio/repository/ChatRepository.java`
- Create: `src/main/java/net/sahibnanda/portfolio/repository/jooq/JooqChatRepository.java`
- Test: `src/test/java/net/sahibnanda/portfolio/repository/jooq/JooqChatRepositoryTest.java`

**Interfaces:**
- Consumes: `DSLContext`, `Tables.CHATS`/`ChatsRecord` (Task 1 codegen), `ChatEntity`/`Message`/`Role` (Task 6), `ChatNotFoundException`/`DatabaseOperationException` (Task 5), `StringUtils.isValidUlid` (Task 4), `JsonUtils.toJson`/`fromJson` (Task 4), `JooqUserRepository`/`UserRepository` (Task 8, for the FK parent row in tests).
- Produces: `ChatRepository` interface with `create`, `findByChatId`, `findChats`, `saveMessages`, `delete`, `updateChatTitle`.

- [ ] **Step 1: Write the interface**

Create `src/main/java/net/sahibnanda/portfolio/repository/ChatRepository.java`:

```java
package net.sahibnanda.portfolio.repository;

import java.util.List;
import java.util.Optional;
import net.sahibnanda.portfolio.entity.ChatEntity;
import net.sahibnanda.portfolio.entity.Message;

public interface ChatRepository {

  ChatEntity create(String chatId, String username, String chatTitle, List<Message> messages);

  Optional<ChatEntity> findByChatId(String chatId);

  List<ChatEntity> findChats(String username);

  void saveMessages(String chatId, List<Message> messages);

  void delete(String chatId);

  void updateChatTitle(String chatId, String title);
}
```

- [ ] **Step 2: Write failing integration tests for every method, including ordering and cascade behavior**

Create `src/test/java/net/sahibnanda/portfolio/repository/jooq/JooqChatRepositoryTest.java`:

```java
package net.sahibnanda.portfolio.repository.jooq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import net.sahibnanda.portfolio.entity.ChatEntity;
import net.sahibnanda.portfolio.entity.Message;
import net.sahibnanda.portfolio.entity.Role;
import net.sahibnanda.portfolio.exception.ChatNotFoundException;
import net.sahibnanda.portfolio.repository.AbstractRepositoryIntegrationTest;
import net.sahibnanda.portfolio.repository.UserRepository;
import net.sahibnanda.portfolio.utils.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JooqChatRepositoryTest extends AbstractRepositoryIntegrationTest {

  @Autowired private JooqChatRepository chatRepository;
  @Autowired private UserRepository userRepository;

  @BeforeEach
  void createOwningUser() {
    userRepository.create("alice", "hashed-pw");
  }

  @Test
  void createPersistsChatWithMessagesSortedByTimestamp() {
    String chatId = StringUtils.generateUlid();
    Message later = new Message(Role.ASSISTANT, "hi back", Instant.parse("2026-08-02T12:00:05Z"));
    Message earlier = new Message(Role.USER, "hi", Instant.parse("2026-08-02T12:00:00Z"));

    ChatEntity created = chatRepository.create(chatId, "alice", "Chat 1", List.of(later, earlier));

    assertThat(created.getChatId()).isEqualTo(chatId);
    assertThat(created.getMessages()).containsExactly(earlier, later);
  }

  @Test
  void createRejectsNonUlidChatId() {
    assertThatThrownBy(
            () -> chatRepository.create("not-a-ulid", "alice", "Chat 1", List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void findByChatIdReturnsEmptyWhenAbsent() {
    Optional<ChatEntity> found = chatRepository.findByChatId(StringUtils.generateUlid());

    assertThat(found).isEmpty();
  }

  @Test
  void findChatsOrdersNewestFirst() {
    String firstChatId = StringUtils.generateUlid();
    chatRepository.create(firstChatId, "alice", "Chat 1", List.of());
    String secondChatId = StringUtils.generateUlid();
    chatRepository.create(secondChatId, "alice", "Chat 2", List.of());

    List<ChatEntity> chats = chatRepository.findChats("alice");

    assertThat(chats).extracting(ChatEntity::getChatId).containsExactly(secondChatId, firstChatId);
  }

  @Test
  void saveMessagesReplacesAndSortsMessages() {
    String chatId = StringUtils.generateUlid();
    chatRepository.create(chatId, "alice", "Chat 1", List.of());
    Message later = new Message(Role.ASSISTANT, "hi back", Instant.parse("2026-08-02T12:00:05Z"));
    Message earlier = new Message(Role.USER, "hi", Instant.parse("2026-08-02T12:00:00Z"));

    chatRepository.saveMessages(chatId, List.of(later, earlier));

    ChatEntity updated = chatRepository.findByChatId(chatId).orElseThrow();
    assertThat(updated.getMessages()).containsExactly(earlier, later);
  }

  @Test
  void saveMessagesThrowsWhenChatMissing() {
    assertThatThrownBy(
            () -> chatRepository.saveMessages(StringUtils.generateUlid(), List.of()))
        .isInstanceOf(ChatNotFoundException.class);
  }

  @Test
  void deleteRemovesChat() {
    String chatId = StringUtils.generateUlid();
    chatRepository.create(chatId, "alice", "Chat 1", List.of());

    chatRepository.delete(chatId);

    assertThat(chatRepository.findByChatId(chatId)).isEmpty();
  }

  @Test
  void deleteThrowsWhenChatMissing() {
    assertThatThrownBy(() -> chatRepository.delete(StringUtils.generateUlid()))
        .isInstanceOf(ChatNotFoundException.class);
  }

  @Test
  void updateChatTitleRenamesChat() {
    String chatId = StringUtils.generateUlid();
    chatRepository.create(chatId, "alice", "Chat 1", List.of());

    chatRepository.updateChatTitle(chatId, "Renamed Chat");

    assertThat(chatRepository.findByChatId(chatId).orElseThrow().getChatTitle())
        .isEqualTo("Renamed Chat");
  }

  @Test
  void updateChatTitleThrowsWhenChatMissing() {
    assertThatThrownBy(
            () -> chatRepository.updateChatTitle(StringUtils.generateUlid(), "Renamed"))
        .isInstanceOf(ChatNotFoundException.class);
  }

  @Test
  void deletingOwningUserCascadesToChats() {
    String chatId = StringUtils.generateUlid();
    chatRepository.create(chatId, "alice", "Chat 1", List.of());

    userRepository.delete("alice");

    assertThat(chatRepository.findByChatId(chatId)).isEmpty();
  }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn -q test -Dtest=JooqChatRepositoryTest`
Expected: FAIL — `JooqChatRepository` does not exist.

- [ ] **Step 4: Implement JooqChatRepository**

Create `src/main/java/net/sahibnanda/portfolio/repository/jooq/JooqChatRepository.java`:

```java
package net.sahibnanda.portfolio.repository.jooq;

import com.fasterxml.jackson.core.type.TypeReference;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import net.sahibnanda.portfolio.entity.ChatEntity;
import net.sahibnanda.portfolio.entity.Message;
import net.sahibnanda.portfolio.exception.ChatNotFoundException;
import net.sahibnanda.portfolio.exception.DatabaseOperationException;
import net.sahibnanda.portfolio.jooq.Tables;
import net.sahibnanda.portfolio.jooq.tables.records.ChatsRecord;
import net.sahibnanda.portfolio.repository.ChatRepository;
import net.sahibnanda.portfolio.utils.JsonUtils;
import net.sahibnanda.portfolio.utils.StringUtils;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

@Repository
public class JooqChatRepository implements ChatRepository {

  private final DSLContext dslContext;

  public JooqChatRepository(DSLContext dslContext) {
    this.dslContext = dslContext;
  }

  @Override
  public ChatEntity create(
      String chatId, String username, String chatTitle, List<Message> messages) {
    if (!StringUtils.isValidUlid(chatId)) {
      throw new IllegalArgumentException("chatId is not a valid ULID: " + chatId);
    }
    LocalDateTime now = LocalDateTime.now();
    List<Message> sortedMessages = Message.sortedByTimestamp(messages);
    try {
      dslContext
          .insertInto(Tables.CHATS)
          .set(Tables.CHATS.CHAT_ID, chatId)
          .set(Tables.CHATS.USERNAME, username)
          .set(Tables.CHATS.CHAT_TITLE, chatTitle)
          .set(Tables.CHATS.MESSAGES, serializeMessages(sortedMessages))
          .set(Tables.CHATS.CREATED_AT, now)
          .set(Tables.CHATS.UPDATED_AT, now)
          .execute();
    } catch (DataAccessException e) {
      throw new DatabaseOperationException("Failed to create chat: " + chatId, e);
    }
    return ChatEntity.builder()
        .chatId(chatId)
        .username(username)
        .chatTitle(chatTitle)
        .messages(sortedMessages)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  @Override
  public Optional<ChatEntity> findByChatId(String chatId) {
    try {
      return dslContext
          .selectFrom(Tables.CHATS)
          .where(Tables.CHATS.CHAT_ID.eq(chatId))
          .fetchOptional(this::toEntity);
    } catch (DataAccessException e) {
      throw new DatabaseOperationException("Failed to find chat: " + chatId, e);
    }
  }

  @Override
  public List<ChatEntity> findChats(String username) {
    try {
      return dslContext
          .selectFrom(Tables.CHATS)
          .where(Tables.CHATS.USERNAME.eq(username))
          .orderBy(Tables.CHATS.CREATED_AT.desc())
          .fetch(this::toEntity);
    } catch (DataAccessException e) {
      throw new DatabaseOperationException("Failed to find chats for user: " + username, e);
    }
  }

  @Override
  public void saveMessages(String chatId, List<Message> messages) {
    List<Message> sortedMessages = Message.sortedByTimestamp(messages);
    int updated;
    try {
      updated =
          dslContext
              .update(Tables.CHATS)
              .set(Tables.CHATS.MESSAGES, serializeMessages(sortedMessages))
              .set(Tables.CHATS.UPDATED_AT, LocalDateTime.now())
              .where(Tables.CHATS.CHAT_ID.eq(chatId))
              .execute();
    } catch (DataAccessException e) {
      throw new DatabaseOperationException("Failed to save messages for chat: " + chatId, e);
    }
    if (updated == 0) {
      throw new ChatNotFoundException(chatId);
    }
  }

  @Override
  public void delete(String chatId) {
    int deleted;
    try {
      deleted =
          dslContext.deleteFrom(Tables.CHATS).where(Tables.CHATS.CHAT_ID.eq(chatId)).execute();
    } catch (DataAccessException e) {
      throw new DatabaseOperationException("Failed to delete chat: " + chatId, e);
    }
    if (deleted == 0) {
      throw new ChatNotFoundException(chatId);
    }
  }

  @Override
  public void updateChatTitle(String chatId, String title) {
    int updated;
    try {
      updated =
          dslContext
              .update(Tables.CHATS)
              .set(Tables.CHATS.CHAT_TITLE, title)
              .set(Tables.CHATS.UPDATED_AT, LocalDateTime.now())
              .where(Tables.CHATS.CHAT_ID.eq(chatId))
              .execute();
    } catch (DataAccessException e) {
      throw new DatabaseOperationException("Failed to update title for chat: " + chatId, e);
    }
    if (updated == 0) {
      throw new ChatNotFoundException(chatId);
    }
  }

  private ChatEntity toEntity(ChatsRecord record) {
    return ChatEntity.builder()
        .chatId(record.getChatId())
        .username(record.getUsername())
        .chatTitle(record.getChatTitle())
        .messages(Message.sortedByTimestamp(deserializeMessages(record.getMessages())))
        .createdAt(record.getCreatedAt())
        .updatedAt(record.getUpdatedAt())
        .build();
  }

  private JSONB serializeMessages(List<Message> messages) {
    return JSONB.jsonb(JsonUtils.toJson(messages));
  }

  private List<Message> deserializeMessages(JSONB jsonb) {
    return JsonUtils.fromJson(jsonb.data(), new TypeReference<List<Message>>() {});
  }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn -q test -Dtest=JooqChatRepositoryTest`
Expected: PASS, 10 tests green.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/net/sahibnanda/portfolio/repository/ChatRepository.java \
        src/main/java/net/sahibnanda/portfolio/repository/jooq/JooqChatRepository.java \
        src/test/java/net/sahibnanda/portfolio/repository/jooq/JooqChatRepositoryTest.java
git commit -m "feat: add ChatRepository and its jOOQ implementation"
```

---

### Task 10: Full verification pass

**Files:** none (verification only).

- [ ] **Step 1: Run the full test suite**

Run: `mvn -q test`
Expected: exit code 0, all tests (unit + Testcontainers integration) green.

- [ ] **Step 2: Run a full build**

Run: `mvn -q clean verify`
Expected: exit code 0 — confirms jOOQ codegen, compilation, formatting (Spotless), and tests all succeed together from a clean state.

- [ ] **Step 3: Confirm the app boots end-to-end against a real Postgres**

Run (adjust if a local Postgres isn't running — using Docker directly for a quick manual check):

```bash
docker run --rm -d --name repo-layer-check -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=defaultDB -p 5432:5432 postgres:16-alpine
```

Then in a separate step, run: `mvn -q spring-boot:run` and check the logs for successful startup with no schema errors, then stop it (`Ctrl+C` or the equivalent for your shell) and clean up:

```bash
docker stop repo-layer-check
```

Expected: application logs show Spring Boot starting on port 8080 with no `SchemaInitializer`/jOOQ errors.

- [ ] **Step 4: Re-check each acceptance criterion from BRD Section 25 against the implementation**

No code change — this is a manual checklist pass to confirm each of the 12 acceptance criteria is met by the tasks above (schema auto-creation → Task 7; hashed-only passwords → Task 8 stores whatever hash is passed in, never hashes/logs itself; ULID uniqueness → Task 4 + Task 9 tests; FK ownership → schema.sql + Task 9's cascade test; JSONB storage → Task 9; message sorting on write and read → `Message.sortedByTimestamp` used in both directions in Task 9; interfaces-only exposure → Task 8/9 interfaces vs `jooq` package impls; jOOQ as sole SQL abstraction → Task 1 codegen + no raw SQL anywhere except `schema.sql`; extensibility → repository interfaces unchanged by internal implementation swaps).
