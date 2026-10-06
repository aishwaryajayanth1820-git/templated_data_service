# TDS Application Design (class level)

Status: **Accepted baseline** · Owner: Zypher / Aabel · 2026-10-03
Implements: [02-architecture.md](02-architecture.md) · Decisions: [ADR log](adr/README.md)

This document is the contract between design and code. Package and class names
here are the ones in `src/main/java/io/github/aishwaryajayanth1820/tds`. Changes to a public signature
or a package boundary need a short update here, plus an ADR if they meet the
criteria in [ADR-0001](adr/0001-record-architecture-decisions.md).

---

## 1. Conventions

| Topic | Rule |
|---|---|
| Java | 21 (`--release 21`). Records for immutable data, sealed interfaces for closed hierarchies, `Optional` only as a return type |
| Nulls | No `null` in collections. Model getters return empty collections, never `null` (handled in compact constructors) |
| JSON | Jackson 3 (`tools.jackson.*`); one shared `JsonMapper` bean. Template documents are kept as text **and** as `JsonNode` so round-trips don't lose formatting intent |
| Time | `Instant` everywhere in Java; UTC in storage; ISO-8601 on the wire |
| Transactions | `@Transactional` only on service classes (`*Service`). Controllers and repositories never open transactions |
| Logging | SLF4J. INFO for lifecycle (publish, script reload, bootstrap), WARN for drift or recoverable errors, never log passwords or row contents |
| Errors | Throw `ApiException` subclasses. `ApiExceptionHandler` maps them to RFC 9457 `ProblemDetail` with a stable `code` (§3.2) |
| Identifiers | Table and field names reach SQL only after catalog lookup (`SqlIdentifiers.quote(name)` asserts `^[a-z][a-z0-9_]{0,62}$`) |
| Visibility | Package-private by default; `public` only for a package's API types listed below |
| Tests | `*Test` = unit (no Spring), `*IT` = Spring Boot integration (temp SQLite file). Test names state behaviour: `rejectsWriteToFieldWithoutCreate()` |

---

## 2. Package map and dependency rules

```
io.github.aishwaryajayanth1820.tds
├─ TdsApplication
├─ config      → (wires everything)
├─ jdbc        → DbVendor, DbVendorResolver, JdbcTime     (depends on: NOTHING in tds; ADR-0014)
├─ web         → error mapping, SPA forward          (depends on: nothing domain-specific)
├─ security    → users, roles, login                 (depends on: web, jdbc, config)
├─ grammar     → tds/v1 model, parser, validators, JsonLogic   (depends on: NOTHING in tds; no Spring)
├─ ddl         → dialects, DDL, planner, executor, codec       (depends on: grammar, jdbc)
├─ catalog     → drafts, versions, publish, snapshot           (depends on: grammar, ddl, script(api), security(roles))
├─ access      → AccessEvaluator                     (depends on: grammar, catalog, security)
├─ data        → generic CRUD/query                  (depends on: grammar, ddl, catalog, access)
├─ script      → registry, watcher, runner           (depends on: grammar, catalog(api), access)
├─ meta        → per-user UI metadata                (depends on: catalog, access)
└─ admin       → Studio/roles/users/scripts APIs     (depends on: catalog, security, script)
```

Enforced by `ArchitectureTest` (ArchUnit): `grammar` must not import
`org.springframework..` or `io.github.aishwaryajayanth1820.tds..` outside itself; `jdbc` must not import other `io.github.aishwaryajayanth1820.tds..` packages; there are no cycles
between packages; controllers live only in `web`, `security`, `data`, `meta`
and `admin`.

To break the `catalog` ↔ `script` cycle, `grammar` defines the
`ScriptCatalog` port, `script.ScriptRegistry` implements it, and the catalog
only sees the port.

---

## 3. `web`, `config`

### 3.1 Classes

```java
// config
@ConfigurationProperties("tds")
public record TdsProperties(Paths paths, Seed seed, Scripts scripts, Security security) {
  public record Paths(Path templates, Path scripts, Path output) {}
  public record Seed(boolean importOnStart, boolean autoPublish) {}
  public record Scripts(boolean watch, int maxConcurrent, int defaultTimeoutMs) {}
  public record Security(String bootstrapAdmin) {}
}
class DataSourceConfig        // BeanPostProcessor: for jdbc:sqlite: URLs creates the parent dir and adds the pragmas as Hikari data-source properties

// web
public class ApiException extends RuntimeException {          // status comes from the ErrorCode
  public ApiException(ErrorCode code, String title, String detail[, Map<String,Object> properties]);
  public ErrorCode code(); public String title(); public Map<String, Object> properties();   // extra ProblemDetail members
  public static ApiException notFound(String what); forbidden(String); badRequest(String); conflict(ErrorCode, String);
}
public class ValidationException extends ApiException {        // VALIDATION, 422, members errors{} + general[]
  public ValidationException(Map<String,String> fieldErrors, List<String> general);
  public static ValidationException field(String field, String message);
}
@Component public class Problems                       // ProblemDetail factory; writes problem+json from servlet filters
@RestControllerAdvice class ApiExceptionHandler   // ApiException, AuthenticationException, AccessDenied, @Valid errors, framework errors (code via createResponseEntity), generic 500 with ref id
@Configuration class SpaResourceConfig            // /** resource handler: existing static file, else index.html for extension-less non-/api paths
class CatalogGenerationHeaderFilter               // adds X-TDS-Catalog-Generation (from M2)
```

