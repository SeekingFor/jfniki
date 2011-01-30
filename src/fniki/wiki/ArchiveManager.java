/* Class to manage reading from and writing Freenet WORM Archives.
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
 *  This file was developed as component of
 * "fniki" (a wiki implementation running over Freenet).
 */

package fniki.wiki;

import java.io.IOException;
import java.io.PrintStream;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fmsutil.FMSUtil;
import wormarc.Archive;
import wormarc.AuditArchive;
import wormarc.ExternalRefs;
import wormarc.FileManifest;
import wormarc.IOUtil;
import wormarc.LinkDigest;
import wormarc.RootObjectKind;
import wormarc.io.FreenetIO;

public class ArchiveManager {
    private final static String FCP_HOST = "127.0.0.1";
    private final static int FCP_PORT = 9481;

    private final static String FMS_HOST = "127.0.0.1";
    private final static int FMS_PORT = 1119;
    private final static String FMS_GROUP = "biss.test000";
    private final static String BISS_NAME = "testwiki";
    // Maximum number of versions to read from FMS.
    private final static int MAX_VERSIONS = 50;

    String mFcpHost = FCP_HOST;
    int mFcpPort = FCP_PORT;
    String mFmsHost = FMS_HOST;
    int mFmsPort = FMS_PORT;
    String mFmsGroup = FMS_GROUP;
    String mBissName= BISS_NAME;

    String mPrivateSSK;
    String mFmsId;

    // Base64 SSK public key hash to FMS name. i.e. the part before '@'.
    Map<String, String> mNymLut = new HashMap<String, String>();

    String mParentUri;
    Archive mArchive;
    FileManifest mFileManifest;
    LocalWikiChanges mOverlay;

    public void setDebugOutput(PrintStream out) {
        FreenetIO.setDebugOutput(out);
    }

    public void setPrivateSSK(String value) {
        if (!value.startsWith("SSK@") || !value.endsWith(",AQECAAE/")) {
            throw new IllegalArgumentException("That doesn't look like a private SSK. " +
                                           "Did you forget the trailing '/'?");
        }
        mPrivateSSK = value;
    }

    public String getPrivateSSK() { return mPrivateSSK; }
    public String getParentUri() { return mParentUri; }

    public void setFmsId(String value) {
        if (value.indexOf("@") != -1) {
            throw new IllegalArgumentException("FMS Id Should only include the part before the '@'!");
        }

        mFmsId = value;
    }

    public String getFmsId() { return mFmsId; }

    public void setFcpHost(String value) {mFcpHost = value; }
    public String getFcpHost() { return mFcpHost; }

    public void setFcpPort(int value) {mFcpPort = value; }
    public int getFcpPort() { return mFcpPort; }

    public void setFmsHost(String value) { mFmsHost = value; }
    public String getFmsHost() { return mFmsHost; }

    public void setFmsPort(int value) { mFmsPort = value; }
    public int getFmsPort() { return mFmsPort; }

    public void setFmsGroup(String value) { mFmsGroup = value; }
    public String getFmsGroup() { return mFmsGroup; }

    public void setBissName(String value) { mBissName= value; }
    public String getBissName() { return mBissName; }

    // DCI: Fix this to roll back state on exceptions.
    public void load(String uri) throws IOException {
        FreenetIO io = new FreenetIO(mFcpHost, mFcpPort);
        io.setRequestUri(uri);
        mArchive = Archive.load(io);
        mFileManifest = FileManifest.fromArchiveRootObject(mArchive);
        mOverlay = new LocalWikiChanges(mArchive, mFileManifest); // DCI: why copy ?
        mParentUri = uri;
    }

    public void createEmptyArchive() throws IOException {
        mArchive = new Archive();
        mFileManifest = FileManifest.fromArchiveRootObject(mArchive);
        mOverlay = new LocalWikiChanges(mArchive, mFileManifest); // DCI: why copy ?
        mParentUri = null;
    }

    public static class UpToDateException extends IOException {
        public UpToDateException() {
            super("There are no local changes to submit.");
        }
    }

    private String getInsertUri(Archive archive) throws IOException {
        // Generate a unique SSK.
        LinkDigest digest = archive.getRootObject(RootObjectKind.ARCHIVE_MANIFEST);
        // The hash of the actual file, not just the chain head SHA.
        LinkDigest fileHash = IOUtil.getFileDigest(archive.getFile(digest));
        return mPrivateSSK + fileHash.hexDigest(8);
    }

