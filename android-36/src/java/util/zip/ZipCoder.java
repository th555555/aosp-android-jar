/*
 * Copyright (c) 2009, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package java.util.zip;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.DirectByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Utility class for zipfile name and comment decoding and encoding
 */
class ZipCoder {

    // Android-removed:
    // private static final jdk.internal.access.JavaLangAccess JLA =
    //    jdk.internal.access.SharedSecrets.getJavaLangAccess();

    // Encoding/decoding is stateless, so make it singleton.
    // Android-changed: use StandardCharsets.
    // static final UTF8ZipCoder UTF8 = new UTF8ZipCoder(UTF_8.INSTANCE);
    static final UTF8ZipCoder UTF8 = new UTF8ZipCoder(StandardCharsets.UTF_8);

    public static ZipCoder get(Charset charset) {
        // Android-changed: use equals method, not reference comparison.
        // if (charset == UTF_8.INSTANCE) {
        if (StandardCharsets.UTF_8.equals(charset)) {
            return UTF8;
        }
        return new ZipCoder(charset);
    }

    String toString(byte[] ba, int off, int length) {
        try {
            return decoder().decode(ByteBuffer.wrap(ba, off, length)).toString();
        } catch (CharacterCodingException x) {
            throw new IllegalArgumentException(x);
        }
    }

    String toString(byte[] ba, int length) {
        return toString(ba, 0, length);
    }

    String toString(byte[] ba) {
        return toString(ba, 0, ba.length);
    }

    // Android-changed: don't keep CEN bytes in heap memory after initialization.
    String toString(DirectByteBuffer bb, int off, int length) {
        try {
            return decoder().decode(bb.slice(off, length)).toString();
        } catch (CharacterCodingException x) {
            throw new IllegalArgumentException(x);
        }
    }

    byte[] getBytes(String s) {
        try {
            ByteBuffer bb = encoder().encode(CharBuffer.wrap(s));
            int pos = bb.position();
            int limit = bb.limit();
            if (bb.hasArray() && pos == 0 && limit == bb.capacity()) {
                return bb.array();
            }
            byte[] bytes = new byte[bb.limit() - bb.position()];
            bb.get(bytes);
            return bytes;
        } catch (CharacterCodingException x) {
            throw new IllegalArgumentException(x);
        }
    }

    static String toStringUTF8(byte[] ba, int len) {
        return UTF8.toString(ba, 0, len);
    }

    boolean isUTF8() {
        return false;
    }

    // Hash code functions for ZipFile entry names. We generate the hash as-if
    // we first decoded the byte sequence to a String, then appended '/' if no
    // trailing slash was found, then called String.hashCode(). This
    // normalization ensures we can simplify and speed up lookups.
    //
    // Does encoding error checking and hashing in a single pass for efficiency.
    // On an error, this function will throw CharacterCodingException while the
    // UTF8ZipCoder override will throw IllegalArgumentException, so we declare
    // throws Exception to keep things simple.
    int checkedHash(byte[] a, int off, int len) throws Exception {
        if (len == 0) {
            return 0;
        }

        int h = 0;
        // cb will be a newly allocated CharBuffer with pos == 0,
        // arrayOffset == 0, backed by an array.
        CharBuffer cb = decoder().decode(ByteBuffer.wrap(a, off, len));
        int limit = cb.limit();
        char[] decoded = cb.array();
        for (int i = 0; i < limit; i++) {
            h = 31 * h + decoded[i];
        }
        if (limit > 0 && decoded[limit - 1] != '/') {
            h = 31 * h + '/';
        }
        return h;
    }

    // Hash function equivalent of checkedHash for String inputs
    static int hash(String name) {
        int hsh = name.hashCode();
        int len = name.length();
        if (len > 0 && name.charAt(len - 1) != '/') {
            hsh = hsh * 31 + '/';
        }
        return hsh;
    }

    boolean hasTrailingSlash(byte[] a, int end) {
        byte[] slashBytes = slashBytes();
        return end >= slashBytes.length &&
            Arrays.mismatch(a, end - slashBytes.length, end, slashBytes, 0, slashBytes.length) == -1;
    }

