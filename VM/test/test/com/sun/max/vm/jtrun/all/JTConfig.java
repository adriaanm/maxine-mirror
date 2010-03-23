/*
 * Copyright (c) 2009 Sun Microsystems, Inc.  All rights reserved.
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
package test.com.sun.max.vm.jtrun.all;

import test.com.sun.max.vm.jtrun.*;

/**
 * The {@code JavaTesterConfig} class contains generated code which contains the class list
 * for a generated JavaTesterRuns class.
 *
 * @author Ben L. Titzer
 */
public class JTConfig {

// GENERATED TEST CLASS LIST
    private static final Class<?>[] classList = {
        jtt.bytecode.BC_aaload.class,
        jtt.bytecode.BC_aaload_1.class,
        jtt.bytecode.BC_aastore.class,
        jtt.bytecode.BC_aload_0.class,
        jtt.bytecode.BC_aload_1.class,
        jtt.bytecode.BC_aload_2.class,
        jtt.bytecode.BC_aload_3.class,
        jtt.bytecode.BC_anewarray.class,
        jtt.bytecode.BC_areturn.class,
        jtt.bytecode.BC_arraylength.class,
        jtt.bytecode.BC_athrow.class,
        jtt.bytecode.BC_baload.class,
        jtt.bytecode.BC_bastore.class,
        jtt.bytecode.BC_caload.class,
        jtt.bytecode.BC_castore.class,
        jtt.bytecode.BC_checkcast01.class,
        jtt.bytecode.BC_checkcast02.class,
        jtt.bytecode.BC_d2f.class,
        jtt.bytecode.BC_d2i01.class,
        jtt.bytecode.BC_d2i02.class,
        jtt.bytecode.BC_d2l01.class,
        jtt.bytecode.BC_d2l02.class,
        jtt.bytecode.BC_dadd.class,
        jtt.bytecode.BC_daload.class,
        jtt.bytecode.BC_dastore.class,
        jtt.bytecode.BC_dcmp01.class,
        jtt.bytecode.BC_dcmp02.class,
        jtt.bytecode.BC_dcmp03.class,
        jtt.bytecode.BC_dcmp04.class,
        jtt.bytecode.BC_dcmp05.class,
        jtt.bytecode.BC_dcmp06.class,
        jtt.bytecode.BC_dcmp07.class,
        jtt.bytecode.BC_dcmp08.class,
        jtt.bytecode.BC_dcmp09.class,
        jtt.bytecode.BC_dcmp10.class,
        jtt.bytecode.BC_ddiv.class,
        jtt.bytecode.BC_dmul.class,
        jtt.bytecode.BC_dneg.class,
        jtt.bytecode.BC_drem.class,
        jtt.bytecode.BC_dreturn.class,
        jtt.bytecode.BC_dsub.class,
        jtt.bytecode.BC_f2d.class,
        jtt.bytecode.BC_f2i01.class,
        jtt.bytecode.BC_f2i02.class,
        jtt.bytecode.BC_f2l01.class,
        jtt.bytecode.BC_f2l02.class,
        jtt.bytecode.BC_fadd.class,
        jtt.bytecode.BC_faload.class,
        jtt.bytecode.BC_fastore.class,
        jtt.bytecode.BC_fcmp01.class,
        jtt.bytecode.BC_fcmp02.class,
        jtt.bytecode.BC_fcmp03.class,
        jtt.bytecode.BC_fcmp04.class,
        jtt.bytecode.BC_fcmp05.class,
        jtt.bytecode.BC_fcmp06.class,
        jtt.bytecode.BC_fcmp07.class,
        jtt.bytecode.BC_fcmp08.class,
        jtt.bytecode.BC_fcmp09.class,
        jtt.bytecode.BC_fcmp10.class,
        jtt.bytecode.BC_fdiv.class,
        jtt.bytecode.BC_fload.class,
        jtt.bytecode.BC_fload_2.class,
        jtt.bytecode.BC_fmul.class,
        jtt.bytecode.BC_fneg.class,
        jtt.bytecode.BC_frem.class,
        jtt.bytecode.BC_freturn.class,
        jtt.bytecode.BC_fsub.class,
        jtt.bytecode.BC_getfield.class,
        jtt.bytecode.BC_getstatic_b.class,
        jtt.bytecode.BC_getstatic_c.class,
        jtt.bytecode.BC_getstatic_clinit.class,
        jtt.bytecode.BC_getstatic_d.class,
        jtt.bytecode.BC_getstatic_f.class,
        jtt.bytecode.BC_getstatic_i.class,
        jtt.bytecode.BC_getstatic_l.class,
        jtt.bytecode.BC_getstatic_s.class,
        jtt.bytecode.BC_getstatic_z.class,
        jtt.bytecode.BC_i2b.class,
        jtt.bytecode.BC_i2c.class,
        jtt.bytecode.BC_i2d.class,
        jtt.bytecode.BC_i2f.class,
        jtt.bytecode.BC_i2l.class,
        jtt.bytecode.BC_i2s.class,
        jtt.bytecode.BC_iadd.class,
        jtt.bytecode.BC_iadd2.class,
        jtt.bytecode.BC_iadd3.class,
        jtt.bytecode.BC_iaload.class,
        jtt.bytecode.BC_iand.class,
        jtt.bytecode.BC_iastore.class,
        jtt.bytecode.BC_iconst.class,
        jtt.bytecode.BC_idiv.class,
        jtt.bytecode.BC_idiv2.class,
        jtt.bytecode.BC_ifeq.class,
        jtt.bytecode.BC_ifeq_2.class,
        jtt.bytecode.BC_ifeq_3.class,
        jtt.bytecode.BC_ifge.class,
        jtt.bytecode.BC_ifge_2.class,
        jtt.bytecode.BC_ifge_3.class,
        jtt.bytecode.BC_ifgt.class,
        jtt.bytecode.BC_ificmplt1.class,
        jtt.bytecode.BC_ificmplt2.class,
        jtt.bytecode.BC_ificmpne1.class,
        jtt.bytecode.BC_ificmpne2.class,
        jtt.bytecode.BC_ifle.class,
        jtt.bytecode.BC_iflt.class,
        jtt.bytecode.BC_ifne.class,
        jtt.bytecode.BC_ifnonnull.class,
        jtt.bytecode.BC_ifnonnull_2.class,
        jtt.bytecode.BC_ifnonnull_3.class,
        jtt.bytecode.BC_ifnull.class,
        jtt.bytecode.BC_ifnull_2.class,
        jtt.bytecode.BC_ifnull_3.class,
        jtt.bytecode.BC_iinc_1.class,
        jtt.bytecode.BC_iinc_2.class,
        jtt.bytecode.BC_iinc_3.class,
        jtt.bytecode.BC_iinc_4.class,
        jtt.bytecode.BC_iload_0.class,
        jtt.bytecode.BC_iload_0_1.class,
        jtt.bytecode.BC_iload_0_2.class,
        jtt.bytecode.BC_iload_1.class,
        jtt.bytecode.BC_iload_1_1.class,
        jtt.bytecode.BC_iload_2.class,
        jtt.bytecode.BC_iload_3.class,
        jtt.bytecode.BC_imul.class,
        jtt.bytecode.BC_ineg.class,
        jtt.bytecode.BC_instanceof.class,
        jtt.bytecode.BC_invokeinterface.class,
        jtt.bytecode.BC_invokespecial.class,
        jtt.bytecode.BC_invokespecial2.class,
        jtt.bytecode.BC_invokestatic.class,
        jtt.bytecode.BC_invokestatic_clinit.class,
        jtt.bytecode.BC_invokevirtual.class,
        jtt.bytecode.BC_ior.class,
        jtt.bytecode.BC_irem.class,
        jtt.bytecode.BC_irem2.class,
        jtt.bytecode.BC_ireturn.class,
        jtt.bytecode.BC_ishl.class,
        jtt.bytecode.BC_ishr.class,
        jtt.bytecode.BC_isub.class,
        jtt.bytecode.BC_iushr.class,
        jtt.bytecode.BC_ixor.class,
        jtt.bytecode.BC_l2d.class,
        jtt.bytecode.BC_l2f.class,
        jtt.bytecode.BC_l2i.class,
        jtt.bytecode.BC_ladd.class,
        jtt.bytecode.BC_ladd2.class,
        jtt.bytecode.BC_laload.class,
        jtt.bytecode.BC_land.class,
        jtt.bytecode.BC_lastore.class,
        jtt.bytecode.BC_lcmp.class,
        jtt.bytecode.BC_ldc_01.class,
        jtt.bytecode.BC_ldc_02.class,
        jtt.bytecode.BC_ldc_03.class,
        jtt.bytecode.BC_ldc_04.class,
        jtt.bytecode.BC_ldc_05.class,
        jtt.bytecode.BC_ldc_06.class,
        jtt.bytecode.BC_ldiv.class,
        jtt.bytecode.BC_ldiv2.class,
        jtt.bytecode.BC_lload_0.class,
        jtt.bytecode.BC_lload_01.class,
        jtt.bytecode.BC_lload_1.class,
        jtt.bytecode.BC_lload_2.class,
        jtt.bytecode.BC_lload_3.class,
        jtt.bytecode.BC_lmul.class,
        jtt.bytecode.BC_lneg.class,
        jtt.bytecode.BC_lookupswitch01.class,
        jtt.bytecode.BC_lookupswitch02.class,
        jtt.bytecode.BC_lookupswitch03.class,
        jtt.bytecode.BC_lookupswitch04.class,
        jtt.bytecode.BC_lor.class,
        jtt.bytecode.BC_lrem.class,
        jtt.bytecode.BC_lrem2.class,
        jtt.bytecode.BC_lreturn.class,
        jtt.bytecode.BC_lshl.class,
        jtt.bytecode.BC_lshr.class,
        jtt.bytecode.BC_lsub.class,
        jtt.bytecode.BC_lushr.class,
        jtt.bytecode.BC_lxor.class,
        jtt.bytecode.BC_monitorenter.class,
        jtt.bytecode.BC_multianewarray01.class,
        jtt.bytecode.BC_multianewarray02.class,
        jtt.bytecode.BC_multianewarray03.class,
        jtt.bytecode.BC_multianewarray04.class,
        jtt.bytecode.BC_new.class,
        jtt.bytecode.BC_new_clinit.class,
        jtt.bytecode.BC_newarray.class,
        jtt.bytecode.BC_putfield.class,
        jtt.bytecode.BC_putstatic.class,
        jtt.bytecode.BC_putstatic_clinit.class,
        jtt.bytecode.BC_saload.class,
        jtt.bytecode.BC_sastore.class,
        jtt.bytecode.BC_tableswitch.class,
        jtt.bytecode.BC_tableswitch2.class,
        jtt.bytecode.BC_tableswitch3.class,
        jtt.bytecode.BC_tableswitch4.class,
        jtt.bytecode.BC_wide01.class,
        jtt.bytecode.BC_wide02.class,
        jtt.except.BC_aaload0.class,
        jtt.except.BC_aaload1.class,
        jtt.except.BC_aastore0.class,
        jtt.except.BC_aastore1.class,
        jtt.except.BC_anewarray.class,
        jtt.except.BC_arraylength.class,
        jtt.except.BC_athrow0.class,
        jtt.except.BC_athrow1.class,
        jtt.except.BC_athrow2.class,
        jtt.except.BC_athrow3.class,
        jtt.except.BC_baload.class,
        jtt.except.BC_bastore.class,
        jtt.except.BC_caload.class,
        jtt.except.BC_castore.class,
        jtt.except.BC_checkcast.class,
        jtt.except.BC_checkcast1.class,
        jtt.except.BC_checkcast2.class,
        jtt.except.BC_checkcast3.class,
        jtt.except.BC_checkcast4.class,
        jtt.except.BC_checkcast5.class,
        jtt.except.BC_checkcast6.class,
        jtt.except.BC_daload.class,
        jtt.except.BC_dastore.class,
        jtt.except.BC_faload.class,
        jtt.except.BC_fastore.class,
        jtt.except.BC_getfield.class,
        jtt.except.BC_iaload.class,
        jtt.except.BC_iastore.class,
        jtt.except.BC_idiv.class,
        jtt.except.BC_idiv2.class,
        jtt.except.BC_invokespecial01.class,
        jtt.except.BC_invokevirtual01.class,
        jtt.except.BC_invokevirtual02.class,
        jtt.except.BC_irem.class,
        jtt.except.BC_laload.class,
        jtt.except.BC_lastore.class,
        jtt.except.BC_ldiv.class,
        jtt.except.BC_ldiv2.class,
        jtt.except.BC_lrem.class,
        jtt.except.BC_monitorenter.class,
        jtt.except.BC_multianewarray.class,
        jtt.except.BC_newarray.class,
        jtt.except.BC_putfield.class,
        jtt.except.BC_saload.class,
        jtt.except.BC_sastore.class,
        jtt.except.Catch_Loop01.class,
        jtt.except.Catch_Loop02.class,
        jtt.except.Catch_NASE_1.class,
        jtt.except.Catch_NASE_2.class,
        jtt.except.Catch_NPE_00.class,
        jtt.except.Catch_NPE_01.class,
        jtt.except.Catch_NPE_02.class,
        jtt.except.Catch_NPE_03.class,
        jtt.except.Catch_NPE_04.class,
        jtt.except.Catch_NPE_05.class,
        jtt.except.Catch_NPE_06.class,
        jtt.except.Catch_NPE_07.class,
        jtt.except.Catch_NPE_08.class,
        jtt.except.Catch_NPE_09.class,
        jtt.except.Catch_NPE_10.class,
        jtt.except.Catch_NPE_11.class,
        jtt.except.Catch_StackOverflowError_01.class,
        jtt.except.Catch_StackOverflowError_02.class,
        jtt.except.Catch_StackOverflowError_03.class,
        jtt.except.Catch_Two01.class,
        jtt.except.Catch_Two02.class,
        jtt.except.Catch_Two03.class,
        jtt.except.Except_Synchronized01.class,
        jtt.except.Except_Synchronized02.class,
        jtt.except.Except_Synchronized03.class,
        jtt.except.Except_Synchronized04.class,
        jtt.except.Except_Synchronized05.class,
        jtt.except.Finally01.class,
        jtt.except.Finally02.class,
        jtt.except.StackTrace_AIOOBE_00.class,
        jtt.except.StackTrace_CCE_00.class,
        jtt.except.StackTrace_NPE_00.class,
        jtt.except.StackTrace_NPE_01.class,
        jtt.except.StackTrace_NPE_02.class,
        jtt.except.StackTrace_NPE_03.class,
        jtt.except.Throw_InCatch01.class,
        jtt.except.Throw_InCatch02.class,
        jtt.except.Throw_InCatch03.class,
        jtt.except.Throw_NPE_01.class,
        jtt.except.Throw_Synchronized01.class,
        jtt.except.Throw_Synchronized02.class,
        jtt.except.Throw_Synchronized03.class,
        jtt.except.Throw_Synchronized04.class,
        jtt.except.Throw_Synchronized05.class,
        jtt.hotpath.HP_allocate01.class,
        jtt.hotpath.HP_allocate02.class,
        jtt.hotpath.HP_allocate03.class,
        jtt.hotpath.HP_allocate04.class,
        jtt.hotpath.HP_array01.class,
        jtt.hotpath.HP_array02.class,
        jtt.hotpath.HP_array03.class,
        jtt.hotpath.HP_array04.class,
        jtt.hotpath.HP_control01.class,
        jtt.hotpath.HP_control02.class,
        jtt.hotpath.HP_convert01.class,
        jtt.hotpath.HP_count.class,
        jtt.hotpath.HP_dead01.class,
        jtt.hotpath.HP_demo01.class,
        jtt.hotpath.HP_field01.class,
        jtt.hotpath.HP_field02.class,
        jtt.hotpath.HP_field03.class,
        jtt.hotpath.HP_field04.class,
        jtt.hotpath.HP_idea.class,
        jtt.hotpath.HP_inline01.class,
        jtt.hotpath.HP_inline02.class,
        jtt.hotpath.HP_invoke01.class,
        jtt.hotpath.HP_life.class,
        jtt.hotpath.HP_nest01.class,
        jtt.hotpath.HP_nest02.class,
        jtt.hotpath.HP_scope01.class,
        jtt.hotpath.HP_scope02.class,
        jtt.hotpath.HP_series.class,
        jtt.hotpath.HP_trees01.class,
        jtt.jasm.BC_dcmpg.class,
        jtt.jasm.BC_dcmpg2.class,
        jtt.jasm.BC_dcmpl.class,
        jtt.jasm.BC_dcmpl2.class,
        jtt.jasm.BC_fcmpg.class,
        jtt.jasm.BC_fcmpg2.class,
        jtt.jasm.BC_fcmpl.class,
        jtt.jasm.BC_fcmpl2.class,
        jtt.jasm.BC_lcmp.class,
        jtt.jasm.Invokevirtual_private00.class,
        jtt.jasm.Invokevirtual_private01.class,
        jtt.jasm.Loop00.class,
        jtt.jdk.Class_getName.class,
        jtt.jdk.EnumMap01.class,
        jtt.jdk.EnumMap02.class,
        jtt.jdk.System_currentTimeMillis01.class,
        jtt.jdk.System_currentTimeMillis02.class,
        jtt.jdk.System_nanoTime01.class,
        jtt.jdk.System_nanoTime02.class,
        jtt.jdk.UnsafeAccess01.class,
        jtt.jni.JNI_IdentityBoolean.class,
        jtt.jni.JNI_IdentityByte.class,
        jtt.jni.JNI_IdentityChar.class,
        jtt.jni.JNI_IdentityFloat.class,
        jtt.jni.JNI_IdentityInt.class,
        jtt.jni.JNI_IdentityLong.class,
        jtt.jni.JNI_IdentityObject.class,
        jtt.jni.JNI_IdentityShort.class,
        jtt.jni.JNI_ManyObjectParameters.class,
        jtt.jni.JNI_ManyParameters.class,
        jtt.jni.JNI_Nop.class,
        jtt.jni.JNI_OverflowArguments.class,
        jtt.jvmni.JVM_ArrayCopy01.class,
        jtt.jvmni.JVM_GetClassContext01.class,
        jtt.jvmni.JVM_GetClassContext02.class,
        jtt.jvmni.JVM_GetFreeMemory01.class,
        jtt.jvmni.JVM_GetMaxMemory01.class,
        jtt.jvmni.JVM_GetTotalMemory01.class,
        jtt.jvmni.JVM_IsNaN01.class,
        jtt.lang.Boxed_TYPE_01.class,
        jtt.lang.Bridge_method01.class,
        jtt.lang.ClassLoader_loadClass01.class,
        jtt.lang.Class_Literal01.class,
        jtt.lang.Class_asSubclass01.class,
        jtt.lang.Class_cast01.class,
        jtt.lang.Class_cast02.class,
        jtt.lang.Class_forName01.class,
        jtt.lang.Class_forName02.class,
        jtt.lang.Class_forName03.class,
        jtt.lang.Class_forName04.class,
        jtt.lang.Class_forName05.class,
        jtt.lang.Class_getComponentType01.class,
        jtt.lang.Class_getInterfaces01.class,
        jtt.lang.Class_getName01.class,
        jtt.lang.Class_getName02.class,
        jtt.lang.Class_getSimpleName01.class,
        jtt.lang.Class_getSimpleName02.class,
        jtt.lang.Class_getSuperClass01.class,
        jtt.lang.Class_isArray01.class,
        jtt.lang.Class_isAssignableFrom01.class,
        jtt.lang.Class_isAssignableFrom02.class,
        jtt.lang.Class_isAssignableFrom03.class,
        jtt.lang.Class_isInstance01.class,
        jtt.lang.Class_isInstance02.class,
        jtt.lang.Class_isInstance03.class,
        jtt.lang.Class_isInstance04.class,
        jtt.lang.Class_isInstance05.class,
        jtt.lang.Class_isInstance06.class,
        jtt.lang.Class_isInterface01.class,
        jtt.lang.Class_isPrimitive01.class,
        jtt.lang.Double_toString.class,
        jtt.lang.Float_01.class,
        jtt.lang.Float_02.class,
        jtt.lang.Int_greater01.class,
        jtt.lang.Int_greater02.class,
        jtt.lang.Int_greater03.class,
        jtt.lang.Int_greaterEqual01.class,
        jtt.lang.Int_greaterEqual02.class,
        jtt.lang.Int_greaterEqual03.class,
        jtt.lang.Int_less01.class,
        jtt.lang.Int_less02.class,
        jtt.lang.Int_less03.class,
        jtt.lang.Int_lessEqual01.class,
        jtt.lang.Int_lessEqual02.class,
        jtt.lang.Int_lessEqual03.class,
        jtt.lang.JDK_ClassLoaders01.class,
        jtt.lang.JDK_ClassLoaders02.class,
        jtt.lang.Long_greater01.class,
        jtt.lang.Long_greater02.class,
        jtt.lang.Long_greater03.class,
        jtt.lang.Long_greaterEqual01.class,
        jtt.lang.Long_greaterEqual02.class,
        jtt.lang.Long_greaterEqual03.class,
        jtt.lang.Long_less01.class,
        jtt.lang.Long_less02.class,
        jtt.lang.Long_less03.class,
        jtt.lang.Long_lessEqual01.class,
        jtt.lang.Long_lessEqual02.class,
        jtt.lang.Long_lessEqual03.class,
        jtt.lang.Long_reverseBytes01.class,
        jtt.lang.Long_reverseBytes02.class,
        jtt.lang.Math_pow.class,
        jtt.lang.Object_clone01.class,
        jtt.lang.Object_clone02.class,
        jtt.lang.Object_equals01.class,
        jtt.lang.Object_getClass01.class,
        jtt.lang.Object_hashCode01.class,
        jtt.lang.Object_notify01.class,
        jtt.lang.Object_notify02.class,
        jtt.lang.Object_notifyAll01.class,
        jtt.lang.Object_notifyAll02.class,
        jtt.lang.Object_toString01.class,
        jtt.lang.Object_toString02.class,
        jtt.lang.Object_wait01.class,
        jtt.lang.Object_wait02.class,
        jtt.lang.Object_wait03.class,
        jtt.lang.ProcessEnvironment_init.class,
        jtt.lang.StringCoding_Scale.class,
        jtt.lang.String_intern01.class,
        jtt.lang.String_intern02.class,
        jtt.lang.String_intern03.class,
        jtt.lang.String_valueOf01.class,
        jtt.lang.System_identityHashCode01.class,
        jtt.loop.Loop01.class,
        jtt.loop.Loop02.class,
        jtt.loop.Loop03.class,
        jtt.loop.Loop04.class,
        jtt.loop.Loop05.class,
        jtt.loop.Loop06.class,
        jtt.loop.LoopSwitch01.class,
        jtt.max.Fold01.class,
        jtt.max.Fold02.class,
        jtt.max.Fold03.class,
        jtt.max.Hub_Subtype01.class,
        jtt.max.Hub_Subtype02.class,
        jtt.max.ImmortalHeap_allocation.class,
        jtt.max.ImmortalHeap_gc.class,
        jtt.max.ImmortalHeap_switching.class,
        jtt.max.Inline01.class,
        jtt.max.Invoke_except01.class,
        jtt.max.Prototyping01.class,
        jtt.max.Unsigned_idiv01.class,
        jtt.max.Unsigned_irem01.class,
        jtt.max.Unsigned_ldiv01.class,
        jtt.max.Unsigned_lrem01.class,
        jtt.micro.ArrayCompare01.class,
        jtt.micro.ArrayCompare02.class,
        jtt.micro.BC_invokevirtual2.class,
        jtt.micro.BigByteParams01.class,
        jtt.micro.BigDoubleParams02.class,
        jtt.micro.BigFloatParams01.class,
        jtt.micro.BigFloatParams02.class,
        jtt.micro.BigIntParams01.class,
        jtt.micro.BigIntParams02.class,
        jtt.micro.BigInterfaceParams01.class,
        jtt.micro.BigLongParams02.class,
        jtt.micro.BigMixedParams01.class,
        jtt.micro.BigMixedParams02.class,
        jtt.micro.BigMixedParams03.class,
        jtt.micro.BigObjectParams01.class,
        jtt.micro.BigObjectParams02.class,
        jtt.micro.BigParamsAlignment.class,
        jtt.micro.BigShortParams01.class,
        jtt.micro.BigVirtualParams01.class,
        jtt.micro.Bubblesort.class,
        jtt.micro.Fibonacci.class,
        jtt.micro.InvokeVirtual_01.class,
        jtt.micro.InvokeVirtual_02.class,
        jtt.micro.Matrix01.class,
        jtt.micro.StrangeFrames.class,
        jtt.micro.String_format01.class,
        jtt.micro.String_format02.class,
        jtt.micro.VarArgs_String01.class,
        jtt.micro.VarArgs_boolean01.class,
        jtt.micro.VarArgs_byte01.class,
        jtt.micro.VarArgs_char01.class,
        jtt.micro.VarArgs_double01.class,
        jtt.micro.VarArgs_float01.class,
        jtt.micro.VarArgs_int01.class,
        jtt.micro.VarArgs_long01.class,
        jtt.micro.VarArgs_short01.class,
        jtt.optimize.ArrayLength01.class,
        jtt.optimize.BC_idiv_16.class,
        jtt.optimize.BC_idiv_4.class,
        jtt.optimize.BC_imul_16.class,
        jtt.optimize.BC_imul_4.class,
        jtt.optimize.BC_ldiv_16.class,
        jtt.optimize.BC_ldiv_4.class,
        jtt.optimize.BC_lmul_16.class,
        jtt.optimize.BC_lmul_4.class,
        jtt.optimize.BC_lshr_C16.class,
        jtt.optimize.BC_lshr_C24.class,
        jtt.optimize.BC_lshr_C32.class,
        jtt.optimize.BlockSkip01.class,
        jtt.optimize.DeadCode01.class,
        jtt.optimize.Fold_Cast01.class,
        jtt.optimize.Fold_Convert01.class,
        jtt.optimize.Fold_Convert02.class,
        jtt.optimize.Fold_Convert03.class,
        jtt.optimize.Fold_Convert04.class,
        jtt.optimize.Fold_Double01.class,
        jtt.optimize.Fold_Double02.class,
        jtt.optimize.Fold_Float01.class,
        jtt.optimize.Fold_Float02.class,
        jtt.optimize.Fold_InstanceOf01.class,
        jtt.optimize.Fold_Int01.class,
        jtt.optimize.Fold_Int02.class,
        jtt.optimize.Fold_Long01.class,
        jtt.optimize.Fold_Long02.class,
        jtt.optimize.Fold_Math01.class,
        jtt.optimize.Inline01.class,
        jtt.optimize.Inline02.class,
        jtt.optimize.List_reorder_bug.class,
        jtt.optimize.NCE_01.class,
        jtt.optimize.NCE_02.class,
        jtt.optimize.NCE_03.class,
        jtt.optimize.NCE_04.class,
        jtt.optimize.NCE_FlowSensitive01.class,
        jtt.optimize.NCE_FlowSensitive02.class,
        jtt.optimize.NCE_FlowSensitive03.class,
        jtt.optimize.NCE_FlowSensitive04.class,
        jtt.optimize.Narrow_byte01.class,
        jtt.optimize.Narrow_byte02.class,
        jtt.optimize.Narrow_byte03.class,
        jtt.optimize.Narrow_char01.class,
        jtt.optimize.Narrow_char02.class,
        jtt.optimize.Narrow_char03.class,
        jtt.optimize.Narrow_short01.class,
        jtt.optimize.Narrow_short02.class,
        jtt.optimize.Narrow_short03.class,
        jtt.optimize.Phi01.class,
        jtt.optimize.Phi02.class,
        jtt.optimize.Phi03.class,
        jtt.optimize.Reduce_Convert01.class,
        jtt.optimize.Reduce_Double01.class,
        jtt.optimize.Reduce_Float01.class,
        jtt.optimize.Reduce_Int01.class,
        jtt.optimize.Reduce_Int02.class,
        jtt.optimize.Reduce_Int03.class,
        jtt.optimize.Reduce_Int04.class,
        jtt.optimize.Reduce_IntShift01.class,
        jtt.optimize.Reduce_IntShift02.class,
        jtt.optimize.Reduce_Long01.class,
        jtt.optimize.Reduce_Long02.class,
        jtt.optimize.Reduce_Long03.class,
        jtt.optimize.Reduce_Long04.class,
        jtt.optimize.Reduce_LongShift01.class,
        jtt.optimize.Reduce_LongShift02.class,
        jtt.optimize.Switch01.class,
        jtt.optimize.Switch02.class,
        jtt.optimize.TypeCastElem.class,
        jtt.optimize.VN_Cast01.class,
        jtt.optimize.VN_Cast02.class,
        jtt.optimize.VN_Convert01.class,
        jtt.optimize.VN_Convert02.class,
        jtt.optimize.VN_Double01.class,
        jtt.optimize.VN_Double02.class,
        jtt.optimize.VN_Field01.class,
        jtt.optimize.VN_Field02.class,
        jtt.optimize.VN_Float01.class,
        jtt.optimize.VN_Float02.class,
        jtt.optimize.VN_InstanceOf01.class,
        jtt.optimize.VN_InstanceOf02.class,
        jtt.optimize.VN_InstanceOf03.class,
        jtt.optimize.VN_Int01.class,
        jtt.optimize.VN_Int02.class,
        jtt.optimize.VN_Int03.class,
        jtt.optimize.VN_Long01.class,
        jtt.optimize.VN_Long02.class,
        jtt.optimize.VN_Long03.class,
        jtt.optimize.VN_Loop01.class,
        jtt.reflect.Array_get01.class,
        jtt.reflect.Array_get02.class,
        jtt.reflect.Array_get03.class,
        jtt.reflect.Array_getBoolean01.class,
        jtt.reflect.Array_getByte01.class,
        jtt.reflect.Array_getChar01.class,
        jtt.reflect.Array_getDouble01.class,
        jtt.reflect.Array_getFloat01.class,
        jtt.reflect.Array_getInt01.class,
        jtt.reflect.Array_getLength01.class,
        jtt.reflect.Array_getLong01.class,
        jtt.reflect.Array_getShort01.class,
        jtt.reflect.Array_newInstance01.class,
        jtt.reflect.Array_newInstance02.class,
        jtt.reflect.Array_newInstance03.class,
        jtt.reflect.Array_newInstance04.class,
        jtt.reflect.Array_newInstance05.class,
        jtt.reflect.Array_newInstance06.class,
        jtt.reflect.Array_set01.class,
        jtt.reflect.Array_set02.class,
        jtt.reflect.Array_set03.class,
        jtt.reflect.Array_setBoolean01.class,
        jtt.reflect.Array_setByte01.class,
        jtt.reflect.Array_setChar01.class,
        jtt.reflect.Array_setDouble01.class,
        jtt.reflect.Array_setFloat01.class,
        jtt.reflect.Array_setInt01.class,
        jtt.reflect.Array_setLong01.class,
        jtt.reflect.Array_setShort01.class,
        jtt.reflect.Class_getDeclaredField01.class,
        jtt.reflect.Class_getDeclaredMethod01.class,
        jtt.reflect.Class_getField01.class,
        jtt.reflect.Class_getField02.class,
        jtt.reflect.Class_getMethod01.class,
        jtt.reflect.Class_getMethod02.class,
        jtt.reflect.Class_newInstance01.class,
        jtt.reflect.Class_newInstance02.class,
        jtt.reflect.Class_newInstance03.class,
        jtt.reflect.Class_newInstance06.class,
        jtt.reflect.Class_newInstance07.class,
        jtt.reflect.Field_get01.class,
        jtt.reflect.Field_get02.class,
        jtt.reflect.Field_get03.class,
        jtt.reflect.Field_get04.class,
        jtt.reflect.Field_getType01.class,
        jtt.reflect.Field_set01.class,
        jtt.reflect.Field_set02.class,
        jtt.reflect.Field_set03.class,
        jtt.reflect.Invoke_except01.class,
        jtt.reflect.Invoke_main01.class,
        jtt.reflect.Invoke_main02.class,
        jtt.reflect.Invoke_main03.class,
        jtt.reflect.Invoke_virtual01.class,
        jtt.reflect.Method_getParameterTypes01.class,
        jtt.reflect.Method_getReturnType01.class,
        jtt.reflect.Reflection_getCallerClass01.class,
        jtt.threads.Monitor_contended01.class,
        jtt.threads.Monitor_notowner01.class,
        jtt.threads.Monitorenter01.class,
        jtt.threads.Monitorenter02.class,
        jtt.threads.Object_wait01.class,
        jtt.threads.Object_wait02.class,
        jtt.threads.Object_wait03.class,
        jtt.threads.Object_wait04.class,
        jtt.threads.ThreadLocal01.class,
        jtt.threads.ThreadLocal02.class,
        jtt.threads.ThreadLocal03.class,
        jtt.threads.Thread_currentThread01.class,
        jtt.threads.Thread_getState01.class,
        jtt.threads.Thread_getState02.class,
        jtt.threads.Thread_holdsLock01.class,
        jtt.threads.Thread_isAlive01.class,
        jtt.threads.Thread_isInterrupted01.class,
        jtt.threads.Thread_isInterrupted02.class,
        jtt.threads.Thread_isInterrupted03.class,
        jtt.threads.Thread_isInterrupted04.class,
        jtt.threads.Thread_isInterrupted05.class,
        jtt.threads.Thread_join01.class,
        jtt.threads.Thread_join02.class,
        jtt.threads.Thread_join03.class,
        jtt.threads.Thread_new01.class,
        jtt.threads.Thread_new02.class,
        jtt.threads.Thread_setPriority01.class,
        jtt.threads.Thread_sleep01.class,
        jtt.threads.Thread_start01.class,
        jtt.threads.Thread_yield01.class
    };
// END GENERATED TEST CLASS LIST

    public static final JTClasses testClasses = new JTClasses(classList, JTRuns.class);

}
