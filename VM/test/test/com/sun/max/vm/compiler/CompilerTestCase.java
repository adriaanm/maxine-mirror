/*
 * Copyright (c) 2007 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
package test.com.sun.max.vm.compiler;

import static com.sun.max.vm.classfile.constant.SymbolTable.*;
import static com.sun.max.vm.reflection.GeneratedStub.*;

import java.io.*;
import java.lang.reflect.*;

import junit.framework.*;

import org.junit.runner.*;

import sun.reflect.*;
import test.com.sun.max.vm.compiler.cir.generate.*;

import com.sun.max.*;
import com.sun.max.annotate.*;
import com.sun.max.asm.*;
import com.sun.max.asm.dis.*;
import com.sun.max.collect.*;
import com.sun.max.ide.*;
import com.sun.max.io.*;
import com.sun.max.lang.*;
import com.sun.max.program.*;
import com.sun.max.unsafe.*;
import com.sun.max.unsafe.box.*;
import com.sun.max.util.*;
import com.sun.max.vm.*;
import com.sun.max.vm.actor.*;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.bytecode.*;
import com.sun.max.vm.bytecode.graft.*;
import com.sun.max.vm.classfile.*;
import com.sun.max.vm.classfile.ClassfileWriter.*;
import com.sun.max.vm.classfile.constant.*;
import com.sun.max.vm.classfile.constant.ConstantPool;
import com.sun.max.vm.classfile.create.*;
import com.sun.max.vm.compiler.*;
import com.sun.max.vm.compiler.ir.*;
import com.sun.max.vm.compiler.target.*;
import com.sun.max.vm.interpreter.*;
import com.sun.max.vm.prototype.*;
import com.sun.max.vm.reflection.*;
import com.sun.max.vm.type.*;
import com.sun.max.vm.value.*;

/**
 *
 * @author Bernd Mathiske
 * @author Doug Simon
 */
@RunWith(org.junit.runners.AllTests.class)
public abstract class CompilerTestCase<Method_Type extends IrMethod> extends MaxTestCase {

    /**
     * An indent writer that sends its output to the standard {@linkplain Trace#stream() trace stream}.
     */
    public static final IndentWriter INDENT_WRITER = new IndentWriter(new PrintWriter(Trace.stream(), true));

    protected CompilerTestCase() {
        super();
    }

    protected CompilerTestCase(String name) {
        super(name);
    }

    private final AppendableSequence<ClassMethodActor> _compiledMethods = new LinkSequence<ClassMethodActor>();

    @Override
    public void tearDown() {
        for (ClassMethodActor classMethodActor : _compiledMethods) {
            CompilationScheme.Static.resetMethodState(classMethodActor);
        }
    }

    /**
     * A facility for creating a class method actor via bytecode assembly. A subclass simply has to override
     * {@link #generateCode()} to emit the desired bytecode.
     */
    public abstract class TestBytecodeAssembler extends BytecodeAssembler {

        private final boolean _isStatic;
        private final Utf8Constant _methodName;
        private final Utf8Constant _className;
        private final SignatureDescriptor _signature;
        private ClassMethodActor _classMethodActor;

        /**
         * Generates a class method actor via bytecode assembly.
         *
         * @param isStatic specifies if the generated class method is static
         * @param className the {@linkplain ClassActor#name() name} of the class actor. If null, then the name will be
         *            derived from the {@code superClass} parameter provided to {@link #compile(Class)}.
         * @param methodName the {@linkplain Actor#name() name} of the class method actor
         * @param signature the {@linkplain MethodActor#descriptor() signature} of the class method actor
         */
        public TestBytecodeAssembler(boolean isStatic, String className, String methodName, SignatureDescriptor signature) {
            super(new ConstantPool(PrototypeClassLoader.PROTOTYPE_CLASS_LOADER).edit());
            _isStatic = isStatic;
            _methodName = makeSymbol(methodName);
            _className = className == null ? null : makeSymbol(className);
            _signature = signature;
            _codeStream = new SeekableByteArrayOutputStream();
            allocateParameters(isStatic, signature.getParameterKinds());
        }

        public TestBytecodeAssembler(boolean isStatic, String methodName, SignatureDescriptor signature) {
            this(isStatic, null, methodName, signature);
        }

        private final SeekableByteArrayOutputStream _codeStream;

        @Override
        protected void setWritePosition(int position) {
            _codeStream.seek(position);
        }

