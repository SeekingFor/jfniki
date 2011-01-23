/* Methods to read and write the binary representation of HistoryLinks.
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
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.EOFException;
import java.io.OutputStream;
import java.io.SequenceInputStream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

// All values are nw byte order, mHash is derived, not stored
// <data length:4 byte unsigned int><1 byte flags><parent: 20 bytes><data>
public class BinaryLinkRep {
    private final static int LINK_HEADER_LEN = 4 + 1 + 20;
    private final static int FLAG_IS_END = 1;
    private final static int STREAM_BUFFERING = 4096;
    private final static long INT_MASK = 0xffffffffL;
    // Big-endian.
    private static byte[] unsignedIntToBytes(long value) {
        if (value < 0 || value > 4294967295L) {
            throw new IllegalArgumentException("Not an unsigned int!");
        }

        byte[] bytes = new byte[4];
        for (int index = 0; index < 4; index++) {
            bytes[3 - index] = (byte)(value & 0xff);
            value = value >> 8;
        }

        return bytes;
    }

    static MessageDigest createDigestForLink(long dataLength, boolean isEnd, LinkDigest parent)
        throws IOException {
        MessageDigest sha1 = null;
        try {
            sha1 = MessageDigest.getInstance("SHA");
        }
        catch (NoSuchAlgorithmException nsae) {
            throw new IOException("Couldn't load SHA1 algorithm.");
        }
        // big endian 4 byte unsigned integer.
        sha1.update(unsignedIntToBytes(dataLength));
        // one byte flags field.
        sha1.update((byte)(isEnd ? 1 :0));
        // 20 byte SHA1 digest.
        sha1.update(parent.getBytes());
        return sha1;
    }

    public final static LinkDigest readLinkDigest(DataInputStream source) throws IOException {
        return new LinkDigest(IOUtil.readBytes(source, 20));
    }

    public final static long getRepLength(HistoryLink link) {
        return link.mDataLength + LINK_HEADER_LEN;
    }

    // ASSUMPTION: PACKED!
    public final static long getRepLength(Iterable<HistoryLink> links) {
        long total = 0;
        for (HistoryLink link : links) {
            total += link.mDataLength + LINK_HEADER_LEN;
        }
        return total;
    }

    private final static int getFlags(HistoryLink link) {
        return link.mIsEnd ? FLAG_IS_END : 0;
    }
    private final static boolean isEnd(int flags) {
        return (flags & FLAG_IS_END) != 0;
    }

    public final static InputStream toBytes(HistoryLink link) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(buffer);
        output.writeInt((int)(link.mDataLength + LINK_HEADER_LEN));
        output.writeByte(getFlags(link));
        output.write(link.mParent.getBytes());
        output.close();

        return new SequenceInputStream(new ByteArrayInputStream(buffer.
                                                                toByteArray()),
                                       link.inputStream());
    }

    public final static void write(OutputStream toStream, HistoryLink link) throws IOException {
        DataOutputStream output = new DataOutputStream(toStream);
        output.writeInt((int)(link.mDataLength + LINK_HEADER_LEN));
        output.writeByte(getFlags(link));
        output.write(link.mParent.getBytes());
        output.flush();
        link.copyTo(toStream);
    }

    // Returns null on EOF
    public final static HistoryLink fromBytes(InputStream inputBytes,
                                              LinkDataFactory factory)
        throws IOException {
        try {
            DataInputStream input = new DataInputStream(inputBytes);
            long length = input.readInt() & INT_MASK;
            int flags = input.readByte();
            LinkDigest parent = readLinkDigest(input);
            return HistoryLink.makeLink(length - LINK_HEADER_LEN,
                                        isEnd(flags),
                                        parent,
                                        input,
                                        factory);
        } catch (EOFException eofe) {
            return null;
        }
    }

    ////////////////////////////////////////////////////////////
    // Hmmmm... a bunch of convenient helper stuff that belongs somewhere else?

    // Helper enumeration implementation used to concatinate streams.
    final static class LinkStreamEnumeration implements Enumeration<InputStream> {
        final Iterator<HistoryLink> mLinkIter;

        public LinkStreamEnumeration(Iterator<HistoryLink> links) {
            mLinkIter = links;
        }

        public boolean hasMoreElements() {
            return mLinkIter.hasNext();
        }

        public InputStream nextElement() {
            HistoryLink link = null;
            try {
                link = mLinkIter.next();
                return toBytes(link);
            } catch (IOException ioe) {
                throw new NoSuchElementException("Couldn't open InputStream for link: " + link);
            }
        }
    }

    public final static InputStream toBytes(Iterable<HistoryLink> links)
        throws IOException {
        return new BufferedInputStream(new SequenceInputStream(new LinkStreamEnumeration(links.iterator())),
                                       STREAM_BUFFERING);
    }

    public final static List<HistoryLink> readAll(InputStream in, LinkDataFactory factory)
        throws IOException {
        ArrayList<HistoryLink> links = new ArrayList<HistoryLink>();
        while (true) {
            HistoryLink link = fromBytes(in, factory);
            if (link == null) {
                break;
            }
            links.add(link);
        }
        return Collections.unmodifiableList(links);
    }
}