### 3.2 Error codes (`ErrorCode` enum)

| Code | HTTP | Raised when |
|---|---|---|
| `BAD_REQUEST` | 400 | malformed JSON, bad query parameter |
| `UNAUTHENTICATED` | 401 | no or expired session, bad credentials |
| `FORBIDDEN` | 403 | role lacks the operation |
| `PASSWORD_CHANGE_REQUIRED` | 403 | `must_change_password` is set and the path is outside `/api/auth/**` |
| `NOT_FOUND` | 404 | unknown template, record, role, user or script |
| `METHOD_NOT_ALLOWED` | 405 | wrong HTTP method |
| `CONFLICT_VERSION` | 409 | `row_version` mismatch, or a stale draft checksum |
| `CONFLICT_REFERENCE` | 409 | delete restricted by FK; a role in use |
| `CONFLICT_UNIQUE` | 409 | unique constraint violated |
| `PUBLISH_BLOCKED` | 409 | validation errors, blocked steps, failed pre-checks, missing confirmation |
| `VALIDATION` | 422 | record or request validation failed (`errors`, `general`) |
| `TOO_MANY_ACTIONS` | 429 | script semaphore is full |
| `ACTION_FAILED` | 422 | script threw, or returned an invalid result |
| `ACTION_TIMEOUT` | 504 | script exceeded `timeoutMs` |
| `INTERNAL` | 500 | anything else (logged with a correlation id) |

---

## 4. `security`

```java
public record UserAccount(long id, String username, String passwordHash, String displayName,
                          boolean enabled, boolean mustChangePassword, Instant createdAt, Instant updatedAt) {}
public record Role(long id, String name, String description, boolean builtin) {}

class UserRepository {                       // JdbcClient
  Optional<UserAccount> findByUsername(String username);
  List<UserAccount> findAll();
  long insert(String username, String hash, String displayName, boolean mustChange);
  void updatePassword(long id, String hash, boolean mustChange);
  void updateProfile(long id, String displayName, boolean enabled);
  long count();
  Set<String> roleNames(long userId);
  void replaceRoles(long userId, Set<Long> roleIds);
}
class RoleRepository {
  List<Role> findAll();  Optional<Role> findByName(String n);
  long insert(String name, String description);  void update(long id, String description);
  void delete(long id);  long countUsers(long roleId);
}

public record TdsPrincipal(String username, String displayName, Set<String> roles, boolean mustChangePassword)
    implements Serializable {                // roles always contain "viewer"
  public boolean isAdmin() { return roles.contains(Roles.ADMIN); }
}
public final class Roles { public static final String ADMIN = "admin", VIEWER = "viewer"; }

class TdsUserDetails implements UserDetails, CredentialsContainer   // wraps UserAccount + roles → ROLE_<name> authorities
class TdsUserDetailsService implements UserDetailsService

@Configuration class SecurityConfig {
  SecurityFilterChain api(HttpSecurity http);          // stateful session, CSRF (cookie, SPA), 401 entry point, JSON logout
  PasswordEncoder passwordEncoder();                   // BCrypt(12)
  AuthenticationManager authenticationManager(...);    // DaoAuthenticationProvider
}
class PasswordChangeRequiredFilter extends OncePerRequestFilter   // 403 PASSWORD_CHANGE_REQUIRED outside /api/auth/**; built by SecurityConfig, not a bean (so it is not also registered as a servlet filter)
public final class CurrentPrincipal { public static Optional<TdsPrincipal> find(); public static TdsPrincipal get(); }

@RestController @RequestMapping("/api/auth")
class AuthController {
  @GetMapping("/csrf")      CsrfResponse csrf(CsrfToken token);                // primes the XSRF-TOKEN cookie
  @PostMapping("/login")    MeResponse login(@Valid LoginRequest r, HttpServletRequest, HttpServletResponse);  // new session id + rotated CSRF cookie in the response
  @GetMapping("/me")        MeResponse me(Authentication a);
  @PostMapping("/password") MeResponse changePassword(@Valid PasswordChangeRequest r, …);  // re-issues the security context
  // POST /api/auth/logout is handled by Spring Security's LogoutFilter (204)
}
record LoginRequest(@NotBlank String username, @NotBlank String password) {}
record PasswordChangeRequest(@NotBlank String currentPassword, @Size(min = 10, max = 128) String newPassword) {}
record MeResponse(String username, String displayName, List<String> roles, boolean mustChangePassword) {}

@Service class AccountService {                      // @Transactional; used by AuthController and admin
  TdsPrincipal changePassword(String username, String current, String next);
  UserAccount createUser(String username, String displayName, String initialPassword, Set<String> roles);
  void resetPassword(String username, String newPassword);   // sets mustChangePassword
  void assignRoles(String username, Set<String> roles);
}
@Component class BootstrapAdmin implements ApplicationRunner   // @Order(10): creates admin if no users exist
```

