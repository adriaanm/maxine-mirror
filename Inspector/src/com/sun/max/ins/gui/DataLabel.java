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
package com.sun.max.ins.gui;

import java.awt.event.*;

import com.sun.max.ins.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.stack.*;

/**
 * A selectable, lightweight, label for basic kinds of data
 * that we don't know much about.
 *
 * @author Michael Van De Vanter
 */
public abstract class DataLabel extends InspectorLabel {

    protected DataLabel(Inspection inspection, String text) {
        super(inspection, text);
    }

    protected DataLabel(Inspection inspection, String text, String toolTipText) {
        this(inspection, text);
        setToolTipText(toolTipText);
    }

    public final void refresh(boolean force) {
        // Values don't change unless explicitly set
    }

    public void redisplay() {
        // Default styles
        setFont(style().primitiveDataFont());
        setForeground(style().primitiveDataColor());
        setBackground(style().primitiveDataBackgroundColor());
    }

    /**
     * A label that displays an unchanging boolean value as "true" or "false".
     */
    public static final class BooleanAsText extends DataLabel {
        public BooleanAsText(Inspection inspection, boolean b) {
            super(inspection, b ? "true" : "false");
            redisplay();
        }
    }

    /**
     * A label that displays an unchanging byte value in decimal; a ToolTip shows the value in hex.
     */
    public static final class ByteAsDecimal extends DataLabel {
        public ByteAsDecimal(Inspection inspection, byte b) {
            super(inspection, Byte.toString(b), "byte: 0x" + Integer.toHexString(Byte.valueOf(b).intValue()));
            redisplay();
        }
        @Override
        public void redisplay() {
            setFont(style().decimalDataFont());
            setForeground(style().decimalDataColor());
            setBackground(style().decimalDataBackgroundColor());
        }
    }

    /**
     * A label that displays an unchanging byte value in hex; a ToolTip shows the value in decimal.
     */
    public static final class ByteAsHex extends DataLabel {
        public ByteAsHex(Inspection inspection, byte b) {
            super(inspection, "0x" + Integer.toHexString(Byte.valueOf(b).intValue()), "byte:  " + Byte.toString(b));
            redisplay();
        }
        @Override
        public void redisplay() {
            setFont(style().hexDataFont());
            setForeground(style().hexDataColor());
            setBackground(style().hexDataBackgroundColor());
        }
    }

    /**
     * A label that displays a changeable array of bytes in a bracketed string of hex pairs.
     */
    public static class ByteArrayAsHex extends DataLabel {

        private byte[] bytes;

        public ByteArrayAsHex(Inspection inspection, byte[] bytes) {
            super(inspection, "");
            this.bytes = bytes;
            redisplay();
        }

        @Override
        public void redisplay() {
            setFont(style().hexDataFont());
            setForeground(style().hexDataColor());
            setBackground(style().hexDataBackgroundColor());
            updateText();
        }

        protected void setValue(byte[] bytes) {
            this.bytes = bytes;
            updateText();
        }

        private void updateText() {
            if (bytes != null && bytes.length > 0) {
                final StringBuilder result = new StringBuilder(100);
                String prefix = "[";
                for (byte b : bytes) {
                    result.append(prefix);
                    result.append(String.format("%02X", b));
                    prefix = " ";
                }
                result.append("]");
                setText(result.toString());
            }
        }
    }

    /**
     * A label that displays a changeable array of bytes as 8 bit characters.
     */
    public static class ByteArrayAsUnicode extends DataLabel {

        private byte[] bytes;

        public ByteArrayAsUnicode(Inspection inspection, byte[] bytes) {
            super(inspection, "");
            this.bytes = bytes;
            redisplay();
        }

        @Override
        public void redisplay() {
            setFont(style().hexDataFont());
            setForeground(style().hexDataColor());
            setBackground(style().hexDataBackgroundColor());
            updateText();
        }