        @Override
        protected void writeByte(byte b) {
            _codeStream.write(b);
        }

        @Override
        public byte[] code() {
            fixup();
            return _codeStream.toByteArray();
        }

        protected abstract void generateCode();

        /**
         * Generates a default constructor that simply calls the super class' default constructor. If the
         * latter does not exist, then this method returns null.
         */
        private ClassMethodActor generateDefaultConstructor(Class<?> superClass) {
            final ByteArrayBytecodeAssembler asm = new ByteArrayBytecodeAssembler(constantPoolEditor());
            asm.allocateLocal(Kind.REFERENCE);

            try {
                final Constructor superDefaultConstructor = superClass.getDeclaredConstructor();
                asm.aload(0);
                asm.invokespecial(PoolConstantFactory.createClassMethodConstant(superDefaultConstructor), 1, 0);
                asm.vreturn();

                final CodeAttribute codeAttribute = new CodeAttribute(
                                constantPool(),
                                asm.code(),
                                (char) asm.maxStack(),
                                (char) asm.maxLocals(),
                                CodeAttribute.NO_EXCEPTION_HANDLER_TABLE,
                                LineNumberTable.EMPTY,
                                LocalVariableTable.EMPTY,
                                null);
                return new VirtualMethodActor(
                                SymbolTable.INIT,
                                SignatureDescriptor.fromJava(Void.TYPE),
                                Modifier.PUBLIC | Actor.INSTANCE_INITIALIZER,
                                codeAttribute);
            } catch (NoSuchMethodException e) {
                return null;
            }
        }

        /**
         * @param superClass the super class of generated holder for the generated class method actor
         */
        public ClassMethodActor classMethodActor(Class superClass) {
            if (_classMethodActor == null) {
                generateCode();
                final CodeAttribute codeAttribute = new CodeAttribute(
                                constantPool(),
                                code(),
                                (char) 100, // TODO: compute max stack
                                (char) maxLocals(),
                                CodeAttribute.NO_EXCEPTION_HANDLER_TABLE,
                                LineNumberTable.EMPTY,
                                LocalVariableTable.EMPTY,
                                null);
                _classMethodActor = _isStatic ?
                    new StaticMethodActor(
                                    _methodName,
                                    _signature,
                                    Modifier.PUBLIC | Modifier.STATIC,
                                    codeAttribute) :
                    new VirtualMethodActor(
                                    _methodName,
                                    _signature,
                                    Modifier.PUBLIC,
                                    codeAttribute);
                final Utf8Constant className = _className == null ? makeSymbol(superClass.getName() + "_$GENERATED$_" + _methodName) : _className;
                final ClassMethodActor defaultConstructor = generateDefaultConstructor(superClass);
                final ClassMethodActor[] classMethodActors;
                if (defaultConstructor != null) {
                    classMethodActors = new ClassMethodActor[]{_classMethodActor, defaultConstructor};
                } else {
                    classMethodActors = new ClassMethodActor[]{_classMethodActor};
                }
                final ClassActor classActor = ClassActorFactory.createTupleOrHybridClassActor(
                    constantPool(),
                    VmClassLoader.VM_CLASS_LOADER,
                    className,
                    ClassfileReader.JAVA_1_5_VERSION,
                    (char) 0,
                    Modifier.PUBLIC | Actor.GENERATED,
                    ClassActor.fromJava(superClass),
                    new InterfaceActor[0],
                    new FieldActor[0],
                    classMethodActors,
                    Actor.NO_GENERIC_SIGNATURE,
                    Actor.NO_RUNTIME_VISIBLE_ANNOTATION_BYTES,
                    ClassActor.NO_SOURCE_FILE_NAME,
                    ClassActor.NO_INNER_CLASSES,
                    ClassActor.NO_OUTER_CLASS,
                    ClassActor.NO_ENCLOSING_METHOD_INFO);
                try {
                    ClassfileWriter.saveGeneratedClass(new ClassInfo(classActor), constantPoolEditor());
                } catch (IOException e) {
                    throw (NoClassDefFoundError) new NoClassDefFoundError(className.toString()).initCause(e);
                }
                constantPoolEditor().release();
            }
            return _classMethodActor;
        }

        public Method_Type compile(Class superClass) {
            return compileMethod(classMethodActor(superClass));
        }
    }

