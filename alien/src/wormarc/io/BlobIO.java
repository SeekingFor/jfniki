// DCI: Should this be called StreamIO?
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


// NOTE:
// This relies on the ArchiveManifest class to marshal / unmarshal
// the Archive.ArchiveData.
//
// BINARY FORMAT:
// <version><archive manifest chain head digest><metadata length><metadata>[<link>]+
public class BlobIO implements Archive.IO {
    protected final StreamFactory mStreamFactory;
    protected String mVersion;
    protected String mMetaData;
    protected LinkDigest mManifestChainHead;

    // REQUIRES: Length must not change.
    protected final static String VERSION_2 = "BLOB0002";

    // Anything that you can read or write a BLOB from/to.
    public interface StreamFactory {
        InputStream getInputStream() throws IOException;
        OutputStream getOutputStream() throws IOException;
        boolean shouldCloseInputStream();
        boolean shouldCloseOutputStream();
    }

    // Helper class used to extract the persisted ArchiveManifest
    // (~= Archive.ArchiveData) file from a history link chain.
    protected final static class LinkSourceAdapter implements Archive.LinkSource {
        private final HistoryLinkMap mLinkMap;
        public LinkSourceAdapter(HistoryLinkMap linkMap) {
            mLinkMap = linkMap;
        }
        public HistoryLink readLink(HistoryLinkMap linkMap,
                                    LinkDataFactory linkFactory,
                                    LinkDigest digest) throws IOException {
            try {
                return mLinkMap.getLink(digest);
            } catch (HistoryLinkMap.LinkNotFoundException lnfe) {
                lnfe.rethrowAsIOException();
                return null; // unreachable
            }
        }
    }

    // Get ArchiveData from a raw ArchiveManifest file chainHead
    // and a HistoryLinkMap.
    protected static Archive.ArchiveData
        getArchiveData(HistoryLinkMap linkMap, LinkDigest chainHead)
        throws IOException {

        Archive.LinkSource source = new LinkSourceAdapter(linkMap);
        InputStream manifestBytes = Archive.readFile(chainHead, source);

        return ArchiveManifest.fromBytes(manifestBytes,
                                         chainHead,
                                         true).makeArchiveData();
    }

    // Raise an IOException if the manifest implicit in the ARCHIVE_MANIFEST
    // doesn't match the blocks and root objects.
    protected static void validateManifest(HistoryLinkMap linkMap,
                                           List<Block> blocks,
                                           List<Archive.RootObject> rootObjects)
        throws IOException {

        LinkDigest digest = ArchiveManifest.
            getArchiveManifestDigest(rootObjects);
        if (digest.isNullDigest()) {
            throw new IOException("Archive has a null ARCHIVE_MANIFEST " +
                                  "root object.");
        }

        if (!(new Archive.ArchiveData(blocks, rootObjects))
            .equals(getArchiveData(linkMap, digest))) {
            throw new IOException("ARCHIVE_MANIFEST root object " +
                                  "doesn't match ArchiveData.");
        }
    }

    protected void readHeader(DataInputStream inputStream) throws IOException {
        // 8 Bytes of file format string.
        byte[] versionBytes = new byte[8];
        inputStream.readFully(versionBytes);
        mVersion = new String(versionBytes, IOUtil.UTF8);
        if (!mVersion.equals(VERSION_2)) {
            throw new IOException("Version mismatch or bad data.");
        }

        //20 bytes for the chain head digest for the ArchiveManifest.
        byte[] rawManifestChainHeadDigest = new byte[20];
        inputStream.readFully(rawManifestChainHeadDigest);
        mManifestChainHead = new LinkDigest(rawManifestChainHeadDigest);
        if (mManifestChainHead.isNullDigest()) {
            throw new IOException("Manifest digest is null.");
        }

        // 2 bytes for the size of the metadata string.
        int rawLength = inputStream.readUnsignedShort();
        mMetaData = "";
        if (rawLength == 0) {
            return;
        }

        // rawLength bytes of metadata.
        byte[] raw = new byte[rawLength];
        inputStream.readFully(raw);
        mMetaData = new String(raw, IOUtil.UTF8);

        // ... binary history links... //
    }

    protected Set<LinkDigest> readLinks(HistoryLinkMap linkMap, LinkDataFactory linkFactory,
                                        DataInputStream inputStream) throws IOException {
        Set<LinkDigest> knownLinks = new HashSet<LinkDigest>();
        while (true) {
            HistoryLink link = BinaryLinkRep.fromBytes(inputStream, linkFactory);
            if (link == null) {
                break;
            }
            linkMap.addLink(link);
            knownLinks.add(link.mHash);
        }
        return knownLinks;
    }

    protected void writeHeader(DataOutputStream outputStream) throws IOException {
        outputStream.write(VERSION_2.getBytes(IOUtil.UTF8));
        outputStream.write(mManifestChainHead.getBytes());
        byte[] raw = mMetaData.getBytes(IOUtil.UTF8);
        if (raw.length > 65535) {
            throw new IOException("Metadata is too big.");
        }

        outputStream.writeShort(raw.length);
        if (mMetaData.length() == 0) {
            return;
        }
        outputStream.write(raw);
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

        // Make sure rootObjects contains a matching ARCHIVE_MANIFEST.
        validateManifest(linkMap, blocks, rootObjects);

        mManifestChainHead = ArchiveManifest.getArchiveManifestDigest(rootObjects);

        DataOutputStream outputStream = new DataOutputStream(mStreamFactory.getOutputStream());
        try {
            writeHeader(outputStream);

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
            Set<LinkDigest> readLinks = readLinks(linkMap, linkFactory, inputStream);
            // This will fail with an IO exception if the
            // archive manifest can't be read.
            Archive.ArchiveData archiveData =
                getArchiveData(linkMap, mManifestChainHead);

            Set<LinkDigest> referencedLinks = new HashSet<LinkDigest>();
            for (Archive.RootObject obj : archiveData.mRootObjects) {
                if (!readLinks.contains(obj.mDigest)) {
                    throw new IOException("Root object not read from input stream: " + obj.mDigest);
                }
                referencedLinks.add(obj.mDigest);
            }

            for (Block block : archiveData.mBlocks) {
                for (LinkDigest digest : block.getDigests()) {
                    referencedLinks.add(digest);
                }
            }
            // Paranoid check to make sure that every link referenced
            // in the manifest was read, and no extra links were
            // included.
            if (!referencedLinks.equals(readLinks)) {
                throw new IOException("Data doesn't match manifest???");
            }

            return archiveData;
        } finally {
            if (mStreamFactory.shouldCloseInputStream()) {
                inputStream.close();
            }
        }
    }
}