        protected void setValue(byte[] bytes) {
            this.bytes = bytes;
            updateText();
        }

        private void updateText() {
            if (bytes != null && bytes.length > 0) {
                final StringBuilder result = new StringBuilder(100);
                String prefix = "[";
                for (int i = 0; i < bytes.length / 2; i++) {
                    result.append(prefix);
                    final int index = 2 * i;
                    final char ch = (char) ((bytes[index + 1] * 256) + bytes[index]);
                    result.append(Character.toString(ch));
                    prefix = " ";
                }
                result.append("]");
                setText(result.toString());
            }
        }
    }

    /**
     * A label that displays a changeable array of bytes as 8 bit characters.
     */
    public static class ByteArrayAsChar extends DataLabel {

        private byte[] bytes;

        public ByteArrayAsChar(Inspection inspection, byte[] bytes) {
            super(inspection, "");
            this.bytes = bytes;
            redisplay();
        }

        @Override
        public void redisplay() {
            setFont(style().hexDataFont());
            setForeground(style().hexDataColor());
            setBackground(style().hexDataBackgroundColor());
            updateText();
        }

        protected void setValue(byte[] bytes) {
            this.bytes = bytes;
            updateText();
        }

        private void updateText() {
            if (bytes != null && bytes.length > 0) {
                final StringBuilder result = new StringBuilder(100);
                String prefix = "[";
                for (byte b : bytes) {
                    result.append(prefix);
                    final char ch = (char) b;
                    result.append(Character.toString(ch));
                    prefix = " ";
                }
                result.append("]");
                setText(result.toString());
            }
        }
    }

    /**
     * A label that displays an unchanging short value in decimal;  a ToolTip displays the value in hex.
     */
    public static final class ShortAsDecimal extends DataLabel {
        ShortAsDecimal(Inspection inspection, short n) {
            super(inspection, Short.toString(n), "short: 0x" + Integer.toHexString(Short.valueOf(n).intValue()));
            redisplay();
        }
        @Override
        public void redisplay() {
            setFont(style().decimalDataFont());
            setForeground(style().decimalDataColor());
            setBackground(style().decimalDataBackgroundColor());
        }
    }

    /**
     * A label that displays an unchanging char value as a textual character; a ToolTip displays the value in decimal and hex.
     */
    public static final class CharAsText extends DataLabel {
        CharAsText(Inspection inspection, char c) {
            super(inspection, "'" + c + "'");
            final int n = Character.getNumericValue(c);
            setToolTipText("char:  " + Integer.toString(n) + ", 0x" + Integer.toHexString(n));
            redisplay();
        }
        @Override
        public void redisplay() {
            setFont(style().charDataFont());
            setForeground(style().charDataColor());
            setBackground(style().primitiveDataBackgroundColor());
        }
    }

    /**
     * A label that displays the decimal value of an unchanging char; a ToolTip displays the character and hex value.
     */
    public static final class CharAsDecimal extends DataLabel {
        public CharAsDecimal(Inspection inspection, char c) {
            super(inspection, Integer.toString(Character.getNumericValue(c)));
            setToolTipText("char:  '" + c + "', 0x" + Integer.toHexString(Character.getNumericValue(c)));
            redisplay();
        }
        @Override
        public void redisplay() {
            setFont(style().decimalDataFont());
            setForeground(style().decimalDataColor());
            setBackground(style().decimalDataBackgroundColor());
        }
    }

    /**
     * A label that displays the decimal value of an integer; a ToolTip displays the value in hex.
     */
    public static class IntAsDecimal extends DataLabel {

        private int n;

        public IntAsDecimal(Inspection inspection, int n) {
            super(inspection, "");
            this.n = n;
            updateText();
            redisplay();
        }
        @Override
        public void redisplay() {
            setFont(style().decimalDataFont());
            setForeground(style().decimalDataColor());
            setBackground(style().decimalDataBackgroundColor());
        }

