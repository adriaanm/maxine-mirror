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
package com.sun.max.tele.object;

import java.util.*;

import com.sun.max.jdwp.vm.proxy.*;
import com.sun.max.jdwp.vm.proxy.VMValue.Type;
import com.sun.max.tele.*;
import com.sun.max.tele.reference.*;
import com.sun.max.tele.util.*;
import com.sun.max.tele.value.*;
import com.sun.max.vm.actor.*;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.type.*;

/**
 * Canonical surrogate for a  {@link ClassActor} in the VM.
 */
public abstract class TeleClassActor extends TeleActor implements ReferenceTypeProvider {

    private ClassActor classActor;

    private boolean initialized = false;

    // Fields are final; cache them.
    private TeleClassLoader teleClassLoader;

    private int id;

    private TeleTypeDescriptor teleTypeDescriptor;

    protected TeleClassActor(TeleVM vm, RemoteReference classActorReference) {
        super(vm, classActorReference);
    }

    @Override
    public final Actor actor() {
        if (classActor == null) {
            classActor = classes().makeClassActor(reference());
        }
        return classActor;
    }

    /**
     * @return Local {@link ClassActor} corresponding the {@link ClassActor} in the VM.
     */
    public final ClassActor classActor() {
        return (ClassActor) actor();
    }

    private void initialize() {
        if (!initialized) {
            final RemoteReference classLoaderReference = fields().ClassActor_classLoader.readRemoteReference(reference());
            teleClassLoader = (TeleClassLoader) objects().makeTeleObject(classLoaderReference);

            id = fields().ClassActor_id.readInt(reference());

            final RemoteReference typeDescriptorReference = fields().ClassActor_typeDescriptor.readRemoteReference(reference());
            teleTypeDescriptor = (TeleTypeDescriptor) objects().makeTeleObject(typeDescriptorReference);

            initialized = true;
        }
    }

    /**
     * @return surrogate for the {@link ClassLoader} in the VM for this class in the VM.
     * There is no local counterpart for these; all local surrogate objects are created by one
     * ClassLoader.
     */
    public TeleClassLoader getTeleClassLoader() {
        initialize();
        return teleClassLoader;
    }

    /**
     * @return the serial ID of the {@link ClassActor} in the VM.
     */
    public int getId() {
        initialize();
        return id;
    }

    /**
     * @return surrogate for the {@link TypeDescriptor} in theVM for this class in the VM.
     */
    public TeleTypeDescriptor getTeleTypeDescriptor() {
        initialize();
        return teleTypeDescriptor;
    }

//    @Override
//    protected Object createDeepCopy(DeepCopier context) {
//        // Translate into local equivalent
//        return classActor();
//    }

    public TeleClass getTeleClass() {
        final RemoteReference reference = fields().ClassActor_javaClass.readRemoteReference(reference());
        if (reference.isZero()) {
            // TODO: assert that this class is the object class!
            return null;
        }
        return (TeleClass) objects().makeTeleObject(reference);
    }

    /**
     * @return surrogate for the {@link StaticTuple} of the {@link ClassActor} in the VM.
     */
    public TeleStaticTuple getTeleStaticTuple() {
        final RemoteReference staticTupleReference = fields().ClassActor_staticTuple.readRemoteReference(reference());
        return (TeleStaticTuple) objects().makeTeleObject(staticTupleReference);
    }

    /**
     * @return surrogate for the {@link DynamicHub} of the {@link ClassActor} in the VM.
     */
    public TeleDynamicHub getTeleDynamicHub() {
        final RemoteReference dynamicHubReference = fields().ClassActor_dynamicHub.readRemoteReference(reference());
        return (TeleDynamicHub) objects().makeTeleObject(dynamicHubReference);
    }

    /**
     * @return surrogate for the {@link StaticHub} of the {@link ClassActor} in the VM.
     */
    public TeleStaticHub getTeleStaticHub() {
        final RemoteReference staticHubReference = fields().ClassActor_staticHub.readRemoteReference(reference());
        return (TeleStaticHub) objects().makeTeleObject(staticHubReference);
    }

    /**
     * @return surrogate for the {@link ClassActor} in the VM for the type of the components of
     * this array type, null if not an array type
     */
    public TeleClassActor getTeleComponentClassActor() {
        final RemoteReference componentClassActorReference = fields().ClassActor_componentClassActor.readRemoteReference(reference());
        return (TeleClassActor) objects().makeTeleObject(componentClassActorReference);
    }