    /**
     * Gets the method which creates the translation table to be used for enum switches. This may well return null as
     * each Java source compiler differs in the way it generates the translation table.
     *
     * @param clientClass the class that contains a switch statement over an enum type
     * @param enumClass the enum type
     * @return the method that initializes the translation table or null if it could not be found
     */
    protected ClassMethodActor getEnumSwitchTranslationTableInitializer(Class clientClass, Class<? extends Enum> enumClass) {
        final ClassMethodActor classMethodActor;
        switch (IDE.current()) {
            case ECLIPSE: {
                final Utf8Constant name = makeSymbol("$SWITCH_TABLE$" + enumClass.getName().replace('.', '$'));
                final SignatureDescriptor signature = SignatureDescriptor.create(int[].class);
                classMethodActor = ClassActor.fromJava(clientClass).findLocalStaticMethodActor(name, signature);
                break;
            }
            default: {
                classMethodActor = null;
            }
        }
        if (classMethodActor == null) {
            ProgramWarning.message("could not find the method that initializes the translation table used in " + clientClass + " for a switch over values of enum type " + enumClass.getName());
        }
        return classMethodActor;
    }

    protected ClassMethodActor getClassMethodActor(Class javaClass, String methodName, SignatureDescriptor signature) {
        final ClassMethodActor classMethodActor = ClassActor.fromJava(javaClass).findClassMethodActor(makeSymbol(methodName), signature);
        if (classMethodActor == null) {
            fail();
        }
        return classMethodActor;
    }

    protected ClassMethodActor getClassMethodActor(Class javaClass, String methodName) {
        Class thisClass = javaClass;
        ClassMethodActor classMethodActor;
        do {
            classMethodActor = ClassActor.fromJava(thisClass).findLocalClassMethodActor(makeSymbol(methodName));
            thisClass = thisClass.getSuperclass();
        } while (classMethodActor == null && thisClass != null);
        if (classMethodActor == null) {
            fail();
        }
        return classMethodActor;
    }

    protected ClassMethodActor getClassMethodActor(String methodName, SignatureDescriptor signature) {
        final Class testClass = Classes.load(PrototypeClassLoader.PROTOTYPE_CLASS_LOADER, getClass().getName());
        return getClassMethodActor(testClass, methodName, signature);
    }

    protected ClassMethodActor getClassMethodActor(String methodName) {
        final Class testClass = Classes.load(PrototypeClassLoader.PROTOTYPE_CLASS_LOADER, getClass().getName());
        return getClassMethodActor(testClass, methodName);
    }

    protected CompilerTestSetup<Method_Type> compilerTestSetup() {
        final Class<CompilerTestSetup<Method_Type>> compilerTestSetupType = null;
        return StaticLoophole.cast(compilerTestSetupType, CompilerTestSetup.compilerTestSetup());
    }

    private void reportNondeterministicTranslation(Method_Type method1, Method_Type method2) {
        System.err.println("Compiler output differs for different translations to " + method1.getClass().getSimpleName() + " of " + method1.classMethodActor());
        System.err.println("---- Begin first translation ----");
        System.err.println(method1.traceToString());
        System.err.println("---- End first translation ----");
        System.err.println("---- Begin second translation ----");
        System.err.println(method2.traceToString());
        System.err.println("---- End second translation ----");
        addTestError(new Throwable("Compiler output differs for different translations to " + method1.getClass().getSimpleName() + " of " + method1.classMethodActor()));
    }

    private void compareCompilerOutput(Method_Type method1, Method_Type method2) {
        assert method1 != method2;
        if (method1 instanceof TargetMethod) {
            final byte[] code1 = ((TargetMethod) method1).code();
            final byte[] code2 = ((TargetMethod) method2).code();
            assert code1 != code2;
            if (!java.util.Arrays.equals(code1, code2)) {
                reportNondeterministicTranslation(method1, method2);
            }
        } else {
            final String code1 = method1.traceToString();
            final String code2 = method2.traceToString();
            if (!code1.equals(code2)) {
                reportNondeterministicTranslation(method1, method2);
            }
        }
    }