        protected void setValue(int n) {
            this.n = n;
            updateText();
        }

        private void updateText() {
            setText(Integer.toString(n));
            setToolTipText("int: 0x" + Integer.toHexString(n));
        }
    }

    /**
     * A label that displays the hex value of an  int; a ToolTip displays the value in decimal.
     */
    public static class IntAsHex extends DataLabel {

        private int n;

        public IntAsHex(Inspection inspection, int n) {
            super(inspection, "");
            this.n = n;
            updateText();
            redisplay();
        }
        @Override
        public void redisplay() {
            setFont(style().hexDataFont());
            setForeground(style().hexDataColor());
            setBackground(style().hexDataBackgroundColor());
        }

        protected void setValue(int n) {
            this.n = n;
            updateText();
        }

        private void updateText() {
            setText("0x" + Integer.toHexString(n));
            setToolTipText("int:  " + Integer.toString(n));
        }

    }

    public static class FloatAsText extends DataLabel {

        private float f;
        public FloatAsText(Inspection inspection, float f) {
            super(inspection, Float.toString(f), "0x" + Integer.toHexString(Float.floatToIntBits(f)));
            this.f = f;
            updateText();
            redisplay();
        }

        @Override
        public void redisplay() {
            // TODO: define a font, color, and background for floats
            setFont(style().hexDataFont());
            setForeground(style().hexDataColor());
            setBackground(style().hexDataBackgroundColor());
        }

        protected void updateText() {
            setText(Float.toString(f));
            setToolTipText("0x" + Integer.toHexString(Float.floatToIntBits(f)));
        }

        protected void setValue(float f) {
            this.f = f;
            updateText();
        }
    }

    /**
     * A label that displays the decimal value of an unchanging long; a ToolTip displays the value in hex.
     */
    public static final class LongAsDecimal extends DataLabel {
        public LongAsDecimal(Inspection inspection, long n) {
            super(inspection, Long.toString(n), "int: 0x" + Long.toHexString(n));
            redisplay();
        }
        @Override
        public void redisplay() {
            setFont(style().decimalDataFont());
            setForeground(style().decimalDataColor());
            setBackground(style().decimalDataBackgroundColor());
        }
    }

    /**
     * A label that displays the hex value of an unchanging long; a ToolTip displays the value in decimal.
     */
    public static final class LongAsHex extends DataLabel {
        public LongAsHex(Inspection inspection, long n) {
            super(inspection, "0x" + Long.toHexString(n), "long:  " + Long.toString(n));
            redisplay();
        }
        @Override
        public void redisplay() {
            setFont(style().hexDataFont());
            setForeground(style().hexDataColor());
            setBackground(style().hexDataBackgroundColor());
        }
    }

    public static class DoubleAsText extends DataLabel {
        private double f;

        public DoubleAsText(Inspection inspection, double f) {
            super(inspection, "", "");
            this.f = f;
            updateText();
            redisplay();
        }

        @Override
        public void redisplay() {
            // TODO: define a font, color, and background for doubles
            setFont(style().hexDataFont());
            setForeground(style().hexDataColor());
            setBackground(style().hexDataBackgroundColor());
        }

        private void updateText() {
            setText(Double.toString(f));
            setToolTipText("0x" + Long.toHexString(Double.doubleToLongBits(f)));
        }

        protected void setValue(double f) {
            this.f = f;
            updateText();
        }
    }

    /**
     * A label that displays a memory address in hex; if a base address
     * is specified, then a ToolTip displays the offset from the base.
     */
    public static class AddressAsHex extends DataLabel {
        protected Address address;
        private final Address origin;

        public AddressAsHex(Inspection inspection, Address address) {
            this(inspection, address, null);
        }

