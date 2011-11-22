/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.max.graal.compiler.phases;

import java.util.*;

import com.oracle.max.criutils.*;
import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.graphbuilder.*;
import com.oracle.max.graal.compiler.phases.PhasePlan.PhasePosition;
import com.oracle.max.graal.compiler.util.*;
import com.oracle.max.graal.compiler.util.InliningUtil.InlineInfo;
import com.oracle.max.graal.compiler.util.InliningUtil.InliningCallback;
import com.oracle.max.graal.cri.*;
import com.oracle.max.graal.extensions.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;


public class InliningPhase extends Phase implements InliningCallback {
    /*
     * - Detect method which only call another method with some parameters set to constants: void foo(a) -> void foo(a, b) -> void foo(a, b, c) ...
     *   These should not be taken into account when determining inlining depth.
     * - honor the result of overrideInliningDecision(0, caller, invoke.bci, method, true);
     */

    private static final int MAX_ITERATIONS = 1000;

    private final CiTarget target;
    private final GraalRuntime runtime;

    private int inliningSize;
    private final Collection<Invoke> hints;

    private final PriorityQueue<InlineInfo> inlineCandidates = new PriorityQueue<InlineInfo>();
    private NodeMap<InlineInfo> inlineInfos;

    private StructuredGraph graph;
    private CiAssumptions assumptions;

    private final PhasePlan plan;

