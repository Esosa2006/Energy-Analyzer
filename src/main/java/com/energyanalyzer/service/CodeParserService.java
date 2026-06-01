package com.energyanalyzer.service;

import com.energyanalyzer.model.StaticMetrics;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * CodeParserService
 *
 * Responsible for:
 *  1. Scanning a project directory/file for Java source files
 *  2. Parsing each file using JavaParser
 *  3. Extracting raw StaticMetrics for each method
 *
 * Uses visitor pattern (JavaParser VoidVisitorAdapter) to walk the AST.
 */
@Slf4j
@Service
public class CodeParserService {

    private final JavaParser javaParser = new JavaParser();

    /**
     * Parse all Java files in a directory or single file.
     * Returns a list of StaticMetrics, one per method found.
     *
     * @param path Path to .java file or directory containing Java sources
     * @return List of raw metrics for each method
     */
    public List<StaticMetrics> parseProject(Path path) throws IOException {
        List<StaticMetrics> results = new ArrayList<>();
        List<Path> javaFiles = collectJavaFiles(path);

        log.info("Found {} Java source files to analyze", javaFiles.size());

        for (Path javaFile : javaFiles) {
            try {
                results.addAll(parseFile(javaFile));
            } catch (Exception e) {
                log.warn("Failed to parse {}: {}", javaFile, e.getMessage());
            }
        }

        log.info("Extracted metrics from {} methods across {} files",
                results.size(), javaFiles.size());
        return results;
    }

    /**
     * Parse a single Java file and extract per-method metrics.
     */
    public List<StaticMetrics> parseFile(Path filePath) throws IOException {
        ParseResult<CompilationUnit> parseResult = javaParser.parse(filePath);
        if (!parseResult.isSuccessful() || parseResult.getResult().isEmpty()) {
            log.warn("Parse failed or empty result for: {}", filePath);
            return Collections.emptyList();
        }

        CompilationUnit cu = parseResult.getResult().get();
        List<StaticMetrics> metrics = new ArrayList<>();

        // Visit every class and extract method metrics
        cu.findAll(MethodDeclaration.class).forEach(method -> {
            String className = method.findAncestor(ClassOrInterfaceDeclaration.class)
                    .map(c -> c.getNameAsString())
                    .orElse("Unknown");

            StaticMetrics m = extractMethodMetrics(method, className, filePath.toString());
            metrics.add(m);
        });

        return metrics;
    }