    protected Method_Type compileMethod(final ClassMethodActor classMethodActor) {
        _compiledMethods.append(classMethodActor);
        return MaxineVM.usingTarget(new Function<Method_Type>() {
            public Method_Type call() {
                try {
                    final Method_Type method = compilerTestSetup().translate(classMethodActor);

                    assertNotNull(method);

                    if (Trace.hasLevel(3) && method instanceof TargetMethod) {
                        final TargetMethod targetMethod = (TargetMethod) method;
                        Trace.line(3, "Bundle and code for " + targetMethod);
                        traceBundleAndDisassemble(targetMethod);
                    }

                    if (System.getProperty("testDeterministicCompilation") != null) {
                        CompilationScheme.Static.resetMethodState(classMethodActor);
                        compareCompilerOutput(method, compilerTestSetup().translate(classMethodActor));
                    }
                    return method;
                } catch (NoSuchMethodError noSuchMethodError) {
                    if (classMethodActor.isClassInitializer()) {
                        ProgramWarning.message("NoSuchMethodError - probably caused by <clinit> referring to method with @PROTOTYPE_ONLY: " + noSuchMethodError);
                        return null;
                    }
                    throw noSuchMethodError;
                }
            }
        });
    }

    protected Method_Type compileMethod(Class type, String methodName, SignatureDescriptor signature) {
        final ClassMethodActor classMethodActor = getClassMethodActor(type, methodName, signature);
        return compileMethod(classMethodActor);
    }

    protected Method_Type compileMethod(Class type, String methodName) {
        final ClassMethodActor classMethodActor = getClassMethodActor(type, methodName);
        return compileMethod(classMethodActor);
    }

    protected Method_Type compileMethod(String methodName, SignatureDescriptor signature) {
        final ClassMethodActor classMethodActor = getClassMethodActor(methodName, signature);
        return compileMethod(classMethodActor);
    }

    protected Method_Type compileMethod(String methodName) {
        final ClassMethodActor classMethodActor = getClassMethodActor(methodName);
        return compileMethod(classMethodActor);
    }

    protected Method_Type compileMethod(final String className, final byte[] classfileBytes, final String methodName, final SignatureDescriptor signature) {
        return MaxineVM.usingTarget(new Function<Method_Type>() {
            public Method_Type call() {
                final Class testClass = MillClassLoader.makeClass(className, classfileBytes);

                // Save the generated class file to the filesystem so that a generated stub for a method in the
                // generated class can find the corresponding Class instance
                if (MaxineVM.isPrototyping()) {
                    VmClassLoader.VM_CLASS_LOADER.saveGeneratedClassfile(className, classfileBytes);
                }

                final ClassMethodActor classMethodActor = ClassActor.fromJava(testClass).findLocalStaticMethodActor(makeSymbol(methodName), signature);
                assertNotNull(classMethodActor);

                return compileMethod(classMethodActor);
            }
        });
    }

    /**
     * A handle to the result of a running test is maintained so that individual method compilation failures can be
     * reported without short-circuiting compilation of other methods in the enclosing class/package being tested.
     * Of course this will not apply to test cases that only compile one method and execute it.
     */
    private TestResult _testResult;

    protected void addTestError(Throwable error) {
        _testResult.addError(this, error);
    }

    @Override
    public void run(TestResult result) {
        _testResult = result;
        try {
            super.run(result);
        } finally {
            _testResult = null;
        }
    }

    protected void compileClass(ClassActor classActor) {
        for (MethodActor methodActor : classActor.getLocalMethodActors()) {
            if (!methodActor.isAbstract() && !methodActor.isBuiltin() && !MaxineVM.isPrototypeOnly(methodActor)) {
                final ClassMethodActor classMethodActor = (ClassMethodActor) methodActor;
                if (classMethodActor.isClassInitializer() && classMethodActor.holder().name().toString().contains("HexByte")) {
                    continue;
                }

                if (classMethodActor.isInstanceInitializer() && InjectedReferenceFieldActor.class.equals(classActor.toJava().getEnclosingClass())) {
                    // These anonymous inner classes call a super constructor that is annotated with PROTOTYPE_ONLY
                    continue;
                }

                if (Word.class.isAssignableFrom(classActor.toJava()) && !classMethodActor.isStatic() && !classMethodActor.isInstanceInitializer()) {
                    final Method javaMethod = methodActor.toJava();
                    if (!javaMethod.isAnnotationPresent(INLINE.class)) {
                        Trace.line(2, "skipping non-static method in Word subclass that is not annotated with @INLINE: " + methodActor);
                        continue;
                    }
                }

                Trace.begin(2, "compiling method: " + methodActor);
                try {
                    compileMethod(classMethodActor);
                } catch (Throwable error) {
                    addTestError(new Throwable("Error occurred while compiling " + methodActor, error));
                } finally {
                    CompilationScheme.Static.resetMethodState(classMethodActor); // conserve heap space
                    Trace.end(2, "compiling method: " + methodActor);
                }
            }
        }
    }

