/* An ordered sequence of LinkDigest hashes.
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// DCI: Need a notion of being "dirty", and a cached name...
public class Block {
    // No guarantee that links don't appear multiple times???
    private ArrayList<LinkDigest> mDigests;

    public Block(List<LinkDigest> digests) { mDigests = new ArrayList<LinkDigest> (digests);}
    public Block() { this(new ArrayList<LinkDigest>()); }

    public List<LinkDigest> getDigests() { return Collections.unmodifiableList(mDigests); }

    public void prepend(LinkDigest linkDigest) {
        if (linkDigest == null) {
            throw new IllegalArgumentException("linkDigest is null");
        }
        mDigests.add(0, linkDigest);
    }

    public void append(LinkDigest linkDigest) {
        if (linkDigest == null) {
            throw new IllegalArgumentException("linkDigest is null");
        }
        mDigests.add(linkDigest);
    }

    public void append(List<LinkDigest> linkDigests) {
        // TRADE OFF: debuggability vs speed.  Can remove later if this is too much of speed hit.
        for (LinkDigest digest: linkDigests) {
            if (digest == null) {
                throw new IllegalArgumentException("LinkDigests contains a null element");
            }
        }
        mDigests.addAll(linkDigests);
    }

    public boolean equals(Object other) {
        if (!(other instanceof Block)) {
            return false;
        }
        Block otherBlock = (Block)other;
        return mDigests.equals(otherBlock.mDigests);
    }
}

