# ⚡ Java Code Efficiency & Energy-Aware Performance Analyzer

A **Spring Boot** static analysis tool that evaluates Java code efficiency from a performance and energy-awareness perspective.

---

## ⚠️ Academic Disclaimer

The **Energy Efficiency Index (EEI)** is a **relative software efficiency indicator** derived from static analysis.
It does **NOT** measure actual energy consumption in Joules.

EEI is grounded in the relationship:

```
Energy ≈ Power × Time
Execution Time ↑ ← algorithmic complexity + anti-patterns
Structural importance ↑ ← call graph centrality
→ Higher complexity + high centrality = greater relative energy impact
```

This approach is consistent with literature including:
- Pereira et al. (2017) *"Energy Efficiency across Programming Languages"*
- Sahin et al. (2012) *"How Do Code Refactorings Affect Energy Usage?"*

---

## 🏗️ Architecture

```
src/main/java/com/energyanalyzer/
├── EnergyAnalyzerApplication.java   # Entry point (web + CLI)
├── controller/
│   ├── UploadController.java        # File upload handling
│   └── DashboardController.java     # Dashboard + JSON API + exports
├── service/
│   ├── StaticAnalyzerService.java   # Pipeline orchestrator
│   ├── CodeParserService.java       # JavaParser AST extraction
│   ├── ComplexityClassifierService  # Big-O classification
│   ├── EEICalculatorService.java    # EEI + penalty scoring
│   ├── CallGraphService.java        # Static call graph + PageRank centrality
│   ├── WeightedEEIService.java      # Weighted EEI computation
│   ├── HotspotDetectorService.java  # Multi-criteria hotspot detection
│   ├── SuggestionEngineService.java # Rule-based optimization suggestions
│   ├── ThresholdValidationService   # Build gate enforcement
│   └── ExportService.java           # CSV + PDF export
├── model/
│   ├── StaticMetrics.java           # Raw AST-extracted metrics
│   ├── ComplexityClass.java         # O(1)/O(n)/O(n²)/O(n³)/O(2^n)
│   ├── EnergyTier.java              # LOW/MEDIUM/HIGH/CRITICAL
│   ├── MethodNode.java              # Call graph node
│   ├── CallGraph.java               # Full call graph structure
│   ├── MethodAnalysisResult.java    # Complete per-method result
│   ├── OptimizationSuggestion.java  # Suggestion with rationale
│   ├── AnalysisReport.java          # Full project report
│   └── ThresholdConfiguration.java # YAML-driven thresholds
└── cli/
    └── CliRunner.java               # CI/CD CLI integration
```

---

## 🚀 Quick Start

### Prerequisites
- Java 17+
- Maven 3.8+

### Run Web Application

```bash
git clone https://github.com/your-org/java-energy-analyzer.git
cd java-energy-analyzer
mvn clean package -DskipTests
java -jar target/java-energy-analyzer-1.0.0.jar
```

Open: http://localhost:8080
Click **"View Demo Dashboard"** to see a sample analysis immediately.

---

## ⚡ CI/CD Integration

### 1. GitHub Actions

Add `.github/workflows/energy-analysis.yml` to your repository:

```yaml
- name: Run energy analysis
  run: |
    java -jar energy-analyzer.jar src/main/java/ --name="My Project"
```

The workflow:
- Runs on every push and pull request
- Posts analysis summary as PR comment
- Fails the build if EEI thresholds are violated
- Archives the report as a build artifact

See: [`.github/workflows/energy-analysis.yml`](.github/workflows/energy-analysis.yml)

---

### 2. Jenkins Pipeline

Add `Jenkinsfile` to your repository root:

```groovy
stage('⚡ Energy Analysis') {
    steps {
        sh 'java -jar energy-analyzer.jar src/main/java/'
    }
}
```

Features:
- Integrates with `check` stage
- Marks build as `UNSTABLE` on violations (configurable to hard-fail)
- Publishes HTML report via `publishHTML`
- Supports `slackSend` notifications

See: [`Jenkinsfile`](Jenkinsfile)

---

### 3. Maven Integration

Add to your `pom.xml`:

```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>exec-maven-plugin</artifactId>
    <version>3.1.1</version>
    <executions>
        <execution>
            <id>energy-analyze</id>
            <phase>verify</phase>
            <goals><goal>exec</goal></goals>
            <configuration>
                <executable>java</executable>
                <arguments>
                    <argument>-jar</argument>
                    <argument>${project.basedir}/tools/java-energy-analyzer-1.0.0.jar</argument>
                    <argument>${project.build.sourceDirectory}</argument>
                </arguments>
            </configuration>
        </execution>
    </executions>
</plugin>
```

Then run:
```bash
mvn verify                          # Runs energy analysis in verify phase
mvn verify -P trading-system        # Strict 80% EEI threshold
mvn verify -P energy-warn-only      # Warn-only, never fails build
```

See: [`docs/maven-integration.xml`](docs/maven-integration.xml)

---

### 4. Gradle Integration

Add to `build.gradle`:

```groovy
task energyAnalyze(type: JavaExec) {
    args = ['src/main/java/', '--name=My Project']
}
check.dependsOn energyAnalyze
```

