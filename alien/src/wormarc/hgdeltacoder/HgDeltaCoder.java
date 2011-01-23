/* A DeltaCoder implementation implementing the encoding/decoding algorithms used by the hg revlog.
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

// ATTRIBUTION: Mirko Friedenhagen <mfriedenhagen@users.berlios.de>
// This file contains some pieces of compression and decompression code
// taken from hgkit, specifically from:
// src/main/java/org/freehg/hgkit/core/Util.java
//
// This file is indirectly an derived work based on the
// Mercurial Python codebase, written by Matt Mackall (and others).
package wormarc.hgdeltacoder;

import java.io.InputStream;
import java.io.DataInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.SequenceInputStream;
import java.io.IOException;

import java.util.ArrayList;
import java.util.zip.InflaterInputStream;
import java.util.zip.DeflaterOutputStream;

import wormarc.DeltaCoder;
import wormarc.HistoryLink;
import wormarc.LinkDataFactory;
import wormarc.IOUtil;
import wormarc.LinkDigest;

import wormarc.hgdeltacoder.ported.BDiff;
import wormarc.hgdeltacoder.ported.MDiff;

// ALL OPERATION DONE IN RAM!
public class HgDeltaCoder implements DeltaCoder {
    private final static class ZLibResult {
        public byte[] mData;
        public boolean mUncompressed;

        ZLibResult(byte[] data, boolean uncompressed) {
            mData = data;
            mUncompressed = uncompressed;
        }
    }

    ////////////////////////////////////////////////////////////


    private static final int BUFFER_SIZE = 16 * 1024;
    private static final int ASSUMED_COMPRESSION_RATIO = 3;
    private static final char ZLIB_COMPRESSION = 'x';
    private static final char UNCOMPRESSED = 'u';
    static final int EOF = -1;

    private static final byte[] doDecompress(byte[] data) throws IOException {
        ByteArrayOutputStream uncompressedOut = new ByteArrayOutputStream(data.length * ASSUMED_COMPRESSION_RATIO);
        // decompress the bytearray using what should be python zlib
        final byte[] buffer = new byte[BUFFER_SIZE];
        final InflaterInputStream inflaterInputStream = new InflaterInputStream(new ByteArrayInputStream(data));
        int len = 0;
        while ((len = inflaterInputStream.read(buffer)) != EOF) {
            uncompressedOut.write(buffer, 0, len);
        }
        return uncompressedOut.toByteArray();
    }

    private static final byte[] decompress(byte[] data) throws IOException {
        if (data.length < 1) {
            return new byte[0];
        }
        byte dataHeader = data[0];
        switch (dataHeader) {
        case UNCOMPRESSED:
            final byte[] copy = new byte[data.length - 1];
            System.arraycopy(data, 1, copy, 0, data.length - 1);
            return copy;
        case ZLIB_COMPRESSION:
            return doDecompress(data);
        case 0:
            return data;
        default:
            throw new IOException("Unknown compression type : " + (char) (dataHeader));
        }
    }
    ////////////////////////////////////////////////////////////
    private static final byte[] doCompress(byte[] data) throws IOException {
        ByteArrayOutputStream compressedOut = new ByteArrayOutputStream(data.length / ASSUMED_COMPRESSION_RATIO);
        // Compress the byte array using what should be python zlib
        final DeflaterOutputStream deflaterOutputStream = new DeflaterOutputStream(compressedOut);
        deflaterOutputStream.write(data);
        deflaterOutputStream.close();
        return compressedOut.toByteArray();
    }

    private static final ZLibResult compress(byte[] text) throws IOException {
        if (text.length == 0) {
            return new ZLibResult(text, false);
        }

        int length = text.length;
        byte[] bin = null;

        if (length < 44) {
            /* NOP */
        }
        // DCI: required for Java?
        // else if (length > 1000000) {
        //     // # zlib makes an internal copy, thus doubling memory usage for
        //     // # large files, so lets do this in pieces
        //     // z = zlib.compressobj()
        //     // p = []
        //     // pos = 0
        //     // while pos < l:
        //     //     pos2 = pos + 2**20
        //     //     p.append(z.compress(text[pos:pos2]))
        //     //     pos = pos2
        //     // p.append(z.flush())
        //     // if sum(map(len, p)) < l:
        //     //     bin = "".join(p)
        // }
        else {
            bin = doCompress(text);
            //System.err.println("Compressed: " + text.length + " to: " + bin.length);
        }

        if (bin == null || bin.length > length) {
            if (text[0] == 0) {
                return new ZLibResult(text, false);
            }
            return new ZLibResult(text, true);
        }
        return new ZLibResult(bin, false);
    }

    ////////////////////////////////////////////////////////////

    private final static byte[] UNCOMPRESSED_HEADER = {'u', };

    private final static HistoryLink buildLink(byte[] data, boolean uncompressed,
                                               boolean isEnd,
                                               LinkDigest parent,
                                               LinkDataFactory linkDataFactory) throws IOException {
        int dataLength = data.length;
        DataInputStream source = null;

        if (uncompressed) {
            dataLength += 1;
            source = new DataInputStream(new SequenceInputStream(new ByteArrayInputStream(UNCOMPRESSED_HEADER),
                                                                 new ByteArrayInputStream(data)));

        } else {
            source = new DataInputStream(new ByteArrayInputStream(data));
        }

        return HistoryLink.makeLink(dataLength,
                                    isEnd,
                                    parent,
                                    source,
                                    linkDataFactory);
    }

    public HistoryLink makeDelta(LinkDataFactory linkDataFactory,
                                 LinkDigest parent,
                                 InputStream oldData,
                                 InputStream newData,
                                 boolean disableCompression) throws IOException {


        ZLibResult result = null;
        if (oldData == null) {
            if (disableCompression) {
                result = new ZLibResult(IOUtil.readAndClose(newData), true);
                // BUG: shouldn't be 'u' for /0 data or  less than  44
                throw new RuntimeException("You just hit a buggy unimplemented code path. Sorry :-("); // DCI:
            } else {
                result = compress(IOUtil.readAndClose(newData));
            }
            //System.err.println("makeDelta: full insert: " + result.mData.length);
        } else {
            if (disableCompression) {
                throw new IllegalArgumentException("disableCompression only allowed for first link.");
            }
            // result = compress(hgTextDiff(IOUtil.readAndClose(oldData),
            //                              IOUtil.readAndClose(newData)));


            byte[] delta = BDiff.bdiff(IOUtil.readAndClose(oldData),
                                       IOUtil.readAndClose(newData));

            //System.err.println("makeDelta -- made delta: " + delta.length);

            result = compress(delta);

            // DCI: check if this result is larger than compressing the total file?

            // djk: Use a DataOutputStream?

        }

        //System.err.println("makeDelta result, len: " + result.mData.length + " uncompressed: " + result.mUncompressed);
        return buildLink(result.mData, result.mUncompressed,
                         oldData == null, parent, linkDataFactory);

    }

    public InputStream applyDeltas(Iterable<HistoryLink> history) throws IOException {
        byte[] text = null;
        ArrayList<byte[]> deltas = new ArrayList<byte[]>();
        for (HistoryLink link: history) {
            // DCI: memory usage. multiple copies!
            byte[] rawData = new byte[(int)link.mDataLength];
            rawData = decompress(link.copyTo(rawData));
            if (link.mIsEnd) {
                text = rawData;
                break;
            }
            deltas.add(0, rawData);
        }

        if (text == null) {
            throw new IOException("No base file in the history chain.");
        }

        if (deltas.size() == 0) {
            return new ByteArrayInputStream(text);
        }

        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
        MDiff.patches(text, deltas, bytesOut);
        return new ByteArrayInputStream(bytesOut.toByteArray());
    }
}