    protected void compileClass(Class javaClass) {
        // UnsafeBox classes cannot be compiled as they are treated as Word types and any attempt to
        // access fields to will fail as field access only works for non Word types. An example
        // of such a type is BoxedJniHandle.
        assert !(UnsafeBox.class.isAssignableFrom(javaClass));

        final ClassActor classActor = ClassActor.fromJava(javaClass);
        if (classActor != null) {
            Trace.begin(1, "compiling class: " + classActor);
            compileClass(classActor);
            Trace.end(1, "compiling class: " + classActor);
        }
    }

    protected void compilePackage(MaxPackage p) {
        Trace.begin(1, "compiling package: " + p.name());
        for (Class javaType : CirTranslatorTestSetup.packageLoader().load(p, false)) {
            compileClass(javaType);
        }
        Trace.end(1, "compiling package: " + p.name());
    }

    protected void compilePackages(Sequence<MaxPackage> packages) {
        for (MaxPackage p : packages) {
            compilePackage(p);
        }
    }

    public boolean hasInterpreter() {
        return compilerTestSetup().createInterpreter() != null;
    }

    protected IrInterpreter<Method_Type> createInterpreter() {
        final IrInterpreter<Method_Type> interpreter = compilerTestSetup().createInterpreter();
        ProgramError.check(interpreter != null, "no interpreter available for this representation");
        return interpreter;
    }

    protected Method_Type generateAndCompileStubFor(MethodActor classMethodActor, Boxing boxing) {
        if (classMethodActor.isInstanceInitializer()) {
            final GeneratedConstructorStub stub = newConstructorStub(classMethodActor.toJavaConstructor(), false, boxing);
            final ClassActor stubActor = ClassActor.fromJava(stub.getClass());
            compileClass(stubActor);
            final ClassMethodActor newInstanceActor = stubActor.findClassMethodActor(makeSymbol("newInstance"), boxing.newInstanceSignature());
            assert newInstanceActor != null;
            return compileMethod(newInstanceActor);
        }
        final MethodAccessor stub = newMethodStub(classMethodActor.toJava(), boxing);
        final ClassActor stubActor = ClassActor.fromJava(stub.getClass());
        compileClass(stubActor);
        final ClassMethodActor invokeActor = stubActor.findClassMethodActor(makeSymbol("invoke"), boxing.invokeSignature());
        assert invokeActor != null;
        return compileMethod(invokeActor);

    }

    /**
     * Executes a {@linkplain #generateAndCompileStubFor(ClassMethodActor, boolean) generated stub}.
     *
     * @param classMethodActor the method for which the stub was generated
     * @param stub the compiled stub
     * @param arguments the execution arguments
     */
    protected Value executeGeneratedStub(MethodActor classMethodActor, Method_Type stub, Boxing boxing, Value... arguments) throws InvocationTargetException {
        final Value[] boxedArguments;
        if (classMethodActor.isStatic()) {
            assert !classMethodActor.isInstanceInitializer();
            if (boxing == Boxing.JAVA) {
                // Signature: invoke(Object receiver, Object[] args)
                boxedArguments = new Value[]{
                    ReferenceValue.NULL,
                    ReferenceValue.NULL,
                    ReferenceValue.from(Value.asBoxedJavaValues(0, arguments.length, arguments))
                };
            } else {
                // Signature: invoke(Value[] args)
                boxedArguments = new Value[]{
                    ReferenceValue.NULL,
                    ReferenceValue.from(arguments)
                };
            }
        } else {
            if (classMethodActor.isInstanceInitializer()) {
                // Signature: newInstance(Object[] args)  | useJavaBoxing == true
                //            newInstance(Value[] args)   | useJavaBoxing == false
                boxedArguments = new Value[]{
                    ReferenceValue.NULL,
                    ReferenceValue.from(boxing == Boxing.JAVA ? Value.asBoxedJavaValues(0, arguments.length, arguments) : arguments)
                };
            } else {
                if (boxing == Boxing.JAVA) {
                    // Signature: invoke(Object receiver, Object[] args)
                    boxedArguments = new Value[]{
                        ReferenceValue.NULL,
                        arguments[0],
                        ReferenceValue.from(Value.asBoxedJavaValues(1, arguments.length - 1, arguments))
                    };
                } else {
                    // Signature: invoke(Value[] args)
                    boxedArguments = new Value[]{
                        ReferenceValue.NULL,
                        ReferenceValue.from(arguments)
                    };
                }
            }
        }

        Value returnValue = createInterpreter().execute(stub, boxedArguments);
        if (classMethodActor.isInstanceInitializer()) {
            return returnValue;
        }
        if (classMethodActor.resultKind() == Kind.VOID) {
            if (boxing == Boxing.JAVA) {
                assertTrue(returnValue == ReferenceValue.NULL);
                returnValue = VoidValue.VOID;
            } else {
                assertTrue(returnValue.asObject() == VoidValue.VOID);
            }
        } else if (classMethodActor.resultKind().toJava().isPrimitive()) {
            returnValue = Value.fromBoxedJavaValue(returnValue.asBoxedJavaValue());
        }
        return returnValue;
    }