    // Android-changed: don't keep CEN bytes in heap memory after initialization.
    boolean hasTrailingSlash(DirectByteBuffer bb, int end) {
        byte[] slashBytes = slashBytes();
        for (int i = end - slashBytes.length; i < end; i++) {
            byte b = bb.get(i);
            if (b != slashBytes[i - end + slashBytes.length]) {
                return false;
            }
        }
        return true;
    }

    private byte[] slashBytes;
    private final Charset cs;
    protected CharsetDecoder dec;
    private CharsetEncoder enc;

    private ZipCoder(Charset cs) {
        this.cs = cs;
    }

    protected CharsetDecoder decoder() {
        if (dec == null) {
            dec = cs.newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT);
        }
        return dec;
    }

    private CharsetEncoder encoder() {
        if (enc == null) {
            enc = cs.newEncoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT);
        }
        return enc;
    }

    // This method produces an array with the bytes that will correspond to a
    // trailing '/' in the chosen character encoding.
    //
    // While in most charsets a trailing slash will be encoded as the byte
    // value of '/', this does not hold in the general case. E.g., in charsets
    // such as UTF-16 and UTF-32 it will be represented by a sequence of 2 or 4
    // bytes, respectively.
    private byte[] slashBytes() {
        if (slashBytes == null) {
            // Take into account charsets that produce a BOM, e.g., UTF-16
            byte[] slash = "/".getBytes(cs);
            byte[] doubleSlash = "//".getBytes(cs);
            slashBytes = Arrays.copyOfRange(doubleSlash, slash.length, doubleSlash.length);
        }
        return slashBytes;
    }

    static final class UTF8ZipCoder extends ZipCoder {

        private UTF8ZipCoder(Charset utf8) {
            super(utf8);
        }

        @Override
        boolean isUTF8() {
            return true;
        }

        @Override
        String toString(byte[] ba, int off, int length) {
            // Android-changed: JLA is not yet available.
            // return JLA.newStringUTF8NoRepl(ba, off, length);
            return new String(ba, off, length, StandardCharsets.UTF_8);
        }

        // Android-changed: don't keep CEN bytes in heap memory after initialization.
        @Override
        String toString(DirectByteBuffer bb, int off, int length) {
            byte[] bytes = new byte[length];
            bb.get(off, bytes, 0, length);
            // Android-changed: JLA is not yet available.
            // return JLA.newStringUTF8NoRepl(ba, off, length);
            return new String(bytes, 0, length, StandardCharsets.UTF_8);
        }

        @Override
        byte[] getBytes(String s) {
            // Android-changed: JLA is not yet available.
            // return JLA.getBytesUTF8NoRepl(s);
            return s.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        int checkedHash(byte[] a, int off, int len) throws Exception {
            if (len == 0) {
                return 0;
            }

            int end = off + len;
            int h = 0;
            while (off < end) {
                byte b = a[off];
                if (b >= 0) {
                    // ASCII, keep going
                    h = 31 * h + b;
                    off++;
                } else {
                    // Non-ASCII, fall back to decoding a String
                    // We avoid using decoder() here since the UTF8ZipCoder is
                    // shared and that decoder is not thread safe.
                    // We use the JLA.newStringUTF8NoRepl variant to throw
                    // exceptions eagerly when opening ZipFiles
                    // Android-changed: JLA is not yet available.
                    // return hash(JLA.newStringUTF8NoRepl(a, end - len, len));
                    return hash(new String(a, end - len, len, StandardCharsets.UTF_8));
                }
            }

            if (a[end - 1] != '/') {
                h = 31 * h + '/';
            }
            return h;
        }

        @Override
        boolean hasTrailingSlash(byte[] a, int end) {
            return end > 0 && a[end - 1] == '/';
        }

        // Android-changed: don't keep CEN bytes in heap memory after initialization.
        @Override
        boolean hasTrailingSlash(DirectByteBuffer bb, int end) {
            return end > 0 && bb.get(end - 1) == '/';
        }
    }
}
