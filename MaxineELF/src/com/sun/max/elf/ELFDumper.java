/*
 * Copyright (c) 2009 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, U.S.A. All rights reserved.
 * 
 * U.S. Government Rights - Commercial software. Government users are
 * subject to the Sun Microsystems, Inc. standard license agreement and
 * applicable provisions of the FAR and its supplements.
 * 
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties.
 * 
 * Parts of the product may be derived from Berkeley BSD systems,
 * licensed from the University of California. UNIX is a registered
 * trademark in the U.S.  and in other countries, exclusively licensed
 * through X/Open Company, Ltd.
 * 
 * Sun, Sun Microsystems, the Sun logo and Java are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other
 * countries.
 * 
 * This product is covered and controlled by U.S. Export Control laws and
 * may be subject to the export or import laws in other
 * countries. Nuclear, missile, chemical biological weapons or nuclear
 * maritime end uses or end users, whether direct or indirect, are
 * strictly prohibited. Export or reexport to countries subject to
 * U.S. embargo or to entities identified on U.S. export exclusion lists,
 * including, but not limited to, the denied persons and specially
 * designated nationals lists is strictly prohibited.
 * 
 */
/**
 * Copyright (c) 2006, Regents of the University of California
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * Neither the name of the University of California, Los Angeles nor the
 * names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * Created Sep 30, 2006
 */
package com.sun.max.elf;

import com.sun.max.program.ProgramError;

import static com.sun.max.elf.StringUtil.leftJustify;
import static com.sun.max.elf.StringUtil.rightJustify;

import java.io.RandomAccessFile;

/**
 * The {@code ELFDumper} is a class that can load and display information
 * about ELF files.
 *
 * @author Ben L. Titzer
 */
public final class ELFDumper {

    private ELFDumper() {

    }

    public static void main(String[] args) throws Exception {

        if (args.length < 1) {
            System.out.println("usage: elf-dumper <filename>");
            return;
        }

        final String fname = args[0];

        final RandomAccessFile fis = new RandomAccessFile(fname, "r");

        try {
            // read the ELF header
            final ELFHeader header = ELFLoader.readELFHeader(fis);
            printHeader(header);

            // read the program header table (if it exists)
            final ELFProgramHeaderTable pht = ELFLoader.readPHT(fis, header);
            printPHT(pht);

            // read the section header table
            final ELFSectionHeaderTable sht = ELFLoader.readSHT(fis, header);
            printSHT(sht);

            // read the symbol tables
            for (ELFSymbolTable stab : ELFLoader.readSymbolTables(fis, header, sht)) {
                printSymbolTable(stab, sht);
            }

        } catch (ELFHeader.FormatError e) {
            throw ProgramError.unexpected("Invalid ELF file", e);
        }
    }

    public static void printHeader(ELFHeader header) {
        nextln();
        printSeparator();
        println("Ver Machine     Arch     Size  Endian");
        printThinSeparator();
        print(rightJustify(header.e_version, 3));
        print(rightJustify(header.e_machine, 8));
        print(rightJustify(header.getArchitecture(), 9));
        print(rightJustify(header.is64Bit() ? "64 bits" : "32 bits", 9));
        print(header.isLittleEndian() ? "  little" : "  big");
        nextln();
    }

    public static void printSHT(ELFSectionHeaderTable sht) {
        println("Section Header Table");
        printSeparator();
        print("Ent  Name                        Type   Address  Offset    Size  Flags");
        nextln();
        printThinSeparator();
        for (int cntr = 0; cntr < sht.entries.length; cntr++) {
            final ELFSectionHeaderTable.Entry e = sht.entries[cntr];
            print(rightJustify(cntr, 3));
            print("  " + leftJustify(e.getName(), 24));
            print(rightJustify(e.getType(), 11));
            if (e.is32Bit()) {
                final ELFSectionHeaderTable.Entry32 e32 = (ELFSectionHeaderTable.Entry32) e;
                print("  " + toHex(e32.sh_addr));
                print(rightJustify(e32.sh_offset, 8));
                print(rightJustify(e32.sh_size, 8));
            } else {
                final ELFSectionHeaderTable.Entry64 e64 = (ELFSectionHeaderTable.Entry64) e;
                print("  " + toHex(e64.sh_addr));
                print(rightJustify(e64.sh_offset, 8));
                print(rightJustify(e64.sh_size, 8));
            }
            print("  " + e.getFlagString());
            nextln();
        }
        nextln();
    }

