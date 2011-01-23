/* A class to manage mapping LinkDigest references to HistoryLink instances.
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
import java.io.InputStream;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.List;

public class HistoryLinkMap {
    // DCI: fix serialVersionUID
    // DCI: Hmmmm...memory use can grow without bound...
    private Map<LinkDigest,HistoryLink> mMap = new HashMap<LinkDigest,HistoryLink>();

    ////////////////////////////////////////////////////////////
    public static class LinkNotFoundException extends RuntimeException {
        LinkNotFoundException(LinkDigest digest) {
            super(String.format("HistoryLink: %s not found", digest.toString()));
        }
        public void rethrowAsIOException() throws IOException {
            throw new IOException(getMessage());
        }
    }

    ////////////////////////////////////////////////////////////
    // Nested helper classes for iteration.
    final class LinkLookupIterator implements Iterator<HistoryLink> {
        final HistoryLinkMap mLinkMap;
        final Iterator<LinkDigest> mDigestIterator;

        LinkLookupIterator(HistoryLinkMap linkMap, Iterator<LinkDigest> digestIterator) {
            mLinkMap = linkMap;
            mDigestIterator = digestIterator;
        }

        public final boolean hasNext() { return mDigestIterator.hasNext(); }
        public final HistoryLink next() {
            // Let LinkNotFoundException propagate.
            return mLinkMap.getLink(mDigestIterator.next());
        }
        public final void remove() { throw new RuntimeException("Not implemented"); }
    }

    final class LinkLookupIterable implements Iterable<HistoryLink> {
        final HistoryLinkMap mLinkMap;
        final Iterable<LinkDigest> mDigestIterable;

        LinkLookupIterable(HistoryLinkMap linkMap, Iterable<LinkDigest> digestIterable) {
            mLinkMap = linkMap;
            mDigestIterable = digestIterable;
        }

        public Iterator<HistoryLink> iterator() {
            return new LinkLookupIterator(mLinkMap, mDigestIterable.iterator());
        }
    }

    ////////////////////////////////////////////////////////////
    private final static void throwIfNullOrNullDigest(LinkDigest linkDigest) {
        if (linkDigest != null) {
            if (linkDigest.isNullDigest()) {
                throw new IllegalArgumentException("linkDigest was NULL_DIGEST");
            }
            return;
        }
        throw new IllegalArgumentException("linkDigest was null");
    }

    private final static void throwIfNull(LinkDigest linkDigest) {
        if (linkDigest != null) {
            return;
        }
        throw new IllegalArgumentException("linkDigest was null");
    }

    private final static void throwIfNull(Iterable<LinkDigest> linkDigest) {
        if (linkDigest == null) {
            throw new IllegalArgumentException("linkDigests is null");
        }
    }

    public boolean contains(LinkDigest linkDigest) {
        throwIfNull(linkDigest);
        return mMap.containsKey(linkDigest);
    }

    public HistoryLink getLink(LinkDigest linkDigest) {
        throwIfNull(linkDigest);
        HistoryLink link = mMap.get(linkDigest);
        if (link == null) {
            throw new LinkNotFoundException(linkDigest);
        }
        return link;
    }

    public Iterable<HistoryLink> getLinks(List<LinkDigest> linkDigests) {
        throwIfNull(linkDigests);
        return new LinkLookupIterable(this, linkDigests);
    }

    public List<HistoryLink> getChain(LinkDigest linkDigest, boolean stopAtEnd) {
        throwIfNullOrNullDigest(linkDigest);
        
        //System.out.println("Starting: " + linkDigest);
        ArrayList<HistoryLink> links = new ArrayList<HistoryLink>();
        HistoryLink link = new HistoryLink(0, false, null, linkDigest, null);
        int traversalCount = 33; // Hmmm
        while (!link.mParent.isNullDigest() &&
               (!(stopAtEnd && link.mIsEnd))) {
            //System.out.println("Looking up parent: " + link);
            link = getLink(link.mParent);
            links.add(link);
            traversalCount--;
            if (traversalCount == 0 && !stopAtEnd) {
                throw new RuntimeException("getChain() gave up.  Possible loop: " + linkDigest);
            }
        }
        //System.out.println("Exiting: " + linkDigest);
        return links;
    }

    public void addLink(HistoryLink link) {
        if (link == null || link.mHash == null) {
            throw new IllegalArgumentException("Bad HistoryLink");
        }
        mMap.put(link.mHash, link);
    }

    public void addLinks(Iterable<HistoryLink> links) {
        if (links == null) {
            throw new IllegalArgumentException("links is null");
        }
        for (HistoryLink link : links) {
            if (link == null || link.mHash == null) {
                throw new IllegalArgumentException("Bad HistoryLink");
            }
            mMap.put(link.mHash, link);
        }
    }

    public void removeLink(LinkDigest linkDigest) {
        throwIfNull(linkDigest);
        mMap.remove(linkDigest);
    }

    public void removeLinks(Iterable<LinkDigest> linkDigests) {
        throwIfNull(linkDigests);
        for (LinkDigest linkDigest : linkDigests) {
            mMap.remove(linkDigests);
        }
    }

    ////////////////////////////////////////////////////////////
    // Access to underlying map.
    public Map<LinkDigest,HistoryLink> getUnmodifiableMap() {
        return Collections.unmodifiableMap(mMap);
    }

    public void putAll(Map<LinkDigest,HistoryLink> otherMap) {
        mMap.putAll(otherMap);
    }

    ////////////////////////////////////////////////////////////
    // Block helper functions.

    // Get the length of the binary rep of the block.
    public long getLength(List<LinkDigest> digests) throws IOException {
        return BinaryLinkRep.getRepLength(getLinks(digests));
    }

    public Iterable<HistoryLink> getLinks(Block block) throws IOException {
        return getLinks(block.getDigests());
    }

    // Get the length of the binary rep of the block.
    public long getLength(Block block) throws IOException {
        return BinaryLinkRep.getRepLength(getLinks(block.getDigests()));
    }

    // Read the binary rep of the block.
    public InputStream getBinaryRep(Block block) throws IOException {
        return BinaryLinkRep.toBytes(getLinks(block.getDigests()));
    }

    public Block readFrom(InputStream rawByteStream,
                          LinkDataFactory linkDataFactory) throws IOException {

        Block block = new Block();
        // Force iteration over the entire list before adding to the map.
        ArrayList<HistoryLink> links = new ArrayList<HistoryLink>(BinaryLinkRep.readAll(rawByteStream, linkDataFactory));
        for (HistoryLink link : links) {
            block.append(link.mHash);
            addLink(link);
        }

        return block;
    }
}