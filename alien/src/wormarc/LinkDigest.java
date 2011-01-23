/* A reference to a HistoryLink.
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

import java.util.Arrays;

// !^*&^$#&^*&^%  no immutable byte[] values in Java!
public final class LinkDigest implements Comparable<LinkDigest> {
    private final byte[] mBytes = new byte[20];
    private final int mHashCode;

    public final static LinkDigest NULL_DIGEST = new LinkDigest(new byte[20]);
    public final static LinkDigest EMPTY_DIGEST =
        new LinkDigest("da39a3ee5e6b4b0d3255bfef95601890afd80709"); // hash of "";

    public LinkDigest(String linkHash) {
        this(IOUtil.fromHexString(linkHash));
    }

    public LinkDigest(byte[] bytes) {
        if (bytes.length != 20) {
            throw new IllegalArgumentException("Raw SHA1 must be 20 bytes");
        }
        System.arraycopy(bytes, 0, mBytes, 0, 20);
        mHashCode = Arrays.hashCode(mBytes);
    }

    // Deep copy!
    public byte[] getBytes() {
        byte[] bytes = new byte[20];
        System.arraycopy(mBytes, 0, bytes, 0, 20);
        return bytes;
    }

    public String hexDigest(int bytes) {
        return IOUtil.toHexString(mBytes, bytes);
    }

    public int hashCode() { return mHashCode; }

    public boolean equals(Object obj) {
        if (obj == null || (!(obj instanceof LinkDigest))) {
            return false;
        }
        return Arrays.equals(((LinkDigest)obj).mBytes, mBytes);
    }

    public String toString() {
        return hexDigest(20);
    }

    public int compareTo(LinkDigest other) {
        // DCI: TOO SLOW? use Arrays.equals
        return hexDigest(20).compareTo(other.hexDigest(20));
    }

    public boolean isNullDigest() { return equals(NULL_DIGEST); }
}

