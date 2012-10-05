/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.tele.reference.gen.semispace;

import static com.sun.max.tele.object.ObjectStatus.*;

import com.sun.max.tele.heap.*;
import com.sun.max.tele.object.*;
import com.sun.max.tele.reference.*;
import com.sun.max.tele.util.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.heap.*;

/**
 * Representation of a remote object reference in a generational heap with a non-aging nursery and a semi-space old generation.
 * A young generation collection evacuates objects from the young generation into the to-space of the old generation using
 * a copying mechanism that leaves forwarders in the evacuated objects.
 * The to-space of the old generation is almost never empty.
 * An old generation collection occurs only when the young generation is empty (i.e., all live young objects have been evacuated),
 * and proceeds in a classic semi-space fashion: the to and from space swap,
 * and live objects from the from-space are evacuated in the to-space using
 * a copying mechanism that leaves forwarders in the evacuated objects.
 * Complications in the model arise because of the of potential overflow situations:
 * a young generation collection may overflow into the from space.
 * a old generation collection may overflow into the young space.
 */
public class GenSSRemoteReference extends RemoteReference {
    /**
     * The origin of an object.
     * During mutating phases and analysis phase of minor collection, a reference may map to an address in the non-aging nursery (young generation) or the to space of the old generation.
     * After a minor collection, references can only map to the to space of the old generation.
     * During analysis of a old collection, a reference may map to an address in to space of the old generation only.
     */
    private Address origin;
    /**
     * An additional address the reference may relate to. This is used to keep track of forwarder/forwardee relationship during object relocation to ease debugging of moving collectors.
     * Only forwarders or forwarded objects have an alternate origin.
     */
    private Address alternateOrigin;
    /**
     * State of an object reference. The state indicates where the object is located and its liveness status,
     */
    private RefState refState = null;

    private ObjectStatus priorStatus = null;

    private final RemoteGenSSHeapScheme remoteScheme;

    /**
     * An enumeration of possible states of a remote reference for this kind of collector, based on the heap phase and
     * what is known at any given time.
     * <p>
     * Each member encapsulates the <em>behavior</em> associated with a state, including both the interpretation of
     * the data held by the reference and by allowable state transitions.
     */
    private static enum RefState {
        /**
         * Live young reference.
         * Address is in the allocated part of the young generation.
         * Valid only during the {@link HeapPhase#MUTATING} phase.
         */
        YOUNG_REF_LIVE("LIVE(young)") {
            // Properties
            @Override
            ObjectStatus status() {
                return LIVE;
            }
            @Override
            Address origin(GenSSRemoteReference ref) {
                return ref.origin;
            }
            @Override
            Address forwardedFrom(GenSSRemoteReference ref) {
                return Address.zero();
            }
            @Override
            Address forwardedTo(GenSSRemoteReference ref) {
                return Address.zero();
            }
            @Override
            void analysisBegins(GenSSRemoteReference ref, boolean minorCollection) {
                if (minorCollection) {
                    ref.refState = YOUNG_REF_FROM;
                    return;
                }
                TeleError.unexpected("Illegal state transition");
            }
       },
        /**
         * Young reference, not forwarded.
         * Address is in the allocated part of the young generation.
         * Valid only during the {@link HeapPhase#ANALYZING} phase.
         * This is the state {@link #YOUNG_REF_LIVE} changes to when a transition {@link HeapPhase#MUTATING}
         * to {@link HeapPhase#ANALYZING} is detected.
         */
        YOUNG_REF_FROM("LIVE(Analyzing: young, !forwarder)") {

            @Override
            ObjectStatus status() {
                return LIVE;
            }

            @Override
            Address origin(GenSSRemoteReference ref) {
                return ref.origin;
            }

            @Override
            Address forwardedFrom(GenSSRemoteReference ref) {
                return Address.zero();
            }

            @Override
            Address forwardedTo(GenSSRemoteReference ref) {
                return Address.zero();
            }
            @Override
            void analysisEnds(GenSSRemoteReference ref, boolean minorCollection) {
                if (minorCollection) {
                    ref.refState = REF_DEAD;
                    return;
                }
                TeleError.unexpected("Illegal state transition");
            }

            /**
             * The reference turns into a PROMOTED_REF. A forwarder will be created separately.
             */
            @Override
            void discoverForwarded(GenSSRemoteReference ref, Address forwardedOrigin, boolean minorCollection) {
                if (minorCollection) {
                    // FIXME: should also check the current heap phase!
                    ref.alternateOrigin = ref.origin; // the origin now becomes an alternate as it points to the forwarder
                    ref.origin = forwardedOrigin; // the reference now points to the forwarded address
                    ref.refState = PROMOTED_REF;
                    return;
                }
                TeleError.unexpected("Illegal state transition");
            }
        },