    private static boolean usesWordTypes(MethodActor classMethodActor) {
        if (classMethodActor.descriptor().getResultKind() == Kind.WORD) {
            return true;
        }
        for (Kind parameterKind : classMethodActor.descriptor().getParameterKinds()) {
            if (parameterKind == Kind.WORD) {
                return true;
            }
        }
        return false;
    }

    /**
     * Testing generation of stubs for reflective method invocation as an extra on the side.
     */
    private void testStubs(Method_Type method, Value[] arguments, Value returnValue) {
        final MethodActor classMethodActor = method.classMethodActor();
        for (Boxing boxing : Boxing.values()) {
            // Only test with Java boxing if none of the parameter or return types is a Word type
            if (boxing == Boxing.JAVA) {
                final boolean usesWordTypes = usesWordTypes(classMethodActor);
                if (usesWordTypes) {
                    continue;
                }
            }
            final STUB_TEST_PROPERTIES stubProperties = classMethodActor.getAnnotation(STUB_TEST_PROPERTIES.class);
            if (stubProperties == null || stubProperties.execute()) {
                try {
                    final Method_Type stub = generateAndCompileStubFor(method.classMethodActor(), boxing);
                    try {
                        final Value stubReturnValue = executeGeneratedStub(classMethodActor, stub, boxing, arguments);
                        if (stubProperties == null || stubProperties.compareResult()) {
                            if (returnValue.kind() == Kind.REFERENCE) {
                                if (boxing == Boxing.VALUE) {
                                    assertEquals(((Value) stubReturnValue.asObject()).asObject(), returnValue.asObject());
                                } else {
                                    assertEquals(stubReturnValue.asObject(), returnValue.asObject());
                                }
                            } else {
                                if (boxing == Boxing.VALUE) {
                                    assertEquals(returnValue, stubReturnValue.asObject());
                                } else {
                                    assertEquals(returnValue, stubReturnValue);
                                }
                            }
                        }
                    } catch (Throwable e) {
                        CodeAttribute codeAttribute = method.classMethodActor().codeAttribute();
                        ProgramWarning.message("Failed " + boxing + " stub execution or result comparison for " + classMethodActor);
                        System.err.println("original method:");
                        System.err.println(BytecodePrinter.toString(codeAttribute.constantPool(), new BytecodeBlock(codeAttribute.code())));
                        codeAttribute = stub.classMethodActor().codeAttribute();
                        System.err.println("stub: " + stub.classMethodActor());
                        System.err.println(BytecodePrinter.toString(codeAttribute.constantPool(), new BytecodeBlock(codeAttribute.code())));
                        System.err.println("stub IR:");
                        System.err.println(stub.traceToString());
                        e.printStackTrace();
                    }
                } catch (Throwable e) {
                    ProgramWarning.message("Failed " + boxing + " stub generation for " + classMethodActor);
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * TODO: debug stub interpretation and reenable this.
     */
    protected boolean shouldTestStubs() {
        return false;
    }

    /**
     * Executes a given compiled method on an interpreter for the compiler's IR.
     *
     * @param method the compiled method to execute
     * @param arguments the arguments (including the receiver for a non-static method). If {@code method} is a
     *            constructor, then a new instance of {@link UninitializedObject} is prepended to the arguments
     * @return the result of the execution which will be a newly created and initalized object of the appropriate type
     *         if {@code method} is a constructor
     */
    protected final Value executeWithException(final Method_Type method, final Value... arguments) throws InvocationTargetException {
        try {
            return MaxineVM.usingTargetWithException(new Function<Value>() {
                public Value call() throws Exception {
                    Trace.begin(3, "interpreting " + method);
                    try {
                        final ClassMethodActor classMethodActor = method.classMethodActor();
                        final boolean isConstructor = classMethodActor.isInstanceInitializer();
                        final Value[] executeArguments = isConstructor ? Arrays.prepend(arguments, ReferenceValue.from(new UninitializedObject(classMethodActor.holder()))) : arguments;
                        final Kind[] parameterKinds = classMethodActor.descriptor().getParameterKinds();
                        int argumentIndex = arguments.length - 1;
                        int parameterIndex = parameterKinds.length - 1;
                        while (parameterIndex >= 0) {
                            final Kind argumentKind = arguments[argumentIndex].kind();
                            final Kind parameterKind = parameterKinds[parameterIndex];
                            ProgramError.check(argumentKind == parameterKind, "Argument " + argumentIndex + " has kind " + argumentKind + " where as kind " + parameterKind + " is expected");
                            parameterIndex--;
                            argumentIndex--;
                        }

                        final Value value = createInterpreter().execute(method, executeArguments);
                        final Value returnValue = isConstructor ? executeArguments[0] : value;

                        if (shouldTestStubs()) {
                            testStubs(method, arguments, returnValue);
                        }

                        return returnValue;
                    } finally {
                        Trace.end(3, "interpreting " + method);
                    }
                }
            });
        } catch (Exception exception) {
            throw Exceptions.cast(InvocationTargetException.class, exception);
        }
    }

    protected final Value executeWithReceiverAndException(Method_Type method, Value... arguments) throws InvocationTargetException {
        return executeWithException(method, Arrays.prepend(arguments, (Value) ReferenceValue.from(this)));
    }

    protected Value execute(Method_Type method, Value... arguments) {
        try {
            return executeWithException(method, arguments);
        } catch (InvocationTargetException invocationTargetException) {
            invocationTargetException.printStackTrace();
            fail();
            return null;
        }
    }

    protected Value executeWithReceiver(Method_Type method, Value... arguments) {
        try {
            return executeWithReceiverAndException(method, arguments);
        } catch (InvocationTargetException invocationTargetException) {
            invocationTargetException.printStackTrace();
            fail();
            return null;
        }
    }

    /**
     * Gets a disassembler for a given target method.
     *
     * @param targetMethod a compiled method whose {@linkplain TargetMethod#code() code} is to be disassembled
     * @return a disassembler for the ISA specific code in {@code targetMethod} or null if no such disassembler is available
     */
    protected Disassembler disassemblerFor(TargetMethod targetMethod) {
        return compilerTestSetup().disassemblerFor(targetMethod);
    }

    /**
     * Traces the metadata of the compiled code represented by a given target method followed by a disassembly of
     * the compiled code. If a disassembler is not available for the code, then only the metadata is traced.
     * The trace is sent to the standard {@linkplain Trace#stream() trace stream}.
     */
    protected void traceBundleAndDisassemble(TargetMethod targetMethod) {
        targetMethod.traceBundle(INDENT_WRITER);
        INDENT_WRITER.println("Reference Maps:");
        INDENT_WRITER.indent();
        INDENT_WRITER.println(targetMethod.referenceMapsToString());
        INDENT_WRITER.outdent();
        disassemble(targetMethod);
    }

    /**
     * Disassembles the compiled code of a given target method if a disassembler is
     * {@linkplain #disassemblerFor(TargetMethod) available}. The disassembler output is sent to the standard
     * {@linkplain Trace#stream() trace stream}.
     */
    protected void disassemble(TargetMethod targetMethod) {
        final int level = Trace.level();
        try {
            Trace.off();
            final Disassembler disassembler = disassemblerFor(targetMethod);
            if (disassembler == null) {
                return;
            }
            final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            final byte[] code = targetMethod.code();
            if (code.length != 0) {
                disassembler.scanAndPrint(new BufferedInputStream(new ByteArrayInputStream(code)), buffer);
                INDENT_WRITER.printLines(new ByteArrayInputStream(buffer.toByteArray()));
            }
        } catch (IOException e) {
            ProgramError.unexpected("disassembly failed for target method " + targetMethod + " :" + e.getMessage());
        } catch (AssemblyException e) {
            ProgramError.unexpected("disassembly failed for target method " + targetMethod + " :" + e.getMessage());
        } finally {
            Trace.on(level);
        }
    }
}
