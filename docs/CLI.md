# Karate v2 CLI Design

This document describes the CLI architecture for Karate v2, including subcommand design and integration with the Rust launcher.

> See also: [RUNTIME.md](./RUNTIME.md) | [Rust Launcher Spec](../../karate-cli/docs/spec.md)

---

## Architecture Overview

Karate v2 CLI has a two-tier architecture:

```
┌─────────────────────────────────────────────────────────────┐
│  Rust Launcher (karate binary)                              │
│  - Management commands: setup, update, config, doctor       │
│  - Delegates runtime commands to JVM                        │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼ delegates
┌─────────────────────────────────────────────────────────────┐
│  Java CLI (karate-core Main.java)                           │
│  - Runtime commands: run, mock, mcp, init, clean            │
│  - Receives args from Rust launcher                         │
│  - Uses PicoCLI for parsing                                 │
└─────────────────────────────────────────────────────────────┘
```

**Key principle:** The Rust launcher handles installation/management; the Java CLI handles test execution and runtime features.

---

## Subcommand Design

### Command Hierarchy

```
karate <command> [options] [args]

Runtime Commands (implemented in Java):
  run              Run Karate tests
  mock             Start mock server
  mcp              MCP server commands
  clean            Clean output directories

Management Commands (implemented in Rust):
  setup            First-run wizard
  update           Check for updates
  config           View/edit configuration
  init             Initialize new project (interactive)
  doctor           System diagnostics
  version          Show version info
```

### Java CLI Responsibility

The Java CLI (`io.karatelabs.Main`) implements:

| Command | Description | Status |
|---------|-------------|--------|
| `run` | Run Karate tests | Priority 1 |
| `clean` | Clean output directories | Priority 2 |
| `mock` | Start mock server | Future |
| `mcp` | MCP server mode | Future |

> **Note:** `init` is implemented in Rust (not Java) because it needs to scaffold different project types (Maven, Gradle, standalone) before Java/JVM is involved. See [Rust Launcher Spec](../../karate-cli/docs/spec.md).

---

## The `run` Command

### Synopsis

```bash
karate run [options] [paths...]
```

### Behavior

1. **With paths:** Run specified feature files/directories (inherits other settings from pom)
2. **Without paths:** Look for `karate-pom.json` in current directory
3. **With `--pom`:** Use specified project file
4. **With `--no-pom`:** Ignore `karate-pom.json` even if present

### Supported File Types

| Pattern | Description |
|---------|-------------|
| `*.feature` | Gherkin feature files (standard Karate tests) |
| `*.karate.js` | JavaScript scripts with full `karate.*` API |

**JavaScript scripts (`*.karate.js`)** are ideal for:
- Complex async operations with callbacks
- Process management (`karate.fork()`, `karate.exec()`)
- Custom test harnesses and orchestration
- Scenarios that benefit from native JS control flow

```bash
# Run feature files
karate run tests/users.feature

# Run JS scripts
karate run tests/setup.karate.js

# Run mixed (discovers both .feature and .karate.js)
karate run tests/
```

### Running Specific Scenarios by Line Number

You can run specific scenarios by appending `:LINE` to the feature file path:

```bash
# Run scenario at line 10
karate run tests/users.feature:10

# Run multiple scenarios (lines 10 and 25)
karate run tests/users.feature:10:25

# Works with classpath resources too
karate run classpath:features/users.feature:10
```

**Line number matching:**

| Line Points To | What Runs |
|----------------|-----------|
| `Scenario:` declaration | That specific scenario |
| Any step within a scenario | That scenario |
| `Scenario Outline:` declaration | All examples from that outline |
| `Examples:` table | All examples from that table |

**Note:** Line number selection bypasses tag filters (`@ignore`, `@env`, etc.), allowing you to run specific scenarios regardless of their tags. This is useful for debugging or running individual tests from an IDE.

### Options

