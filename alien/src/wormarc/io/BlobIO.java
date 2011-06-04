/* An Archive.IO implementation to read/write an Archive to/from a BLOB.
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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import wormarc.Archive;
import wormarc.ArchiveManifest; // Hmmm...
import wormarc.BinaryLinkRep;
import wormarc.Block;
import wormarc.HistoryLink;
import wormarc.HistoryLinkMap;
import wormarc.IOUtil;
import wormarc.LinkDataFactory;
import wormarc.LinkDigest;

public class BlobIO implements Archive.IO {
    protected final StreamFactory mStreamFactory;
    protected String mVersion;
    protected String mMetaData;
    protected LinkDigest mFixup;

    // REQUIRES: Length must not change.
    protected final static String VERSION_1 = "BLOB0001";

    // Anything that you can read or write a BLOB to.
    public interface StreamFactory {
        InputStream getInputStream() throws IOException;
        OutputStream getOutputStream() throws IOException;
        boolean shouldCloseInputStream();
        boolean shouldCloseOutputStream();
    }

    protected void readHeader(DataInputStream inputStream) throws IOException {
        byte[] versionBytes = new byte[8];
        inputStream.readFully(versionBytes);
        mVersion = new String(versionBytes, IOUtil.UTF8);
        if (!mVersion.equals(VERSION_1)) {
            throw new IOException("Version mismatch or bad data.");
        }

        int rawLength = inputStream.readUnsignedShort();
        mMetaData = "";
        if (rawLength == 0) {
            return;
        }

        byte[] raw = new byte[rawLength];
        inputStream.readFully(raw);
        mMetaData = new String(raw, IOUtil.UTF8);

    }

    // Doesn't read the history links.
    protected Archive.ArchiveData readArchiveData(DataInputStream inputStream) throws IOException {
        byte[] rawFixup = new byte[20];
        inputStream.readFully(rawFixup);
        return ArchiveManifest.fromBytes(inputStream, new LinkDigest(rawFixup), false).makeArchiveData();
    }

    protected void readLinks(HistoryLinkMap linkMap, LinkDataFactory linkFactory,
                             DataInputStream inputStream) throws IOException {
        while (true) {
            HistoryLink link = BinaryLinkRep.fromBytes(inputStream, linkFactory);
            if (link == null) {
                break;
            }
            linkMap.addLink(link);
        }
    }

    protected void writeHeader(DataOutputStream outputStream) throws IOException {
        outputStream.write(VERSION_1.getBytes(IOUtil.UTF8));

        byte[] raw = mMetaData.getBytes(IOUtil.UTF8);
        if (raw.length > 65535) {
            throw new IOException("Metadata is too big.");
        }

        outputStream.writeShort(raw.length); // DCI: test? works for unsigned values right?
        if (mMetaData.length() == 0) {
            return;
        }
        outputStream.write(raw);
    }

    protected void writeArchiveData(List<Block> blocks, List<Archive.RootObject> rootObjects,
                                    DataOutputStream outputStream)
        throws IOException {

        LinkDigest digest = ArchiveManifest.getArchiveManifestDigest(rootObjects);
        if (digest.isNullDigest()) {
            throw new IOException("Can't write an archive without a non-null  ARCHIVE_MANIFEST root object.");
        }

        // We are using ArchiveManifest to serialize the ArchiveData
        // purely for expediency.  There is some dodgy code
        // in it which requires the chainHeadFixup, even when we know it. :-(
        //
        // HACK: We need to store this for ArchiveManifest.fromBytes().
        outputStream.write(digest.getBytes());

        ArchiveManifest manifest = new ArchiveManifest(rootObjects, blocks);
        outputStream.write(IOUtil.readAndClose(manifest.toBytes()));
    }

    protected Set<LinkDigest> writeBlocks(HistoryLinkMap linkMap, List<Block> blocks,
                                       DataOutputStream outputStream) throws IOException {
        Set<LinkDigest> knownLinks = new HashSet<LinkDigest>();
        for (Block block : blocks) {
            for (HistoryLink link : linkMap.getLinks(block)) {
                if (knownLinks.contains(link)) {
                    continue;
                }
                BinaryLinkRep.write(outputStream, link);
                knownLinks.add(link.mHash);
            }
        }
        return knownLinks;
    }

    public BlobIO(StreamFactory streamFactory, String metaData) {
        if (streamFactory == null) {
            throw new IllegalArgumentException("streamFactory is null");
        }
        mStreamFactory = streamFactory;

        if (metaData == null) {
            throw new IllegalArgumentException("metaData is null");
        }
        mMetaData = metaData;

    }

    public String getMetaData() { return mMetaData; }
    public void setMetaData(String value) {
        if (value == null) {
            throw new IllegalArgumentException();
        }
        mMetaData = value;
    }

    ////////////////////////////////////////////////////////////
    // Archive.IO implementation.
    ////////////////////////////////////////////////////////////

    public void write(HistoryLinkMap linkMap, List<Block> blocks, List<Archive.RootObject> rootObjects)
        throws IOException {
        DataOutputStream outputStream = new DataOutputStream(mStreamFactory.getOutputStream());
        try {
            writeHeader(outputStream);
            writeArchiveData(blocks, rootObjects, outputStream);

            Set<LinkDigest> knownLinks = writeBlocks(linkMap, blocks, outputStream);
            for (Archive.RootObject obj : rootObjects) {
                if (!knownLinks.contains(obj.mDigest)) {
                    throw new IOException("Root object not in blocks: " + obj.mDigest);
                }
            }

        } finally {
            if (mStreamFactory.shouldCloseOutputStream()) {
                outputStream.close();
            }
        }
    }

    public Archive.ArchiveData read(HistoryLinkMap linkMap, LinkDataFactory linkFactory) throws IOException {
        DataInputStream inputStream = new DataInputStream(mStreamFactory.getInputStream());
        try {
            readHeader(inputStream);
            Archive.ArchiveData archiveData = readArchiveData(inputStream);
            readLinks(linkMap, linkFactory, inputStream);

            for (Archive.RootObject obj : archiveData.mRootObjects) {
                if (!linkMap.contains(obj.mDigest)) {
                    throw new IOException("Root object not read from input stream: " + obj.mDigest);
                }
            }
            return archiveData;
        } finally {
            if (mStreamFactory.shouldCloseInputStream()) {
                inputStream.close();
            }
        }
    }
}
