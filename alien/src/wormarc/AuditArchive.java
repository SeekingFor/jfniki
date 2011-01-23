/* A collection of functions used to check the integrity of archives.
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

import java.io.IOException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

// DCI: fix name. Does intra archive stuff.
public class AuditArchive {
    public static final class Changes {
        public final Set<LinkDigest> mAdded;
        public final Set<LinkDigest> mRemoved;
        public final Set<LinkDigest> mCommon;

        Changes(Set<LinkDigest> added,
                Set<LinkDigest> removed,
                Set<LinkDigest> common) {
            mAdded = Collections.unmodifiableSet(added);
            mRemoved = Collections.unmodifiableSet(removed);
            mCommon = Collections.unmodifiableSet(common);
        }
    }

    public static Changes changes(Archive newer, ExternalRefs.Reference older, ArchiveResolver resolver) throws IOException {
        if (!newer.hasValidArchiveManifest()) {
            throw new IOException("The newer archive doesn't have a valid manifest.");
        }

        Archive other = resolver.resolve(older);
        if (!other.hasValidArchiveManifest()) {
            throw new IOException("The older archive doesn't have a valid manifest.");
        }

        Set<LinkDigest> allA = other.allLinks();
        Set<LinkDigest> allB = newer.allLinks();

        // Add to the new archive.
        Set<LinkDigest> added = new HashSet<LinkDigest>(allB);
        added.removeAll(allA);

        // Removed from the old archive.
        Set<LinkDigest> removed = new HashSet<LinkDigest>(allA);
        added.removeAll(allB);

        // In both
        Set<LinkDigest> common = new HashSet<LinkDigest>(allB);
        added.removeAll(added);

        return new Changes(added, removed, common);
    }

    public static Set<LinkDigest> added(Archive archive, ArchiveResolver resolver) throws IOException {
        LinkDigest refsDigest = archive.getRootObject(RootObjectKind.PARENT_REFERENCES);
        if (refsDigest.isNullDigest()) {
            throw new IOException("Archive doesn't have PARENT_REFERENCES.");
        }
        ExternalRefs refs = ExternalRefs.fromBytes(archive.getFile(refsDigest));
        Set<LinkDigest> allParentLinks = new HashSet<LinkDigest>();
        for (ExternalRefs.Reference ref : refs.mRefs) {
            Archive parent = resolver.resolve(ref);
            if (!parent.hasValidArchiveManifest()) {
                // DCI: PredicateFailureException?
                throw new IOException("The parent archive doesn't have a valid manifest:"
                                      + ref.mExternalKey);
            }
            parent.addAllLinks(allParentLinks);
        }

        Set<LinkDigest> currentLinks = archive.allLinks();
        currentLinks.removeAll(allParentLinks);
        return currentLinks;
    }

    ////////////////////////////////////////////////////////////
    private static final class SuspectInfo {
        final ExternalRefs.Reference mRef;
        final Set<LinkDigest> mAllLinks;
        final ExternalRefs mRefs;
        SuspectInfo(ExternalRefs.Reference ref,
                    Set<LinkDigest> allLinks,
                    ExternalRefs refs) {
            mRef = ref;
            mAllLinks = Collections.unmodifiableSet(allLinks);
            mRefs = refs;
        }
    }

    private static final SuspectInfo getSuspectInfo(Archive archive,
                                                    ExternalRefs.Reference archiveRef,
                                                    ArchiveResolver resolver,
                                                    Map<String, SuspectInfo> suspects)
        throws IOException {
        SuspectInfo suspect = suspects.get(archiveRef.mExternalKey);
        if (suspect== null) {
            if (archive == null) {
                System.err.println("getSuspectInfo -- resolving: " + archiveRef.mExternalKey);
                archive = resolver.resolve(archiveRef);
            }
            System.err.println("getSuspectInfo -- " + archiveRef.mExternalKey);
            System.err.println("getSuspectInfo -- 1:" + archive.getRootObject(RootObjectKind.ARCHIVE_MANIFEST));
            if (!archive.hasValidArchiveManifest()) {

                // DCI: fails.  This is a real bug.  Investigate further?
                // DCI: PredicateFailureException?
                throw new IOException("The parent archive doesn't have a valid manifest: "
                                      + archiveRef.mExternalKey);
            }
            LinkDigest refsDigest = archive.getRootObject(RootObjectKind.PARENT_REFERENCES);
            ExternalRefs refs = null;
            if (refsDigest.isNullDigest()) {
                refs = ExternalRefs.NONE;
            } else {
                refs = ExternalRefs.fromBytes(archive.getFile(refsDigest));
            }

            suspect = new SuspectInfo(archiveRef,
                                      archive.allLinks(),
                                      refs);
            // Cache
            suspects.put(archiveRef.mExternalKey, suspect);
        }
        return suspect;
    }

    private static boolean isPerpetrator(ExternalRefs.Reference archiveRef,
                                         LinkDigest link,
                                         ArchiveResolver resolver,
                                         Map<String, SuspectInfo> infoCache) throws IOException {

        SuspectInfo current = getSuspectInfo(null, archiveRef, resolver, infoCache);
        Set<LinkDigest> allParentLinks = new HashSet<LinkDigest>();
        for (ExternalRefs.Reference ref : current.mRefs.mRefs) {
            SuspectInfo parent = getSuspectInfo(null, ref, resolver, infoCache);
            allParentLinks.addAll(parent.mAllLinks);
        }

        Set<LinkDigest> currentLinks = new HashSet<LinkDigest>(current.mAllLinks);
        currentLinks.removeAll(allParentLinks);

        return currentLinks.contains(link);
    }

    // Breadth first!
    private static ExternalRefs.Reference findPerpetrator(ExternalRefs.Reference archiveRef,
                                                          LinkDigest link,
                                                          ArchiveResolver resolver,
                                                          Map<String, SuspectInfo> infoCache)
        throws IOException {

        List<ExternalRefs.Reference> suspects = new ArrayList<ExternalRefs.Reference>();
        suspects.add(archiveRef); // Current archive version. MUST be cached.

        while (!suspects.isEmpty()) {
            ExternalRefs.Reference suspectRef = suspects.remove(0);
            SuspectInfo suspect = getSuspectInfo(null, suspectRef, resolver, infoCache);
            if (isPerpetrator(suspectRef, link, resolver, infoCache)) {
                return suspect.mRef;
            }
            suspects.addAll(suspect.mRefs.mRefs);
        }

        // DCI: predicate failure?
        throw new IOException("Couldn't find link???");
    }

    public static List<ExternalRefs.Reference> history(Archive archive,
                                                       ExternalRefs.Reference archiveRef,
                                                       List<LinkDigest> chain,
                                                       ArchiveResolver resolver) throws IOException {
        chain = new ArrayList<LinkDigest>(chain); // Deep copy so we can consume.
        if (archiveRef == null) {
            archiveRef = ExternalRefs.CURRENT_ARCHIVE;
        }
        Map<String, SuspectInfo> infoCache = new HashMap<String, SuspectInfo>();
        infoCache.put(archiveRef.mExternalKey, getSuspectInfo(archive, archiveRef, resolver, infoCache));

        List<ExternalRefs.Reference> historyList = new ArrayList<ExternalRefs.Reference>();
        while (!chain.isEmpty()) {
            LinkDigest link = chain.remove(0);
            ExternalRefs.Reference perpetrator = findPerpetrator(archiveRef, link, resolver, infoCache);
            historyList.add(perpetrator);
            // DCI: test. Think. This stuff makes my head hurt.
            // Don't need to search above the last link we found in the chain.
            archiveRef = perpetrator;
        }

        return historyList;
    }

    public interface ChangeLogCallback {
        boolean onChangeEntry(ExternalRefs.Reference oldVer,
                              ExternalRefs.Reference newVer,
                              FileManifest.Changes fromNewToOld);
    }

    // Generate a change log like the one in the current wikibot based
    // fniki wiki.
    // LATER: Deal with non-linear change history.
    public static void getManifestChangeLog(ExternalRefs.Reference latestRef,
                                            Archive archive,
                                            ArchiveResolver resolver,
                                            ChangeLogCallback callback) throws IOException {

        if (archive.getRootObject(RootObjectKind.FILE_MANIFEST).isNullDigest()) {
            throw new IOException("No FILE_MANIFEST in root objects: " +
                                  latestRef.toString());
        }

        Map<String, LinkDigest> currentMap = FileManifest.fromArchiveRootObject(archive).getMap();
        ExternalRefs.Reference currentRef = latestRef;
        while (currentRef != ExternalRefs.NULL_ARCHIVE) {
            ExternalRefs.Reference nextRef = ExternalRefs.NULL_ARCHIVE;
            Map<String, LinkDigest> nextMap = new HashMap<String, LinkDigest>();

            LinkDigest digest = archive.getRootObject(RootObjectKind.PARENT_REFERENCES);
            if (!digest.isNullDigest()) {
                ExternalRefs refs = ExternalRefs.fromBytes(archive.getFile(digest));

                if (refs.mRefs.size() > 1) {
                    throw new IOException("Code too dumb to deal with multiple parents. Sorry :-(");
                }

                if (refs.mRefs.size() > 0) {
                    nextRef = refs.mRefs.get(0);
                    archive = resolver.resolve(nextRef);

                    if (archive.getRootObject(RootObjectKind.FILE_MANIFEST).isNullDigest()) {
                        throw new IOException("No FILE_MANIFEST in root objects: " +
                                              nextRef.toString());
                    }

                    nextMap = FileManifest.fromArchiveRootObject(archive).getMap();
                }
            }

            if (!callback.onChangeEntry(currentRef, nextRef, FileManifest.diff(nextMap, currentMap))) {
                break; // Client code told us to give up.
            }

            currentRef = nextRef;
            currentMap = nextMap;
        }
    }

    ////////////////////////////////////////////////////////////
}