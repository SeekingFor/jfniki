/* A single delta coded change to a file.
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
// DCI:  rationalize argument order. parent, sha, datalength, data
// Hmmm... this doesn't belong in the public interface... don't want to add a virtual to HistoryLink
// to hide it.

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

import java.security.MessageDigest;

public final class HistoryLink {
    public final long mDataLength; // DCI: DOCUMENT
    public final boolean mIsEnd;
    // DCI: design flaw: what you really want is an immutable array
    // DCI: rename to mDigest?
    public final LinkDigest mHash; // DCI: no way to DECLARE fixed length in java?
    public final LinkDigest mParent;
    public final LinkData mData;
    // DCI: make protected?
    //DCI: fix equality / hash
    public HistoryLink(final long dataLength,
                       final boolean isEnd,
                       final LinkDigest linkDigest,
                       final LinkDigest parent,
                       final LinkData data) {
        mDataLength = dataLength;
        mIsEnd = isEnd;
        mHash = linkDigest;
        mParent = parent;
        mData = data;
        if (mParent.isNullDigest() && !mIsEnd) {
            throw new IllegalArgumentException("parent is NULL_DIGEST but not isEnd???: " + linkDigest);
        }
    }

    public String toString() {
        final StringBuffer buf = new StringBuffer(64);
        buf.append("{mHash=");
        buf.append(mHash);
        buf.append(", mParent=");
        buf.append(mParent);
        buf.append(", mDataLength=");
        buf.append(Long.toString(mDataLength));
        buf.append(", mIsEnd=");
        buf.append(Boolean.toString(mIsEnd));
        buf.append('}');
        return buf.toString();
    }

    // Caller must close.
    public InputStream inputStream() throws IOException {
        return mData.openInputStream();
    }

    public void copyTo(final OutputStream destination) throws IOException {
        mData.copyTo(destination);
    }

    public byte[] copyTo(final byte[] destination) throws IOException {
        return mData.copyTo(destination);
    }

    public static HistoryLink makeLink(final long dataLength,
                                       final boolean isEnd,
                                       final LinkDigest parent,
                                       final InputStream dataStream,
                                       final LinkDataFactory linkDataFactory) throws IOException {

        final MessageDigest sha1 = BinaryLinkRep.createDigestForLink(dataLength, isEnd, parent);
        final LinkData linkData = linkDataFactory.makeLinkData(dataStream,
                                                               dataLength,
                                                               sha1);
        return new HistoryLink(dataLength,
                               isEnd,
                               new LinkDigest(sha1.digest()),
                               parent, linkData);
    }

    // RepInvariant -- checks mHash
}

