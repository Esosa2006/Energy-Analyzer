package com.energyanalyzer.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single method node in the static call graph.
 *
 * The call graph is a directed graph where:
 *   - Nodes = methods
 *   - Edges = "method A calls method B"
 *
 * This class captures both the node data and its graph relationships.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MethodNode {

    /** Unique identifier: ClassName#methodName(paramTypes) */
    private String id;

    /** Simple method name */
    private String methodName;

    /** Owning class name */
    private String className;

    /** Methods that THIS method calls (outgoing edges) */
    @Builder.Default
    private List<String> callees = new ArrayList<>();

    /** Methods that call THIS method (incoming edges) */
    @Builder.Default
    private List<String> callers = new ArrayList<>();

    /**
     * Structural centrality score.
     *
     * Inspired by PageRank: a method is important if it's called
     * by many other important methods.
     *
     * Formula (simplified):
     *   centrality = (fan-in × fanInWeight) + (callChainDepth × depthWeight)
     *   normalized to 0.0–1.0
     *
     * This is a deterministic, rule-based approximation of graph centrality.
     */
    private double centralityScore;

    /**
     * Weight multiplier for EEI calculation.
     *
     * WeightMultiplier = 1.0 + (centralityScore × maxBoost)
     *
     * A method called by 20 others gets a higher weight than an isolated helper,
     * because inefficiency in central methods is amplified across the system.
     */
    private double weightMultiplier;

    /** Number of methods in the codebase that call this method */
    private int fanIn;

    /** Number of methods this method calls */
    private int fanOut;

    /** Maximum depth from this node through the call chain */
    private int callChainDepth;
}