| Option | Description |
|--------|-------------|
| `-t, --tags <expr>` | Tag expression filter (e.g., `@smoke`, `~@slow`) |
| `-T, --threads <n>` | Parallel thread count (default: 1) |
| `-e, --env <name>` | Karate environment (karate.env) |
| `-n, --name <regex>` | Scenario name filter |
| `-o, --output <dir>` | Output directory (default: target/karate-reports) |
| `-w, --workdir <dir>` | Working directory for relative paths |
| `-g, --configdir <dir>` | Directory containing karate-config.js |
| `-p, --pom <file>` | Path to project file (default: karate-pom.json) |
| `--no-pom` | Ignore karate-pom.json even if present |
| `-C, --clean` | Clean output directory before running |
| `-D, --dryrun` | Parse but don't execute |
| `--no-color` | Disable colored output |
| `--report-log-level <level>` | Minimum log level for HTML reports (default: info) |
| `--runtime-log-level <level>` | SLF4J logger level for console output |
| `-f, --format <formats>` | Output formats (see below) |
| `--log-mask <presets>` | Log masking presets (comma-separated) |
| `--listener <classes>` | Comma-separated RunListener class names |
| `--listener-factory <classes>` | Comma-separated RunListenerFactory class names |

### Output Formats

Control report output with `-f/--format`. Comma-separated, use `~` to disable:

| Format | Description | Default |
|--------|-------------|---------|
| `html` | Karate HTML reports | On |
| `cucumber:json` | Per-feature Cucumber JSON (for Allure, ReportPortal) | Off |
| `junit:xml` | JUnit XML reports | Off |
| `karate:jsonl` | JSONL event stream (for CI/CD, IDEs) | Off |

**Examples:**

```bash
# Enable Cucumber JSON (HTML still on by default)
karate run -f cucumber:json features/

# Enable multiple formats
karate run -f cucumber:json,junit:xml features/

# Disable HTML, enable Cucumber JSON
karate run -f ~html,cucumber:json features/

# Only JSONL output (disable HTML)
karate run -f ~html,karate:jsonl features/
```

### Log Masking

Mask sensitive data in logs using built-in presets:

```bash
# Single preset
karate run --log-mask PASSWORDS features/

# Multiple presets
karate run --log-mask PASSWORDS,CREDIT_CARDS features/

# All sensitive data
karate run --log-mask ALL_SENSITIVE features/
```

**Available presets:** `PASSWORDS`, `CREDIT_CARDS`, `SSN`, `EMAILS`, `API_KEYS`, `BEARER_TOKENS`, `BASIC_AUTH`, `ALL_SENSITIVE`

For custom patterns, use `karate-pom.json`:
```json
{
  "logMask": {
    "patterns": [
      {"regex": "secret[=:]([^\\s]+)", "replacement": "***"}
    ]
  }
}
```

### Examples

```bash
# Run with auto-detected karate-pom.json
karate run

# Run specific paths (inherits env, threads, etc. from pom)
karate run src/test/features

# Run with explicit pom file
karate run --pom my-project.json

# Run ignoring karate-pom.json
karate run --no-pom src/test/features

# Run with options
karate run -t @smoke -e dev -T 5 src/test/features

# Run from different working directory
karate run -w /home/user/project src/test/features
```

---

## Project File

### Canonical Name: `karate-pom.json`