**Password policy (v1):** 10–128 characters, and must differ from the current
password and from the username.

---

## 5. `grammar` (pure Java)

### 5.1 Model (`grammar.model`)

```java
public record Template(String grammar, String name, String label, String description, ManageType manageType,
                       Options options, List<Field> fields, List<IndexSpec> indexes, List<Rule> rules,
                       List<ActionSpec> actions, Map<String, Set<String>> access /* nullable = defaults */, ViewSpec view) {
  public Optional<Field> field(String name);
  public Field idField();
  public Map<String, Set<String>> effectiveAccess();     // defaults per grammar §6.1 when access == null
}
public enum ManageType { VIEW, MANAGE_VIEW, DATA_SOURCE }
public record Options(boolean audit, boolean optimisticLock) { public static final Options DEFAULT = new Options(true, true); }

public record Field(String name, String label, String description, FieldType type,
                    Integer length, Integer precision, Integer scale, List<EnumValue> values, RefSpec ref,
                    boolean required, boolean unique, DefaultValue defaultValue, Constraints constraints,
                    JsonNode requiredWhen, FieldUi ui, Map<String, Set<FieldOp>> access /* nullable */, String renamedFrom) {
  public Placement placement();      // default: id → HIDDEN, else COLUMN
  public Widget widget();            // ui.widget or the type default
  public int stringLength();         // length or 255
}
public enum FieldType { ID, STRING, TEXT, INTEGER, LONG, DECIMAL, DOUBLE, BOOLEAN, DATE, DATETIME, TIME, ENUM, REF, JSON, UUID;
  public boolean isString(); public boolean isNumeric(); public boolean isIntegral(); public boolean isTemporal();
  public List<Widget> widgets(); }   // JSON value = lower-case name
public record EnumValue(String value, String label, EnumColor color, String description) {}  // deserialises from "X" or {…}
public record RefSpec(String target, String display, OnDelete onDelete) {}
public enum OnDelete { RESTRICT, CASCADE, SET_NULL }
public sealed interface DefaultValue { record Literal(JsonNode value) implements DefaultValue {}
                                       record Fn(DefaultFn fn) implements DefaultValue {} }
public enum DefaultFn { NOW, TODAY, UUID, CURRENT_USER }
public record Constraints(boolean notBlank, Integer minLength, Integer maxLength, String pattern,
                          StringFormat format, JsonNode min, JsonNode max) { public static final Constraints NONE; }
public record FieldUi(Placement placement, Widget widget, Integer order, Integer width, String group, String help,
                      String placeholder, String format, JsonNode visibleWhen, JsonNode readonlyWhen) {}
public enum Placement { COLUMN, DETAIL, HIDDEN }   public enum Widget { TEXT, TEXTAREA, NUMBER, SWITCH, CHECKBOX, DATE, DATETIME, TIME, SELECT, RADIO, LOOKUP, JSON }
public enum FieldOp { READ, CREATE, UPDATE }
public record IndexSpec(String name, List<String> fields, boolean unique) {}
public sealed interface Rule permits Rule.Requires, Rule.Exclusive, Rule.AtLeastOne, Rule.Expr {
  String id(); JsonNode when(); String message();
  record Requires(String id, String ifField, List<String> then, JsonNode when, String message) implements Rule {}
  record Exclusive(String id, List<String> fields, JsonNode when, String message) implements Rule {}
  record AtLeastOne(String id, List<String> fields, JsonNode when, String message) implements Rule {}
  record Expr(String id, JsonNode assertion, List<String> fields, JsonNode when, String message) implements Rule {}
}
public record ActionSpec(String name, String label, String icon, ActionPlacement placement, String script,
                         String function, String confirm, JsonNode visibleWhen, int timeoutMs) {}
public enum ActionPlacement { ROW, TOOLBAR, SELECTION }
public record ViewSpec(String titleField, List<SortSpec> defaultSort, int pageSize, List<String> search, List<String> filters) {}
public record SortSpec(String field, SortDir dir) {}   public enum SortDir { ASC, DESC }
public final class Audit { public static final List<String> COLUMNS = List.of("created_at","created_by","updated_at","updated_by","row_version"); }
```