    /**
     * @return Reference to the constants in the VM's constant pool for the class, null if an array type
     */
    public TeleConstantPool getTeleConstantPool() {
        if (classActor().isTupleClass()) {
            return (TeleConstantPool) objects().makeTeleObject(fields().TupleClassActor_constantPool.readRemoteReference(reference()));
        } else if (classActor().isHybridClass()) {
            return (TeleConstantPool) objects().makeTeleObject(fields().HybridClassActor_constantPool.readRemoteReference(reference()));
        }
        return null;
    }

    private List<TeleFieldActor> readTeleStaticFieldActors() {
        final List<TeleFieldActor> teleStaticFieldActors = new LinkedList<TeleFieldActor>();
        final RemoteReference staticFieldActorsArrayRef = fields().ClassActor_localStaticFieldActors.readRemoteReference(reference());
        final TeleArrayObject teleStaticFieldActorsArray = (TeleArrayObject) objects().makeTeleObject(staticFieldActorsArrayRef);
        for (int index = 0; index < teleStaticFieldActorsArray.length(); index++) {
            final TeleFieldActor teleStaticFieldActor = (TeleFieldActor) objects().makeTeleObject(teleStaticFieldActorsArray.readRemoteReference(index));
            teleStaticFieldActors.add(teleStaticFieldActor);
        }
        return teleStaticFieldActors;
    }

    /**
     * @return surrogates for the local (not inherited) static {@link FieldActor}s associated with the class in the VM.
     */
    public List<TeleFieldActor> getTeleStaticFieldActors() {
        return readTeleStaticFieldActors();
    }

    private List<TeleFieldActor> readTeleInstanceFieldActors() {
        final List<TeleFieldActor> teleInstanceFieldActors = new LinkedList<TeleFieldActor>();
        final RemoteReference instanceFieldActorsArrayRef = fields().ClassActor_localInstanceFieldActors.readRemoteReference(reference());
        final TeleArrayObject teleInstanceFieldActorsArray = (TeleArrayObject) objects().makeTeleObject(instanceFieldActorsArrayRef);
        for (int index = 0; index < teleInstanceFieldActorsArray.length(); index++) {
            final TeleFieldActor teleInstanceFieldActor = (TeleFieldActor) objects().makeTeleObject(teleInstanceFieldActorsArray.readRemoteReference(index));
            teleInstanceFieldActors.add(teleInstanceFieldActor);
        }
        return teleInstanceFieldActors;
    }

    /**
     * @return surrogates for the local (not inherited) instance {@link FieldActor}s associated with the class in the VM.
     */
    public List<TeleFieldActor> getTeleInstanceFieldActors() {
        return readTeleInstanceFieldActors();
    }

    /**
     * @return surrogates for the local (not inherited) {@link FieldActor}s associated with the class in the VM: both static and instance fields.
     */
    public List<TeleFieldActor> getTeleFieldActors() {
        final List<TeleFieldActor> teleFieldActors = new LinkedList<TeleFieldActor>();
        teleFieldActors.addAll(readTeleStaticFieldActors());
        teleFieldActors.addAll(readTeleInstanceFieldActors());
        return teleFieldActors;
    }

    private List<TeleInterfaceMethodActor> readTeleInterfaceMethodActors() {
        final List<TeleInterfaceMethodActor> teleInterfaceMethodActors = new LinkedList<TeleInterfaceMethodActor>();
        final RemoteReference interfaceMethodActorsArrayRef = fields().ClassActor_localInterfaceMethodActors.readRemoteReference(reference());
        final TeleArrayObject teleInterfaceMethodActorsArray = (TeleArrayObject) objects().makeTeleObject(interfaceMethodActorsArrayRef);
        for (int index = 0; index < teleInterfaceMethodActorsArray.length(); index++) {
            final TeleInterfaceMethodActor teleInterfaceMethodActor = (TeleInterfaceMethodActor) objects().makeTeleObject(teleInterfaceMethodActorsArray.readRemoteReference(index));
            teleInterfaceMethodActors.add(teleInterfaceMethodActor);
        }
        return teleInterfaceMethodActors;
    }

