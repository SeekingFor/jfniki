/* An Archive.IO and ArchiveResolver implementation which read and writes to Freenet.
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

import java.io.InputStream;
import java.io.IOException;
import java.io.PrintStream;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import wormarc.Archive;
import wormarc.ArchiveResolver;
import wormarc.BinaryLinkRep;
import wormarc.Block;
import wormarc.ExternalRefs;
import wormarc.HistoryLink;
import wormarc.HistoryLinkMap;
import wormarc.IOUtil;
import wormarc.LinkDataFactory;
import wormarc.LinkDigest;
import wormarc.RootObjectKind;

public class FreenetIO implements Archive.IO, ArchiveResolver {
    private LinkCache mCache;

    // Transient
    private HistoryLinkMap mLinkMap;
    private LinkDataFactory mLinkDataFactory;

    private String mHost;
    private int mPort;
    private String mClientName = "FreenetIO_";

    private int mMaxBlockLength = 8 * 1024 * 1024;
    private int mMaxBlockCount = 4;

    private String mInsertUri;
    private String mRequestUri;
    private FreenetTopKey mPreviousTopKey;

    private static PrintStream sDebugOut = System.err;

    // Cache can be null.
    // When it is non-null all links read from Freenet are dumped to the cache.
    public FreenetIO(String host, int port, LinkCache cache) {
        mHost = host;
        mPort = port;
        mCache = cache;
    }

    public FreenetIO(String host, int port) {
        this(host, port, null);
    }

    public static void setDebugOutput(PrintStream out) {
        synchronized(FreenetIO.class) {
            sDebugOut = out;
            FCPCommandRunner.setDebugOutput(sDebugOut);
        }
    }

    public String getInsertUri() { return mInsertUri; }
    public void setInsertUri(String uri) { mInsertUri = uri; }

    public String getRequestUri() { return mRequestUri; }
    public void setRequestUri(String uri) { mRequestUri = uri; }

    public FreenetTopKey readTopKey(String uri) throws IOException {
        FCPCommandRunner runner = null;
        try {
            runner = new FCPCommandRunner(mHost, mPort,
                                          mClientName +
                                          IOUtil.randomHexString(12));
            FCPCommandRunner.GetTopKey requestTopKey =
                runner.sendGetTopKey(uri);

            runner.waitUntilAllFinished();
            requestTopKey.raiseOnFailure();
            return requestTopKey.getTopKey();

        } catch (InterruptedException ie) {
            throw new IOException("FreenetTopKey read timed out.", ie);
        } finally {
            if (runner != null) {
                runner.disconnect();
            }
        }
    }

    // Speeds up inserting by allowing write() to skip blocks that were
    // already inserted.
    // For this to work, the PARENT_REFERENCES root object must be up to date.
    public void maybeLoadPreviousTopKey(Archive archive) throws IOException {
        mPreviousTopKey = null;
        if (archive.getRootObject(RootObjectKind.PARENT_REFERENCES).isNullDigest()) {
            return;
        }

        ExternalRefs refs =
            ExternalRefs.fromBytes(archive.
                                   getFile(archive.
                                           getRootObject(RootObjectKind.PARENT_REFERENCES)));

        if (refs.mRefs.size() != 1 ||
            refs.mRefs.get(0).mKind != ExternalRefs.KIND_FREENET) {
            throw new IOException("Expected a single Freenet URI!");
            // LATER: Must remove this constraint to allow merging.
        }

        String topKeyUri = refs.mRefs.get(0).mExternalKey;
        mPreviousTopKey = readTopKey(topKeyUri);
    }

    // DCI: BUG: redundant inserts are not supported yet. False assumption Block <-> CHK
    // Updates the request URI on success.
    public void write(HistoryLinkMap linkMap, List<Block> blocks, List<Archive.RootObject> rootObjects) throws IOException {
        if (mInsertUri == null) {
            throw new IllegalStateException("Set the uri!");
        }

        // DCI: fail early for inserts that are too big.
        FCPCommandRunner runner = null;
        try {
            runner = new FCPCommandRunner(mHost, mPort,
                                          mClientName +
                                          IOUtil.randomHexString(12));

            // Precompute the block CHKs so we can skip blocks that
            // are already in Freenet.
            List<FreenetTopKey.BlockDescription> descriptions =
                precomputeDescriptions(runner, linkMap, blocks);

            if (blocks.size() != descriptions.size()) {
                throw new RuntimeException("Assertion Failure: blocks.size() != descriptions.size()");
            }

            Set<String> previousChks =  new HashSet<String>();
            if (mPreviousTopKey != null) {
                for (FreenetTopKey.BlockDescription desc : mPreviousTopKey.mBlockDescriptions) {
                    for (int subIndex = 0; subIndex < desc.mCHKs.size(); subIndex++) {
                        previousChks.add(desc.getCHK(subIndex));
                    }
                }
            }

            List<FCPCommandRunner.PutBlock> puts = new ArrayList<FCPCommandRunner.PutBlock>();
            for (int index = 0; index < descriptions.size(); index++) {
                FreenetTopKey.BlockDescription desc = descriptions.get(index);
                if (previousChks.contains(desc.getCHK(0))) {
                    // i.e. the block was already inserted, so skip it.
                    continue;
                }
                puts.add(runner.sendPutBlock(index, linkMap, blocks.get(index)));
            }

            runner.waitUntilAllFinished();

            for (FCPCommandRunner.PutBlock put : puts) {
                put.raiseOnFailure();
            }

            FreenetTopKey topKey = new FreenetTopKey(rootObjects, descriptions);
            raiseOnSuspectTopKey(topKey); // Fails, but too late!

            FCPCommandRunner.PutTopKey putTopKey =
                runner.sendPutTopKey(mInsertUri, topKey);

            runner.waitUntilAllFinished();
            putTopKey.raiseOnFailure();
            mRequestUri = putTopKey.getUri();
        } catch (InterruptedException ie) {
            throw new IOException("Write timed out.", ie);
        } catch (IllegalBase64Exception ibe) {
            throw new IOException("Binary URI decode failed", ibe);
        } finally {
            if (runner != null) {
                runner.disconnect();
            }
        }
    }

    private void raiseOnSuspectTopKey(FreenetTopKey topKey) throws IOException {
        if (topKey.mBlockDescriptions.size() > mMaxBlockCount) {
            throw new IOException(String.format("To many blocks in FreenetTopKey: %d",
                                                topKey.mBlockDescriptions.size()));
        }

        for (FreenetTopKey.BlockDescription desc : topKey.mBlockDescriptions ) {
            if (desc.mLength > mMaxBlockLength) {
                throw new IOException(String.format("Block too big: %d",
                                                    desc.mLength));
            }
        }
        int length = IOUtil.readAndClose(topKey.toBytes()).length;
        if (length > FreenetTopKey.MAX_LENGTH) {
            throw new IOException("FreenetTopKey is too big!");
        }
    }

    public Archive.ArchiveData read(HistoryLinkMap linkMap, LinkDataFactory linkFactory) throws IOException {
        // For now, we request everything.
        // LATER: Think through incremental requesting. We can do much better.
        if (linkMap == null) {
            throw new IllegalArgumentException("linkMap == null");
        }

        if (linkFactory == null) {
            throw new IllegalArgumentException("linkFactory == null");
        }

        FCPCommandRunner runner = null;
        try {
            // Read topkey
            mLinkMap = linkMap;
            mLinkDataFactory = linkFactory;

            runner = new FCPCommandRunner(mHost, mPort,
                                          mClientName +
                                          IOUtil.randomHexString(12));

            // Read the topkey from Freenet.
            FCPCommandRunner.GetTopKey requestTopKey =
                runner.sendGetTopKey(mRequestUri);

            runner.waitUntilAllFinished();
            requestTopKey.raiseOnFailure();

            FreenetTopKey topKey = requestTopKey.getTopKey();
            raiseOnSuspectTopKey(topKey);

            // Read all the blocks listed in the top key.
            // Note: The GetBlock requests read and cache the links
            //       by calling back into readLinks(). See below.
            int count = 0;
            List<FCPCommandRunner.GetBlock> gets = new ArrayList<FCPCommandRunner.GetBlock>();
            for (FreenetTopKey.BlockDescription desc : topKey.mBlockDescriptions ) {
                // LATER: Handle redundant block fetches.
                sDebugOut.println(String.format("Requesting[%d]: %s",
                                                desc.mLength,
                                                desc.getCHK(0)));
                gets.add(runner.sendGetBlock(desc.getCHK(0), desc.mLength, count++, this));
            }
            runner.waitUntilAllFinished();

            // Collect the Blocks.
            List<Block> blocks = new ArrayList<Block>();
            for (FCPCommandRunner.GetBlock get : gets) {
                get.raiseOnFailure();
                blocks.add(get.getBlock());
            }
            return new Archive.ArchiveData(blocks, topKey.mRootObjects);

        } catch (InterruptedException ie) {
            throw new IOException("Read timed out.", ie);
        } catch (IllegalBase64Exception ibe) {
            throw new IOException("Binary URI decode failed", ibe);
        } finally {
            mLinkMap = null;
            mLinkDataFactory = null;
            if (runner != null) {
                sDebugOut.println("FCP Connection -- DISCONNECTING!");
                runner.disconnect();
            }
        }
    }

    // Used by FCPCommandRunner.
    protected Block readLinks(InputStream data) throws IOException {
        if (mLinkDataFactory == null || mLinkMap  == null) {
            throw new IllegalStateException("Not expecting call.");
        }

        List<LinkDigest> digests = new ArrayList<LinkDigest>();
        while (true) {
            HistoryLink link = BinaryLinkRep.fromBytes(data, mLinkDataFactory);
            if (link == null) {
                break;
            }
            digests.add(link.mHash);
            mLinkMap.addLink(link);
            if (mCache != null) {
                mCache.writeLink(link);
            }
        }
        return new Block(digests);
    }

    ////////////////////////////////////////////////////////////
    private List<FreenetTopKey.BlockDescription> precomputeDescriptions(FCPCommandRunner runner,
                                                                        HistoryLinkMap linkMap,
                                                                        List<Block> blocks)
        throws IllegalBase64Exception,
               InterruptedException,
               IOException {

        // Use the Freenet node to tell us the CHKs for the new blocks without
        // inserting them.
        int count = 0;
        List<FCPCommandRunner.GetBlockChk> getChks = new ArrayList<FCPCommandRunner.GetBlockChk>();
        for (Block block : blocks) {
            getChks.add(runner.sendGetBlockChk(count++, linkMap, block));
        }

        runner.waitUntilAllFinished();

        int index = 0;
        List<FreenetTopKey.BlockDescription> descriptions = new ArrayList<FreenetTopKey.BlockDescription>();
        for (FCPCommandRunner.GetBlockChk get : getChks) {
            get.raiseOnFailure();
            descriptions.add(FreenetTopKey.makeDescription(get.getLength(), Arrays.asList(get.getUri())));
            index++;
        }
        return descriptions;
    }

    ////////////////////////////////////////////////////////////

    public Archive resolve(ExternalRefs.Reference fromReference) throws IOException {
        String previousRequestUri = mRequestUri;
        try {
            if (fromReference.mKind != ExternalRefs.KIND_FREENET) {
                throw new IOException("Reference is not a Freenet URI");
            }
            sDebugOut.println("resolving Archive from: " + fromReference.mExternalKey);
            mRequestUri = fromReference.mExternalKey;
            Archive loaded = Archive.load(this); // Hmmmm... slurps stuff into the cache. ???
            if (!loaded.getRootObject(RootObjectKind.ARCHIVE_MANIFEST).isNullDigest()) {
                if (!loaded.hasValidArchiveManifest()) {
                    throw new IOException("Invalid ARCHIVE_MANIFEST: " + fromReference.mExternalKey);
                }
            }
            return loaded;

        } finally {
            mRequestUri = previousRequestUri;
        }
    }

    public String getNym(ExternalRefs.Reference fromReference) throws IOException {
        if (fromReference.mKind != ExternalRefs.KIND_FREENET ||
            (!fromReference.mExternalKey.startsWith("SSK@") ||
            fromReference.mExternalKey.indexOf("/") == -1)) {
            return "notfreenetssk";
        }

        // Public key part of the SSK.
        return fromReference.mExternalKey.
            substring(4, fromReference.mExternalKey.indexOf(","));
    }
}