### 5.2 Parsing & validation

```java
public record Issue(Severity severity, String code, String message, String path) {}   // path like "fields[3]"
public enum Severity { ERROR, WARNING }

public final class TemplateParser {
  public TemplateParser(JsonMapper mapper, StructuralValidator structural);
  public ParseResult parse(String json);       // syntax → structural (meta-schema) → model binding
}
public record ParseResult(Optional<Template> template, JsonNode tree, List<Issue> issues) {
  public boolean ok();                          // no ERROR issues
}

public final class StructuralValidator {       // networknt 3.x against classpath:tds/tds-template.schema.json
  public List<Issue> validate(JsonNode tree);  // codes S1xx; path from the instance location
}

public interface ScriptCatalog {               // port implemented by script.ScriptRegistry
  boolean exists(String scriptPath);
  boolean hasFunction(String scriptPath, String function);
}
public interface TemplateLookup {              // port implemented by catalog
  Optional<Template> draftOrPublished(String name);
  boolean isPublished(String name);
}
public record ValidationContext(TemplateLookup templates, Set<String> roles, ScriptCatalog scripts) {}

public final class SemanticValidator {
  public List<Issue> validate(Template t, ValidationContext ctx);   // E0xx / W0xx exactly as grammar §11 and the Studio
}
```

### 5.3 JsonLogic (`grammar.logic`), see [ADR-0003](adr/0003-jsonlogic-in-house-evaluators.md)

```java
public final class JsonLogic {
  public static CompiledLogic compile(JsonNode rule);              // throws UnsupportedOperatorException (→ E032)
  public static Set<String> variables(JsonNode rule);              // first path segment of every var
  public static boolean truthy(Object v);                          // JsonLogic truthiness ([] false, "" false, 0 false)
  public static boolean present(Object v);                         // grammar §4.4
}
public interface CompiledLogic { Object evaluate(Map<String, Object> data); default boolean test(Map<String,Object> d) { … } }
```

Values inside the evaluator are `null`, `Boolean`, `Number` (as `BigDecimal`
for comparisons), `String`, `List`, and `Map`.

### 5.4 Record validation (shared by data and Studio preview)

```java
public final class RecordValidator {
  public RecordValidation validate(CompiledRules rules, Template t, Map<String, Object> record, WriteOp op, UserRef user);
}
public record RecordValidation(Map<String, String> fieldErrors, List<String> general) { public boolean ok(); }
public enum WriteOp { CREATE, UPDATE }
public record UserRef(String username, Set<String> roles) {}
public record CompiledRules(Map<String, CompiledLogic> requiredWhen, Map<String, CompiledLogic> visibleWhen,
                            Map<String, CompiledLogic> readonlyWhen, Map<String, CompiledLogic> ruleWhen,
                            Map<String, CompiledLogic> ruleAssert, Map<String, CompiledLogic> actionVisibleWhen,
                            Map<String, Pattern> patterns) {
  public static CompiledRules compile(Template t);
}
```

Uniqueness isn't checked here. The DB enforces it, and the resulting violation
maps to `CONFLICT_UNIQUE`.

---

## 6. `ddl`

