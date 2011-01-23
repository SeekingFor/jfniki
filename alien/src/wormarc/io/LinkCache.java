/* A helper class to store links on the local file system.
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

import wormarc.BinaryLinkRep;
import wormarc.HistoryLink;
import wormarc.HistoryLinkMap;
import wormarc.LinkDataFactory;
import wormarc.LinkDigest;

public class LinkCache {
    protected File mDirectory;

    // Flat directory for now. Could use subdirs based on start of digest, like git.
    protected File getLinkFile(LinkDigest digest) {
        return new File(mDirectory, digest.hexDigest(20));
    }

    public LinkCache(String directory) throws IOException {
        if (directory == null) {
            throw new IllegalArgumentException("directory is null.");
        }

       File file =  new File(directory);
       if (!(file.exists() && file.isDirectory() && file.canWrite() && file.canRead())) {
           throw new IOException("Directory must exist and have read and write access.");
       }
       mDirectory = file;
    }

    public HistoryLink readLink(HistoryLinkMap linkMap, LinkDataFactory linkFactory, LinkDigest digest)
        throws IOException {

        if (digest.isNullDigest()) {
            throw new IOException("Refused to read NULL_DIGEST link.");
        }

        InputStream inputStream = new FileInputStream(getLinkFile(digest));
        try {
            HistoryLink link = BinaryLinkRep.fromBytes(inputStream, linkFactory);
            if (link == null) {
                throw new IOException(String.format("Link: %s not found on file system.", digest.toString()));
            }
            linkMap.addLink(link);
            return link;
        } finally {
            inputStream.close();
        }
    }

    public void writeLink(HistoryLink link) throws IOException {
        if (link.mHash.isNullDigest()) {
            throw new IOException("Refused to write NULL_DIGEST link.");
        }

        OutputStream outputStream = new FileOutputStream(getLinkFile(link.mHash));
        boolean raised = true;
        try {
            BinaryLinkRep.write(outputStream, link);
            raised = false;
        } finally {
            outputStream.close();
            if (raised && getLinkFile(link.mHash).exists()) {
                // Don't leave corrupt link files on disk.
                getLinkFile(link.mHash).delete();
            }
        }
    }
}