        /**
         * Reference to a young forwarding object.
         * Address is in the young generation, and its header comprises a forwarder to the old generation.
         * A reference to the forwarded object must be in the @link {@link #PROMOTED_REF} state.
         */
        YOUNG_FORWARDER("FORWARDER(Quasi object, only during minor collection analyzing") {
            @Override
            ObjectStatus status() {
                return FORWARDER;
            }

            @Override
            Address origin(GenSSRemoteReference ref) {
                return ref.origin;
            }

            @Override
            Address forwardedFrom(GenSSRemoteReference ref) {
                return Address.zero();
            }

            @Override
            Address forwardedTo(GenSSRemoteReference ref) {
                return ref.alternateOrigin;
            }

            @Override
            void analysisEnds(GenSSRemoteReference ref, boolean minorCollection) {
                ref.alternateOrigin = Address.zero();
                ref.refState = REF_DEAD;
            }
        },

        /**
         * Reference to an object promoted from the young generation to the old generation
         * whose forwarder is unknown.
         * Address in the old to-space, or in the from space if a minor evacuation overflow occurred.
         */
        PROMOTED_UNKNOWN_FORWARDER_REF("LIVE(Analyzing: promoted, young forwarder unknown)") {

            @Override
            ObjectStatus status() {
                return LIVE;
            }

            @Override
            Address origin(GenSSRemoteReference ref) {
                return ref.origin;
            }

            @Override
            Address forwardedFrom(GenSSRemoteReference ref) {
                return Address.zero();
            }
            @Override
            Address forwardedTo(GenSSRemoteReference ref) {
                return Address.zero();
            }

            @Override
            void analysisEnds(GenSSRemoteReference ref, boolean minorCollection) {
                if (minorCollection) {
                    ref.refState = OLD_REF_LIVE;
                    return;
                }
                TeleError.unexpected("Illegal state transition");
            }
            @Override
            void discoverForwarder(GenSSRemoteReference ref, Address fromOrigin, boolean minorCollection) {
                if (minorCollection) {
                    ref.alternateOrigin = fromOrigin;
                    ref.refState = PROMOTED_REF;
                    return;
                }
                TeleError.unexpected("Illegal state transition");
            }
        },

        /**
         * Reference to an object promoted from the young generation to the old generation whose forwarder in young space is known.
         * Address is in the old to-space.
         * There must be a reference in the {@link #YOUNG_FORWARDER} for a forwarding object in the young generation.
         */
        PROMOTED_REF("LIVE(Analyzing: promoted, young forwarder known)") {

            @Override
            ObjectStatus status() {
                return LIVE;
            }

            @Override
            Address origin(GenSSRemoteReference ref) {
                return ref.origin;
            }

            @Override
            Address forwardedFrom(GenSSRemoteReference ref) {
                return ref.alternateOrigin;
            }

            @Override
            Address forwardedTo(GenSSRemoteReference ref) {
                return Address.zero();
            }
            @Override
            void analysisEnds(GenSSRemoteReference ref, boolean minorCollection) {
                if (minorCollection) {
                    ref.alternateOrigin = Address.zero();
                    ref.refState = OLD_REF_LIVE;
                    return;
                }
                TeleError.unexpected("Illegal state transition");
            }
            @Override
            RefState forwarderState() {
                return YOUNG_FORWARDER;
            }
        },


        /**
         * Reference to an old forwarding object.
         * The forwarded may be known, in which case, it has a {@link #OLD_SURVIVOR_REF} reference.
         * Address must be in from-space.
         * State is valid only during  {@link HeapPhase#ANALYZING} of old generation collections.
         */
        OLD_FORWARDER("FORWARDER(Quasi object, old collection analyzing only") {
            @Override
            ObjectStatus status() {
                return FORWARDER;
            }

            @Override
            Address origin(GenSSRemoteReference ref) {
                return ref.origin;
            }

            @Override
            Address forwardedFrom(GenSSRemoteReference ref) {
                return Address.zero();
            }

            @Override
            Address forwardedTo(GenSSRemoteReference ref) {
                return ref.alternateOrigin;
            }

            @Override
            void analysisEnds(GenSSRemoteReference ref, boolean minorCollection) {
                ref.alternateOrigin = Address.zero();
                ref.refState = REF_DEAD;
            }

        },