```java
public interface SqlDialect {
  String id();                                   // "sqlite" | "postgresql"
  String columnType(Field f);
  String primaryKeyColumn(String name);
  String defaultExpression(DefaultValue d, Field f);   // null when it is app-side only
  List<String> columnChecks(Field f);            // dialect-specific (SQLite length, bool, json_valid)
  String literal(JsonNode v, Field f);
  String auditColumns();                         // created_at … row_version, dialect types
  String likeOperator();                         // LIKE | ILIKE
  boolean canAlterConstraints();                 // false → SQLite rebuild
  String limitOffset(int limit, long offset);
}
final class SqliteDialect implements SqlDialect { … }   final class PostgresDialect implements SqlDialect { … }
@Component public class DialectProvider { public SqlDialect current(); }   // from jdbc.DbVendorResolver (ADR-0014)

public final class SqlIdentifiers { public static String quote(String name); }   // asserts the name pattern, returns "name"

public final class DdlGenerator {
  public DdlGenerator(SqlDialect d);
  public List<String> createTable(Template t);   // CREATE TABLE + CREATE INDEX (explicit + FK)
  public String preview(Template t);             // single script with comments, as shown in Studio
  public String presentSql(Template t, String field);
  public Optional<String> ruleCheck(Template t, Rule r);
}

public enum StepClass { METADATA, SAFE, CHECKED, DESTRUCTIVE, BLOCKED }
public enum StepKind { CREATE_TABLE, ADD_COLUMN, RENAME_COLUMN, DROP_COLUMN, ALTER_NULLABILITY, ALTER_UNIQUE, ALTER_LENGTH,
                       ALTER_ENUM, ALTER_CONSTRAINTS, ALTER_REF, ALTER_DEFAULT, CHANGE_TYPE, ADD_INDEX, DROP_INDEX,
                       ADD_RULE_CHECK, DROP_RULE_CHECK, METADATA }
public record MigrationStep(StepClass cls, StepKind kind, String subject, String description, PreCheck preCheck /*nullable*/) {}
public record PreCheck(String description, String countSql) {}
public record MigrationPlan(String table, Integer fromVersion, List<MigrationStep> steps, boolean rebuild) {
  public boolean hasBlocked(); public boolean hasDestructive(); public boolean isEmpty();
}
public final class MigrationPlanner {
  public MigrationPlanner(SqlDialect d, DdlGenerator g);
  public MigrationPlan plan(Template published /*nullable*/, Template draft);
}
public record PreCheckResult(PreCheck check, long violations) {}
@Component public class MigrationExecutor {
  public List<PreCheckResult> preCheck(MigrationPlan plan);
  public AppliedMigration apply(MigrationPlan plan, Template from, Template to);   // inside caller's tx; returns SQL list
}
interface MigrationStrategy { List<String> statements(MigrationPlan p, Template from, Template to); }
final class AlterTableStrategy implements MigrationStrategy { … }      // PostgreSQL; SQLite add/rename-only plans
final class SqliteRebuildStrategy implements MigrationStrategy { … }   // 12-step rebuild with column mapping
public record AppliedMigration(List<String> sql) {}

@Component public class SchemaInspector { public List<String> drift(Template t); }   // JDBC metadata vs template

@Component public class ValueCodec {
  public Object toDb(Field f, JsonNode v);        // throws CodecException(field, message) → 422
  public JsonNode fromDb(Field f, Object v);
  public Object toDbFilter(Field f, String raw);  // query-string values
}
```

---

## 7. `catalog`

```java
public record TemplateRow(long id, String name, ManageType manageType, String draftJson, String draftChecksum,
                          Integer publishedVersion, Instant updatedAt, String updatedBy) {}
public record TemplateVersionRow(long id, long templateId, int version, String templateJson, String checksum,
                                 String planJson, String appliedSql, Instant publishedAt, String publishedBy) {}
class TemplateRepository {
  List<TemplateRow> findAll(); Optional<TemplateRow> find(String name);
  long insert(String name, ManageType mt, String json, String checksum, String user);
  void updateDraft(String name, ManageType mt, String json, String checksum, String user);
  void markPublished(String name, int version);
  void insertVersion(long templateId, int version, String json, String checksum, String plan, String sql, String user);
  List<TemplateVersionRow> versions(String name); Optional<TemplateVersionRow> version(String name, int v);
  Map<String, TemplateVersionRow> latestPublished();
  void delete(String name);
}

public final class CompiledTemplate {
  public Template model(); public int version(); public CompiledRules rules();
  public List<Field> orderedFields(); public Optional<Field> field(String name);
  public List<String> columns();                  // incl. audit columns when enabled
  public List<RefInfo> incomingRefs();            // other templates referencing this one
}
public record RefInfo(String template, String field) {}
public record CatalogSnapshot(long generation, Map<String, CompiledTemplate> templates) {
  public Optional<CompiledTemplate> find(String name);
}
@Service public class CatalogService implements TemplateLookup {
  public CatalogSnapshot snapshot();                          // volatile read
  public CompiledTemplate require(String name);               // NotFoundException
  void load();                                                // startup: from latestPublished(), @Order(30)
  void swap(Map<String, CompiledTemplate> next);              // generation + 1
}

public record DraftView(String name, ManageType manageType, String json, String checksum, Integer publishedVersion,
                        DraftStatus status, List<Issue> issues) {}
public enum DraftStatus { DRAFT, PUBLISHED, CHANGED }
@Service public class DraftService {                          // @Transactional
  List<DraftView> list(); DraftView get(String name);
  DraftView create(String json, String user); DraftView save(String name, String json, String expectedChecksum, String user);
  List<Issue> validate(String name); void deleteDraft(String name);
}
public record PlanView(MigrationPlan plan, List<PreCheckResult> preChecks, Map<String, String> ddl, List<Issue> issues, boolean publishable) {}
public record PublishRequest(String expectedDraftChecksum, String confirm) {}
@Service public class PublishService {
  PlanView plan(String name);
  int publish(String name, PublishRequest req, String user);  // lock → validate → plan → pre-check → apply → version row → swap
}
@Component class TemplateSeeder implements ApplicationRunner  // @Order(40): import templates/*.json (checksum) as drafts; auto-publish in ref order (dev)
```