    public static String getName(ELFStringTable st, int ind) {
        if (st == null) {
            return "";
        }
        return st.getString(ind);
    }

    public static void printPHT(ELFProgramHeaderTable pht) {
        println("Program Header Table");
        printSeparator();
        print("Ent     Type  Virtual   Physical  Offset  Filesize  Memsize  Flags");
        nextln();
        printThinSeparator();
        for (int cntr = 0; cntr < pht.entries.length; cntr++) {
            final ELFProgramHeaderTable.Entry e = pht.entries[cntr];
            print(rightJustify(cntr, 3));
            print(rightJustify(ELFProgramHeaderTable.getType(e), 9));
            if (e.is32Bit()) {
                final ELFProgramHeaderTable.Entry32 e32 = (ELFProgramHeaderTable.Entry32) e;
                print("  " + toHex(e32.p_vaddr));
                print("  " + toHex(e32.p_paddr));
                print(rightJustify(e32.p_offset, 8));
                print(rightJustify(e32.p_filesz, 10));
                print(rightJustify(e32.p_memsz, 9));
            } else {
                final ELFProgramHeaderTable.Entry64 e64 = (ELFProgramHeaderTable.Entry64) e;
                print("  " + toHex(e64.p_vaddr));
                print("  " + toHex(e64.p_paddr));
                print(rightJustify(e64.p_offset, 8));
                print(rightJustify(e64.p_filesz, 10));
                print(rightJustify(e64.p_memsz, 9));
            }
            print("  " + e.getFlagString());
            nextln();
        }
    }

    public static void printSymbolTable(ELFSymbolTable stab, ELFSectionHeaderTable sht) {
        println("Symbol Table");
        printSeparator();
        print("Ent  Type     Section     Bind    Name                     Address      Size");
        nextln();
        printThinSeparator();
        final ELFStringTable str = stab.getStringTable();
        for (int cntr = 0; cntr < stab.entries.length; cntr++) {
            final ELFSymbolTable.Entry e = stab.entries[cntr];
            print(rightJustify(cntr, 3));
            print("  " + leftJustify(e.getType(), 7));
            print("  " + leftJustify(sht.getSectionName(e.getSectionHeaderIndex()), 14));
            print(leftJustify(e.getBinding(), 8));
            print(leftJustify(getName(str, e.getNameIndex()), 32));
            if (e.is32Bit()) {
                final ELFSymbolTable.Entry32 e32 = (ELFSymbolTable.Entry32) e;
                print("  " + toHex(e32.st_value));
                print("  " + rightJustify(e32.st_size, 8));
            } else {
                final ELFSymbolTable.Entry64 e64 = (ELFSymbolTable.Entry64) e;
                print("  " + toHex(e64.st_value));
                print("  " + rightJustify(e64.st_size, 12));
            }
            nextln();
        }
    }

    static void print(String str) {
        System.out.print(str);
    }
    static void print(char c) {
        System.out.print(c);
    }

    static void println(String str) {
        System.out.println(str);
    }

    static void nextln() {
        System.out.println("");
    }

    static void printSeparator() {
        System.out.println("==============================================================================");
    }

    static void printThinSeparator() {
        System.out.println("------------------------------------------------------------------------------");
    }

    static String toHex(int v) {
        return StringUtil.toHex(v, 8);
    }

    static String toHex(long v) {
        return StringUtil.toHex(v, 16);
    }
}
