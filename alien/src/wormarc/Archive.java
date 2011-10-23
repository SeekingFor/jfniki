/* Freenet Write Once Read Multiple ARChive.
 *
 * Copyright (C) 2010, 2011 Darrell Karbott
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation; either
 * version 2.0 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 * Author: djk@isFiaD04zgAgnrEC5XJt1i4IE7AkNPqhBG5bONi6Yks
 *
 * This file was developed as component of
 * "fniki" (a wiki implementation running over Freenet).
 */

package wormarc;

import java.io.InputStream;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import wormarc.hgdeltacoder.HgDeltaCoder;

public class Archive {
    private final DeltaCoder mCoder = new HgDeltaCoder();
    private final HistoryLinkMap mLinkMap = new HistoryLinkMap();
    private final LinkDataFactory mLinkDataFactory = new RamLinkDataFactory();

    ////////////////////////////////////////////////////////////
    // Total in memory non-transient rep of an archive.
    private List<Block> mBlocks;
    private List<RootObject> mRootObjects;
    ////////////////////////////////////////////////////////////

    private Block mUpdates;

    public final static class RootObject implements Comparable<RootObject> {
        public final LinkDigest mDigest;
        public final int mKind;
        public RootObject(final LinkDigest digest, final int kind) {
            mDigest = digest;
            mKind = kind;
        }

        public boolean equals(final Object other) {
            if (!(other instanceof RootObject)) {
                return false;
            }
            return compareTo((RootObject)other) == 0;
        }

        // IMPORTANT: Must be able to sort stably so you get an identical binary rep for the same list.
        public int compareTo(final RootObject obj) {
            if (mKind - obj.mKind == 0) {
                // Then by digest hex string.
                return mDigest.toString().compareTo(obj.mDigest.toString());
            }
            // First by kind.
            return mKind - obj.mKind;
        }
    }

    // LATER: do better?
    // I just need a way to return a struct from Archive.IO.read().
    // Imperfect. Risky, Lists are mutable, Blocks are mutable.
    public final static class ArchiveData {
        public final List<Block> mBlocks;
        public final List<RootObject> mRootObjects;

        public ArchiveData(final List<Block> blocks, final List<RootObject> rootObjects) {
            mBlocks = Collections.unmodifiableList(blocks);
            mRootObjects = Collections.unmodifiableList(rootObjects);
        }

        // DCI: define hash too?
        public boolean equals(final Object other) {
            if (!(other instanceof ArchiveData)) {
                return false;
            }
            final ArchiveData otherData = (ArchiveData)other;
            return mBlocks.equals(otherData.mBlocks) && mRootObjects.equals(otherData.mRootObjects);
        }

        public String pretty() {
            final StringBuilder buffer = new StringBuilder();
            buffer.append("--- ArchiveData ---\n");
            buffer.append("\nmRootObjects:\n");
            for (Archive.RootObject obj : mRootObjects) {
                buffer.append(String.format("   %s:%d\n", obj.mDigest, obj.mKind));
            }
            buffer.append("mBlocks:\n");
            int count = 0;
            for (Block block : mBlocks) {
                buffer.append(String.format("   [%d]\n", count));
                for (LinkDigest digest : block.getDigests()) {
                    buffer.append("      ");
                    buffer.append(digest.toString());
                    buffer.append("\n");
                }
                count++;
            }
            buffer.append("---\n");
            return buffer.toString();
        }
    }

    public interface IO {
        // DCI: back to this.
        // It would be cleaner to have this take an ArchiveData, but the block list isn't immutable.
        void write(HistoryLinkMap linkMap, List<Block> blocks, List<Archive.RootObject> rootObjects) throws IOException;
        // ArchiveData == blocks, rootObjects
        Archive.ArchiveData read(HistoryLinkMap linkMap, LinkDataFactory linkFactory) throws IOException;
    }

