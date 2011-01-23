/* An Archive.IO implementation which can read and write Archive's to the local file system.
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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.SequenceInputStream;

import java.util.List;

import wormarc.Archive;
import wormarc.ArchiveManifest;
import wormarc.BinaryLinkRep;
import wormarc.Block;
import wormarc.IOUtil;
import wormarc.HistoryLinkMap;
import wormarc.LinkDataFactory;
import wormarc.LinkDigest;


// LATER: I reused ArchiveManifest as a quick hack.
//        Should be rewritten to read / write ArchiveData.
//        DCI: This breaks the IO contract which doesn't require an ARCHIVE_MANIFEST.
public class ArchiveCache extends ArchiveCacheBase {
    String mName;

    protected Archive.ArchiveData readArchiveData(HistoryLinkMap linkMap,
                                                  LinkDataFactory linkFactory)
        throws IOException {
        File file = new File(mDirectory, mName);
        DataInputStream dis = new DataInputStream(new FileInputStream(file));
        boolean raisedReadingDigest = true;
        try {
            // <digest><archive manifest bytes>
            LinkDigest digest = BinaryLinkRep.readLinkDigest(dis);
            raisedReadingDigest = false;
            ArchiveManifest manifest = ArchiveManifest.fromBytes(dis, digest);
            return manifest.makeArchiveData();
        } finally {
            if (raisedReadingDigest) {
                dis.close();
            }
        }
    }

    public ArchiveCache(String directory) throws IOException {
        super(directory);
    }

    // The named version of the archive to read / write next.
    public void setName(String name) { mName = name; }
    public String getName() { return mName; }

    // Override to dump ArchiveData after dumping links.
    public void write(HistoryLinkMap linkMap, List<Block> blocks, List<Archive.RootObject> rootObjects) throws IOException {
        if (mName == null) {
            throw new IllegalStateException("Name not set!");
        }

        // Raises for no / multiple ARCHIVE_MANIFEST objects.
        LinkDigest digest = ArchiveManifest.getArchiveManifestDigest(rootObjects);

        super.write(linkMap, blocks, rootObjects);

        // Write the manifest so it's available for subsequent read.
        File file = new File(mDirectory, mName);
        OutputStream outputStream = new FileOutputStream(file);
        InputStream inputStream = null;
        boolean raised = true;
        try {
            inputStream = (new ArchiveManifest(rootObjects, blocks)).toBytes();
            raised = false;
        } finally {
            if (raised) {
                outputStream.close();
            }
        }

        // <digest><archive manifest bytes>
        IOUtil.copyAndClose(new SequenceInputStream(new ByteArrayInputStream(digest.getBytes()),
                                                    inputStream),
                            outputStream);
    }
}