        /**
         * Live old object.
         * Valid during the {@link HeapPhase#MUTATING}, and {@link HeapPhase#ANALYZING} of minor collections.
         */
        OLD_REF_LIVE("LIVE(old)") {

            @Override
            ObjectStatus status() {
                return LIVE;
            }

            @Override
            Address origin(GenSSRemoteReference ref) {
                return ref.origin;
            }

            @Override
            Address forwardedFrom(GenSSRemoteReference ref) {
                return Address.zero();
            }

            @Override
            Address forwardedTo(GenSSRemoteReference ref) {
                return Address.zero();
            }
            @Override
            void analysisBegins(GenSSRemoteReference ref, boolean minorCollection) {
                if (minorCollection) {
                    // nothing to do.
                    return;
                }
                ref.refState = OLD_REF_FROM;
            }
        },

        /**
         * Old reference not forwarded.
         * Address is in the allocated part of the old from-space.
         * Valid only during the {@link HeapPhase#ANALYZING} phase of old generation collections.
         * This is the state {@link #OLD_REF_LIVE} references change to when a transition {@link HeapPhase#MUTATING}
         * to {@link HeapPhase#ANALYZING} for an old generation collection is detected.
         */
        OLD_REF_FROM("LIVE(Analyzing: old from-only, !forwarder)") {
            @Override
            ObjectStatus status() {
                return LIVE;
            }

            @Override
            Address forwardedTo(GenSSRemoteReference ref) {
                return Address.zero();
            }
            @Override
            Address origin(GenSSRemoteReference ref) {
                return ref.origin;
            }

            @Override
            Address forwardedFrom(GenSSRemoteReference ref) {
                return Address.zero();
            }

            @Override
            void analysisEnds(GenSSRemoteReference ref, boolean minorCollection) {
                if (!minorCollection) {
                    ref.refState = REF_DEAD;
                    return;
                }
                TeleError.unexpected("Illegal state transition");
            }

            @Override
            void discoverForwarded(GenSSRemoteReference ref, Address toOrigin, boolean minorCollection) {
                if (!minorCollection) {
                    ref.alternateOrigin = ref.origin;
                    ref.origin = toOrigin;
                    ref.refState = OLD_SURVIVOR_REF;
                    return;
                }
                TeleError.unexpected("Illegal state transition");
            }

        },

        /**
         * Survivor of old collection.
         * Forwarder is not known.
         * Address is in survivor range.
         * State is only valid during the {@link HeapPhase#ANALYZING} of an old generation collection.
         * Reference is created in this state when a reference in to-space is discovered
         * during the analyzing phase of an old collection.
         */
        OLD_SURVIVOR_UNKNOWN_FORWARDER_REF("LIVE (Analyzing: old survivor, forwarder unknown)") {
            @Override
            ObjectStatus status() {
                return LIVE;
            }

            @Override
            Address origin(GenSSRemoteReference ref) {
                return ref.origin;
            }

            @Override
            Address forwardedFrom(GenSSRemoteReference ref) {
                return Address.zero();
            }

            @Override
            Address forwardedTo(GenSSRemoteReference ref) {
                return Address.zero();
            }

            @Override
            void analysisEnds(GenSSRemoteReference ref, boolean minorCollection) {
                if (!minorCollection) {
                    assert ref.alternateOrigin == Address.zero();
                    ref.refState = ref.remoteScheme.isInOverflowArea(ref.origin)  ? YOUNG_REF_LIVE : OLD_REF_LIVE;
                    return;
                }
                TeleError.unexpected("Illegal state transition");
            }
            @Override
            void discoverForwarder(GenSSRemoteReference ref, Address forwarderOrigin, boolean minorCollection) {
                if (!minorCollection) {
                    ref.alternateOrigin = forwarderOrigin;
                    ref.refState = OLD_SURVIVOR_REF;
                    return;
                }
                TeleError.unexpected("Illegal state transition");
            }
       },

