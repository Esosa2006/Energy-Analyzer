package com.energyanalyzer.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;

/**
 * Static call graph of the entire analyzed codebase.
 *
 * Represents the caller → callee relationship map between all methods.
 * Used to compute fan-in, fan-out, call chain depth, and centrality scores.
 *
 * Implementation uses an adjacency list representation for efficiency.
 */
@Data
@NoArgsConstructor
public class CallGraph {

    /** All method nodes, keyed by their unique ID */
    private Map<String, MethodNode> nodes = new LinkedHashMap<>();

    /** Adjacency list: caller ID → set of callee IDs */
    private Map<String, Set<String>> edges = new LinkedHashMap<>();

    /** Reverse adjacency: callee ID → set of caller IDs (for fan-in lookup) */
    private Map<String, Set<String>> reverseEdges = new LinkedHashMap<>();

    /** Add a method node to the graph */
    public void addNode(MethodNode node) {
        nodes.put(node.getId(), node);
        edges.putIfAbsent(node.getId(), new LinkedHashSet<>());
        reverseEdges.putIfAbsent(node.getId(), new LinkedHashSet<>());
    }

    /** Add a directed edge: caller calls callee */
    public void addEdge(String callerId, String calleeId) {
        edges.computeIfAbsent(callerId, k -> new LinkedHashSet<>()).add(calleeId);
        reverseEdges.computeIfAbsent(calleeId, k -> new LinkedHashSet<>()).add(callerId);
    }

    /** Get all callers of a given method */
    public Set<String> getCallers(String methodId) {
        return reverseEdges.getOrDefault(methodId, Collections.emptySet());
    }

    /** Get all callees of a given method */
    public Set<String> getCallees(String methodId) {
        return edges.getOrDefault(methodId, Collections.emptySet());
    }

    /** Total number of methods in the graph */
    public int size() {
        return nodes.size();
    }

    /** Total number of edges (call relationships) */
    public int edgeCount() {
        return edges.values().stream().mapToInt(Set::size).sum();
    }
}