---

## 8. `access`

```java
public enum TableOp { READ, CREATE, UPDATE, DELETE }
@Component public class AccessEvaluator {
  public boolean can(Template t, TdsPrincipal p, TableOp op);
  public boolean canRun(Template t, TdsPrincipal p, String action);
  public Set<FieldOp> fieldOps(Template t, Field f, TdsPrincipal p);
  public void require(Template t, TdsPrincipal p, TableOp op);      // ForbiddenException
  public List<Field> readableFields(Template t, TdsPrincipal p);
}
// CurrentPrincipal lives in security (§4)
```

The rules are those of grammar §6 and ADR-0008. `admin` gets every operation;
everything else is the union over roles of the table operations intersected
with the field operations.

---

## 9. `data`

```java
@RestController @RequestMapping("/api/data/{template}")
class RecordController {
  @GetMapping                       PageResponse list(@PathVariable String template, @RequestParam MultiValueMap<String,String> q);
  @GetMapping("/{id}")              ObjectNode get(…);
  @PostMapping                      ResponseEntity<ObjectNode> create(…, @RequestBody ObjectNode body);   // 201 + Location
  @PutMapping("/{id}")              ObjectNode replace(…);
  @PatchMapping("/{id}")            ObjectNode patch(…);
  @DeleteMapping("/{id}")           ResponseEntity<Void> delete(…);
  @GetMapping("/fields/{field}/options") List<Option> options(…, @RequestParam(defaultValue="") String q);
}
public record PageResponse(List<ObjectNode> items, int page, int size, long total) {}
public record Option(long id, String label) {}

public record RecordQuery(int page, int size, List<SortSpec> sort, String q, List<FilterSpec> filters) {}
public record FilterSpec(String field, FilterOp op, String raw) {}
public enum FilterOp { EQ, NE, GT, GTE, LT, LTE, IN, LIKE, NULL }
final class RecordQueryParser { static RecordQuery parse(MultiValueMap<String,String> params, CompiledTemplate t); }  // 400 on unknown field/op

record SqlQuery(String sql, Map<String, Object> params) {}
final class QueryBuilder {                       // per dialect, only catalog identifiers
  SqlQuery select(CompiledTemplate t, List<Field> fields, RecordQuery q);
  SqlQuery count(CompiledTemplate t, RecordQuery q);
  SqlQuery byId(CompiledTemplate t, List<Field> fields, long id);
  SqlQuery insert(CompiledTemplate t, Map<String,Object> values);
  SqlQuery update(CompiledTemplate t, long id, long rowVersion, Map<String,Object> values);
  SqlQuery delete(CompiledTemplate t, long id);
  SqlQuery refDisplay(CompiledTemplate target, String displayField, Collection<Long> ids);
}

@Service public class RecordService {            // @Transactional
  PageResponse list(String t, RecordQuery q, TdsPrincipal p);
  ObjectNode get(String t, long id, TdsPrincipal p);
  ObjectNode create(String t, ObjectNode body, TdsPrincipal p);
  ObjectNode update(String t, long id, ObjectNode body, boolean partial, TdsPrincipal p);
  void delete(String t, long id, TdsPrincipal p);
  Map<String,Object> loadForAction(String t, long id, TdsPrincipal p);   // for script ctx
}
final class RecordMapper {                       // row → JSON: codec, $display for ref/enum, strips unreadable fields
  ObjectNode toJson(CompiledTemplate t, Map<String,Object> row, List<Field> readable, RefDisplays displays);
}
@Component class DbErrorTranslator { ApiException translate(DataAccessException e, CompiledTemplate t); }   // FK/unique/check → 409/422
@Service class LookupService { List<Option> options(String t, String field, String q, TdsPrincipal p); }
```

---

## 10. `script`

