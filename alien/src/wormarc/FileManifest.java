/* A set of files with human readable names, stored in an Archive.
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

// DCI: catch null pointers in all public interfaces?
// DCI: helperfunc nullCheck(refValue, "refValue"); throws on null.
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;

import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FileManifest implements LinkContainer {
    public final static Changes NO_CHANGES = new FileManifest.Changes();

    // Map the digest of a file to the head of the file chain it is stored in.
    Map<LinkDigest, LinkDigest> mFileDigestToChainHeadDigest = new HashMap<LinkDigest, LinkDigest>();
    // Map a human readable name to the digest of a file
    Map<String, LinkDigest> mNameToFileDigest = new HashMap<String, LinkDigest>();

    // There is no cleanup contract.
    // This may leave a puss oozing sore behind on failure.

    // Names are just strings.
    // Mapping them to directories is implementation dependent.
    // Empty directory insertion? Handled below the line of this interface.
    public interface IO {
        // Can OPTIONALLY send hash.
        // ShaDigest.NULL_DIGEST is allowed to indicate DUNNO hash yet.
        Map<String, LinkDigest> getFiles() throws IOException;
        InputStream getFile(String name) throws IOException;

        void putFile(String name, InputStream rawBytes) throws IOException;
        void deleteFile(String name) throws IOException;

        // DCI: think. start and end sync are hacks so I can implment empty dir
        // handling below the line.
        void startSync(Set<String> allFiles) throws IOException;
        void endSync(Set<String> allFiles) throws IOException;
    }

    // "!H20s20s" + name bytes, H == unsigned short, is full length, including header and name
    private final static int HEADER_LENGTH = 2 + 2 * 20;

    public InputStream getFile(Archive archive, String name) throws IOException {
        if (archive == null) {
            throw new IllegalArgumentException("archive is null");
        }
        if (name == null) {
            throw new IllegalArgumentException("name is null");
        }

        return archive.getFile(getChainHeadDigest(name));
    }

    public InputStream getFile(Archive archive, LinkDigest fileDigest) throws IOException {
        if (archive == null) {
            throw new IllegalArgumentException("archive is null");
        }
        if (fileDigest == null) {
            throw new IllegalArgumentException("fileDigest is null");
        }

        LinkDigest chainHeadDigest = mFileDigestToChainHeadDigest.get(fileDigest);
        if (chainHeadDigest == null) {
            throw new FileNotFoundException("fileDigest not in the FileManifest");
        }
        return archive.getFile(chainHeadDigest);
    }

    // should call purge() when done putting files
    public LinkDigest putFile(Archive archive, String name, InputStream rawBytes) throws IOException {
        if (archive == null) {
            throw new IllegalArgumentException("archive is null");
        }
        if (name == null) {
            throw new IllegalArgumentException("name is null");
        }
        if (rawBytes == null) {
            throw new IllegalArgumentException("rawBytes is null");
        }

        return putFile(archive, name, null, rawBytes);
    }

    // should call purge() when done putting files
    // prevFileDigest == null means lookup from name. INTENT: Allow cheap renaming/forking
    public LinkDigest putFile(Archive archive, String name, LinkDigest prevFileDigest, InputStream rawBytes)
        throws IOException {
        if (archive == null) {
            throw new IllegalArgumentException("archive is null");
        }
        if (name == null) {
            throw new IllegalArgumentException("name is null");
        }
        if (rawBytes == null) {
            throw new IllegalArgumentException("rawBytes is null");
        }

        LinkDigest prevChain = LinkDigest.NULL_DIGEST;
        if (prevFileDigest == null) {
            prevFileDigest = mNameToFileDigest.get(name);
            if (prevFileDigest != null) {
                prevChain = mFileDigestToChainHeadDigest.get(prevFileDigest);
            }
        }

        MessageDigest insertedFileDigest = null;
        try {
            insertedFileDigest = MessageDigest.getInstance("SHA");
        } catch (NoSuchAlgorithmException nsae) {
            rawBytes.close();
            throw new IOException("Couldn't load SHA1 algorithm");
        }

        DigestInputStream wrappedInput = new DigestInputStream(rawBytes, insertedFileDigest);
        LinkDigest newChainHead = archive.putFile(wrappedInput, prevChain);  // closes on failure.
        LinkDigest newFileDigest = new LinkDigest(insertedFileDigest.digest());
        // DCI: need purge() to delete the old mappings?

        // Reuse copies of the same file inserted under different names.
        if (!mFileDigestToChainHeadDigest.containsKey(newFileDigest)) {
            mFileDigestToChainHeadDigest.put(newFileDigest, newChainHead);
        }
        mNameToFileDigest.put(name, newFileDigest);
        return newFileDigest;
    }

    public void removeFiles(List<String> names) {
        if (names == null) {
            throw new IllegalArgumentException("names is null");
        }
        boolean changed = false;
        for (String name : names) {
            if (name == null) {
                throw new IllegalArgumentException("names contains a null entry.");
            }
            if (mNameToFileDigest.containsKey(name)) {
                mNameToFileDigest.remove(name);
                changed = true;
            }
        }
        if (changed) {
            purge();
        }
    }

    // DCI: think. Making the initial copy is cheap. making a change that
    //      causes the chain to shorten may be really expensive.. hmmmm....
    // DCI: Not legal when not updating
    // Cheap.
    // Overwrites.
    public void copy(String fromName, String toName) throws IOException {
        if (fromName == null) {
            throw new IllegalArgumentException("fromName is null");
        }
        if (toName == null) {
            throw new IllegalArgumentException("toName is null");
        }

        mNameToFileDigest.put(toName, getFileDigest(fromName));
    }

    // DCI: not legal when not updating
    // Cheap.
    public void rename(String fromName, String toName) throws IOException {
        if (fromName == null) {
            throw new IllegalArgumentException("fromName is null");
        }
        if (toName == null) {
            throw new IllegalArgumentException("toName is null");
        }

        mNameToFileDigest.put(toName, getFileDigest(fromName));
        mNameToFileDigest.remove(fromName);
    }

    // DCI: Better name?, document
    // Remove ophaned file digest entries.
    public void purge() {
        Set<LinkDigest> referencedFileDigests = new HashSet<LinkDigest>(mNameToFileDigest.values());
        Set<LinkDigest> knownFileDigests = new HashSet<LinkDigest>(mFileDigestToChainHeadDigest.keySet());
        if (!knownFileDigests.containsAll(referencedFileDigests)) {
            throw new RuntimeException("Assertion Failure: Unresolved fileDigest links");
        }
        knownFileDigests.removeAll(referencedFileDigests);
        if (knownFileDigests.size() == 0) {
            return;
        }
        for (LinkDigest key : knownFileDigests) {
            mFileDigestToChainHeadDigest.remove(key);
        }
    }

    public boolean contains(String name) {
        return mNameToFileDigest.containsKey(name);
    }

    public boolean contains(LinkDigest fileDigest) {
        return mFileDigestToChainHeadDigest.containsKey(fileDigest);
    }

    public LinkDigest getChainHeadDigest(String name) throws IOException {
        LinkDigest chainHeadDigest = mFileDigestToChainHeadDigest.get(getFileDigest(name));
        if (chainHeadDigest == null) {
            // i.e. The name is in the name map, but the file digest it points to can't be found.
            throw new IOException("Badly formed or corrupt file manifest");
        }
        return chainHeadDigest;
    }

    public LinkDigest getFileDigest(String name) throws IOException {
        LinkDigest fileDigest = mNameToFileDigest.get(name);
        if (fileDigest == null) {
            throw new FileNotFoundException(String.format("File doesn't exist in the archive: %s", name));
        }
        return fileDigest;
    }

    public Map<String, LinkDigest> getMap() {
        return Collections.unmodifiableMap(mNameToFileDigest);
    }

    // DCI: rationalize names. e.g. getFiles()?
    public Set<String> allFiles() {
        return new HashSet<String>(mNameToFileDigest.keySet());
    }

    // May contain spurious values if you don't call purge.
    public Set<LinkDigest> referencedFileDigests() {
        return new HashSet<LinkDigest>(mFileDigestToChainHeadDigest.keySet());
    }

    // May contain spurious values if you don't call purge.
    public Set<LinkDigest> referencedChainHeads() {
        return new HashSet<LinkDigest>(mFileDigestToChainHeadDigest.values());
    }

    public InputStream toBytes() throws IOException {
        // DCI: entire list is built in RAM
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream outputStream = new DataOutputStream(buffer);
        List<String> keys = new ArrayList<String>(mNameToFileDigest.keySet());
        Collections.sort(keys); // Sort so we alwasy get the same bytes for the same list.
        for (String key : keys) {
            byte[] name = key.getBytes(IOUtil.UTF8);
            int length = HEADER_LENGTH + name.length;
            if (length < 0 || length > 32767) {
                throw new RuntimeException("Length doesn't fit in a signed short");
            }
            outputStream.writeShort(length);
            LinkDigest fileDigest = mNameToFileDigest.get(key);
            outputStream.write(fileDigest.getBytes());
            outputStream.write(mFileDigestToChainHeadDigest.get(fileDigest).getBytes());
            outputStream.write(name);
        }
        outputStream.close();
        return new ByteArrayInputStream(buffer.toByteArray());
    }

    // DCI: C&P, move into BinaryLinkRep?
    private static byte[] readBytes(DataInputStream source, int numberOfBytes) throws IOException {
        byte[] data = new byte[numberOfBytes];
        int count = 0;
        while (count < data.length) {
            int bytesRead = source.read(data, count, data.length - count);
            if (bytesRead == -1) {
                throw new IOException("Unexpected EOF reading bytes");
            }
            count += bytesRead;
        }
        return data;
    }

    // Returns a new empty manifest if the FILE_MANIFEST value is NULL_DIGEST.
    public static FileManifest fromArchiveRootObject(Archive archive) throws IOException {
        LinkDigest manifestDigest = archive.getRootObject(RootObjectKind.FILE_MANIFEST);
        if (manifestDigest.isNullDigest()) {
            return new FileManifest();
        } else {
            return fromBytes(archive.getFile(manifestDigest));
        }
    }

    // Hmmmm... not possible to stack multiple manifests on the same stream. DCI: fix
    // DCI: test against python generated binary rep.
    // Closes rawBytes.
    public static FileManifest fromBytes(InputStream rawBytes) throws IOException {
        FileManifest manifest = new FileManifest();
        DataInputStream inputStream = new DataInputStream(rawBytes);
        boolean eofAllowed = true;
        try {
            while (true) {
                int length = inputStream.readShort();
                eofAllowed = false;
                LinkDigest fileDigest = BinaryLinkRep.readLinkDigest(inputStream);
                LinkDigest chainHeadDigest = BinaryLinkRep.readLinkDigest(inputStream);
                String name = new String(readBytes(inputStream, length - HEADER_LENGTH), IOUtil.UTF8);
                manifest.mFileDigestToChainHeadDigest.put(fileDigest, chainHeadDigest);
                manifest.mNameToFileDigest.put(name, fileDigest);
                eofAllowed = true;
            }
        } catch (EOFException eof) {
            if (!eofAllowed) {
                throw new EOFException("Unexpected EOF reading manifest. Corrupt?");
            }
        } finally {
            rawBytes.close();
        }
        return manifest;
    }

    public static final class Changes {
        public final Set<String> mDeleted;
        public final Set<String> mAdded;
        public final Set<String> mModified;
        public final Set<String> mUnmodified; // DCI: GET RID OF THIS?

        // PUNT for now. takes a little work.
        // The LinkDigest -> name inverse map isn't guaranteed to be one to one.
        // public final Set<StringPair> mRenamed; // Better mForked.
        public Changes(Set<String> deleted, Set<String> added,
                       Set<String> modified, Set<String> unmodified) {
            mDeleted = deleted;
            mAdded = added;
            mModified = modified;
            mUnmodified = unmodified;
        }

        protected Changes() { // For NO_CHANGES
            this(new HashSet<String>(),
                 new HashSet<String>(),
                 new HashSet<String>(),
                 new HashSet<String>() // Not correct. LATER: get rid of unmodified?
                 );
        }

        public boolean isUnmodified() {
            return mDeleted.isEmpty() && mAdded.isEmpty() && mModified.isEmpty();
        }

        public String toString() {
            return String.format("{mDeleted=%s, mAdded=%s, mModified=%s, mUnmodified=%s}",
                                 mDeleted,
                                 mAdded,
                                 mModified,
                                 mUnmodified);

        }

    }

    public static void debugDump(Map<String, LinkDigest> fileMap, String msg) {
        if (msg == null) {
            msg = "";
        }
        System.err.println(String.format("--- Dumping file map: %s ---", msg));
        List<String> names = new ArrayList<String>(fileMap.keySet());
        Collections.sort(names);
        for (String name : names) {
            System.err.println(String.format("%s -> %s", name, fileMap.get(name)));
        }
        System.err.println("---");
    }

    // DCI: really not built into the java libraries somewhere?
    // Determines the changes that you must apply to the oldMap
    // to transform it into the newMap.
    public static Changes diff(Map<String, LinkDigest> oldMap,
                               Map<String, LinkDigest> newMap) {
        Set<String> ourKeys = getKeys(oldMap);
        Set<String> otherKeys = getKeys(newMap);

        Set<String> unchangedNames = new HashSet<String>(ourKeys);
        unchangedNames.retainAll(otherKeys); // Intersection.

        // Deleted
        Set<String> deletedNames = new HashSet<String>(ourKeys);
        deletedNames.removeAll(otherKeys); // Subtraction.

        // Added
        Set<String> addedNames = new HashSet<String>(otherKeys);
        addedNames.removeAll(ourKeys); // Subtraction.

        // Modified / Unmodified.
        Set<String> modifiedNames = new HashSet<String>();
        Set<String> unmodifiedNames = new HashSet<String>();
        for (String name : unchangedNames) {
            if (oldMap.get(name).equals(newMap.get(name))) {
                unmodifiedNames.add(name);
            } else {
                modifiedNames.add(name);
            }
        }
        return new Changes(deletedNames, addedNames, modifiedNames, unmodifiedNames);
    }

    ////////////////////////////////////////////////////////////
    protected void doPreCommitCleanup() throws IOException {
        // DCI: cleanup filedigest -> headDigest so that files with multiple names
        //      point to the shortest chain.
        // DCI: Check for possible reinsert win.  i.e. full re-insert  smaller than delta?
        //      Doesn't deltacoder do that?
        // DCI: Optional chain shortening?
    }

    // DCI: cleanup c&p
    public Changes diffTo(Archive archive, IO newer) throws IOException  {
        Map<String, LinkDigest> otherMap = new HashMap<String, LinkDigest>(newer.getFiles());
        for (String name : getKeys(otherMap)) {
            LinkDigest fileDigest = otherMap.get(name);
            if (fileDigest == null) {
                throw new IOException("FileManifest.IO returned a null fileDigest in the getFiles() map.");
            }
            if (fileDigest.isNullDigest()) {
                // DCI: Think.  Way to prevent double read?
                // Fixup missing hashes.
                otherMap.put(name, IOUtil.getFileDigest(newer.getFile(name)));
            }
        }

        return diff(mNameToFileDigest, otherMap);
    }

    // DCI: startSync, endSync
    public Changes syncFilesTo(Archive archive, IO sink) throws IOException {

        sink.startSync(getKeys(mNameToFileDigest));

        HashMap<String, LinkDigest> oldMap = new HashMap<String, LinkDigest>(sink.getFiles());

        // Hack so we don't sha1 hash files which are going to be deleted.
        Set<String> deletedNames = getKeys(oldMap); // MUST copy keySet() is a reference not a copy!
        deletedNames.removeAll(mNameToFileDigest.keySet());
        for (String name : getKeys(oldMap)) { // DCI: use itr that allows deletion instead?
            if (deletedNames.contains(name)) {
                oldMap.remove(name);
                continue;
            }

            LinkDigest fileDigest = oldMap.get(name);
            if (fileDigest == null) {
                throw new IOException("FileManifest.IO returned a null fileDigest in the getFiles() map.");
            }
            if (fileDigest.isNullDigest()) {
                // DCI: test this code path.
                // DCI: Think.  Way to prevent double read?
                // Fixup missing hashes.
                oldMap.put(name, IOUtil.getFileDigest(sink.getFile(name)));
            }
        }

        Changes changes = diff(oldMap, mNameToFileDigest);

        changes = new Changes(deletedNames, changes.mAdded, changes.mModified, changes.mUnmodified);

        if (changes.isUnmodified()) {
            return new Changes(deletedNames, changes.mAdded, changes.mModified, changes.mUnmodified);
        }

        for (String name : changes.mDeleted) { // NOT changes.
            sink.deleteFile(name);
        }

        Set<String> updatedNames = new HashSet<String>();
        updatedNames.addAll(changes.mAdded);
        updatedNames.addAll(changes.mModified);
        for (String name : updatedNames) {
            sink.putFile(name, getFile(archive, name));
        }

        sink.endSync(getKeys(mNameToFileDigest));

        return changes;
    }

    public Changes updateFrom(Archive archive, IO source) throws IOException {
        if (!archive.isUpdating()) {
            throw new IllegalStateException("!archive.isUpdating()");
        }

        Map<String, LinkDigest> otherMap = new HashMap<String, LinkDigest>(source.getFiles());
        for (String name : getKeys(otherMap)) {
            LinkDigest fileDigest = otherMap.get(name);
            if (fileDigest == null) {
                throw new IOException("FileManifest.IO returned a null fileDigest in the getFiles() map.");
            }
            if (fileDigest.isNullDigest()) {
                // DCI: Think.  Way to prevent double read?
                // Fixup missing hashes.
                otherMap.put(name, IOUtil.getFileDigest(source.getFile(name)));
            }
        }

        Changes changes = diff(mNameToFileDigest, otherMap);

        if (changes.isUnmodified()) {
            return changes;
        }

        removeFiles(new ArrayList<String>(changes.mDeleted));
        for (String added : changes.mAdded) {
            putFile(archive, added, source.getFile(added));
        }
        for (String modified : changes.mModified) {
            putFile(archive, modified, source.getFile(modified));
        }

        doPreCommitCleanup();
        return changes;
    }

    ////////////////////////////////////////////////////////////
    // Every link referenced by every file.
    public Set<LinkDigest> getReferencedLinks(HistoryLinkMap linkMap) {
        purge();
        Set<LinkDigest> links = new HashSet<LinkDigest>();
        for (LinkDigest head : mFileDigestToChainHeadDigest.values()) {
            if (head == null) {
                throw new RuntimeException("head is null.");
            }
            // DCI: better way to filter?
            for (HistoryLink link : linkMap.getChain(head, true)) {
                links.add(link.mHash);
            }
        }
        return links;
    }

    // Deep copy.
    static Set<String> getKeys(Map<String, LinkDigest> source) {
        return new HashSet<String>(source.keySet());
    }

    public String pretty(Archive archive) {
        StringBuilder buffer = new StringBuilder();
        buffer.append("--- FileManifest ---\n");
        try {
            List<String> names = new ArrayList<String>(mNameToFileDigest.keySet());
            Collections.sort(names);
            for (String name : names) {
                buffer.append("   ");
                buffer.append(getFileDigest(name));
                buffer.append(" : [");
                // DCI: add full file length
                buffer.append(name);
                buffer.append("]\n");
                for (LinkDigest digest : archive.getChain(getChainHeadDigest(name), true)) {
                    buffer.append("      ");
                    buffer.append(archive.prettyLink(digest.toString()));
                    buffer.append("\n");
                }
            }
        } catch (IOException ioe) {
            buffer.append("FAILED WITH IO ERROR!");
        }

        buffer.append("---\n");
        return buffer.toString();
    }
}