    public InliningPhase(CiTarget target, GraalRuntime runtime, Collection<Invoke> hints, CiAssumptions assumptions, PhasePlan plan) {
        this.target = target;
        this.runtime = runtime;
        this.hints = hints;
        this.assumptions = assumptions;
        this.plan = plan;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void run(StructuredGraph graph) {
        this.graph = graph;
        inlineInfos = graph.createNodeMap();

        if (hints != null) {
            Iterable<? extends Node> hints = Util.uncheckedCast(this.hints);
            scanInvokes(hints, 0);
        } else {
            scanInvokes(graph.getNodes(InvokeNode.class), 0);
            scanInvokes(graph.getNodes(InvokeWithExceptionNode.class), 0);
        }

        while (!inlineCandidates.isEmpty() && graph.getNodeCount() < GraalOptions.MaximumDesiredSize) {
            InlineInfo info = inlineCandidates.remove();
            double penalty = Math.pow(GraalOptions.InliningSizePenaltyExp, graph.getNodeCount() / (double) GraalOptions.MaximumDesiredSize) / GraalOptions.InliningSizePenaltyExp;
            if (info.weight > GraalOptions.MaximumInlineWeight / (1 + penalty * GraalOptions.InliningSizePenalty)) {
                if (GraalOptions.TraceInlining) {
                    TTY.println("not inlining (cut off by weight):");
                    while (info != null) {
                        TTY.println("    %f %s", info.weight, info);
                        info = inlineCandidates.poll();
                    }
                }
                return;
            }
            Iterable<Node> newNodes = null;
            if (info.invoke.node().isAlive()) {
                try {
                    info.inline(this.graph, runtime, this);
                    if (GraalOptions.TraceInlining) {
                        TTY.println("inlining %f: %s", info.weight, info);
                    }
                    if (GraalOptions.TraceInlining) {
                        context.observable.fireCompilationEvent("after inlining " + info, graph);
                    }
                    // get the new nodes here, the canonicalizer phase will reset the mark
                    newNodes = graph.getNewNodes();
                    new CanonicalizerPhase(target, runtime, true, assumptions).apply(graph);
                    new PhiSimplificationPhase().apply(graph, context);
                    if (GraalOptions.Intrinsify) {
                        new IntrinsificationPhase(runtime).apply(graph, context);
                    }
                    if (GraalOptions.Meter) {
                        context.metrics.InlinePerformed++;
                    }
                } catch (CiBailout bailout) {
                    // TODO determine if we should really bail out of the whole compilation.
                    throw bailout;
                } catch (AssertionError e) {
                    throw new GraalInternalError(e).addContext(info.toString());
                } catch (RuntimeException e) {
                    throw new GraalInternalError(e).addContext(info.toString());
                } catch (GraalInternalError e) {
                    throw e.addContext(info.toString());
                }
            }
            if (newNodes != null && info.level <= GraalOptions.MaximumInlineLevel) {
                scanInvokes(newNodes, info.level + 1);
            }
        }
    }

    private void scanInvokes(Iterable<? extends Node> newNodes, int level) {
        graph.mark();
        for (Node node : newNodes) {
            if (node != null) {
                if (node instanceof Invoke) {
                    Invoke invoke = (Invoke) node;
                    scanInvoke(invoke, level);
                }
                for (Node usage : node.usages().snapshot()) {
                    if (usage instanceof Invoke) {
                        Invoke invoke = (Invoke) usage;
                        scanInvoke(invoke, level);
                    }
                }
            }
        }
    }

    private void scanInvoke(Invoke invoke, int level) {
        InlineInfo info = InliningUtil.getInlineInfo(invoke, level, runtime, assumptions, this);
        if (info != null) {
            if (GraalOptions.Meter) {
                context.metrics.InlineConsidered++;
            }

            inlineCandidates.add(info);
        }
    }

    public static final Map<RiMethod, Integer> parsedMethods = new HashMap<RiMethod, Integer>();

    @Override
    public StructuredGraph buildGraph(RiResolvedMethod method) {
        StructuredGraph graph = new StructuredGraph();
        new GraphBuilderPhase(runtime, method).apply(graph, context, true, false);

        plan.runPhases(PhasePosition.AFTER_PARSING, graph, context);

        if (GraalOptions.ProbabilityAnalysis) {
            new DeadCodeEliminationPhase().apply(graph, context, true, false);
            new ComputeProbabilityPhase().apply(graph, context, true, false);
        }
        new CanonicalizerPhase(target, runtime, assumptions).apply(graph, context, true, false);
        return graph;
    }

    @Override
    public double inliningWeight(RiResolvedMethod caller, RiResolvedMethod method, Invoke invoke) {
        double ratio;
        if (hints != null && hints.contains(invoke)) {
            ratio = 1000000;
        } else {
            if (GraalOptions.ProbabilityAnalysis) {
                ratio = invoke.node().probability();
            } else {
                RiTypeProfile profile = caller.typeProfile(invoke.bci());
                if (profile != null && profile.count > 0) {
                    RiResolvedMethod parent = invoke.stateAfter().method();
                    ratio = profile.count / (float) parent.invocationCount();
                } else {
                    ratio = 1;
                }
            }
        }

        final double normalSize;
        // TODO(ls) get rid of this magic, it's here to emulate the old behavior for the time being
        if (ratio < 0.01) {
            ratio = 0.01;
        }
        if (ratio < 0.5) {
            normalSize = 10 * ratio / 0.5;
        } else if (ratio < 2) {
            normalSize = 10 + (35 - 10) * (ratio - 0.5) / 1.5;
        } else if (ratio < 20) {
            normalSize = 35;
        } else if (ratio < 40) {
            normalSize = 35 + (350 - 35) * (ratio - 20) / 20;
        } else {
            normalSize = 350;
        }

        int count;
        if (GraalOptions.ParseBeforeInlining) {
            if (!parsedMethods.containsKey(method)) {
                StructuredGraph graph = new StructuredGraph();
                new GraphBuilderPhase(runtime, method, null).apply(graph, context, true, false);
                new CanonicalizerPhase(target, runtime, assumptions).apply(graph, context, true, false);
                count = graphComplexity(graph);
                parsedMethods.put(method, count);
            } else {
                count = parsedMethods.get(method);
            }
        } else {
            count = method.codeSize();
        }

        return count / normalSize;
    }


    public static int graphComplexity(StructuredGraph graph) {
        int result = 0;
        for (Node node : graph.getNodes()) {
            if (node instanceof ConstantNode || node instanceof LocalNode || node instanceof BeginNode || node instanceof ReturnNode || node instanceof UnwindNode) {
                result += 0;
            } else if (node instanceof PhiNode) {
                result += 5;
            } else if (node instanceof MergeNode || node instanceof Invoke || node instanceof LoopEndNode || node instanceof EndNode) {
                result += 0;
            } else if (node instanceof ControlSplitNode) {
                result += ((ControlSplitNode) node).blockSuccessorCount();
            } else {
                result += 1;
            }
        }
//        ReturnNode ret = graph.getReturn();
//        if (ret != null && ret.result() != null) {
//            if (ret.result().kind() == CiKind.Object && ret.result().exactType() != null) {
//                result -= 5;
//            }
//        }
        return Math.max(1, result);
    }

    public static ThreadLocal<ServiceLoader<InliningGuide>> guideLoader = new ThreadLocal<ServiceLoader<InliningGuide>>();

    private boolean overrideInliningDecision(int iteration, RiMethod caller, int bci, RiMethod target, boolean previousDecision) {
        ServiceLoader<InliningGuide> serviceLoader = guideLoader.get();
        if (serviceLoader == null) {
            serviceLoader = ServiceLoader.load(InliningGuide.class);
            guideLoader.set(serviceLoader);
        }

        boolean neverInline = false;
        boolean alwaysInline = false;
        for (InliningGuide guide : serviceLoader) {
            InliningHint hint = guide.getHint(iteration, caller, bci, target);

            if (hint == InliningHint.ALWAYS) {
                alwaysInline = true;
            } else if (hint == InliningHint.NEVER) {
                neverInline = true;
            }
        }

        if (neverInline && alwaysInline) {
            if (GraalOptions.TraceInlining) {
                TTY.println("conflicting inlining hints");
            }
        } else if (neverInline) {
            return false;
        } else if (alwaysInline) {
            return true;
        }
        return previousDecision;
    }


    @Override
    public void recordConcreteMethodAssumption(RiResolvedMethod method, RiResolvedMethod concrete) {
        assumptions.recordConcreteMethod(method, concrete);
    }

}