    /**
     * Recursively collect all .java files under a given path.
     */
    private List<Path> collectJavaFiles(Path root) throws IOException {
        if (!Files.exists(root)) {
            throw new IOException("Path does not exist: " + root);
        }
        if (Files.isRegularFile(root)) {
            return root.toString().endsWith(".java")
                    ? List.of(root)
                    : Collections.emptyList();
        }

        List<Path> files = new ArrayList<>();
        Files.walk(root)
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> !p.toString().contains("test")) // optionally skip test files
                .forEach(files::add);
        return files;
    }

    // ── Core Metric Extraction ────────────────────────────────────────────────

    private StaticMetrics extractMethodMetrics(MethodDeclaration method,
                                               String className,
                                               String sourceFile) {
        String methodName = method.getNameAsString();
        int startLine = method.getBegin().map(p -> p.line).orElse(0);
        int endLine = method.getEnd().map(p -> p.line).orElse(startLine);
        int linesOfCode = Math.max(1, endLine - startLine + 1);

        return StaticMetrics.builder()
                .methodName(methodName)
                .className(className)
                .sourceFile(sourceFile)
                .startLine(startLine)
                .linesOfCode(linesOfCode)
                .cyclomaticComplexity(computeCyclomaticComplexity(method))
                .loopCount(countLoops(method))
                .nestedLoopDepth(computeMaxNestedLoopDepth(method))
                .recursive(isRecursive(method))
                .stringConcatenationCount(countStringConcatenation(method))
                .objectCreationCount(countObjectCreations(method))
                .ioOperationCount(countIoOperations(method))
                .methodCallCount(countMethodCalls(method))
                // fan-in/fan-out/callChainDepth are populated later by CallGraphService
                .fanIn(0)
                .fanOut(0)
                .callChainDepth(0)
                .build();
    }

    /**
     * Cyclomatic Complexity = 1 + number of decision points.
     *
     * Decision points: if, else-if, for, while, do-while, case (switch),
     * catch, &&, || (conditional operators).
     *
     * Reference: McCabe, T. (1976). "A Complexity Measure"
     */
    private int computeCyclomaticComplexity(MethodDeclaration method) {
        int cc = 1; // Base complexity

        cc += method.findAll(IfStmt.class).size();
        cc += method.findAll(ForStmt.class).size();
        cc += method.findAll(ForEachStmt.class).size();
        cc += method.findAll(WhileStmt.class).size();
        cc += method.findAll(DoStmt.class).size();
        cc += method.findAll(SwitchEntry.class).size();
        cc += method.findAll(CatchClause.class).size();

        // Count logical operators (each && or || creates a branch)
        cc += method.findAll(BinaryExpr.class).stream()
                .filter(b -> b.getOperator() == BinaryExpr.Operator.AND
                          || b.getOperator() == BinaryExpr.Operator.OR)
                .count();

        return cc;
    }

    /**
     * Count all loop constructs in the method.
     */
    private int countLoops(MethodDeclaration method) {
        return method.findAll(ForStmt.class).size()
             + method.findAll(ForEachStmt.class).size()
             + method.findAll(WhileStmt.class).size()
             + method.findAll(DoStmt.class).size();
    }

    /**
     * Compute maximum nesting depth of loops.
     *
     * Nested loops are a primary indicator of polynomial complexity:
     *   depth 1 → O(n), depth 2 → O(n²), depth 3 → O(n³)
     *
     * Uses recursive AST traversal to find the deepest loop nesting.
     */
    private int computeMaxNestedLoopDepth(MethodDeclaration method) {
        NestedLoopDepthVisitor visitor = new NestedLoopDepthVisitor();
        method.accept(visitor, null);
        return visitor.maxDepth;
    }

    /**
     * Detect direct recursion: method calls itself by name.
     *
     * Note: this detects DIRECT recursion only.
     * Indirect/mutual recursion would require inter-procedural analysis.
     */
    private boolean isRecursive(MethodDeclaration method) {
        String name = method.getNameAsString();
        return method.findAll(MethodCallExpr.class).stream()
                .anyMatch(call -> call.getNameAsString().equals(name));
    }

    /**
     * Count String concatenation using the '+' operator.
     *
     * Each 'str + other' in a loop creates a new String object.
     * In a loop of n iterations: O(n²) character copies.
     */
    private int countStringConcatenation(MethodDeclaration method) {
        return (int) method.findAll(BinaryExpr.class).stream()
                .filter(b -> b.getOperator() == BinaryExpr.Operator.PLUS)
                .filter(b -> {
                    // Heuristic: one operand involves a String-typed variable name
                    String left = b.getLeft().toString();
                    String right = b.getRight().toString();
                    return left.contains("str") || right.contains("str")
                        || left.startsWith("\"") || right.startsWith("\"")
                        || left.contains("String") || right.contains("String")
                        || left.contains("result") || left.contains("output")
                        || left.contains("message") || left.contains("text");
                })
                .count();
    }

    /**
     * Count object creation expressions (new Foo(), new ArrayList(), etc.)
     */
    private int countObjectCreations(MethodDeclaration method) {
        return method.findAll(ObjectCreationExpr.class).size();
    }

    /**
     * Count I/O operations.
     *
     * Heuristic: look for common I/O method call patterns in the AST.
     * Includes: File I/O, Stream I/O, System.out, logger calls, JDBC.
     */
    private int countIoOperations(MethodDeclaration method) {
        Set<String> ioIndicators = Set.of(
            "read", "write", "open", "close", "flush", "getConnection",
            "executeQuery", "executeUpdate", "prepareStatement",
            "println", "print", "printf", "format",
            "readLine", "readAllBytes", "readAllLines",
            "newInputStream", "newOutputStream", "newBufferedReader",
            "createFile", "delete", "copy", "move", "connect", "send", "receive"
        );

        return (int) method.findAll(MethodCallExpr.class).stream()
                .filter(call -> ioIndicators.contains(call.getNameAsString()))
                .count();
    }

    /**
     * Count total method calls within this method.
     */
    private int countMethodCalls(MethodDeclaration method) {
        return method.findAll(MethodCallExpr.class).size();
    }

    // ── Inner Visitor: Nested Loop Depth ──────────────────────────────────────

    /**
     * AST visitor to compute maximum nested loop depth.
     *
     * Tracks current depth as it enters/exits loop nodes.
     */
    private static class NestedLoopDepthVisitor extends VoidVisitorAdapter<Void> {
        int currentDepth = 0;
        int maxDepth = 0;

        @Override
        public void visit(ForStmt n, Void arg) {
            enterLoop();
            super.visit(n, arg);
            exitLoop();
        }

        @Override
        public void visit(ForEachStmt n, Void arg) {
            enterLoop();
            super.visit(n, arg);
            exitLoop();
        }

        @Override
        public void visit(WhileStmt n, Void arg) {
            enterLoop();
            super.visit(n, arg);
            exitLoop();
        }

        @Override
        public void visit(DoStmt n, Void arg) {
            enterLoop();
            super.visit(n, arg);
            exitLoop();
        }

        private void enterLoop() {
            currentDepth++;
            maxDepth = Math.max(maxDepth, currentDepth);
        }

        private void exitLoop() {
            currentDepth--;
        }
    }
}