    public interface LinkSource {
        HistoryLink readLink(HistoryLinkMap linkMap, LinkDataFactory linkFactory, LinkDigest digest) throws IOException;
    }

    public final static int REPARTITION_MULTIPLE = 2;
    public final static int MAX_CHAIN_LENGTH = 16;
    public final static int MAX_BLOCKS = 4;

    public Archive() {
        reset();
    }

    public Archive deepCopy() {
        if (mUpdates != null) {
            throw new IllegalStateException("Can't copy while updating.");
        }
        Archive ret = new Archive();
        ret.mBlocks = new ArrayList<Block>(mBlocks);
        ret.mRootObjects = new ArrayList<RootObject>(mRootObjects);
        ret.mUpdates = null;
        ret.mLinkMap.putAll(mLinkMap.getUnmodifiableMap());
        return ret;
    }

    public final void reset() {
        mBlocks = new ArrayList<Block>();
        mRootObjects = new ArrayList<RootObject>();
        mUpdates = null;
    }

    public ArchiveData getData() {
        return new ArchiveData(mBlocks, mRootObjects);
    }

    public void setFromData(final ArchiveData data) {
        mBlocks = new ArrayList<Block>(data.mBlocks);
        mRootObjects = new ArrayList<RootObject>(data.mRootObjects);
        mUpdates = null;
    }

    public InputStream getFile(final LinkDigest chainHead) throws IOException {
        if (chainHead == null) {
            throw new IllegalArgumentException("chainHead is null");
        }

        try {
            return mCoder.applyDeltas(mLinkMap.getChain(chainHead, true));
        } catch (HistoryLinkMap.LinkNotFoundException lookupFailed) {
            lookupFailed.rethrowAsIOException();
            return null; // Unreachable.
        }
    }

    // Calling this with stopAtEnd == false allows you to read change history past the
    // last time the chain was truncated.
    public List<LinkDigest> getChain(final LinkDigest chainHead, final boolean stopAtEnd) throws IOException {
        if (chainHead == null) {
            throw new IllegalArgumentException("chainHead is null");
        }
        try {
            final List<LinkDigest> digests = new ArrayList<LinkDigest>();
            for (HistoryLink link : mLinkMap.getChain(chainHead, stopAtEnd)) {
                digests.add(link.mHash);
            }
            return digests;
        } catch (HistoryLinkMap.LinkNotFoundException lookupFailed) {
            lookupFailed.rethrowAsIOException();
            return null; // Unreachable.
        }
    }

    public LinkDigest putFile(final InputStream rawBytes, final LinkDigest prevChainHead) throws IOException {
        if (mUpdates == null) {
            IOUtil.silentlyClose(rawBytes);
            throw new IllegalStateException("Not updating. Did you forget to call startUpdate?");
        }

        if (rawBytes == null) {
            throw new IllegalArgumentException("rawBytes is null");
        }

        if (prevChainHead == null) {
            throw new IllegalArgumentException("prevChainHead is null");
        }

        InputStream prevStream = null; // Leaving this null causes a full reinsert.
        try {
            if (!prevChainHead.isNullDigest() &&
                mLinkMap.getChain(prevChainHead, true).size() < MAX_CHAIN_LENGTH) {
                // Add to chain the existing chain.
                prevStream = getFile(prevChainHead);
            }

            final HistoryLink link = mCoder.makeDelta(mLinkDataFactory,
                                                      prevChainHead,
                                                      prevStream,
                                                      rawBytes,
                                                      false);

            mUpdates.append(link.mHash);
            mLinkMap.addLink(link);

            return link.mHash;

        } finally {
            IOUtil.silentlyClose(rawBytes);
            IOUtil.silentlyClose(prevStream);
        }
    }

    public void startUpdate() {
        if (mUpdates != null) {
            throw new IllegalStateException("Already updating.");
        }
        mUpdates = new Block();
    }

    // LATER: revisit.
    // Doesn't guarantee that the Archive's state is rolled back to where it was
    // when startUpdate was called.
    public void abandonUpdate() {
        if (mUpdates == null) {
            return;
        }
        mUpdates = null;
    }