    // DCI: commitAndPushToFreenet() ?
    public String pushToFreenet(PrintStream out) throws IOException {
        FileManifest.Changes changes = mFileManifest.diffTo(mArchive, mOverlay);
        if (changes.isUnmodified()) {
            throw new IOException("Didn't find any local changes to submit.");
        }

        // Copy so that we can cleanly role back if an exception is raised.
        Archive copy = mArchive.deepCopy();
        FileManifest files = FileManifest.fromArchiveRootObject(copy);

        // Commit local changes to the Archive.
        copy.unsetRootObject(RootObjectKind.PARENT_REFERENCES);

        // Update the archive
        copy.startUpdate();
        files.updateFrom(copy, mOverlay);

        LinkDigest digest = copy.updateRootObject(files.toBytes(), RootObjectKind.FILE_MANIFEST);

        if (mParentUri != null) {
            out.println("Set PARENT_REFERENCES: " + mParentUri);
            List<String> keys = Arrays.asList(mParentUri);
            LinkDigest refs =
                copy.updateRootObject(ExternalRefs.create(keys, ExternalRefs.KIND_FREENET)
                                      .toBytes(),
                                      RootObjectKind.PARENT_REFERENCES);
        }

        copy.commitUpdate();
        copy.compressAndUpdateArchiveManifest();

        // Generate a unique SSK based on the SHA hash of the archive manifest.
        String insertUri = getInsertUri(copy);

        out.println("Insert URI: " + insertUri);

        // Push the updated version into Freenet.
        FreenetIO io = new FreenetIO(mFcpHost, mFcpPort);
        io.setInsertUri(insertUri);
        out.println("Trying to read previous top key if possible...");
        io.maybeLoadPreviousTopKey(copy);
        out.println("Writing to Freenet...");
        copy.write(io);

        if (mFmsId != null) {
            try {
                out.println("Sending FMS update notification to: " + mFmsGroup);
                FMSUtil.sendBISSMsg(mFmsHost, mFmsPort, mFmsId, mFmsGroup,
                                    mBissName, io.getRequestUri());
            } catch (IOException ioe) {
                out.println("FMS send failed: " + ioe.getMessage());
            }
        }

        // Don't update any state until all calls which could raise have finished.
        mArchive = copy;
        mFileManifest = FileManifest.fromArchiveRootObject(mArchive);
        mOverlay = new LocalWikiChanges(mArchive, mFileManifest);
        mParentUri = io.getRequestUri();
        out.println("Request URI: " + mParentUri);

        return mParentUri;
    }

    public FileManifest.Changes getLocalChanges() throws IOException {
        return mFileManifest.diffTo(mArchive, mOverlay);
    }

    public void readChangeLog(PrintStream out,
                              AuditArchive.ChangeLogCallback callback) throws IOException {

        if (mParentUri == null) {
            throw new IOException("URI not set!");
        }

        ExternalRefs.Reference head =
            new ExternalRefs.Reference(ExternalRefs.KIND_FREENET, mParentUri);

        FreenetIO freenetResolver = new FreenetIO(mFcpHost, mFcpPort);
        Archive archive = freenetResolver.resolve(head);
        AuditArchive.getManifestChangeLog(head, archive, freenetResolver, callback);
    }

    public List<FMSUtil.BISSRecord> getRecentWikiVersions(PrintStream out) throws IOException {
        List<FMSUtil.BISSRecord> records =
            FMSUtil.getBISSRecords(mFmsHost, mFmsPort, mFmsId, mFmsGroup, mBissName, MAX_VERSIONS);

        // LATER: do better.
        for (FMSUtil.BISSRecord record : records) {
            String fields[] = record.mFmsId.split("@");
            if (fields.length != 2) {
                continue;
            }
            mNymLut.put(fields[1].trim(), fields[0].trim());
        }

        return records;
    }

    public WikiTextStorage getStorage() throws IOException {
        if (mOverlay == null) {
            throw new IllegalStateException("No archive loaded!");
        }
        return mOverlay;
    }

    public String getNym(String sskRequestUri) {
        int start = sskRequestUri.indexOf("@");
        int end = sskRequestUri.indexOf(",");
        if (start == -1 || end == -1 || start >= end) {
            return "???";
        }

        String publicKeyHash = sskRequestUri.substring(start + 1, end - start + 3);

        // SSK@THIS_PART,
        String nym = mNymLut.get(publicKeyHash);
        if (nym == null) {
            return "???";
        }
        return nym;
    }
}