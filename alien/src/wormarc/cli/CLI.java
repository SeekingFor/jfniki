/* An experimental command line client for manipulating WORMArc archives.
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

package wormarc.cli;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;


import fmsutil.FMSUtil;
import wormarc.Archive;
import wormarc.AuditArchive;
import wormarc.FileManifest;
import wormarc.IOUtil;
import wormarc.LinkDigest;
import wormarc.ExternalRefs;
import wormarc.RootObjectKind;

import wormarc.io.FreenetIO;
import wormarc.io.FreenetTopKey;

public class CLI {
    private static final PrintStream sOut = System.out;
    private final static String FCP_HOST = "127.0.0.1";
    private final static int FCP_PORT = 19481;

    private static CLICache getCache(boolean createCache) throws IOException {
        String cwd = (new File(".")).getCanonicalPath();
        File cacheDir = new File(cwd, ".wormarc");

        if (!(cacheDir.exists() && cacheDir.isDirectory())) {
            if (!createCache) {
                throw new IOException("No .wormarc directory here!");
            }
            sOut.println(String.format("Creating new archive: %s", cacheDir));
            cacheDir.mkdir();
        } else if (createCache) {
            sOut.println(String.format("The archive already exists!"));
        }
        CLICache cache =  new CLICache(cacheDir.getCanonicalPath());
        String head = null;
        try {
            head = cache.readHead();
        } catch (IOException ioe) {
            if (!createCache) {
                sOut.println("Head not set?");
            }
            head = "default";
        }
        cache.setName(head);
        cache.saveHead(cache.getName());
        return cache;
    }

    private static Archive loadHead(CLICache cache) throws IOException {
        return Archive.load(cache);
    }

    private final static void dumpList(Set<String> values, String prefix) {
        if (values.isEmpty()) {
            return;
        }
        List<String> ordered = new ArrayList<String>(values);
        Collections.sort(ordered);
        for (String name : ordered) {
            sOut.println(String.format("%s %s", prefix, name));
        }
    }

    private final static void showChanges(FileManifest.Changes changes, boolean verbose) {
        dumpList(changes.mDeleted, verbose ? "deleted" : "!");
        dumpList(changes.mAdded, verbose ? "added" : "A");
        dumpList(changes.mModified, verbose ? "modified" : "M");
    }

    private final static LinkDigest getChainHead(Archive archive, String chainId, boolean silent) throws IOException {
        LinkDigest chainHead = LinkDigest.NULL_DIGEST;
        try {
            chainHead = new LinkDigest(chainId);
        } catch (IllegalArgumentException notAHash) {
        }

        if (chainHead.isNullDigest()) {
            try {
                FileManifest current = FileManifest.fromArchiveRootObject(archive);
                chainHead = current.getChainHeadDigest(chainId);
                if (!silent) {
                    sOut.println("Looked up chain digest from FileManifest: " + chainHead);
                }
            } catch (IOException notAFileName) {
            }
        }

        if (chainHead.isNullDigest()) {
            try {
                chainHead = archive.getRootObject(Integer.parseInt(chainId));
                if (!silent) {
                    sOut.println("Looked up as a RootObject ordinal: " + chainHead);
                }
            } catch (NumberFormatException notANumber) {
            }
        }

        if (chainHead.isNullDigest()) {
            sOut.println("Try a 20 digit hex string a file name from the FileManifest or a RootObject kind number.");
        }
        return chainHead;
    }

    ////////////////////////////////////////////////////////////
    static class Command {
        final String mName;
        final boolean mNeedsCache;
        final boolean mCreateCache;
        final String mBrief;
        final String mArgs;
        final String mLonger;
        Command(String name, boolean needsCache, boolean createCache,
                String args, String brief, String longer) {
            mName = name;
            mNeedsCache = needsCache;
            mCreateCache = createCache;
            mArgs = args;
            mBrief = brief;
            mLonger = longer;
        }

        Command(String name, boolean needsCache, boolean createCache, String args, String brief) {
            this(name, needsCache, createCache, args, brief, null);
        }

        boolean canParse(String[] args) { return true; }
        void invoke(String[] args, CLICache cache) throws Exception {};
    }

    private final static String HELP_TEXT =
        "wormarc: Write Once Read Multiple ARChive command line client.\n" +
        "written as part of the fniki Freenet Wiki project\n" +
        "Copyright (C) 2010, 2011 Darrell Karbott, GPL2 (or later)\n\n" +
        "SUMMARY:\n" +
        "This is a command line client to test the wormarc library.\n" +
        "It is experimental code. Use it at your own peril.\n\n" +
        "COMMANDS:\n";

    final static Command COMMANDS[] = new Command[] {
        // MUST be first.
        new Command("help", false, false, "", "display this message") {
            public void invoke(String[] args, CLICache cache) throws Exception {
                System.out.print(HELP_TEXT);
                Map<String, Command> table = new HashMap<String, Command>();
                for (Command command : COMMANDS) {
                    table.put(command.mName, command);
                }
                List<String> names = new ArrayList<String>(table.keySet());
                Collections.sort(names);
                for (String name : names) {
                    Command value = table.get(name);
                    sOut.println(String.format("   %s%s -- %s", value.mName, value.mArgs, value.mBrief));
                    if (value.mLonger != null) {
                        sOut.println(value.mLonger);
                        sOut.println("");
                    }
                }
            }
        },
        new Command("cat", true, false, " <chainId>", "print file to stdout") {
            public boolean canParse(String[] args) { return args.length == 2; }
            public void invoke(String[] args, CLICache cache) throws Exception {
                String chainId = args[1];
                Archive archive = null;
                try {
                    archive = loadHead(cache);
                } catch (IOException ioe) {
                    sOut.println("No head found!");
                    return;
                }

                LinkDigest chainHead = getChainHead(archive, chainId, true);
                if (chainHead.isNullDigest()) {
                    return;
                }

                // Hmmmm... potenially closing stdout.
                IOUtil.copyAndClose(archive.getFile(chainHead), sOut);
            }
        },
        new Command("create", true, true, "", "create the local .wormarc cache directory") {
            public boolean canParse(String[] args) { return args.length == 1; }
        },
        new Command("head", true, false, " <name>", "set the head") {
            public boolean canParse(String[] args) { return args.length == 2; }
            public void invoke(String[] args, CLICache cache) throws Exception {
                cache.saveHead(args[1]);
            }
        },
        new Command("branch", true, false, " <name>", "copy the current head to a new name") {
            public boolean canParse(String[] args) { return args.length == 2; }
            public void invoke(String[] args, CLICache cache) throws Exception {
                try {
                    String name = cache.cloneHead(args[1]);
                    cache.setName(name);
                    cache.saveHead(name);
                    sOut.println(String.format("Created branch: %s", name));
                } catch (FileNotFoundException fne) {
                    sOut.println("Branch failed. Maybe head doesn't exist?");
                } catch (IOException ioe) {
                    sOut.println(String.format("Branch failed. Maybe branch[%s] already exists?", args[1]));
                }
            }
        },
        new Command("update", true, false, "", "update the local directory to match the head",
                    "      BE CAREFUL. This command can DELETE ALL FILES in the working directory\n" +
                    "      when you update from an empty archive!") {
            public boolean canParse(String[] args) { return args.length == 1; }
            public void invoke(String[] args, CLICache cache) throws Exception {
                Archive archive = null;
                try {
                    archive = loadHead(cache);
                } catch (IOException ioe) {
                    sOut.println("No previous head found.");
                    return;
                }

                FileManifest current = FileManifest.fromArchiveRootObject(archive);
                sOut.println(String.format("Synching files from %s to local directory.", cache.getName()));
                FileManifest.Changes changes = current.syncFilesTo(archive, cache.getManifestIO());
                showChanges(changes, true);
            }
        },
        new Command("commit", true, false, "", "commit changes to the local directory to the head") {
            public boolean canParse(String[] args) { return args.length == 1; }
            public void invoke(String[] args, CLICache cache) throws Exception {
                Archive archive = null;
                boolean incremental = true;
                try {
                    archive = loadHead(cache);
                } catch (IOException ioe) {
                    sOut.println("No previous head found. Doing non-incremental write.");
                    archive = new Archive();
                    incremental = false;
                }

                archive.unsetRootObject(RootObjectKind.PARENT_REFERENCES);

                FileManifest current = FileManifest.fromArchiveRootObject(archive);
                if (incremental) {
                    sOut.println(String.format("Previous version: %s", cache.getName()));
                }
                archive.startUpdate();
                FileManifest.Changes changes = current.updateFrom(archive, cache.getManifestIO());
                showChanges(changes, true);
                if (changes.isUnmodified()) {
                    sOut.println("No changes.");
                    archive.abandonUpdate();
                    return;
                }

                archive.updateRootObject(current.toBytes(), RootObjectKind.FILE_MANIFEST);

                try {
                    List<String> keys = Arrays.asList(cache.readValue("remote"));
                    LinkDigest refs =
                        archive.updateRootObject(ExternalRefs.create(keys, ExternalRefs.KIND_FREENET)
                                                 .toBytes(),
                                                 RootObjectKind.PARENT_REFERENCES);
                    sOut.println("set PARENT_REFERENCES with one parent uri:");
                    sOut.println(keys.get(0));
                } catch (IOException ioe) {
                    sOut.println("Couldn't set parent PARENT_REFERENCES");
                }

                archive.commitUpdate();
                archive.compressAndUpdateArchiveManifest();
                cache.bumpOrdinal();
                archive.write(cache);

                String readName = cache.getName();
                cache.saveHead(readName);
                sOut.println(String.format("Wrote version: %s", readName));
            }
        },

        new Command("status", true, false, "", "show the status of the local directory") {
            public boolean canParse(String[] args) { return args.length == 1; }
            public void invoke(String[] args, CLICache cache) throws Exception {
                String key = "SSK private key not set.";
                try {
                    key = cache.readValue("key");
                } catch (IOException ioe) {
                    // NOP
                }

                String remote = "Parent URI not set.";
                try {
                    remote = cache.readValue("remote");
                } catch (IOException ioe) {
                    // NOP
                }

                Archive archive = null;
                try {
                    archive = loadHead(cache);
                } catch (IOException ioe) {
                    sOut.println("Couldn't read head. Maybe you haven't committed to it yet?");
                    return;
                }

                FileManifest current = FileManifest.fromArchiveRootObject(archive);
                sOut.println(String.format("Directory   : %s", cache.toString()));
                sOut.println(String.format("Private Key : %s", key));
                sOut.println(String.format("Remote      : %s", remote));
                sOut.println(String.format("Head version: %s", cache.getName()));
                sOut.println("");

                FileManifest.Changes changes = current.diffTo(archive, cache.getManifestIO());
                showChanges(changes, false);
                if (changes.isUnmodified()) {
                    sOut.println("No changes.");
                }
            }
        },
        new Command("filehistory", true, false, " <chainId>", "show the change history for a single chain") {
            public boolean canParse(String[] args) { return args.length == 2; }
            public void invoke(String[] args, CLICache cache) throws Exception {
                String chainId = args[1];
                Archive archive = null;
                try {
                    archive = loadHead(cache);
                } catch (IOException ioe) {
                    sOut.println("No head found!");
                    return;
                }

                LinkDigest chainHead = getChainHead(archive, chainId, false);
                if (chainHead.isNullDigest()) {
                    return;
                }

                List<LinkDigest> chain = archive.getChain(chainHead, true); // DCI: fails if not cached.

                sOut.println(String.format("Searching for %d links... ", chain.size()));

                FreenetIO freenetResolver = new FreenetIO(FCP_HOST, FCP_PORT, cache);

                List<ExternalRefs.Reference> refs =
                    AuditArchive.history(archive,
                                         cache.getHeadRef(),
                                         chain,
                                         freenetResolver);

                for (int index = 0; index < chain.size(); index++) {
                    sOut.println(String.format("  [%s]:%s", freenetResolver.getNym(refs.get(index)),
                                               chain.get(index)));
                }
            }
        },
        new Command("manifesthistory", true, false, "", "shows the change history of the manifest") {
            public boolean canParse(String[] args) { return args.length == 1; }
            public void invoke(String[] args, CLICache cache) throws Exception {
                ExternalRefs.Reference remote = null;
                try {
                    // LATER: parse a command line URI?
                    remote = new ExternalRefs.Reference(ExternalRefs.KIND_FREENET,
                                                        cache.readValue("remote"));
                } catch (IOException ioe) {
                    sOut.println("Couldn't read remote. Don't know what version to start from.");
                }

                FreenetIO freenetResolver = new FreenetIO(FCP_HOST, FCP_PORT, cache);
                Archive archive = freenetResolver.resolve(remote);
                AuditArchive.ChangeLogCallback callback = new AuditArchive.ChangeLogCallback () {
                        public boolean onChangeEntry(ExternalRefs.Reference oldVer,
                                                     ExternalRefs.Reference newVer,
                                                     FileManifest.Changes  fromNewToOld) {

                            sOut.println("---");
                            sOut.println("[" + oldVer.mExternalKey + "]");
                            showChanges(fromNewToOld, true);
                            sOut.println("---");
                            return true;
                        }
                    };

                AuditArchive.getManifestChangeLog(remote, archive, freenetResolver, callback);
            }
        },
        new Command("key", true, false, " <ssk_private_key>", "Set the default SSK insert key.",
                    "      This is stored UNENCRYPTED in the .wormarc directory." ) {
            public boolean canParse(String[] args) { return args.length <= 2; }
            public void invoke(String[] args, CLICache cache) throws Exception {
                if (args.length == 2) {
                    checkPrivateKey(args[1]);
                    cache.saveValue("key", args[1]);
                } else {
                    try {
                        String key = cache.readValue("key");
                        checkPrivateKey(key);
                        sOut.println(String.format("key: %s", key));
                    } catch (IOException ioe) {
                        sOut.println("Key not set.");
                    }
                }
            }
        },
        new Command("remote", true, false, " <ssk_requesturi>", "Sets the parent request uri for the next push.",
                    "      Use the value 'clear' to unset this." ) {
            public boolean canParse(String[] args) { return args.length <= 2; }
            public void invoke(String[] args, CLICache cache) throws Exception {
                if (args.length == 2) {
                    if (args[1].equals("clear")) {
                        cache.deleteValue("remote");
                    } else {
                        cache.saveValue("remote", args[1]);
                    }
                } else {
                    try {
                        String remote = cache.readValue("remote");
                        sOut.println(String.format("remote: %s", remote));
                    } catch (IOException ioe) {
                        sOut.println("Remote not set.");
                    }
                }
            }
        },
        new Command("dump", true, false, "", "dump debug information about the current head") {
            public boolean canParse(String[] args) { return args.length == 1; }
            public void invoke(String[] args, CLICache cache) throws Exception {
                Archive archive = null;
                try {
                    archive = loadHead(cache);
                } catch (IOException ioe) {
                    sOut.println("No head found!");
                    return;
                }

                FileManifest current = FileManifest.fromArchiveRootObject(archive);
                sOut.println(cache);
                sOut.println(String.format("Head version: %s", cache.getName()));
                sOut.println(archive.pretty());
                sOut.println(current.pretty(archive));
                LinkDigest digest = archive.getRootObject(RootObjectKind.PARENT_REFERENCES);
                if (!digest.isNullDigest()) {
                    ExternalRefs parents = ExternalRefs.fromBytes(archive.getFile(digest));
                    sOut.println(parents.pretty("parent "));
                } else {
                    sOut.println("No PARENT_REFERENCES in the root objects!");
                }
                digest = archive.getRootObject(RootObjectKind.REBASE_REFERENCES);
                if (!digest.isNullDigest()) {
                    ExternalRefs parents = ExternalRefs.fromBytes(archive.getFile(digest));
                    sOut.println(parents.pretty("rebase "));
                } else {
                    sOut.println("No REBASE_REFERENCES in the root objects!");
                }
            }
        },
        new Command("push", true, false, " <insert_uri>", "insert the current head into Freenet") {
            public boolean canParse(String[] args) { return args.length == 2; }
            public void invoke(String[] args, CLICache cache) throws Exception {
                Archive archive = null;
                try {
                    archive = loadHead(cache);
                } catch (IOException ioe) {
                    sOut.println("No head found!");
                    return;
                }

                String insertUri = args[1];
                if (!(insertUri.startsWith("SSK@") || insertUri.startsWith("CHK@"))) {
                    try {
                        insertUri =  cache.readValue("key") + insertUri;
                        sOut.println("Used stored private key. Inserting to: ");
                        sOut.println(insertUri);
                    } catch (IOException ioe) {
                        sOut.println("Couldn't prepend private key. Maybe it wasn't set?");
                        return;
                    }
                }

                FreenetIO io = new FreenetIO(FCP_HOST, FCP_PORT, cache);
                io.setInsertUri(insertUri);
                sOut.println(String.format("Pushing version: %s to Freenet Insert URI:", cache.getName()));
                sOut.println(insertUri);

                io.maybeLoadPreviousTopKey(archive);

                archive.write(io);

                sOut.println(String.format("Pushed to: %s", io.getRequestUri()));
            }
        },
        new Command("pull", true, false, " <request_uri>", "read the archive out of freenet. ???") {
            public boolean canParse(String[] args) { return args.length == 2; }
            public void invoke(String[] args, CLICache cache) throws Exception {
                String requestUri = args[1];
                FreenetIO io = new FreenetIO(FCP_HOST, FCP_PORT, cache);
                io.setRequestUri(requestUri);
                sOut.println(String.format("Reading: %s", requestUri));
                Archive archive = Archive.load(io);
                cache.setName("lastpull.0");
                archive.write(cache);
                cache.saveHead(cache.getName());
                sOut.println(String.format("Read and switched head to: %s", cache.getName()));
                cache.saveValue("remote", requestUri);
                sOut.println(String.format("Set remote to: %s", requestUri));
            }
        },
        new Command("topkey", true, false, " <request_uri>", "Dump the contents of a top key.") {
            public boolean canParse(String[] args) { return args.length == 2; }
            public void invoke(String[] args, CLICache cache) throws Exception {
                String requestUri = args[1];
                FreenetIO io = new FreenetIO(FCP_HOST, FCP_PORT, cache);
                sOut.println(String.format("Reading Top Key: %s", requestUri));
                FreenetTopKey topKey = io.readTopKey(requestUri);
                sOut.println(String.format("Version: %s", topKey.mVersion));
                sOut.println("Root Objects:");
                for (Archive.RootObject obj : topKey.mRootObjects) {
                    sOut.println(String.format("   [%d] -> %s", obj.mKind, obj.mDigest.toString()));
                }
                int count = 0;
                sOut.println("Blocks:");
                for (FreenetTopKey.BlockDescription desc : topKey.mBlockDescriptions) {
                    sOut.println(String.format("   Block[%d]: %d", count++, desc.mLength));
                    for (int index = 0; index < desc.mCHKs.size(); index++) {
                        sOut.println(String.format("      %s", desc.getCHK(index)));
                    }
                }
            }
        },
        new Command("resolvename", false, false, " <fmsuser> <fmsgroup> <name_to_resolve>",
                    "Read BISS name resolution records.") {
            public boolean canParse(String[] args) { return args.length == 4; }
            public void invoke(String[] args, CLICache cache) throws Exception {
                List<FMSUtil.BISSRecord> records =
                    FMSUtil.getBISSRecords("127.0.0.1", 11119, args[1], args[2], args[3], 20);

                for (FMSUtil.BISSRecord record : records) {
                    sOut.println(record);
                }
            }
        },
        new Command("setname", false, false, " <fmsuser> <fmsgroup> <name_to_set> <value>",
                    "Send a BISS name update msg.") {
            public boolean canParse(String[] args) { return args.length == 5; }
            public void invoke(String[] args, CLICache cache) throws Exception {
                FMSUtil.sendBISSMsg("127.0.0.1", 11119, args[1], args[2], args[3], args[4]);
                sOut.println("Sent message.");
            }
        },

        // Debugging hack. Remove.
        new Command("showargs", false, false, " <arg list>", "print the args passed to it.") {
            public boolean canParse(String[] args) { return true; }
            public void invoke(String[] args, CLICache cache) throws Exception {
                int index = 0;
                for (String arg : args) {
                    sOut.println(String.format("[%d]:[%s]", index, arg));
                    index++;
                }
            }
        },
    };

    private static Command HELP = COMMANDS[0];

    private static void checkPrivateKey(String ssk) throws IOException {
        if (!ssk.startsWith("SSK@") || ssk.indexOf("AQECAAE/") == -1) {
            throw new IOException("Expected a SSK private key with a trailing '/' " +
                                  "but didn't find one.");
        }
    }

    private static Command lookupCommand(String[] args) {
        if (args == null || args.length == 0) {
            return HELP;
        }
        String abbrev = args[0];
        Command hit = null;
        for (Command candidate : COMMANDS) {
            if (candidate.mName.startsWith(abbrev)) {
                if (hit != null) {
                    return HELP;
                }
                hit = candidate;
            }
        }

        if (hit != null && hit.canParse(args)) {
            return hit;
        }
        return HELP;
    }
    public final static void main(String[] args) {
        try {
            Command command = lookupCommand(args);
            CLICache cache = null;
            if (command.mNeedsCache) {
                cache = getCache(command.mCreateCache);
            }
            command.invoke(args, cache);
        } catch (Exception ioe) {
            sOut.println("FAILED with Exception");
            ioe.printStackTrace(sOut);
        }
    }
}