    public boolean commitUpdate() {
        if (mUpdates == null) {
            throw new IllegalStateException("Not updating. Did you forget to call startUpdate?");
        }

        if (mUpdates.getDigests().size() == 0) {
            mUpdates = null;
            return false;
        }

        mBlocks.add(0, mUpdates);
        mUpdates = null;
        return true;
    }

    public Set<LinkDigest> addAllLinks(final Set<LinkDigest> all) {
        for (Block block : mBlocks) {
            all.addAll(block.getDigests());
        }

        if (all.contains(LinkDigest.NULL_DIGEST)) {
            throw new RuntimeException("Assertion Failure: NULL_DIGEST in blocks");
        }

        for (RootObject obj : mRootObjects) {
            if (obj.mDigest.isNullDigest()) {
                continue;
            }
            if (!all.contains(obj.mDigest)) {
                throw new RuntimeException("Assertion Failure: root object not in blocks: " + obj.mDigest);
            }
        }

        return all;
    }

    public Set<LinkDigest> allLinks() {
        return addAllLinks(new HashSet<LinkDigest>());
    }

    public Set<LinkDigest> referencedLinks() throws IOException {
        // DCI: DOG SLOW.
        // DCI: cache value? notion of archive being "dirty"
        // Set<LinkDigest> getReferencedLinks(Achive archive, Archive.RootObject obj);
        final Set<LinkDigest> links = new HashSet<LinkDigest>();
        for (RootObject obj : mRootObjects) {
            if (obj.mDigest.isNullDigest()) {
                continue;
            }

            // Adds the links for the file that the object is stored in.
            links.addAll(getChain(obj.mDigest, true));

            // Marshal an instance of the object and ask it if it has any additional
            // links.
            links.addAll(RootObjectKind.getContainer(this, obj).getReferencedLinks(mLinkMap));
        }
        return links;
    }

    protected static List<Block> mergeBlocks(final List<Block> blocks,
                                             final List<PartitioningMath.Partition> partitions,
                                             final Set<LinkDigest> survivors) {
        final List<Block> merged = new ArrayList<Block>();
        for (PartitioningMath.Partition partition : partitions) {
            if (partition.getStart() == partition.getEnd()) {
                merged.add(blocks.get(partition.getStart()));
                continue;
            }
            final ArrayList<LinkDigest> digests = new ArrayList<LinkDigest>();
            for (int index = partition.getStart(); index <= partition.getEnd(); index++) {
                digests.addAll(blocks.get(index).getDigests());
            }

            List<LinkDigest> filtered = new ArrayList<LinkDigest>();
            for (LinkDigest digest : digests) {
                if (!survivors.contains(digest)) {
                    //System.out.println("DROPPED: " + digest);
                    continue;
                }
                filtered.add(digest);
                survivors.remove(digest); // i.e. no duplicates.
            }
            merged.add(new Block(filtered));
        }

        return merged;
    }

    // Can break ordering constraint.
    // Prepend the manifest update to the start of the first block.
    public void compressAndUpdateArchiveManifest() throws IOException {
        compressAndUpdateArchiveManifest(MAX_BLOCKS);
    }

    public void compressAndUpdateArchiveManifest(final int maxBlocks) throws IOException {
        if (mUpdates != null) {
            throw new IllegalStateException("Can't update the archive manifest while updating");
        }

        if (mBlocks.size() > maxBlocks) {
            compress(maxBlocks); // DCI: what if you fail below?
        }

        // IMPORTANT: Clear the ARCHIVE_MANIFEST value.
        // There are tricks in ManifestArchive.fromBytes to fixup the top link
        // that depend on this value being NULL_DIGEST.
        LinkDigest currentVersion = getRootObject(RootObjectKind.ARCHIVE_MANIFEST);
        setRootObject(LinkDigest.NULL_DIGEST, RootObjectKind.ARCHIVE_MANIFEST);

        mUpdates = new Block();

        final ArchiveManifest newManifest = new ArchiveManifest(mRootObjects, mBlocks);
        try {
            // DCI: retest
            currentVersion = putFile(newManifest.toBytes(), currentVersion);
            mBlocks.get(0).prepend(currentVersion);
        } finally {
            // IMPORTANT: Backs out change on failure.
            setRootObject(currentVersion, RootObjectKind.ARCHIVE_MANIFEST);
            mUpdates = null;
        }

        assertArchiveManifestIsValid("compressAndupdateArchiveManifest " +
                                     "produced an invalid ARCHIVE_MANIFEST!");
    }

