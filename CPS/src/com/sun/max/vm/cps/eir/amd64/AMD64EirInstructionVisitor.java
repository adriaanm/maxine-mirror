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
package com.sun.max.vm.cps.eir.amd64;

import com.sun.max.vm.cps.eir.*;
import com.sun.max.vm.cps.eir.amd64.AMD64EirInstruction.*;

/**
 * @author Bernd Mathiske
 */
public interface AMD64EirInstructionVisitor extends EirInstructionVisitor {
    void visit(AMD64EirLoad instruction);
    void visit(AMD64EirStore instruction);
    void visit(AMD64EirCompareAndSwap instruction);

    void visit(ADD_I32 instruction);
    void visit(ADD_I64 instruction);
    void visit(ADDSD instruction);
    void visit(ADDSS instruction);
    void visit(AND_I32 instruction);
    void visit(AND_I64 instruction);
    void visit(BSF_I64 instruction);
    void visit(BSR_I64 instruction);
    void visit(CDQ instruction);
    void visit(CMOVA_I32 instruction);
    void visit(CMOVB_I32 instruction);
    void visit(CMOVE_I32 instruction);
    void visit(CMOVAE_I32 instruction);
    void visit(CMOVL_I32 instruction);
    void visit(CMOVBE_I32 instruction);
    void visit(CMOVP_I32 instruction);
    void visit(CMP_I32 instruction);
    void visit(CMP_I64 instruction);
    void visit(CMPSD instruction);
    void visit(CMPSS instruction);
    void visit(COMISD instruction);
    void visit(COMISS instruction);
    void visit(CQO instruction);
    void visit(CVTTSD2SI_I32 instruction);
    void visit(CVTTSD2SI_I64 instruction);
    void visit(CVTSD2SS instruction);
    void visit(CVTSI2SD_I32 instruction);
    void visit(CVTSI2SD_I64 instruction);
    void visit(CVTSI2SS_I32 instruction);
    void visit(CVTSI2SS_I64 instruction);
    void visit(CVTSS2SD instruction);
    void visit(CVTTSS2SI_I32 instruction);
    void visit(CVTTSS2SI_I64 instruction);
    void visit(DEC_I64 instruction);
    void visit(DIV_I64 instruction);
    void visit(DIVSD instruction);
    void visit(DIVSS instruction);
    void visit(IDIV_I32 instruction);
    void visit(IDIV_I64 instruction);
    void visit(IMUL_I32 instruction);
    void visit(IMUL_I64 instruction);
    void visit(JMP instruction);
    void visit(JMP_indirect instruction);
    void visit(JA instruction);
    void visit(JAE instruction);
    void visit(JB instruction);
    void visit(JBE instruction);
    void visit(JG instruction);
    void visit(JGE instruction);
    void visit(JL instruction);
    void visit(JLE instruction);
    void visit(JNZ instruction);
    void visit(JZ instruction);
    void visit(LEA_STACK_ADDRESS instruction);
    void visit(LFENCE instruction);
    void visit(MFENCE instruction);
    void visit(PAUSE instruction);
    void visit(MOVD_I32_F32 instruction);
    void visit(MOVD_I64_F64 instruction);
    void visit(MOVD_F32_I32 instruction);
    void visit(MOVD_F64_I64 instruction);
    void visit(MOVSX_I8 instruction);
    void visit(MOVSX_I16 instruction);
    void visit(MOVSXD instruction);
    void visit(MOVZX_I16 instruction);
    void visit(MOVZXD instruction);
    void visit(MULSD instruction);
    void visit(MULSS instruction);
    void visit(NEG_I32 instruction);
    void visit(NEG_I64 instruction);
    void visit(NOT_I32 instruction);
    void visit(NOT_I64 instruction);
    void visit(OR_I32 instruction);
    void visit(OR_I64 instruction);
    void visit(POP instruction);
    void visit(PUSH instruction);
    void visit(RET instruction);
    void visit(SAL_I32 instruction);
    void visit(SAL_I64 instruction);
    void visit(SAR_I32 instruction);
    void visit(SAR_I64 instruction);
    void visit(SETB instruction);
    void visit(SETBE instruction);
    void visit(SETL instruction);
    void visit(SETNB instruction);
    void visit(SETNBE instruction);
    void visit(SETNLE instruction);
    void visit(SETP instruction);
    void visit(SETNP instruction);
    void visit(SFENCE instruction);
    void visit(SHR_I32 instruction);
    void visit(SHR_I64 instruction);
    void visit(STACK_ALLOCATE instruction);
    void visit(SUB_I32 instruction);
    void visit(SUB_I64 instruction);
    void visit(SUBSD instruction);
    void visit(SUBSS instruction);
    void visit(SWITCH_I32 instruction);
    void visit(XCHG instruction);
    void visit(XOR_I32 instruction);
    void visit(XOR_I64 instruction);
    void visit(XORPD instruction);
    void visit(XORPS instruction);
    void visit(ZERO instruction);
}
