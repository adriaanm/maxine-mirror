/*
 * Copyright (c) 2007, 2010, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.tele;

import java.io.*;

import com.sun.max.tele.object.*;
import com.sun.max.tele.util.*;
import com.sun.max.vm.type.*;

/**
 * TODO: The break command uses code that was mostly cut and paste from
 * TargetMethodSearchDialog. The latter should be refactored to disentangle
 * the logic used here from the GUI aspects.
 * TODO: The command parsing is very picky - it needs a real lexer to deal with white space properly.
 *
 * @author Mick Jordan
 */
public class FileCommands {
    private static final String DEFAULT_COMMAND_FILE_PROPERTY = "max.ins.defaultcommandfile";
    private static final String USER_HOME_PROPERTY = "user.home";
    private static final String DEFAULT_COMMAND_FILE = ".max_ins_commands";

    private static String defaultCommandFile;
    private static int lineNumber;

    static {
        defaultCommandFile = System.getProperty(DEFAULT_COMMAND_FILE_PROPERTY);
        if (defaultCommandFile == null) {
            final String userHome = System.getProperty(USER_HOME_PROPERTY);
            if (userHome != null) {
                defaultCommandFile = userHome + File.separator + DEFAULT_COMMAND_FILE;
            }
        }

    }

    public static String defaultCommandFile() {
        return defaultCommandFile;
    }

    public static void executeCommandsFromFile(TeleVM teleVM, String filename) {
        lineNumber = 0;
        BufferedReader bs = null;
        try {
            bs = new BufferedReader(new FileReader(filename));
            while (true) {
                final String line = bs.readLine();
                if (line == null) {
                    break;
                }
                lineNumber++;
                if (line.length() == 0) {
                    continue;
                }
                try {
                    doCommand(teleVM, line);
                } catch (CommandException commandException) {
                    TeleError.unexpected("File Command failed ", commandException);
                }
            }
        } catch (IOException ex) {
            TeleError.unexpected("Failed to open file: " + filename);
        } finally {
            if (bs != null) {
                try {
                    bs.close();
                } catch (IOException ex) {
                }
            }
        }
    }

    private static void doCommand(TeleVM teleVM, String line) throws CommandException {
        final int index = line.indexOf(' ');
        final String command = (index < 0) ? line : line.substring(0, index);
        final String arguments = (index < 0) ? "" : line.substring(index + 1);
        if (command.equals("break")) {
            doBreak(teleVM, arguments);
        }
    }

    private static void doBreak(TeleVM teleVM, String arg) throws CommandException {
        final int index = arg.lastIndexOf('.');
        if (index < 0) {
            throw new CommandException("syntax error: class name missing");
        }
        final String className = arg.substring(0, index);
        final String methodSignature = arg.substring(index + 1);
        final TeleClassActor teleClassActor = teleVM.findTeleClassActor(JavaTypeDescriptor.getDescriptorForJavaString(className));
        if (teleClassActor == null) {
            throw new CommandException("failed to find class: " + className + " (not qualified or misspelled?)");
        }
        boolean found = false;
        for (TeleClassMethodActor teleClassMethodActor : teleClassActor.getTeleClassMethodActors()) {
            if (teleClassMethodActor.classMethodActor().format("%n(%p)").equals(methodSignature)) {
                found = true;
                final TeleTargetMethod teleTargetMethod = teleClassMethodActor.getCurrentCompilation();
                if (teleTargetMethod != null) {
                    final MaxCompiledCode compiledCode = teleVM.codeCache().findCompiledCode(teleTargetMethod.callEntryPoint());
                    if (compiledCode != null) {
                        try {
                            teleVM.breakpointManager().makeBreakpoint(compiledCode.getCallEntryLocation());
                        } catch (MaxVMBusyException e) {
                            TeleError.unexpected(" failed to set breakpoint from file: VM Busy");
                            e.printStackTrace();
                        }
                    }
                }
                break;
            }
        }
        if (!found) {
            throw new CommandException("failed to find method: " + arg);
        }
    }

    static class CommandException extends Exception {
        CommandException(String message) {
            super("error at line " + +lineNumber + ": " + message);
        }
    }
}