    public boolean compress() throws IOException {
        return compress(MAX_BLOCKS);
    }

    public boolean compress(final int maxBlocks) throws IOException {
        if (mUpdates != null) {
            throw new IllegalStateException("Can't compress while updating");
        }

        final Set<LinkDigest> referenced = referencedLinks();
        final ArrayList<PartitioningMath.Partition> uncompressed = new ArrayList<PartitioningMath.Partition>();
        int index = 0;
        for (Block block : mBlocks) {
            final ArrayList<LinkDigest> survivors = new ArrayList<LinkDigest>();
            for (LinkDigest digest : block.getDigests()) {
                if (referenced.contains(digest)) {
                    survivors.add(digest);
                }
            }
            // Length after dropping unreferenced links.
            uncompressed.add(new PartitioningMath.Partition(index, index, mLinkMap.getLength(survivors)));
            index++;
        }

        final List<PartitioningMath.Partition> compressed = PartitioningMath.compress(uncompressed,
                                                                                maxBlocks,
                                                                                REPARTITION_MULTIPLE);
        if (uncompressed.size() == compressed.size()) {
            return false;
        }

        mBlocks = mergeBlocks(mBlocks, compressed, referencedLinks());
        return true;
    }

    ////////////////////////////////////////////////////////////
    public void read(final IO source) throws IOException {
        if (source == null) {
            throw new IllegalArgumentException("source is null.");
        }
        if (mUpdates != null) {
            throw new IllegalStateException("Can't read while updating.");
        }
        setFromData(source.read(mLinkMap, mLinkDataFactory));
    }

    public void write(final IO sink) throws IOException {
        if (sink == null) {
            throw new IllegalArgumentException("sink is null.");
        }
        if (mUpdates != null) {
            throw new IllegalStateException("Can't write while updating.");
        }

        assertArchiveManifestIsValid("Trying to write archive with invalid ARCHIVE_MANIFEST!");

        sink.write(mLinkMap, mBlocks, mRootObjects);
    }

    public static Archive load(final IO source, boolean skipValidation) throws IOException {
        final Archive loaded = new Archive();
        loaded.read(source);
        if (!loaded.getRootObject(RootObjectKind.ARCHIVE_MANIFEST).isNullDigest()) {
             if (!loaded.hasValidArchiveManifest()) {
                 if (!skipValidation) {
                     // This is a runtime error, not an assertion failure
                     // because we didn't create the manifest.
                     throw new IOException("Invalid ARCHIVE_MANIFEST!"); // DCI: predicate failure
                 }
             }
        }
        return loaded;
    }

    public static Archive load(final IO source) throws IOException {
        return load(source, false);
    }

    ////////////////////////////////////////////////////////////
    // Why so complicated?
    // It would have been simpler to instantiate the delta coder
    // and link map directly, but I don't want to break the
    // encapsulation of that functionality in Archive.