    /**
     * @return surrogates for the local (not inherited) {@link InterfaceMethodActor}s associated with the class in the VM.
     */
    public List<TeleInterfaceMethodActor> getTeleInterfaceMethodActors() {
        return readTeleInterfaceMethodActors();
    }

    private List<TeleStaticMethodActor> readTeleStaticMethodActors() {
        final List<TeleStaticMethodActor> teleStaticMethodActors = new LinkedList<TeleStaticMethodActor>();
        final RemoteReference staticMethodActorsArrayRef = fields().ClassActor_localStaticMethodActors.readRemoteReference(reference());
        final TeleArrayObject teleStaticMethodActorsArray = (TeleArrayObject) objects().makeTeleObject(staticMethodActorsArrayRef);
        TeleError.check(teleStaticMethodActorsArray != null, "Can't find static methd actors array for " + classActor());
        for (int index = 0; index < teleStaticMethodActorsArray.length(); index++) {
            final TeleStaticMethodActor teleStaticMethodActor = (TeleStaticMethodActor) objects().makeTeleObject(teleStaticMethodActorsArray.readRemoteReference(index));
            teleStaticMethodActors.add(teleStaticMethodActor);
        }
        return teleStaticMethodActors;
    }

    /**
     * @return surrogates for the local (not inherited) {@link StaticMethodActor}s associated with the class in the VM.
     */
    public List<TeleStaticMethodActor> getTeleStaticMethodActors() {
        return readTeleStaticMethodActors();
    }

    private List<TeleVirtualMethodActor> readTeleVirtualMethodActors() {
        final List<TeleVirtualMethodActor> tleVirtualMethodActors = new LinkedList<TeleVirtualMethodActor>();
        final RemoteReference virtualMethodActorsArrayRef = fields().ClassActor_localVirtualMethodActors.readRemoteReference(reference());
        final TeleArrayObject teleVirtualMethodActorsArray = (TeleArrayObject) objects().makeTeleObject(virtualMethodActorsArrayRef);
        for (int index = 0; index < teleVirtualMethodActorsArray.length(); index++) {
            final TeleVirtualMethodActor teleVirtualMethodActor = (TeleVirtualMethodActor) objects().makeTeleObject(teleVirtualMethodActorsArray.readRemoteReference(index));
            tleVirtualMethodActors.add(teleVirtualMethodActor);
        }
        return tleVirtualMethodActors;
    }

    /**
     * @return surrogates for the local (not inherited) {@link VirtualMethodActor}s associated with the class in the VM.
     */
    public List<TeleVirtualMethodActor> getTeleVirtualMethodActors() {
        return readTeleVirtualMethodActors();
    }

    /**
     * @return surrogates for the local (not inherited) {@link ClassMethodActor}s associated with the class in the VM: both static and virtual methods.
     */
    public List<TeleClassMethodActor> getTeleClassMethodActors() {
        ArrayList<TeleClassMethodActor> result = new ArrayList<TeleClassMethodActor>();
        result.addAll(readTeleStaticMethodActors());
        result.addAll(readTeleVirtualMethodActors());
        return result;
    }

    /**
     * @return surrogates for all of the the local (not inherited) {@link MethodActor}s associated with the class in the VM:  static, virtual, and interface methods.
     */
    public List<TeleMethodActor> getTeleMethodActors() {
        ArrayList<TeleMethodActor> result = new ArrayList<TeleMethodActor>();
        result.addAll(readTeleStaticMethodActors());
        result.addAll(readTeleVirtualMethodActors());
        result.addAll(readTeleInterfaceMethodActors());
        return result;
    }

    @Override
    public String maxineTerseRole() {
        return "ClassActor";
    }

    public final String getName() {
        return actorName().string;
    }

    public ClassLoaderProvider classLoader() {
        return getTeleClassLoader();
    }

    public ClassObjectProvider classObject() {
        return getTeleClass();
    }

    private abstract class FakeFieldProvider implements FieldProvider {

        private static final String PREFIX = "X_";

        private final String name;
        private final VMValue.Type type;
        private final String signature;

        public FakeFieldProvider(String name, VMValue.Type type, String signature) {
            this.name = PREFIX + name;
            this.type = type;
            this.signature = signature;
        }

        public ReferenceTypeProvider getReferenceTypeHolder() {
            return TeleClassActor.this;
        }