```java
public enum ScriptStatus { OK, ERROR }
public record ScriptFile(String path, String sha256, Source source /*last good*/, Set<String> functions,
                         ScriptStatus status, String error, Instant loadedAt) {}
@Component public class ScriptRegistry implements grammar.ScriptCatalog {
  public void loadAll();                                   // @Order(20) at startup
  public void reload(Path file);  public void remove(Path file);
  public Optional<ScriptFile> get(String path);  public List<ScriptFile> list();
  public void write(String path, String content);          // atomic temp+move, then reload (admin)
}
@Component class ScriptWatcher implements SmartLifecycle   // WatchService on tds.paths.scripts; debounce 300 ms → registry.reload

public record ActionResult(String kind, String title, String content, String file) {}   // kind ∈ text|download|message|refresh
@Service public class ActionRunner {
  public ActionResult run(CompiledTemplate t, ActionSpec a, Map<String,Object> record /*nullable*/,
                          List<Map<String,Object>> records /*nullable*/, TdsPrincipal p);
}
final class ActionContextFactory {                         // builds the ProxyObject ctx (record, records, display, user, template, now, files, log)
  ProxyObject create(…, ActionOutputStore.Sink sink);
}
@Component class ActionOutputStore {
  Sink sink(String template);                              // writeText(name, text): name regex, 1 MB cap, path containment
  Resource read(String template, String file);
}
class ActionLogRepository { void insert(String template, String action, Long recordId, String user, Instant start, int ms, String status, String msg); }

@RestController class ActionController {                  // under /api/data/{template}
  @PostMapping("/{id}/actions/{action}")  ActionResult runRow(…);
  @PostMapping("/actions/{action}")       ActionResult runBulk(…, @RequestBody BulkActionRequest r);   // {ids:[…]} or {}
  @GetMapping("/action-output/{file}")    ResponseEntity<Resource> download(…);
}
```

---

## 11. `meta`, `admin`

```java
@RestController @RequestMapping("/api/meta") class MetaController {
  @GetMapping            NavigationMeta nav();
  @GetMapping("/{name}") TemplateMeta template(@PathVariable String name);
}
public record NavigationMeta(long generation, List<NavItem> templates) {}
public record NavItem(String name, String label, ManageType manageType) {}
public record TemplateMeta(String name, String label, String description, ManageType manageType, int version,
                           List<FieldMeta> fields, List<ActionMeta> actions, ViewSpec view, List<Rule> rules,
                           boolean canCreate, boolean canUpdate, boolean canDelete) {}
public record FieldMeta(Field field, Set<FieldOp> ops) {}
public record ActionMeta(ActionSpec action) {}
@Component class MetaAssembler { TemplateMeta assemble(CompiledTemplate t, TdsPrincipal p); }   // strips unreadable fields and unrunnable actions

// admin (all require ROLE_admin)
@RestController @RequestMapping("/api/admin/templates") class TemplateAdminController { … }   // → DraftService, PublishService
@RestController @RequestMapping("/api/admin/roles")     class RoleAdminController { … }       // → RoleService
@RestController @RequestMapping("/api/admin/users")     class UserAdminController { … }       // → AccountService
@RestController @RequestMapping("/api/admin/scripts")   class ScriptAdminController { … }     // → ScriptRegistry, ActionRunner(test)
@Service class RoleService { … }   // create/update/delete with builtin + in-use (users or templates) guards
```

---

## 12. Startup order

| `@Order` | Runner | Does |
|---|---|---|
| (Flyway) | Spring Boot | system tables |
| 10 | `BootstrapAdmin` | first admin user |
| 20 | `ScriptRegistry.loadAll` | scripts compiled; status per file |
| 30 | `CatalogService.load` | snapshot from the latest published versions; `SchemaInspector` drift WARNs |
| 40 | `TemplateSeeder` | import changed `templates/*.json` as drafts; auto-publish (dev) in ref order |
| — | `ScriptWatcher.start` | file watching |

---

## 13. Frontend (`ui/src`)

| Module | Key exports |
|---|---|
| `api/client.ts` | `apiFetch<T>(path, init)` adds credentials, `X-XSRF-TOKEN`, and JSON; throws `ApiError {status, code, title, errors, general}`; watches `X-TDS-Catalog-Generation` |
| `api/auth.ts` | `useMe()`, `useLogin()`, `useLogout()`, `useChangePassword()` (TanStack Query) |
| `api/meta.ts` · `api/data.ts` · `api/admin.ts` | `useNav()`, `useTemplateMeta(name)`, `useRecords(name, query)`, `useRecordMutation(name)`, `useRunAction()`, admin hooks |
| `grammar/types.ts` | TS mirror of the `tds/v1` model (§5.1) |
| `grammar/jsonlogic.ts` | `evaluate(rule, data)`, `truthy`, `present`, `variables` (ADR-0003) |
| `grammar/validateRecord.ts` · `validateTemplate.ts` | Ports of the prototype; parity-tested |
| `shell/` | `AppShell` (header, nav from `useNav`, user menu, theme toggle), `LoginPage`, `ChangePasswordModal` (forced when `mustChangePassword`), `RequireAuth`, `RequireAdmin` |
| `data/` | `TemplatePage`, `DataGrid`, `FilterBar`, `DetailPanel`, `RecordDrawer`, `FieldWidget`, `CellValue`, `ActionButton`, `ActionResultModal` |
| `admin/` | `RolesPage`, `UsersPage`, `DataBrowserPage` |
| `studio/` | `StudioPage`, `TemplateList`, `FieldsTab`, `FieldInspector`, `RulesTab`, `ActionsTab`, `AccessTab`, `ViewTab`, `PreviewTab`, `PlanTab`, `JsonTab`, `PublishDialog` |