       /**
        * Survivor of old collection.
        * Forwarder is known (there must be an OLD_FORWARDER in the old-from map).
        * Address is in survivor range.
        * Reference is in old-to map.
        * State is only valid during the {@link HeapPhase#ANALYZING} of an old generation collection.
        */
       OLD_SURVIVOR_REF("LIVE (Analyzing: old survivor, forwarder known)") {
            @Override
            ObjectStatus status() {
                return LIVE;
            }

            @Override
            Address origin(GenSSRemoteReference ref) {
                return ref.origin;
            }

            @Override
            Address forwardedFrom(GenSSRemoteReference ref) {
                return ref.alternateOrigin;
            }

            @Override
            Address forwardedTo(GenSSRemoteReference ref) {
                return Address.zero();
            }
            @Override
            void analysisEnds(GenSSRemoteReference ref, boolean minorCollection) {
                if (!minorCollection) {
                    ref.alternateOrigin = Address.zero();
                    ref.refState = ref.remoteScheme.isInOverflowArea(ref.origin)  ? YOUNG_REF_LIVE : OLD_REF_LIVE;
                    return;
                }
                TeleError.unexpected("Illegal state transition");
            }
            @Override
            RefState forwarderState() {
                return OLD_FORWARDER;
            }
        },

        REF_DEAD ("Dead") {
            // Properties
            @Override ObjectStatus status() {
                return DEAD;
            }
            @Override
            Address origin(GenSSRemoteReference ref) {
                return ref.origin;
            }
            @Override
            Address forwardedFrom(GenSSRemoteReference ref) {
                return Address.zero();
            }

            @Override
            Address forwardedTo(GenSSRemoteReference ref) {
                return Address.zero();
            }
        };

        protected final String label;

        RefState(String label) {
            this.label = label;
        }

        /**
         * @see RemoteReference#status()
         */
        abstract ObjectStatus status();

        /**
         * @see RemoteReference#origin()
         */
        abstract Address origin(GenSSRemoteReference ref);

        /**
         * @see RemoteReference#forwardedFrom()
         */
        abstract Address forwardedFrom(GenSSRemoteReference ref);

        /**
         * @see RemoteReference#forwardedTo()
         */
        abstract Address forwardedTo(GenSSRemoteReference ref);

        String gcDescription(GenSSRemoteReference ref) {
            return label;
        }

        /**
         * Apply state transition to a reference upon beginning of an analysis phase.
         * @param ref reference the state transition applies to
         * @param minorCollection true if the analysis phase if for a minor collection.
         */
        void analysisBegins(GenSSRemoteReference ref, boolean minorCollection) {
            TeleError.unexpected("Illegal state transition");
        }
        /**
         * Apply state transition to a reference upon end an analysis phase.
         *
         * @param ref reference the state transition applies to
         * @param minorCollection true if the analysis phase if for a minor collection.
         */
        void analysisEnds(GenSSRemoteReference ref, boolean minorCollection) {
            TeleError.unexpected("Illegal state transition");
        }

        /**
         * Notify the discovery of a forwarder for this reference, i.e., a forwarding reference is point this to this reference.
         * @param ref
         * @param forwarderOrigin the origin of the forwarder pointing to this reference
         * @param minorCollection true if the discovery took place at minor collection, false if during full collection.
         */
        void discoverForwarder(GenSSRemoteReference ref, Address forwarderOrigin, boolean minorCollection) {
            TeleError.unexpected("Illegal state transition");
        }

        /**
         * Notify the discovery of the forwarded reference for this reference, i.e., this reference must be a forwarding reference.
         *
         * @param ref
         * @param forwardedOrigin the origin of the object this reference forwards to.
         * @param minorCollection true if the discovery took place at minor collection, false if during full collection.
         */
        void discoverForwarded(GenSSRemoteReference ref, Address forwardedOrigin, boolean minorCollection) {
            TeleError.unexpected("Illegal state transition");
        }

        RefState forwarderState() {
            TeleError.unexpected("Reference state without forwarder");
            return null;
        }
    }

    protected GenSSRemoteReference(RemoteGenSSHeapScheme remoteScheme, Address origin, Address alternateOrigin) {
        super(remoteScheme.vm());
        this.origin = origin;
        this.alternateOrigin = alternateOrigin;
        this.remoteScheme = remoteScheme;
    }

    @Override
    public ObjectStatus status() {
        return refState.status();
    }