    // INTENT: Used to bootstrap loading Archive instances out of a
    // cache from an ArchiveManifest file chainHead digest.
    //
    // Read a single file from a source of history links without
    // an Archive instance.
    public static InputStream readFile(final LinkDigest chainHead,
                                       final LinkSource source)
        throws IOException {
        if (chainHead.isNullDigest()) {
            throw new IllegalArgumentException("chainHead is null");
        }
        IO singleFileIO = new IO() {
                public void write(HistoryLinkMap linkMap,
                                  List<Block> blocks,
                                  List<Archive.RootObject> rootObjects)
                    throws IOException { throw new IOException("ENOTIMPL"); }
                public Archive.ArchiveData read(HistoryLinkMap linkMap,
                                                LinkDataFactory linkDataFactory)
                    throws IOException {
                    HistoryLink current = source.readLink(linkMap,
                                                          linkDataFactory,
                                                          chainHead);
                    // Add every link in the file's chain.
                    while (true) {
                        linkMap.addLink(current);
                        if (current.mIsEnd ||
                            current.mParent.isNullDigest()) {
                            break;
                        }
                        current = source.readLink(linkMap,
                                                  linkDataFactory,
                                                  current.mParent);
                    }

                    // Create ArchiveData for a temporary Archive that
                    // can read the file.
                    List<Block> blocks = new ArrayList<Block>();
                    blocks.
                        add(new Block
                            (new ArrayList<LinkDigest>(linkMap.
                                                       getUnmodifiableMap().
                                                       keySet()
                                                       ))
                            );

                    return new ArchiveData(blocks, new ArrayList<RootObject>());
                }
            };

        // At least this is simple.
        return Archive.load(singleFileIO).getFile(chainHead);
    }

    ////////////////////////////////////////////////////////////
    public void setRootObject(final LinkDigest value, final int kind, final boolean replace) {
        final RootObject obj = new RootObject(value, kind);
        if (replace) {
            for (int index = 0; index < mRootObjects.size(); index++) {
                if (mRootObjects.get(index).mKind == kind) {
                    mRootObjects.set(index, obj);
                    return;
                }
            }
        }
        mRootObjects.add(obj);

        // Kind of gross, but this list is tiny.
        Collections.sort(mRootObjects);
    }

    public void unsetRootObject(final int kind) {
        for (RootObject obj : mRootObjects) {
            if (obj.mKind == kind) {
                mRootObjects.remove(obj);
                // Can't continue iterating after modifying. Do better?
                unsetRootObject(kind);
                break;
            }
        }
    }

    public void setRootObject(final LinkDigest value, final int kind) {
        setRootObject(value, kind, true);
    }

    // Returns NULL_DIGEST if not found.
    public LinkDigest getRootObject(final int kind) {
        for (RootObject obj : mRootObjects) {
            if (obj.mKind == kind) {
                return obj.mDigest;
            }
        }
        return LinkDigest.NULL_DIGEST;
    }

    // Updates the first root object of kind kind.
    public LinkDigest updateRootObject(final InputStream data, final int kind) throws IOException {
        final LinkDigest digest = putFile(data, getRootObject(kind));
        setRootObject(digest, kind);
        return digest;
    }

    public boolean hasValidArchiveManifest() throws IOException {
        if (mUpdates != null) {
            throw new IllegalStateException("Can't validate the archive manifest while updating.");
        }

        final LinkDigest digest = getRootObject(RootObjectKind.ARCHIVE_MANIFEST);
        if (digest.isNullDigest()) {
            return false;
        }

        // This doesn't guarantee that the binary reps are identical. hmmmm...
        // DCI: Have seen this fail!
        return ArchiveManifest.fromBytes(getFile(digest), digest).makeArchiveData().equals(getData());
    }

    public void assertArchiveManifestIsValid(String failureMsg) throws IOException {
        if (!getRootObject(RootObjectKind.ARCHIVE_MANIFEST).isNullDigest()) {
            if (!hasValidArchiveManifest()) {
                throw new RuntimeException(String.format("Assertion Failure: %s", failureMsg));
            }
        }
    }

    ////////////////////////////////////////////////////////////
    public int getChainLength(final LinkDigest chainHead) {
        if (chainHead == null) {
            throw new IllegalArgumentException("chainHead is null");
        }

        try {
            return mLinkMap.getChain(chainHead, true).size();
        } catch (HistoryLinkMap.LinkNotFoundException lookupFailed) {
            return -1;
        }
    }

