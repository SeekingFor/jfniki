/* A reference to an archive resolvable by an ArchiveResolver. e.g. a Freenet URI.
 *
 *  Copyright (C) 2010, 2011 Darrell Karbott
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.0 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 *  Author: djk@isFiaD04zgAgnrEC5XJt1i4IE7AkNPqhBG5bONi6Yks
 *
 *  This file was developed as component of
 * "fniki" (a wiki implementation running over Freenet).
 */

package wormarc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ExternalRefs {
    public final static class Reference implements Comparable<Reference> {
        public final int mKind;
        public final String mExternalKey;

        public Reference(int kind, String key) {
            mKind = kind;
            mExternalKey = key;

            if (mExternalKey == null) {
                throw new IllegalArgumentException("Key is null");
            }

            if (mKind < 0 || mKind > 127) {
                throw new IllegalArgumentException("kind out of range [0, 127]");
            }
            if (mExternalKey.length() > 32767) {
                throw new IllegalArgumentException("Key too big.");
            }
        }
        // IMPORTANT: Must be able to sort stably so you get an identical binary rep for the same list.
        public int compareTo(Reference other) {
            if (mKind - other.mKind == 0) {
                // Then by key string.
                return mExternalKey.compareTo(other.mExternalKey);
            }
            // First by kind.
            return mKind - other.mKind;
        }
    }
    public final List<Reference> mRefs;

    public final static int KIND_LOCAL = 1;
    public final static int KIND_FREENET = 2;

    public final static Reference CURRENT_ARCHIVE = new Reference(KIND_LOCAL, "current_archive");
    // Like hg rev -1. i.e. the rev before the first.
    public final static Reference NULL_ARCHIVE = new Reference(KIND_LOCAL, "null_archive");
    public final static ExternalRefs NONE = new ExternalRefs(new ArrayList<Reference>());

    ExternalRefs(List<Reference> refs) {
        Collections.sort(refs);  // To get the same serialed rep.
        mRefs = Collections.unmodifiableList(refs);
        if (mRefs.size() > 127) {
            throw new IllegalArgumentException("Too many refs.");
        }
    }

    public InputStream toBytes() throws IOException {
        ByteArrayOutputStream  buffer = new ByteArrayOutputStream();
        DataOutputStream outputStream = new DataOutputStream(buffer);

        outputStream.writeByte(mRefs.size());
        for (Reference ref : mRefs) {
            outputStream.writeByte(ref.mKind);
            byte[] raw = ref.mExternalKey.getBytes(IOUtil.UTF8);
            outputStream.writeShort(raw.length);
            outputStream.write(raw);
        }
        return new ByteArrayInputStream(buffer.toByteArray());
    }

    public static ExternalRefs fromBytes(InputStream rawBytes) throws IOException {
        DataInputStream inputStream = new DataInputStream(rawBytes);
        int count = inputStream.readByte();
        if (count > 127) {
            throw new IOException("Parse Error: count out of range [0, 127]");
        }

        List<Reference> refs = new ArrayList<Reference>();
        while (count > 0) {
            int kind = inputStream.readByte();
            if (count < 0 || count > 127) {
                throw new IOException("Parse Error: kind out of range [0, 127]");
            }

            int rawLength = inputStream.readShort();
            if (rawLength < 0 || rawLength > 32767) {
                throw new IOException("Parse Error: keyLength out of range [0, 32767]");
            }
            byte[] raw = new byte[rawLength];
            inputStream.readFully(raw);
            String key = new String(raw, IOUtil.UTF8);

            refs.add(new Reference(kind, key));
            count--;
        }
        return new ExternalRefs(refs);
    }

    public String pretty(String labelWithTrailingSpace) {
        StringBuilder buffer = new StringBuilder();
        buffer.append(String.format("--- %sExternalRefs ---\n", labelWithTrailingSpace));
        for (Reference ref: mRefs) {
            buffer.append(String.format("   [%d]:%s\n", ref.mKind, ref.mExternalKey));
        }
        buffer.append("---");
        return buffer.toString();
    }
    public String pretty() { return pretty(""); }


    public static ExternalRefs create(List<String> keys, int kind) {
        List<Reference> refs = new ArrayList<Reference>();
        for (String key : keys) {
            refs.add(new Reference(kind, key));
        }
        return new ExternalRefs(refs);
    }
}
