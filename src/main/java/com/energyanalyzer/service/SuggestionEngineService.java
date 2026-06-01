package com.energyanalyzer.service;

import com.energyanalyzer.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * SuggestionEngineService
 *
 * Generates rule-based optimization suggestions for each analyzed method.
 *
 * Each suggestion includes:
 *   - What the issue is
 *   - Why it matters (performance/energy rationale)
 *   - How to fix it (concrete guidance)
 *   - A code example showing the fix
 *   - Estimated EEI improvement
 *
 * Rules are purely deterministic and statically derived.
 * No machine learning or probabilistic models are used.
 */
@Slf4j
@Service
public class SuggestionEngineService {

    /**
     * Generate all applicable suggestions for a method.
     *
     * @param metrics    Raw static metrics
     * @param complexity Classified complexity class
     * @param eei        Computed EEI score
     * @param fanIn      Fan-in from call graph
     * @return List of optimization suggestions, ordered by priority
     */
    public List<OptimizationSuggestion> generateSuggestions(
            StaticMetrics metrics,
            ComplexityClass complexity,
            double eei,
            int fanIn) {

        List<OptimizationSuggestion> suggestions = new ArrayList<>();

        // ── Rule 1: Replace recursion with iteration ───────────────────────
        if (metrics.isRecursive()) {
            suggestions.add(OptimizationSuggestion.builder()
                    .title("Replace Recursion with Iteration")
                    .issue("Method calls itself recursively")
                    .whyItMatters(
                        "Recursive calls create a new stack frame for each invocation. " +
                        "For large inputs, this leads to StackOverflowError and incurs " +
                        "significant overhead from frame allocation, parameter copying, and " +
                        "return address management. Iterative solutions avoid this entirely.")
                    .howToFix(
                        "Refactor the recursive logic using an explicit stack (java.util.Deque) " +
                        "or convert to a simple loop. For tail-recursive methods, direct loop " +
                        "conversion is straightforward. For tree/graph recursion, use an " +
                        "iterative DFS with an explicit stack.")
                    .codeExample(
                        "// Before (recursive):\n" +
                        "int factorial(int n) { return n <= 1 ? 1 : n * factorial(n-1); }\n\n" +
                        "// After (iterative):\n" +
                        "int factorial(int n) {\n" +
                        "    long result = 1;\n" +
                        "    for (int i = 2; i <= n; i++) result *= i;\n" +
                        "    return (int) result;\n" +
                        "}")
                    .priority(OptimizationSuggestion.Priority.HIGH)
                    .category(OptimizationSuggestion.Category.LOOP_OPTIMIZATION)
                    .estimatedEeiGain(10)
                    .build());
        }

        // ── Rule 2: Reduce nested loops ────────────────────────────────────
        if (metrics.getNestedLoopDepth() >= 2) {
            suggestions.add(OptimizationSuggestion.builder()
                    .title("Reduce Nested Loop Complexity")
                    .issue(String.format("Nested loops detected at depth %d (%s complexity)",
                            metrics.getNestedLoopDepth(), complexity.getLabel()))
                    .whyItMatters(
                        "Nested loops with depth " + metrics.getNestedLoopDepth() +
                        " result in " + complexity.getLabel() + " time complexity. " +
                        "For input size n=1000, this means ~" +
                        formatCost(complexity) + " operations. " +
                        "Reducing nesting depth dramatically reduces execution time and energy use.")
                    .howToFix(
                        "1. Consider algorithmic alternatives (sorting + linear scan instead of " +
                        "nested loops for duplicate detection). " +
                        "2. Use HashMap/HashSet for O(1) lookups to replace inner loops. " +
                        "3. Apply memoization to cache inner loop results. " +
                        "4. Break the problem into separate linear passes.")
                    .codeExample(
                        "// Before O(n²): nested loop duplicate check\n" +
                        "for (int i = 0; i < list.size(); i++)\n" +
                        "    for (int j = i+1; j < list.size(); j++)\n" +
                        "        if (list.get(i).equals(list.get(j))) ...\n\n" +
                        "// After O(n): use HashSet\n" +
                        "Set<T> seen = new HashSet<>();\n" +
                        "for (T item : list) {\n" +
                        "    if (!seen.add(item)) /* duplicate found */;\n" +
                        "}")
                    .priority(OptimizationSuggestion.Priority.HIGH)
                    .category(OptimizationSuggestion.Category.COMPLEXITY)
                    .estimatedEeiGain(20)
                    .build());
        }

        // ── Rule 3: Use StringBuilder for String concatenation ─────────────
        if (metrics.getStringConcatenationCount() > 0 && metrics.getLoopCount() > 0) {
            suggestions.add(OptimizationSuggestion.builder()
                    .title("Replace String '+' with StringBuilder in Loops")
                    .issue(String.format(
                        "%d string concatenation(s) detected within %d loop(s)",
                        metrics.getStringConcatenationCount(), metrics.getLoopCount()))
                    .whyItMatters(
                        "Java String objects are immutable. Each 'str + value' in a loop " +
                        "creates a NEW String object by copying all previous characters plus the new ones. " +
                        "For n iterations: 1+2+3+...+n = O(n²) character copies. " +
                        "This dramatically increases heap allocation and GC pressure.")
                    .howToFix(
                        "Replace all String concatenation within loops with StringBuilder.append(). " +
                        "Initialize StringBuilder outside the loop with an estimated capacity " +
                        "to minimize internal resizing.")
                    .codeExample(
                        "// Before (O(n²) string copies):\n" +
                        "String result = \"\";\n" +
                        "for (String item : items) result += item + \", \";\n\n" +
                        "// After (O(n) - single allocation):\n" +
                        "StringBuilder sb = new StringBuilder(items.size() * 16);\n" +
                        "for (String item : items) sb.append(item).append(\", \");\n" +
                        "String result = sb.toString();")
                    .priority(OptimizationSuggestion.Priority.HIGH)
                    .category(OptimizationSuggestion.Category.STRING_HANDLING)
                    .estimatedEeiGain(15)
                    .build());
        }

        // ── Rule 4: Reduce object creation ────────────────────────────────
        if (metrics.getObjectCreationCount() > 5) {
            suggestions.add(OptimizationSuggestion.builder()
                    .title("Reduce Excessive Object Creation")
                    .issue(String.format("%d object creation(s) detected",
                            metrics.getObjectCreationCount()))
                    .whyItMatters(
                        "Excessive object allocation increases GC pressure. " +
                        "The GC must track, scan, and collect all these objects, " +
                        "causing periodic 'stop-the-world' pauses that directly " +
                        "translate to CPU time and energy consumption.")
                    .howToFix(
                        "1. Use object pooling for frequently-created objects. " +
                        "2. Reuse existing instances by resetting state instead of creating new ones. " +
                        "3. Prefer primitive types (int, long) over boxed types (Integer, Long). " +
                        "4. Use factory methods with caching (e.g., Integer.valueOf()). " +
                        "5. Consider value objects (records) for immutable data.")
                    .codeExample(
                        "// Before: new object each iteration\n" +
                        "for (int i = 0; i < 1000; i++) {\n" +
                        "    Point p = new Point(i, i);  // 1000 allocations\n" +
                        "    process(p);\n" +
                        "}\n\n" +
                        "// After: reuse or use primitives\n" +
                        "Point p = new Point(0, 0);  // 1 allocation\n" +
                        "for (int i = 0; i < 1000; i++) {\n" +
                        "    p.setX(i); p.setY(i);  // reuse\n" +
                        "    process(p);\n" +
                        "}")
                    .priority(OptimizationSuggestion.Priority.MEDIUM)
                    .category(OptimizationSuggestion.Category.MEMORY)
                    .estimatedEeiGain(8)
                    .build());
        }

        // ── Rule 5: Optimize I/O operations ───────────────────────────────
        if (metrics.getIoOperationCount() > 2) {
            suggestions.add(OptimizationSuggestion.builder()
                    .title("Optimize I/O Operations")
                    .issue(String.format("%d I/O operations detected",
                            metrics.getIoOperationCount()))
                    .whyItMatters(
                        "I/O operations (disk, network, database) are typically 10,000–1,000,000× " +
                        "slower than CPU operations. Even a single uncached database query in a " +
                        "frequently-called method can dominate total execution time and energy use.")
                    .howToFix(
                        "1. Batch I/O: replace N individual operations with 1 batched operation. " +
                        "2. Buffer I/O: use BufferedReader/BufferedWriter for file operations. " +
                        "3. Cache results: use a cache (Map, Guava Cache, Caffeine) for repeated reads. " +
                        "4. Use async I/O for non-blocking operations. " +
                        "5. Connection pool DB connections to avoid repeated open/close overhead.")
                    .codeExample(
                        "// Before: N database queries in a loop\n" +
                        "for (Long id : ids) result.add(repo.findById(id));\n\n" +
                        "// After: 1 batched query\n" +
                        "result.addAll(repo.findAllById(ids));")
                    .priority(OptimizationSuggestion.Priority.HIGH)
                    .category(OptimizationSuggestion.Category.IO_OPTIMIZATION)
                    .estimatedEeiGain(20)
                    .build());
        }

        // ── Rule 6: Reduce cyclomatic complexity ───────────────────────────
        if (metrics.getCyclomaticComplexity() > 10) {
            suggestions.add(OptimizationSuggestion.builder()
                    .title("Reduce Cyclomatic Complexity")
                    .issue(String.format("Cyclomatic complexity is %d (threshold: 10)",
                            metrics.getCyclomaticComplexity()))
                    .whyItMatters(
                        "High cyclomatic complexity (CC=" + metrics.getCyclomaticComplexity() +
                        ") means many execution paths, each potentially activating different " +
                        "code branches. This hinders JIT compiler optimization, increases branch " +
                        "misprediction rates, and makes the code harder to optimize.")
                    .howToFix(
                        "1. Extract sub-logic into separate methods (decomposition). " +
                        "2. Replace complex if-else chains with polymorphism (Strategy pattern). " +
                        "3. Use switch expressions (Java 14+) for multi-branch conditionals. " +
                        "4. Replace conditional logic with data structures (Map<Condition, Action>). " +
                        "5. Apply the Single Responsibility Principle.")
                    .priority(OptimizationSuggestion.Priority.MEDIUM)
                    .category(OptimizationSuggestion.Category.REFACTORING)
                    .estimatedEeiGain(5)
                    .build());
        }

        // ── Rule 7: Cache repeated calculations ───────────────────────────
        if (metrics.isRecursive() || (metrics.getMethodCallCount() > 10 && metrics.getLoopCount() > 0)) {
            suggestions.add(OptimizationSuggestion.builder()
                    .title("Cache Repeated Calculations (Memoization)")
                    .issue("Potentially redundant calculations detected in recursive or loop-heavy method")
                    .whyItMatters(
                        "Without caching, the same sub-problem may be computed many times. " +
                        "For recursive functions: memoization can reduce O(2^n) to O(n). " +
                        "For loop-heavy methods: caching loop-invariant expressions saves " +
                        "repeated computation.")
                    .howToFix(
                        "1. For recursion: add a Map<InputKey, Result> cache as a field or parameter. " +
                        "2. For loops: hoist invariant expressions outside the loop. " +
                        "3. Use @Cacheable (Spring) for service-level caching. " +
                        "4. Use Caffeine or Guava Cache for method-level result caching.")
                    .codeExample(
                        "// Memoized Fibonacci:\n" +
                        "private Map<Integer,Long> memo = new HashMap<>();\n" +
                        "long fib(int n) {\n" +
                        "    if (n <= 1) return n;\n" +
                        "    return memo.computeIfAbsent(n, k -> fib(k-1) + fib(k-2));\n" +
                        "}")
                    .priority(OptimizationSuggestion.Priority.HIGH)
                    .category(OptimizationSuggestion.Category.CACHING)
                    .estimatedEeiGain(25)
                    .build());
        }

        // ── Rule 8: Refactor highly central inefficient methods ────────────
        if (fanIn > 5 && eei < 60) {
            suggestions.add(OptimizationSuggestion.builder()
                    .title("Priority Refactor: High-Impact Central Method")
                    .issue(String.format(
                        "Method is called by %d other methods (fan-in=%d) with EEI=%.1f",
                        fanIn, fanIn, eei))
                    .whyItMatters(
                        "This method is called by " + fanIn + " other methods. Any inefficiency " +
                        "in this method is multiplied across every call site in the system. " +
                        "Optimizing this method will have the highest system-wide EEI improvement.")
                    .howToFix(
                        "1. This method should be the FIRST optimization priority given its centrality. " +
                        "2. Consider caching its results if inputs repeat across call sites. " +
                        "3. Optimize the core algorithm first, as improvements propagate to all " + fanIn + " callers. " +
                        "4. Add performance tests to track improvement.")
                    .priority(OptimizationSuggestion.Priority.HIGH)
                    .category(OptimizationSuggestion.Category.REFACTORING)
                    .estimatedEeiGain(15)
                    .build());
        }

        return suggestions;
    }

    private String formatCost(ComplexityClass c) {
        return switch (c) {
            case O_N2 -> "1,000,000";
            case O_N3 -> "1,000,000,000";
            case O_2N -> "2^1000 (astronomically large)";
            default -> "n";
        };
    }
}