        public VMValue getStaticValue() {
            assert false : "This field is not a static field!";
            return null;
        }

        public void setStaticValue(VMValue value) {
            assert false : "This field is not a static field!";
        }

        public void setValue(ObjectProvider object, VMValue value) {
            assert false : "This field is readonly!";
        }

        public int getFlags() {
            return FieldActor.ACC_FINAL;
        }

        public String getGenericSignature() {
            return getSignature();
        }

        public String getName() {
            return actorName().string;
        }

        public String getSignature() {
            return signature;
        }

        public Type getType() {
            return type;
        }
    }

    private final FieldProvider fakeAddressField = new FakeFieldProvider("address", VMValue.Type.LONG, JavaTypeDescriptor.LONG.toString()) {
        public VMValue getValue(ObjectProvider object) {
            return vm().vmAccess().createLongValue(((TeleObject) object).reference().toOrigin().asAddress().toLong());
        }
    };

    private final FieldProvider fakeHubField = new FakeFieldProvider("hub", VMValue.Type.PROVIDER, "Lcom/sun/max/vm/actor/holder/Hub;") {
        public VMValue getValue(ObjectProvider object) {
            return vm().maxineValueToJDWPValue(TeleReferenceValue.from(vm(), ((TeleObject) object).getTeleHub().reference()));
        }
    };

    private final FieldProvider fakeMiscField = new FakeFieldProvider("misc", VMValue.Type.LONG, JavaTypeDescriptor.LONG.toString()) {
        public VMValue getValue(ObjectProvider object) {
            return vm().vmAccess().createLongValue(((TeleObject) object).readMiscWord().asAddress().toLong());
        }
    };

    public FieldProvider[] getFields() {
        final List<TeleFieldActor> list = getTeleFieldActors();
        final List<FieldProvider> result = new ArrayList<FieldProvider>();
        result.addAll(list);
        if (this.classActor().superClassActor == null) {
            result.add(0, fakeAddressField);
            result.add(0, fakeMiscField);
            result.add(0, fakeHubField);
        }
        final FieldProvider[] resultArray = new FieldProvider[list.size()];
        return result.toArray(resultArray);
    }

    public InterfaceProvider[] getImplementedInterfaces() {
        final HashSet<InterfaceActor> interfaces = classActor().getAllInterfaceActors();
        final List<InterfaceProvider> result = new LinkedList<InterfaceProvider>();
        for (InterfaceActor interfaceActor : interfaces) {
            final InterfaceProvider interfaceProvider = (TeleInterfaceActor) classes().findTeleClassActor(interfaceActor.typeDescriptor);
            if (interfaceProvider != this) {
                result.add(interfaceProvider);
            }
        }
        return result.toArray(new InterfaceProvider[result.size()]);
    }

    public MethodProvider[] getMethods() {
        List<TeleMethodActor> teleMethodActors = getTeleMethodActors();
        return teleMethodActors.toArray(new MethodProvider[teleMethodActors.size()]);
    }

    public ReferenceTypeProvider[] getNestedTypes() {
        final ClassActor[] actors = classActor().innerClassActors();
        final ReferenceTypeProvider[] result = new ReferenceTypeProvider[actors.length];
        for (int i = 0; i < actors.length; i++) {
            result[i] = classes().findTeleClassActor(actors[i].typeDescriptor);
        }
        return result;
    }

    public ObjectProvider[] getInstances() {
        // TODO: Implement this correctly.
        return null;
    }

    public String getSignature() {
        return classActor().typeDescriptor.toString();
    }

    public String getSignatureWithGeneric() {
        return classActor().genericSignatureString();
    }

    public int getStatus() {

        // TODO: Correct implementation.

        int status = 0;

        if (classActor().isInitialized()) {
            status |= ClassStatus.INITIALIZED;
        }

        if (classActor().isInitialized()) {
            status |= ClassStatus.PREPARED;
        }

        if (classActor().isInitialized()) {
            status |= ClassStatus.VERIFIED;
        }

        return status;
    }

    public String getSourceFileName() {
        return classActor().sourceFileName;
    }

    public Type getType() {
        return TeleVM.maxineKindToJDWPType(classActor().kind);
    }

    public int majorVersion() {
        return classActor().majorVersion;
    }

    public int minorVersion() {
        return classActor().minorVersion;
    }
}
