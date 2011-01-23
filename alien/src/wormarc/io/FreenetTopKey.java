/* A class to represent the data stored in Freenet for a single Archive version.
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

package wormarc.io;

// DCI: decide, do exception messages have final period?
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import wormarc.Archive;
import wormarc.BinaryLinkRep;
import wormarc.LinkDigest;

/*
WORM0200
<root objects>
<block descriptions>

DCI: better format doc
*/
public final class FreenetTopKey {
    // MUST be 8 bytes
    // Old incompatible binary link rep: {'W', 'O', 'R', 'M', '0', '2', '0', '0',};
    public final static byte[] HEADER = new byte[] {'W', 'O', 'R', 'M', '0', '2', '0', '1',};
    public final static int BINARY_CHK_LENGTH = 69;
    public final static int ENCODED_CHK_LENGTH = 99;

    public final static int MAX_LENGTH = 1024;

    public final static class BlockDescription {
        public final long mLength;
        public final List<byte[]> mCHKs; // INTENT: Allow redundant insertion of the same data.
        public BlockDescription(long length, List<byte[]> chks) {
            mLength = length;
            mCHKs = Collections.unmodifiableList(chks);
            if (mCHKs.size() < 1) {
                throw new IllegalArgumentException("Must have at least one CHK!");
            }
        }

        public String getCHK(int index) throws IllegalBase64Exception {
            return binaryToUri(mCHKs.get(index));
        }
    }

    public final String mVersion;
    public final List<Archive.RootObject> mRootObjects;
    public final List<BlockDescription> mBlockDescriptions;

    public FreenetTopKey(String version,
                         List<Archive.RootObject> rootObjects,
                         List<BlockDescription> blockDescriptions)
        throws UnsupportedEncodingException {
        mVersion = version;
        mRootObjects = Collections.unmodifiableList(rootObjects);
        mBlockDescriptions = Collections.unmodifiableList(blockDescriptions);
        if (mVersion.getBytes("ascii").length != 8) {
            throw new RuntimeException("version header != 8 bytes");
        }
        if (mBlockDescriptions.size() < 1) {
            throw new IllegalArgumentException("Must have at least one block description.");
        }
    }

    public FreenetTopKey(List<Archive.RootObject> rootObjects,
                         List<BlockDescription> blockDescriptions)
        throws UnsupportedEncodingException {
        this(new String(HEADER, "ascii"), rootObjects, blockDescriptions);
    }

    private final static void checkIsShort(int value) {
        if (value < 0 || value > 32767) {
            throw new IllegalArgumentException("Expected value between 0 and 32767.");
        }
    }

    public InputStream toBytes() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream outputStream = new DataOutputStream(buffer);
        outputStream.write(HEADER);

        checkIsShort(mRootObjects.size());
        outputStream.writeShort(mRootObjects.size());
        for (Archive.RootObject obj : mRootObjects) {
            outputStream.write(obj.mDigest.getBytes());
            outputStream.writeInt(obj.mKind);
        }