        public AddressAsHex(Inspection inspection, Address addr, Address origin) {
            super(inspection, addr.toHexString());
            this.address = addr;
            this.origin = origin;
            addMouseListener(new InspectorMouseClickAdapter(inspection()) {
                @Override
                public void procedure(final MouseEvent mouseEvent) {
                    switch (MaxineInspector.mouseButtonWithModifiers(mouseEvent)) {
                        case MouseEvent.BUTTON3: {
                            final InspectorPopupMenu menu = new InspectorPopupMenu("Address");
                            menu.add(inspection().actions().copyWord(address, "Copy address to clipboard"));
                            menu.add(inspection().actions().inspectMemoryWords(address, null));
                            if (maxVM().watchpointsEnabled()) {
                                menu.add(inspection().actions().setWordWatchpoint(address, null));
                            }
                            menu.show(mouseEvent.getComponent(), mouseEvent.getX(), mouseEvent.getY());
                            break;
                        }
                        case MouseEvent.BUTTON2: {
                            changeBiasState();
                            break;
                        }

                    }
                }
            });
            redisplay();
        }

        protected AddressAsHex(Inspection inspection, Address address, Address origin, InspectorMouseClickAdapter mouseListener) {
            super(inspection, address.toHexString());
            this.address = address;
            this.origin = origin;
            addMouseListener(mouseListener);
            redisplay();
        }

        @Override
        public void redisplay() {
            setFont(style().hexDataFont());
            setForeground(style().hexDataColor());
            setBackground(style().hexDataBackgroundColor());
            updateText();
        }

        protected void setValue(Address address) {
            this.address = address;
            updateText();
        }

        protected void changeBiasState() {
        }

        protected String toolTipText() {
            if (origin == null) {
                return null;
            }
            final long position = address.minus(origin).toLong();
            return "AsPosition: " + position + ", " +  "0x" + Long.toHexString(position);
        }

        private void updateText() {
            setText(address.toHexString());
            setToolTipText(toolTipText());
        }
    }

    public static final class BiasedStackAddressAsHex extends AddressAsHex {
        boolean biased;
        final StackBias bias;
        public BiasedStackAddressAsHex(Inspection inspection, Address address, StackBias bias) {
            super(inspection, address, null);
            this.bias = bias;
            biased = true;
        }

        private boolean useBias() {
            return bias != null && !bias.equals(StackBias.NONE);
        }

        @Override
        protected void changeBiasState() {
            if (!useBias()) {
                return;
            }
            if (biased) {
                biased = false;
                setValue(bias.unbias(address.asPointer()));
            } else {
                biased = true;
                setValue(bias.bias(address.asPointer()));
            }
        }

        @Override
        protected String toolTipText() {
            if (useBias()) {
                return biased ? "Biased" : "Unbiased";
            }
            return null;
        }
    }

    /**
     * A label that displays the textual name of an Enum value; a ToolTip displays
     * both the class name and the ordinal of the value.
     */
    public static final class EnumAsText extends DataLabel {
        public EnumAsText(Inspection inspection, Enum e) {
            super(inspection, e.getClass().getSimpleName() + "." + e.name(), "Enum:  " + e.getClass().getName() + "." + e.name() + " ord=" + e.ordinal());
            redisplay();
        }
    }

    public static final class Percent extends DataLabel {

        private long numerator;
        private long denominator;

        public Percent(Inspection inspection, long numerator, long denominator) {
            super(inspection, null);
            assert denominator != 0;
            this.numerator = numerator;
            this.denominator = denominator;
            redisplay();
        }

        @Override
        public void redisplay() {
            setFont(style().decimalDataFont());
            setForeground(style().decimalDataColor());
            setBackground(style().decimalDataBackgroundColor());
            updateText();
        }

        public void setValue(int numerator, int denominator) {
            this.numerator = numerator;
            this.denominator = denominator;
            updateText();
        }

        private void updateText() {
            final long percent = 100 * numerator / denominator;
            setText(Long.toString(percent) + "%");
            setToolTipText(Long.toString(numerator) + " /  " + denominator);
        }
    }

}