Then run:
```bash
./gradlew energyAnalyze             # Manual run
./gradlew check                     # Auto-runs with check
./gradlew energyAnalyze -PeeiThreshold=70  # Custom threshold
./gradlew energyAnalyze -PwarnOnly=true    # Warn-only mode
```

See: [`docs/gradle-integration.gradle`](docs/gradle-integration.gradle)

---

### 5. CLI Direct

```bash
# Analyze a project folder
java -jar analyzer.jar src/main/java/

# With project name
java -jar analyzer.jar src/main/java/ --name="E-Commerce Backend"

# Warn-only (never fail build — useful during migration)
java -jar analyzer.jar src/main/java/ --warn-only

# Override threshold for this run
java -jar analyzer.jar src/main/java/ \
    --energy-analyzer.thresholds.minimum-eei-threshold=70
```

**Exit codes:**
- `0` = All thresholds met (build pass)
- `1` = Threshold violation (build fail)
- `2` = Analysis error

---

## ⚙️ Threshold Configuration

Edit `src/main/resources/application.yml`:

```yaml
energy-analyzer:
  thresholds:
    minimum-eei-threshold: 60     # Fail if any method EEI < 60
    critical-threshold: 30        # CRITICAL tier below this
    max-allowed-hotspots: 5       # Fail if > 5 hotspots
    fail-build-on-violation: true # false = warn-only mode
```

### Project-type presets

| Project Type       | `minimum-eei-threshold` | `max-allowed-hotspots` |
|--------------------|------------------------|------------------------|
| Trading/Financial  | 80                     | 2                      |
| General Web App    | 60 *(default)*         | 5                      |
| Batch Processing   | 50                     | 10                     |
| Prototype/Research | 30                     | 20                     |

---

## 📊 EEI Scoring Formula

```
EEI = BaseScore(ComplexityClass) - Σ(Penalties)

BaseScore:
  O(1)  → 100
  O(n)  → 85
  O(n²) → 50
  O(n³) → 20
  O(2^n)→ 5

Penalties:
  Recursion detected          → -10
  String concat in loop       → -5 per occurrence
  Excessive object creation   → -3 per excess instance
  I/O operations (>3)         → -8 per excess operation
  High cyclomatic complexity  → -5 per unit above 10
  Deep nesting (depth ≥ 3)    → -7
  Excessive method calls      → -2

WeightedEEI = EEI × WeightMultiplier

WeightMultiplier = 1.0 + centralityScore × 2.0
  (based on simplified PageRank of call graph)
  Range: 1.0x (isolated) to 3.0x (highly central)
```

---

## 📈 Dashboard Features

| Feature | Description |
|---------|-------------|
| EEI bar chart | Per-method EEI with threshold line |
| Weighted EEI chart | Structural-importance-adjusted scores |
| Tier distribution | Doughnut chart: LOW/MEDIUM/HIGH/CRITICAL |
| Complexity distribution | Polar chart: O(1) through O(2^n) |
| Methods table | Filterable, sortable, expandable suggestions |
| Hotspot panel | Detailed hotspot analysis with reasons |
| CSV export | Full results for spreadsheet analysis |
| PDF export | Professional report for submission/presentation |
| Dark mode | Default (system-aware toggle planned) |

---

## 🧪 Sample Project

A demo Java file with intentional inefficiencies is in:

```
sample-project/src/main/java/com/sample/SampleECommerceService.java
```

Run analysis on it:
```bash
java -jar target/java-energy-analyzer-1.0.0.jar \
    sample-project/src/main/java/ \
    --name="Sample E-Commerce"
```

**Expected results:**

| Method | Complexity | EEI | Tier | Hotspot |
|--------|-----------|-----|------|---------|
| `processAllOrders()` | O(n²) | ~45 | MEDIUM | ⚠ Yes |
| `computeDiscount()` | O(2^n) | ~5 | CRITICAL | ⚠ Yes |
| `findProductCombinations()` | O(n³) | ~18 | CRITICAL | ⚠ Yes |
| `buildOrderReport()` | O(n) | ~52 | MEDIUM | No |
| `getOrderById()` | O(1) | ~95 | LOW | No |
| `calculateTotal()` | O(n) | ~82 | LOW | No |

---

## 📦 Build & Export

```bash
# Full build
mvn clean package

# Run tests
mvn test

# Build + energy analysis on self
mvn verify
```

---

## 🎓 Academic Defense Points

1. **Methodology justification**: EEI is a proxy metric based on algorithmic complexity theory (Knuth, 1968; McCabe, 1976) and empirical research linking code structure to energy consumption.

2. **Call graph centrality**: Uses a simplified PageRank algorithm (Brin & Page, 1998) to compute structural importance — academically well-established.

3. **Limitation acknowledgment**: The system explicitly states it does not measure Joules. Static analysis cannot capture runtime behavior, JIT optimization, or hardware-specific energy profiles.

4. **Deterministic approach**: All analysis is rule-based, reproducible, and explainable — no black-box machine learning.

5. **Literature grounding**: Consistent with Pereira et al. (2017), Sahin et al. (2012), Saborido et al. (2014).
