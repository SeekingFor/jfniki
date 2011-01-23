/* An implementation helper class for local file system Archive.IO implementations.
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

import java.io.IOException;
import java.util.List;

import wormarc.Archive;
import wormarc.Block;
import wormarc.HistoryLinkMap;
import wormarc.LinkDataFactory;
import wormarc.LinkDigest;

public abstract class ArchiveCacheBase extends LinkCache implements Archive.IO {
    // INTENT: Hook for LATER to allow CLI client to store version data by
    //         ArchiveManifest chain head LinkDigest.
    protected abstract Archive.ArchiveData readArchiveData(HistoryLinkMap linkMap, LinkDataFactory linkFactory) throws IOException;

    public ArchiveCacheBase(String directory) throws IOException {
        super(directory);
    }

    public void write(HistoryLinkMap linkMap, List<Block> blocks, List<Archive.RootObject> rootObjects) throws IOException {
        // There are legitimate cases where the root objects aren't in the blocks. i.e.
        // latest link of the archive manifest file. DCI: HUH? not true.
        for (Archive.RootObject obj : rootObjects) {
            if (obj.mDigest.isNullDigest()) {
                continue;
            }
            writeLink(linkMap.getLink(obj.mDigest));
        }

        for (Block block : blocks) {
            for (LinkDigest digest : block.getDigests()) {
                writeLink(linkMap.getLink(digest));
            }
        }
    }

    public Archive.ArchiveData read(HistoryLinkMap linkMap, LinkDataFactory linkFactory) throws IOException {
        Archive.ArchiveData data = readArchiveData(linkMap, linkFactory);

        for (Archive.RootObject obj : data.mRootObjects) {
            if (obj.mDigest.isNullDigest()) {
                continue;
            }
            readLink(linkMap, linkFactory, obj.mDigest);
        }

        for (Block block : data.mBlocks) {
            for (LinkDigest digest : block.getDigests()) {
                readLink(linkMap, linkFactory, digest);
            }
        }

        return data;
    }
}
