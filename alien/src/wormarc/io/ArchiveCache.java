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
    private String mName;
    private LinkDigest mArchiveManifestChainHead;

    private void setChainHead(File file) throws IOException {
        if (mArchiveManifestChainHead != null) {
            return;
        }

        mArchiveManifestChainHead =
            new LinkDigest(IOUtil.
                           readUtf8StringAndClose(new FileInputStream(file)).
                           trim());
    }

    protected Archive.ArchiveData readArchiveData(HistoryLinkMap linkMap,
                                                  LinkDataFactory linkFactory)
        throws IOException {
        setChainHead(new File(mDirectory, mName));


        ArchiveManifest manifest =
            ArchiveManifest.fromBytes(Archive.
                                      readFile(mArchiveManifestChainHead, this),
                                      mArchiveManifestChainHead);
        return manifest.makeArchiveData();
    }

    protected void
        writeArchiveData(HistoryLinkMap linkMap,
                         List<Block> blocks,
                         List<Archive.RootObject> rootObjects)
        throws IOException {
        if (mName == null) {
            throw new IllegalStateException("Name not set!");
        }
        LinkDigest digest = ArchiveManifest.getArchiveManifestDigest(rootObjects);

        ArchiveManifest manifest =
            ArchiveManifest.fromBytes(Archive.
                                      readFile(digest, this),
                                      digest);

        if (!manifest.makeArchiveData().equals(new Archive.ArchiveData(blocks, rootObjects))) {
            throw new IOException("Missing or malformed Archive Manifest!");
        }
        // Update the pointer file.
        String fileName = new File(mDirectory, mName).getAbsolutePath();
        IOUtil.writeFully(digest.toString().getBytes(IOUtil.UTF8),
                          fileName);
    }


    public ArchiveCache(String directory) throws IOException {
        super(directory);
    }

    // The named version of the archive to read / write next.
    public void setName(String name) {
        mName = name;
        mArchiveManifestChainHead = null;
    }

    public String getName() { return mName; }

    // Setting this causes the name pointer file to be ignored
    // for the next write.
    public void setArchiveManifestChainHead(LinkDigest digest) {
        mArchiveManifestChainHead = digest;
    }
}