The project file name is `karate-pom.json` (inspired by Maven's POM concept). When `karate run` is invoked, it automatically loads `karate-pom.json` from the current directory (or workdir if specified).

> **Note:** `karate-pom.json` is designed for **non-Java teams** using the standalone CLI without Maven/Gradle. Java teams should use `Runner.Builder` directly with system properties for CI/CD overrides.

> **Note:** This is distinct from `karate-config.js` which handles runtime configuration (baseUrl, auth, etc.). The pom file defines *how* to run tests (paths, tags, threads, output).

### Schema

```json
{
  "paths": ["src/test/features/", "classpath:features/"],
  "tags": ["@smoke", "~@slow"],
  "env": "dev",
  "threads": 5,
  "scenarioName": ".*login.*",
  "configDir": "src/test/resources",
  "workingDir": "/home/user/project",
  "dryRun": false,
  "clean": false,
  "output": {
    "dir": "target/karate-reports",
    "html": true,
    "junitXml": false,
    "cucumberJson": false,
    "jsonLines": false
  },
  "logMask": {
    "presets": ["PASSWORDS", "CREDIT_CARDS"],
    "patterns": [
      {"regex": "secret[=:]\\s*([^\\s]+)", "replacement": "***"}
    ],
    "headers": ["Authorization", "X-Api-Key"]
  },
  "listeners": ["com.example.MyListener"],
  "listenerFactories": ["com.example.MyListenerFactory"]
}
```

### Path Resolution

**Important:** `outputDir` and `workingDir` serve different purposes and are **independent**:

| Setting | Purpose | Relative To |
|---------|---------|-------------|
| `workingDir` | Resolves feature paths, config files, and pom location | Process current directory |
| `output.dir` | Where reports are written | Process current directory (NOT workingDir) |

This means if you set `workingDir: "src/test/java"` and `output.dir: "target/karate-reports"`, reports go to `./target/karate-reports` (not `./src/test/java/target/karate-reports`).

**Best practice:** When the process working directory may vary (CI/CD, IDE integrations), use **absolute paths** for `output.dir`:

```json
{
  "workingDir": "/home/user/project/src/test/java",
  "output": {
    "dir": "/home/user/project/target/karate-reports"
  }
}
```

### Precedence

CLI arguments override pom file values:

```
CLI flags → karate-pom.json → defaults
```

The pom is always loaded if present, and CLI args override specific values. Use `--no-pom` to ignore it entirely.

---

## Implementation Plan

### Phase 1: Subcommand Refactoring

Refactor `Main.java` to use PicoCLI subcommands:

```java
@Command(
    name = "karate",
    subcommands = {
        RunCommand.class,
        CleanCommand.class,
    }
)
public class Main implements Callable<Integer> {

    @Parameters(arity = "0..*", hidden = true)
    List<String> unknownArgs;

    @Override
    public Integer call() {
        // No subcommand specified
        if (unknownArgs != null && !unknownArgs.isEmpty()) {
            // Legacy: treat args as paths, delegate to run
            return new RunCommand().runWithPaths(unknownArgs);
        }
        // Look for karate-pom.json in cwd
        if (Files.exists(Path.of("karate-pom.json"))) {
            return new RunCommand().call();
        }
        // Show help
        CommandLine.usage(this, System.out);
        return 0;
    }
}
```

### Phase 2: RunCommand

Extract current `Main.java` logic into `RunCommand`:

```java
@Command(name = "run", description = "Run Karate tests")
public class RunCommand implements Callable<Integer> {

    public static final String DEFAULT_POM_FILE = "karate-pom.json";

    @Parameters(description = "Feature files or directories", arity = "0..*")
    List<String> paths;

    @Option(names = {"-p", "--pom"}, description = "Project file (default: karate-pom.json)")
    String pomFile;

    @Option(names = {"--no-pom"}, description = "Ignore karate-pom.json")
    boolean noPom;

    // ... other options ...

    @Override
    public Integer call() {
        // Load pom if present (unless --no-pom)
        if (!noPom) {
            loadPom();
        }
        // CLI args override pom values
        // ... rest of execution
    }
}
```

### Phase 3: CleanCommand

```java
@Command(name = "clean", description = "Clean output directories")
public class CleanCommand implements Callable<Integer> {

    @Option(names = {"-o", "--output"}, description = "Output directory to clean")
    String outputDir;

    @Override
    public Integer call() {
        // Cleans:
        // - karate-reports/
        // - karate-reports_*/ (backup directories)
        // - karate-temp/ (Chrome user data, callSingle cache, etc.)
        return 0;
    }
}
```

### Temp Directory Structure

Karate uses a standardized temp directory for runtime artifacts:

```
target/                          (or build/ for Gradle)
├── karate-reports/              # Test reports (HTML, JSON, etc.)
├── karate-reports_20260125_*/   # Backup directories (when backupReportDir enabled)
└── karate-temp/                 # Runtime temp files (cleaned by 'karate clean')
    ├── chrome-abc12345/         # Chrome/browser user data directories
    └── cache/                   # callSingle disk cache files
```

**Build directory detection:** Karate automatically detects Maven vs Gradle projects:
- Checks for `build.gradle`, `build.gradle.kts`, `settings.gradle*` → uses `build/`
- Checks for `pom.xml` → uses `target/`
- Falls back to checking existing `build/` or `target/` directories
- Override with system property: `-Dkarate.output.dir=custom/path`

---

## Backward Compatibility

### Legacy Behavior

For backward compatibility, bare arguments (no subcommand) are treated as paths:

| Invocation | Interpretation |
|------------|----------------|
| `karate run src/test` | Explicit run command |
| `karate src/test` | Legacy → delegates to `run` |
| `karate` | Auto-load `karate-pom.json` or show help |
| `karate -t @smoke src/test` | Legacy with options → delegates to `run` |

### Migration Path

1. **v2.0:** Support both `karate run` and legacy `karate <paths>`
2. **v2.1+:** Deprecation warnings for legacy usage
3. **v3.0:** Consider removing legacy support

---

## Integration with Rust Launcher

The Rust launcher (see [spec.md](../../karate-cli/docs/spec.md)) delegates runtime commands to the Java CLI:

```
karate run src/test/features -t @smoke
    │
    ▼ Rust launcher
java -jar ~/.karate/dist/karate-2.0.0.jar run src/test/features -t @smoke
    │
    ▼ Java CLI
Main.java parses and executes
```

### Classpath Construction

The Rust launcher constructs the classpath in this order:
- Karate fatjar (`~/.karate/dist/karate-X.X.X.jar`)
- Extension JARs (`~/.karate/ext/*.jar`)
- Project-local extensions (`.karate/ext/*.jar`)
- Extra classpath entries from `--cp` flags

The `--cp` global flag allows extensions (e.g. IDE integrations) to contribute proprietary JARs:
```bash
karate --cp /path/to/karate-ide-v2.jar run features/
karate --cp /path/to/a.jar --cp /path/to/b.jar run features/
```

### JVM Options

Configured via `karate-cli.json`:
```json
{
  "jvm_opts": "-Xmx512m"
}
```

---

## Future Commands (Java)

### `karate mock`

Start a mock server:

```bash
karate mock --port 8080 mocks/
```

### `karate mcp`

Start MCP server mode for LLM integration:

```bash
karate mcp --stdio
```

> **Note:** `karate init` is implemented in Rust. See [Rust Launcher Spec](../../karate-cli/docs/spec.md) for details.

### `karate perf`

Run performance tests with Gatling (requires karate-gatling-bundle.jar in `~/.karate/ext/`):

```bash
karate perf --users 10 --duration 30s features/
```

See [GATLING.md](./GATLING.md) for full documentation.

---

## CLI Extension SPI (CommandProvider)

Modules can register additional subcommands via ServiceLoader:

```java
// io.karatelabs.cli.CommandProvider
public interface CommandProvider {
    String getName();           // e.g., "perf"
    String getDescription();    // e.g., "Run performance tests"
    Object getCommand();        // PicoCLI command instance
}
```

**Registration:** Create `META-INF/services/io.karatelabs.cli.CommandProvider` with implementation class name.

**Discovery:** Main.java discovers providers on classpath:

```java
ServiceLoader<CommandProvider> providers = ServiceLoader.load(CommandProvider.class);
for (CommandProvider provider : providers) {
    spec.addSubcommand(provider.getName(), provider.getCommand());
}
```

**Use case:** The karate-gatling module uses this to add the `perf` subcommand when its JAR is in `~/.karate/ext/`.

---

## TODO

### Two-Way JSON Configuration

Currently `karate-pom.json` is read-only (JSON → config). Implement bidirectional conversion:

1. **`KaratePom.toJson()`** - serialize config back to JSON
2. **`RunCommand.toPom()`** - convert CLI args to KaratePom object
3. **`karate config --export`** - dump effective merged config as JSON
4. **`karate config --show`** - display current effective configuration

**Use cases:**
- IDE tooling for programmatic config read/write
- `karate init` generating starter `karate-pom.json` with user's CLI preferences
- Debugging: see what config is actually being used after CLI + pom merge

---

## Exit Codes

| Code | Meaning |
|------|---------|
| `0` | Success (all tests passed) |
| `1` | Test failures |
| `2` | Configuration error |
| `3` | Runtime error |

---

## File Structure

After subcommand refactoring:

```
io.karatelabs/
├── Main.java              # Parent command, delegates to subcommands
└── cli/
    ├── RunCommand.java    # karate run
    └── CleanCommand.java  # karate clean

io.karatelabs.core/
├── KaratePom.java         # JSON pom parsing
├── Runner.java            # Programmatic API
└── ...
```

---

## Manual CLI Testing

### Setup

A test script is provided at `etc/test-cli.sh`. The `home/` folder is gitignored for test workspaces.

**Build project:**
```bash
# Standard build
mvn clean install -DskipTests

# Build fatjar (for simpler testing)
mvn clean package -DskipTests -Pfatjar
```

**Test workspace** (already set up in `home/test-project/`):
```
home/test-project/
├── karate-pom.json       # Project file
└── features/
    └── hello.feature     # Test feature with @smoke, @api tags
```

### Running CLI Commands

**Option 1: Using test-cli.sh (recommended)**

The script auto-detects fatjar or builds classpath:
```bash
# Help
./etc/test-cli.sh --help

# Run tests
./etc/test-cli.sh home/test-project/features

# Run with workdir
./etc/test-cli.sh -w home/test-project features

# Run with explicit pom
./etc/test-cli.sh -p home/test-project/karate-pom.json
```

**Option 2: Using fatjar directly**
```bash
# Build fatjar first
mvn package -DskipTests -Pfatjar

# Run
java -jar karate-core/target/karate.jar --help
java -jar karate-core/target/karate.jar home/test-project/features
```

**Option 3: Using mvn exec:java**
```bash
cd karate-core
mvn exec:java -Dexec.mainClass="io.karatelabs.Main" \
  -Dexec.args="--help"
```

### Fatjar Build

The fatjar profile is configured in `karate-core/pom.xml`:

```bash
# Build fatjar
mvn package -DskipTests -Pfatjar

# Output: karate-core/target/karate.jar
```

### Test Scenarios

| Test | Command | Expected |
|------|---------|----------|
| Help | `--help` | Shows usage with all options |
| Version | `--version` | Shows "Karate 2.0" |
| Run with paths | `home/test-project/features` | Executes tests, reports to default dir |
| Run with pom | `-p home/test-project/karate-pom.json` | Loads pom, uses pom paths |
| Run with workdir | `-w home/test-project features` | Clean relative paths in reports |
| Run with env | `-e dev features` | Sets karate.env |
| Run with tags | `-t @smoke features` | Filters by tag |
| Run no pom | `--no-pom features` | Ignores karate-pom.json |
| Dry run | `-D features` | Parses but doesn't execute |
| Clean | `-C -o home/test-project/target features` | Cleans output before run |

### Verify Output

After running tests, verify:

```bash
# Reports generated
ls -la home/test-project/target/reports/

# Expected files
# - karate-summary.html
# - feature-html/*.html (per-feature reports)
```

### Cleanup

```bash
rm -rf home/test-project/target
```

---

## References

- [Rust Launcher Spec](../../karate-cli/docs/spec.md) - Full architecture for Rust-based CLI launcher
- [RUNTIME.md](./RUNTIME.md) - Runtime architecture and feature implementation status
- [PicoCLI Subcommands](https://picocli.info/#_subcommands) - PicoCLI documentation