    public String pretty() {
        final StringBuilder buffer = new StringBuilder();
        buffer.append("--- Archive ---\n");
        buffer.append("\nmRootObjects:\n");
        for (Archive.RootObject obj : mRootObjects) {
            buffer.append(String.format("   %s:%d\n", obj.mDigest, obj.mKind));
        }
        buffer.append("mBlocks:\n");
        int count = 0;
        for (Block block : mBlocks) {
            long blockLength = -1;
            try {
                blockLength = mLinkMap.getLength(block);
            } catch (IOException ioe) {
            }
            buffer.append(String.format("   [%d] : %d\n", count, blockLength));
            for (LinkDigest digest : block.getDigests()) {
                buffer.append("      ");
                buffer.append(prettyLink(digest.toString()));
                buffer.append("\n");
            }
            count++;
        }
        buffer.append("---\n");
        return buffer.toString();
    }

    public String prettyLink(final String linkHash) {
        try {
            final HistoryLink link = mLinkMap.getLink(new LinkDigest(linkHash));
            final long length = BinaryLinkRep.getRepLength(link);
            return String.format("%s:%s [%d]%s", linkHash, link.mParent.toString(),
                                 length, link.mIsEnd ? ":END" : "");

        } catch (HistoryLinkMap.LinkNotFoundException lookupFailed) {
            return linkHash + ":???";
        }
    }

    public boolean isUpdating() { return mUpdates != null; }

    ////////////////////////////////////////////////////////////
    // Stats, for debugging only. PUNISHINGLY SLOW.
    //
    // LATER: rethink interfaces. clean up. speed up.
    //
    // No counts because you can get those from allLinks() / referencedLinks()
    public long sizeInBytes(boolean skipUnreferenced) throws IOException {
        if (skipUnreferenced) {
            return mLinkMap.getLength(new ArrayList<LinkDigest>(referencedLinks()));
        }
        return mLinkMap.getLength(new ArrayList<LinkDigest>(allLinks()));
    }

    public Map<Integer, Integer> linkStats() throws IOException {
        Map<Integer, Integer> values = new HashMap<Integer, Integer>();
        for (RootObject obj : mRootObjects) {
            if (obj.mDigest.isNullDigest()) {
                continue;
            }

            if (values.get(obj.mKind) == null) {
                values.put(obj.mKind, 0);
            }

            int fileChainLength = getChain(obj.mDigest, true).size();
            values.put(obj.mKind, fileChainLength +
                       values.get(obj.mKind) +
                       RootObjectKind.getContainer(this, obj).
                       getReferencedLinks(mLinkMap).size());
        }
        return values;
    }

    public List<Integer> blockLinkCounts(boolean skipUnreferenced) throws IOException {
        Set<LinkDigest> skip = new HashSet<LinkDigest>();
        if (skipUnreferenced) {
            skip = allLinks();
            skip.removeAll(referencedLinks());
        }

        List<Integer> values = new ArrayList<Integer>();

        for (Block block : mBlocks) {
            Set<LinkDigest> digests = new HashSet<LinkDigest>(block.getDigests());
            digests.removeAll(skip);
            values.add(digests.size());
        }
        return values;
    }

    public List<Long> blockLengths(boolean skipUnreferenced) throws IOException {
        Set<LinkDigest> skip = new HashSet<LinkDigest>();
        if (skipUnreferenced) {
            skip = allLinks();
            skip.removeAll(referencedLinks());
        }

        List<Long> values = new ArrayList<Long>();

        for (Block block : mBlocks) {
            Set<LinkDigest> digests = new HashSet<LinkDigest>(block.getDigests());
            digests.removeAll(skip);
            values.add(mLinkMap.getLength(new ArrayList<LinkDigest>(digests)));
        }
        return values;
    }
}