    @Override
    public ObjectStatus priorStatus() {
        return priorStatus;
    }

    @Override
    public Address origin() {
        return refState.origin(this);
    }

    @Override
    public Address forwardedFrom() {
        return refState.forwardedFrom(this);
    }

    @Override
    public Address forwardedTo() {
        return refState.forwardedTo(this);
    }

    @Override
    public String gcDescription() {
        return remoteScheme.heapSchemeClass().getSimpleName() + " state=" + refState.gcDescription(this);
    }

    public void beginAnalyzing(boolean minorCollection) {
        priorStatus = refState.status();
        refState.analysisBegins(this, minorCollection);
    }

    public void endAnalyzing(boolean minorCollection) {
        priorStatus = refState.status();
        refState.analysisEnds(this, minorCollection);
    }

    public void discoverForwarder(Address fromOrigin, boolean minorCollection) {
        priorStatus = refState.status();
        refState.discoverForwarder(this, fromOrigin, minorCollection);
    }

    public void discoverForwarded(Address toOrigin, boolean minorCollection) {
        priorStatus = refState.status();
        refState.discoverForwarded(this, toOrigin, minorCollection);
    }

    /**
     * Creates a reference to a live object, discovered when the heap is <em>not</em> {@link HeapPhase#ANALYZING}.
     * @param remoteScheme
     * @param toOrigin the physical location of the object in virtual memory.
     * @param isYoung true if the object is located in the nursery. Otherwise the object is in the old To-space.
     * @return a remote reference to an live object
     */
    public static GenSSRemoteReference createLive(RemoteGenSSHeapScheme remoteScheme, Address toOrigin, boolean isYoung) {
        TeleError.check(remoteScheme.canCreateLive());
        final GenSSRemoteReference ref = new GenSSRemoteReference(remoteScheme, toOrigin, Address.zero());
        ref.refState = isYoung ? RefState.YOUNG_REF_LIVE : RefState.OLD_REF_LIVE;
        return ref;
    }

    public static GenSSRemoteReference createOldTo(RemoteGenSSHeapScheme remoteScheme, Address toOrigin, boolean isPromoted) {
        final GenSSRemoteReference ref = new GenSSRemoteReference(remoteScheme, toOrigin, Address.zero());
        ref.refState = isPromoted ? RefState.PROMOTED_UNKNOWN_FORWARDER_REF : RefState.OLD_SURVIVOR_UNKNOWN_FORWARDER_REF;
        return ref;
    }

    public static GenSSRemoteReference createFromOnly(RemoteGenSSHeapScheme remoteScheme, Address fromOrigin, boolean isYoung) {
        final GenSSRemoteReference ref = new GenSSRemoteReference(remoteScheme, fromOrigin, Address.zero());
        ref.refState = isYoung ? RefState.YOUNG_REF_FROM : RefState.OLD_REF_FROM;
        return ref;
    }

    public static GenSSRemoteReference createFromTo(RemoteGenSSHeapScheme remoteScheme, Address fromOrigin, Address toOrigin, boolean isYoung) {
        final GenSSRemoteReference ref = new GenSSRemoteReference(remoteScheme, toOrigin, fromOrigin);
        ref.refState = isYoung ? RefState.PROMOTED_REF  : RefState.OLD_SURVIVOR_REF;
        return ref;
    }

    public static void checkNoLiveRef(WeakRemoteReferenceMap<GenSSRemoteReference> map, boolean isYoung) {
        final RefState prohibitedRefState =  isYoung ? RefState.YOUNG_REF_LIVE : RefState.OLD_REF_LIVE;
        for (GenSSRemoteReference ref : map.values()) {
            TeleError.check(ref.refState != prohibitedRefState);
        }
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append(refState.label);
        sb.append(" origin: ");
        sb.append(origin == null ? "<null>" : origin.to0xHexString());
        sb.append(" alt: ");
        sb.append(alternateOrigin == null ? "<null>" : alternateOrigin.to0xHexString());
        return sb.toString();
    }

    public static GenSSRemoteReference createForwarder(RemoteGenSSHeapScheme remoteScheme, GenSSRemoteReference forwardedRef) {
        final GenSSRemoteReference ref = new GenSSRemoteReference(remoteScheme, forwardedRef.alternateOrigin, forwardedRef.origin);
        ref.refState = forwardedRef.refState.forwarderState();
        return ref;
    }
}