State: server state lives in TanStack Query. URL state (page, sort, filters)
lives in `useSearchParams`. There's no global client store.

---

## 14. Test inventory (by milestone)

| Milestone | Tests |
|---|---|
| M1 | `ArchitectureTest`, `SqlitePragmasIT`, `AuthFlowIT` (csrf → login → me → logout; bad credentials 401; forced password change 403 then 200), `BootstrapAdminIT`, `ApiExceptionHandlerTest`, UI `LoginPage.test.tsx` |
| M2 | `TemplateParserTest`, `SemanticValidatorTest` (one fixture per code), `JsonLogicTest` + parity vectors, `DdlGeneratorTest` (golden files, both dialects), `MigrationPlannerTest`, `PublishIT` (incl. rebuild, rollback, pre-check block), `TemplateSeederIT` |
| M3 | `RecordQueryParserTest`, `QueryBuilderTest`, `ValueCodecTest`, `RecordValidatorTest` + parity vectors, `RecordApiIT` (CRUD, filters, paging, optimistic lock, FK 409), `PermissionMatrixIT`, `MetaIT` |
| M4 | `ScriptRegistryTest` (last good on error), `SandboxTest` (no `Java.type`, no IO, timeout), `ActionOutputStoreTest` (traversal), `ActionApiIT` |
| M5–M6 | Vitest component tests; Playwright: login → view alerts → create ticket; Studio → new template → publish → use |
| M7 | Re-run the IT suite on PostgreSQL (Testcontainers); `MigrateCommandIT` |

---

## 15. Implementation notes (2026-10-05, M2–M6)

The code follows this design. These class-level differences were made during
implementation. They didn't need ADRs unless one is linked.

| Area | As designed | As built |
|---|---|---|
| Grammar model | One file per record under `grammar.model` | All records in one holder, `grammar.Model` (`Model.Template`, `Model.Field`, …), plus `FieldType`, `Widget`, `Issue`. JSON is bound by hand in `TemplateBinder` (keys like `default`, `if`, `assert`) |
| Compiled rules | `CompiledRules` / `CompiledLogic` | JsonLogic is validated at publish (`JsonLogic.check`) and evaluated directly on the `JsonNode`. Patterns are cached in `RecordValidator` |
| Catalog | `CatalogSnapshot` + `CompiledTemplate` | `CatalogService.Snapshot` + `CatalogService.Published(model, version)`; `incomingRefs` is computed on the snapshot |
| Dialects | `SqlDialect` interface + 2 classes, `DialectProvider` | A single `SqlDialect` class keyed by `jdbc.DbVendor` ([ADR-0014](adr/0014-jdbc-foundation-package.md)), wired in `catalog.GrammarConfig` |
| Migration | `MigrationStrategy` (ALTER / rebuild) + `PreCheckResult` record | `MigrationPlanner` emits the SQLite rebuild SQL directly; on PostgreSQL, structural changes are `BLOCKED` until M7. `MigrationExecutor.apply(statements, rebuild, sameTx)` runs everything on one dedicated connection |
| Data | `QueryBuilder` + `RecordMapper` + `DbErrorTranslator` + `LookupService` | Mapping and lookups live in `RecordService`; `QueryBuilder`, `RecordQuery` and `DbErrorTranslator` as designed. Row JSON carries `<ref>$display` and `$issues` (app-level rule violations, computed on the server) |
| Seeding | `TemplateSeeder` only | `TemplateSeeder` (order 30) + `SampleDataSeeder` (order 40, dev) ([ADR-0016](adr/0016-template-seeding-and-dev-sample-data.md)) |
| Scripts | `ActionContextFactory`, `ActionLogRepository` | Built inside `ActionRunner`: `ctx` is a read-only `ProxyObject`, and the log is written with `JdbcClient` |
| Admin API | `RoleService`, `AccountService` | As designed, plus `AccountService.UserView` and self-lock-out guards. Template import is `POST /api/admin/templates/import`; live validation of an unsaved text is `POST /api/admin/templates/validate` |
| UI | TanStack Table; client `validateRecord`/`validateTemplate` ports | Mantine `Table` ([ADR-0015](adr/0015-grid-on-mantine-table.md)). Template and record validation come from the server (live, debounced); the client only evaluates JsonLogic conditions in forms. The Studio's `update()` applies mutations immediately (see `StudioEditorPage`) |
