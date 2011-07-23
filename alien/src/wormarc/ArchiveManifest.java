/* A top level manifest of the entire contents of an archive.
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

// LATER: Simplify this.
// 0) Implement toBytes() and fromBytes() on Archive.ArchiveData.
// 1) Use that to implement ArchiveManifest. just a thin wrapper that does the fixup hacks.
// 2) Keep binary compatibility.

package wormarc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// INTENT: Should contain all the information needed to persist an archive.
//         This makes re-insertion possible.
public class ArchiveManifest {
    long mVersion; // DCI: really need a long?
    List<Archive.RootObject> mRootObjects;
    List<Block> mBlocks;

    public final static long SUPPORTED_VERSION = 1; // Binary rep version.

    // These lists are already unmodifiable.
    public List<Archive.RootObject> getRootObjects() { return mRootObjects; }
    public List<Block> getBlocks() { return mBlocks; }

    public ArchiveManifest(List<Archive.RootObject> rootObjects, List<Block> blocks) {
        mVersion = SUPPORTED_VERSION;
        mRootObjects = Collections.unmodifiableList(rootObjects);
        mBlocks = Collections.unmodifiableList(blocks);
    }

    public Archive.ArchiveData makeArchiveData() {
        return new Archive.ArchiveData(mBlocks, mRootObjects);
    }

    ////////////////////////////////////////////////////////////
    // Handle read from / writing to stream.

    /*
      Writing into the Archive is tricky because we can't know the final
      chain head HistoryLink for the file itself.

      REQUIREMENTS:
      0. A well formed ArchiveManifest should a value for the ARCHIVE_MANIFEST
         root object which allows it to be read from the Archive, even if
         that value is unknown at the time it is written into the archive.
      1. Successive to write calls on an ArchiveManifest should always return
         bit for bit identical data.

      SOLUTION:
      REQUIRE that there is only one ARCHIVE_MANIFEST root object.
      REQUIRE that the latest link of the archive manifest is always the
      first link in the first block.

      On write:
      ALWAYS store NULL_DIGEST in the ARCHIVE_MANIFEST root object and in place
      of the chain head digest for the head link of the file itself in the
      block lists.  It should be the first link in the first block.

      On read:
      Fixup the NULL_DIGEST references with the chain head hash that the
      the file was read from in both places.

      Use case:
      Write:
      Archive updates manifest.
      Archive writes manifest into itself.
      At this point the archive has the chain head hash for the update
      manifest, even though it isn't stored in the manifest itself.
      Archive stashes that hash somewhere (e.g. the ARCHIVE_MANIFEST field
      in a FreenetTopKey).

      Write subcases:
      0. We don't know the chain head value (updating)
      ARCHIVE_MANIFEST root object MUST be NULL_DIGEST.
      We need to insert a NULL_DIGEST place holder hash at the start of
      the first block.

      1. We do know the chain head value (re-inserting)
      Save the hash. Set ARCHIVE_MANIFEST root object to NULL_HASH.
      CHECK that the first link in the first block is the saved hash.
      Write NULL_HASH instead.

      NEVER HIT THIS CASE FOR FNIKI!

      Read:
      Archive gets a non NULL_DIGEST ARCHIVE_MANIFEST by some means
      (e.g. it reads it out of the the ARCHIVE_MANIFEST field in
      a FreenetTopKey).
      Archive uses it to read the ArchiveManifest instance out of
      Freenet.

      LATER: Do better.
      Why write the NULL_DIGEST into the block / root objects at all?
      Could save ~= 40 bytes per insert.
      Didn't do this because I didn't want to break format compatibility again.

    */

    public InputStream toBytes() throws IOException {
        // Writes everything into RAM! hmmmm...

        if (mBlocks.size() == 0) {
            throw new IOException("ArchiveManifest must have at least one block!");
        }

        LinkDigest digest = getArchiveManifestDigest(mRootObjects);

        boolean insertSentinel = digest.isNullDigest();

        if ((!insertSentinel) &&
            ((mBlocks.get(0).getDigests().size() == 0) ||
             (!mBlocks.get(0).getDigests().get(0).equals(digest)))) {
            throw new IOException("ARCHIVE_MANIFEST chain head not found at start of first block!");
        }

        ByteArrayOutputStream  buffer = new ByteArrayOutputStream();
        DataOutputStream outputStream = new DataOutputStream(buffer);
        if (mVersion != SUPPORTED_VERSION) {
            throw new IOException("Version mismatch.");
        }
        outputStream.writeLong(mVersion);
        outputStream.writeByte(mRootObjects.size()); // DCI: test, sign?
        for (Archive.RootObject obj : mRootObjects) {
            if (obj.mKind == RootObjectKind.ARCHIVE_MANIFEST) {
                outputStream.write(LinkDigest.NULL_DIGEST.getBytes());
            } else {
                outputStream.write(obj.mDigest.getBytes());
            }
            outputStream.writeInt(obj.mKind);
        }
        outputStream.writeByte(mBlocks.size());

        boolean shouldAddOne = insertSentinel;
        for (Block block : mBlocks) {
            int linkCount = block.getDigests().size();
            if (shouldAddOne) {
                // Handle insert of optional sentinel link at start of first block.
                linkCount++;
                shouldAddOne = false;
            }
            outputStream.writeInt(linkCount);
        }

        // ALWAYS write placeholder for archive manifest topkey.
        // See shouldSkipOne below for the non-sentinel case.
        outputStream.write(LinkDigest.NULL_DIGEST.getBytes());

        boolean shouldSkipOne = (!insertSentinel);
        for (Block block : mBlocks) {
            for (LinkDigest value : block.getDigests()) {
                if (shouldSkipOne) {
                    shouldSkipOne = false;
                    continue;
                }
                // DCI: add a \n so the delta coder works better?
                outputStream.write(value.getBytes());
            }
        }
        outputStream.close();
        return new ByteArrayInputStream(buffer.toByteArray());
    }

    public static ArchiveManifest fromBytes(InputStream rawBytes, LinkDigest chainHeadFixup,
                                            boolean closeStream) throws IOException {
        try {
            if (chainHeadFixup == null || chainHeadFixup.isNullDigest()) {
                throw new IOException("chainHeadFixup is null or NULL_DIGEST!");
            }

            DataInputStream inputStream = new DataInputStream(rawBytes);
            long version = inputStream.readLong();
            if (version != SUPPORTED_VERSION) {
                throw new IOException("ArchiveManifest format version mis-match");
            }
            int rootObjectCount = inputStream.readByte();
            List<Archive.RootObject> rootObjects = new ArrayList<Archive.RootObject>();
            while (rootObjectCount > 0) {
                LinkDigest digest = BinaryLinkRep.readLinkDigest(inputStream);
                int kind = inputStream.readInt();
                if (kind == RootObjectKind.ARCHIVE_MANIFEST && digest.isNullDigest()) {
                    // Fixup the reference to the latest link for this file.
                    digest = chainHeadFixup;
                }
                rootObjects.add(new Archive.RootObject(digest, kind));
                rootObjectCount--;
            }

            int blockCount = inputStream.readByte();
            List<Integer> blockSizes = new ArrayList<Integer>();
            while (blockCount > 0) {
                blockSizes.add(inputStream.readInt());
                blockCount--;
            }

            List<Block> blocks = new ArrayList<Block>();
            for (int digestCount : blockSizes) {
                List<LinkDigest> digests = new ArrayList<LinkDigest>();
                while (digestCount > 0) {
                    digests.add(BinaryLinkRep.readLinkDigest(inputStream));
                    digestCount--;
                }
                if (blocks.size() == 0 && digests.size() >= 1) {
                    if (digests.get(0).isNullDigest()) {
                        digests.set(0, chainHeadFixup); // Fixup the reference to the latest link for this file.
                    } else {
                        throw new IOException("ArchiveManifest missing NULL_DIGEST sentinel block?");
                    }
                }
                blocks.add(new Block(digests));
            }
            if (closeStream) {
                inputStream.close();
            }
            rawBytes = null;
            return new ArchiveManifest(rootObjects, blocks);
        } finally {
            if (rawBytes != null && closeStream) {
                rawBytes.close();
            }
        }
    }
    public static ArchiveManifest fromBytes(InputStream rawBytes, LinkDigest chainHeadFixup)
        throws IOException {
        return fromBytes(rawBytes, chainHeadFixup, true);
    }

    // Hmmmm... Move?
    public static LinkDigest getArchiveManifestDigest(List<Archive.RootObject> rootObjects)
        throws IOException {
        LinkDigest digest = null;
        for (Archive.RootObject obj : rootObjects) {
            if (obj.mKind == RootObjectKind.ARCHIVE_MANIFEST) {
                if (digest != null) {
                    throw new IOException("ArchiveManifest has multiple ARCHIVE_MANIFEST root objects!");
                }
                digest = obj.mDigest;
            }
        }
        if (digest == null) {
            throw new IOException("ArchiveManifest has no ARCHIVE_MANIFEST root object!");
        }
        return digest;
    }

    ////////////////////////////////////////////////////////////
    // Every referenced link.
    public Set<LinkDigest> getReferencedLinks(HistoryLinkMap linkMap) {
        Set<LinkDigest> links = new HashSet<LinkDigest>();
        for (Block block : mBlocks) {
            links.addAll(block.getDigests());
        }

        // Doesn't recurse! If the manifest is well formed it should already
        // contain all it's links.
        for (Archive.RootObject obj : mRootObjects) {
            if (obj.mDigest != LinkDigest.NULL_DIGEST) {
                links.add(obj.mDigest);
            }
        }
        return links;
    }
}