        checkIsShort(mBlockDescriptions.size());
        outputStream.writeShort(mBlockDescriptions.size());
        for (BlockDescription desc : mBlockDescriptions) {
            outputStream.writeLong(desc.mLength);
            checkIsShort(desc.mCHKs.size());
            outputStream.writeShort(desc.mCHKs.size());
            for (byte[] bytes : desc.mCHKs) {
                if (bytes.length != BINARY_CHK_LENGTH) {
                    throw new IllegalArgumentException("Not a binary CHK???");
                }
                outputStream.write(bytes);
            }
        }
        outputStream.close();
        byte[] bytes = buffer.toByteArray();
        if (bytes.length > MAX_LENGTH) {
            throw new IOException("Too big");
        }
        return new ByteArrayInputStream(bytes);
    }

    public static FreenetTopKey fromBytes(InputStream rawBytes) throws IOException {
        DataInputStream inputStream = new DataInputStream(rawBytes);
        try {
            byte[] versionBytes = new byte[HEADER.length];
            inputStream.readFully(versionBytes);
            if (!Arrays.equals(HEADER, versionBytes)) {
                throw new IOException("Version mismatch or bad data.");
            }

            List<Archive.RootObject> rootObjects = new ArrayList<Archive.RootObject>();
            int rootObjectCount = inputStream.readShort();
            while (rootObjectCount > 0) {
                LinkDigest digest = BinaryLinkRep.readLinkDigest(inputStream);
                int kind = inputStream.readInt();
                rootObjects.add(new Archive.RootObject(digest, kind));
                rootObjectCount--;
            }

            List<BlockDescription> blockDescriptions = new ArrayList<BlockDescription>();
            int blockDescriptionCount = inputStream.readShort();
            while (blockDescriptionCount > 0) {
                long blockLength = inputStream.readLong();
                List<byte[]> blockChks = new ArrayList<byte[]>();
                int blockChkCount = inputStream.readShort();
                while (blockChkCount > 0) {
                    byte[] chkBytes = new byte[BINARY_CHK_LENGTH];
                    inputStream.readFully(chkBytes);
                    blockChks.add(chkBytes);
                    blockChkCount--;
                }
                if (blockChks.size() < 1) {
                    throw new IOException("Block description must have at least one CHK.");
                }
                blockDescriptions.add(new BlockDescription(blockLength, blockChks));
                blockDescriptionCount--;
            }
            if (blockDescriptions.size() < 1) {
                throw new IOException("Must have at least one block description.");
            }
            return new FreenetTopKey(new String(versionBytes, "ascii"),
                                     Collections.unmodifiableList(rootObjects),
                                     Collections.unmodifiableList(blockDescriptions));
        } finally {
            //rawBytes.close(); DCI: Don't close the stream passed to you by AllData!
        }
    }

    ////////////////////////////////////////////////////////////
    public final static byte[] chkUriToBinary(String chkUri) throws IllegalBase64Exception {
        if (!chkUri.startsWith("CHK@")) {
            throw new IllegalArgumentException("Must start with 'CHK@'.");
        }
        if (chkUri.length() != ENCODED_CHK_LENGTH) {
            throw new IllegalArgumentException("Only raw CHKs allowed. No trailing '\' or filename part.");
        }
        String[] fields = chkUri.substring(4).split(",");
        if (fields.length != 3) {
            throw new IllegalArgumentException("Couldn't parse ',' delimited fields.");
        }

        byte[] binaryRep = new byte[BINARY_CHK_LENGTH];

        int offset = 0;
        for (int index : new int[] {2, 0, 1}) {
            byte[] decoded = Base64.decode(fields[index]);
            System.arraycopy(decoded, 0, binaryRep, offset, decoded.length);
            offset += decoded.length;
        }
        return binaryRep;
    }
    private final static String encodeRange(byte[] binaryRep, int startPos, int endPos) {
        int length = endPos - startPos;
        byte[] decoded = new byte[length];
        System.arraycopy(binaryRep, startPos, decoded, 0, length);
        return Base64.encode(decoded);
    }

    public final static String binaryToUri(byte[] binaryRep) {
        if (binaryRep.length != BINARY_CHK_LENGTH) {
            throw new IllegalArgumentException(String.format("Expected %d bytes.", BINARY_CHK_LENGTH));
        }

        StringBuilder buffer = new StringBuilder("CHK@");
        buffer.append(encodeRange(binaryRep, 5, 37));
        buffer.append(',');
        buffer.append(encodeRange(binaryRep, 37, 69));
        buffer.append(',');
        buffer.append(encodeRange(binaryRep, 0, 5));
        String asString = buffer.toString();
        if (asString.length() != ENCODED_CHK_LENGTH) {
            throw new RuntimeException("Assertion Failure: Wrong length?");
        }

        return asString;
    }

    public final static List<byte[]> chkUrisToBinary(List<String> chkUris) throws IllegalBase64Exception {
        List<byte[]> binaryList = new ArrayList<byte[]>();
        for (String uri : chkUris) {
            binaryList.add(chkUriToBinary(uri));
        }
        return binaryList;
    }

    // Java is teh suXOR.
    // You can't overload the BlockDescription ctr because of "same erasure" error.
    // http://download.oracle.com/javase/1.4.2/docs/api/java/lang/System.html \
    //      #arraycopy%28java.lang.Object,%20int,%20java.lang.Object,%20int,%20int%29
    public final static BlockDescription makeDescription(long length, List<String> chkUris) throws IllegalBase64Exception {
        return new BlockDescription(length, chkUrisToBinary(chkUris));
    }
}

