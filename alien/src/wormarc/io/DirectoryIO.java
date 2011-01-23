/* A FileManifest.IO implementation for reading / writing FileManifests from/to a directory on the file system.
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

// DCI: SHA1 caching

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


import wormarc.FileManifest;
import wormarc.IOUtil;
import wormarc.LinkDigest;

public class DirectoryIO implements FileManifest.IO {
    private File mRootDirectory;
    private Set<String> mIgnoreDirectories = new HashSet<String>();

    public DirectoryIO(String rootDirectory) throws IOException  {
        File dir = new File(rootDirectory);
        if (!(dir.exists() && dir.isDirectory() && dir.canWrite() && dir.canRead())) {
            throw new IOException("Directory must exist and have read and write access.");
        }
        mRootDirectory = dir;
    }

    // Hacks to deal with storing empty directories.
    private final static String EMPTY_DIRECTORY_SENTINEL = "~3mpt1~"; // 31337, improbable value.
    private final static String SLASH_EMPTY_DIRECTORY_SENTINEL = String.format("/%s", EMPTY_DIRECTORY_SENTINEL);

    private class DirectoryTraverser {
        private HashMap<String, LinkDigest> mFiles = new HashMap<String, LinkDigest>();
        private boolean mCalculateDigests;

        DirectoryTraverser(File rootDirectory, boolean calculateDigests) throws IOException {
            mCalculateDigests = calculateDigests;
            traverse(rootDirectory, "");
        }

        // Return mutable reference on purpose.
        public HashMap<String, LinkDigest> getFiles() { return mFiles; }

        private  String join(String relativePath, String name) {
            if (relativePath.length() == 0) {
                return name;
            }
            scrub(relativePath);
            return String.format("%s/%s", relativePath, name);
        }

        private void addEmptyDirectory(String relativePath) {
            mFiles.put(join(relativePath, EMPTY_DIRECTORY_SENTINEL), LinkDigest.EMPTY_DIGEST);
        }

        private void addFile(String relativePath, LinkDigest digest) {
            mFiles.put(relativePath, digest);
        }

        private void traverse(File dir, String relativePath) throws IOException {
            if (mIgnoreDirectories.contains(relativePath)) {
                // DCI: ok for .git .hg, won't work for .svn sprinkled in every subdir...
                return;
            }
            String[] values = dir.list();
            if (values.length == 0) {
                addEmptyDirectory(relativePath);
            }

            for (String value : values) {
                File file = new File(dir, value);
                if (file.isFile()) {
                    LinkDigest digest = LinkDigest.NULL_DIGEST;
                    if (mCalculateDigests) {
                        digest = IOUtil.getFileDigest(new FileInputStream(file));
                    }
                    addFile(join(relativePath, value), digest);

                }
                if (file.isDirectory()) {
                    traverse(file, join(relativePath, value));
                }
            }
        }
    }

    public void ignore(String name) {
        mIgnoreDirectories.add(name);
    }

    public Map<String, LinkDigest> getFiles() throws IOException {
        DirectoryTraverser files = new DirectoryTraverser(mRootDirectory, true);
        return files.getFiles();
    }

    public InputStream getFile(String name) throws IOException {
        if (name.endsWith(EMPTY_DIRECTORY_SENTINEL)) {
            return new ByteArrayInputStream(new byte[0]);
        }
        // DCI: check to make sure resulting path is under mRootDirectory.
        return new FileInputStream(new File(mRootDirectory, name));
    }

    // LATER: Do better?
    // Start and end sync are hacks so I can implment empty dir
    // handling below the line.
    public void startSync(Set<String> allFiles) throws IOException {}
    public void putFile(String name, InputStream rawBytes) throws IOException {
        if (name.endsWith(SLASH_EMPTY_DIRECTORY_SENTINEL)) {
            File dir = new File(mRootDirectory, name).getParentFile();
            if (dir == null) {
                throw new IOException("Empty root directory???");
            }
            if (!dir.exists() || !dir.isDirectory()) {
                if (!dir.mkdir()) {
                    throw new IOException(String.format("Couldn't make directory: %s", dir));
                }
            }
            return;
        }

        File file = new File(mRootDirectory, name);
        File parent = file.getParentFile();
        if (!parent.exists() || !parent.isDirectory()) {
            if (!parent.mkdirs()) {
                throw new IOException(String.format("Couldn't make directory: %s", parent));
            }
        }
        OutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(new File(mRootDirectory, name));
        } finally {
            if (outputStream == null) {
                rawBytes.close(); // I.e. in the process of throwing.
            }
        }
        IOUtil.copyAndClose(rawBytes, outputStream);
    }

    public void deleteFile(String name) throws IOException {
        if (name.endsWith(EMPTY_DIRECTORY_SENTINEL)) {
            return; // Will be cleaned up by endsync. Might not be empty yet.
        }
        File file = new File(mRootDirectory, name);
        if (!file.delete()) {
            throw new IOException(String.format("Couldn't remove file: %s", file));
        }
    }


    private static void scrub(String relativePath) {
        if (relativePath.length() == 0 ) {
            throw new IllegalArgumentException("relativePath is empty!");
        }
        if (relativePath.trim() != relativePath) {
            throw new IllegalArgumentException("relativePath has external whitespace!");
        }

        if (relativePath.indexOf("//") != -1) {
            throw new IllegalArgumentException("// in relativePath!");
        }

        if (relativePath.startsWith("/")) {
            throw new IllegalArgumentException("relativePath can't start with / !");
        }
    }

    // Hmmm... O(NxM) N = number of files, M = subdir depth.
    private static Set<String> allowedDirectories(Set<String> allFiles) {
        Set<String> allowed = new HashSet<String>();
        for (String path : allFiles) {
            scrub(path);
            SplitResult result = split(path);
            String candidate = result.mDirectory.trim();
            while (!candidate.equals("")) {
                // Add all parent components.
                allowed.add(candidate);
                result = split(candidate);
                candidate = result.mDirectory.trim();
            }
        }
        return allowed;
    }

    private static final class SplitResult {
        final String mDirectory;
        final String mName;
        SplitResult(String directory, String name) {
            mDirectory = directory;
            mName = name;
       }
    }

    private static SplitResult split(String relativePath) {
        scrub(relativePath);

        if (relativePath.equals("/")) {
            return new SplitResult("", "");
        }

        int pos = relativePath.lastIndexOf("/");

        // foo, empty string
        if (pos == -1) {
            return new SplitResult("", relativePath);
        }

        // /foo/bar/baz/qux/
        if (pos == relativePath.length() - 1) {
            return new SplitResult(relativePath.substring(0, pos), "");
        }

        return new SplitResult(relativePath.substring(0, pos),
                               relativePath.substring(pos + 1, relativePath.length()));
    }

    // DCI: test file and dir with same name.
    private void deleteEmptyDirectoryTree(String relativePath) throws IOException  {
        if (relativePath.trim().equals("")) {
            throw new IllegalArgumentException("Empty relativePath.");
        }

        File dir = new File(mRootDirectory, relativePath);
        if (!(dir.exists() && dir.isDirectory())) {
            return;
        }
        for (String subDir : dir.list()) {
            if (mIgnoreDirectories.contains(subDir) || subDir.trim().equals("")) {
                continue;
            }
            deleteEmptyDirectoryTree(String.format("%s/%s", relativePath, subDir));
        }
        // By the time we've recursed to here, all subdirectories should be empty.
        if (dir.exists() && dir.isDirectory()) {
            if (!dir.delete()) {
                throw new IOException(String.format("Couldn't remove: %s", relativePath));
            }
        }
    }

    // DCI: shady, test.
    public void endSync(Set<String> allFiles) throws IOException {
        // By the time we get here, all files not in the version should
        // have been deleted, but empty directories may be left behind.
        // So we clean them up.

        Set<String> allowedDirs = allowedDirectories(allFiles);

        DirectoryTraverser files = new DirectoryTraverser(mRootDirectory, false);

        Set<String> victims = allowedDirectories(files.getFiles().keySet());

        victims.removeAll(allowedDirs);

        while (!victims.isEmpty()) {
            String victim = victims.iterator().next();
            victims.remove(victim);
            // Will fail if the directory isn't empty, so this isn't quite as dangerous
            // as it looks.

            deleteEmptyDirectoryTree(victim);
            String parent = split(victim).mDirectory;
            if (parent.trim().equals("")) {
                continue;
            }

            if (!allowedDirs.contains(parent)) {
                victims.add(parent);
            }
        }
    }
}