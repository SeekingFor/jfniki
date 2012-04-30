/* A UI subcomponent to load a list of other versions of this wiki via NNTP.
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

package fniki.wiki.child;

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


import static ys.wikiparser.Utils.*;

import fmsutil.FMSUtil;
import wormarc.ExternalRefs;
import wormarc.FileManifest;

import fniki.wiki.ArchiveManager;
import fniki.wiki.ChildContainer;
import fniki.wiki.ChildContainerException;
import fniki.wiki.GraphLog;
import static fniki.wiki.HtmlUtils.*;
import fniki.wiki.WikiContext;

public class LoadingVersionList extends AsyncTaskContainer {
    private StringBuilder mListHtml = new StringBuilder();
    private String mName = "";
    private String mContainerPrefix;
    private String mLoadedUri;
    private Set<String> mHeadUris;
    private Set<String> mKnownHeadUris;
    private boolean mSwitchedWiki;
    // Has no parent.
    private final static String BASE_VERSION = "0000000000000000";
    // We don't know parent.
    private final static String UNKNOWN_VERSION = "???";

    // CSS class names for version links. //
    // Is the loaded version and is a head.
    private final static String CSS_LOADED_HEAD = "verLoadedHead";
    // Is a head that is not the loaded version.
    // djk20120429 -- I set this to the same as verLoadedHead in
    // the CSS.  It seemed too confusing to make them different.
    // i.e. all I care about is, is this one I haven't seen before.
    private final static String CSS_NON_LOADED_HEAD = "verNonLoadedHead";
    // Is a head that we haven't seen before.
    private final static String CSS_NEW_HEAD = "verNewHead";
    // Is the loaded version, but isn't head.
    private final static String CSS_LOADED_NON_HEAD = "verLoadedNonHead";
    // Is neither the loaded version nor a head.
    private final static String CSS_NON_HEAD = "verNonHead";

    public LoadingVersionList(ArchiveManager archiveManager) {
        super(archiveManager);
    }

    public synchronized String getListHtml() {
        return mListHtml.toString();
    }

    public String getHtml(WikiContext context) throws ChildContainerException {
        try {
            if (context.getQuery().get("wikiName") != null && !mSwitchedWiki) {
                // LATER: Handle false error code. This should "never" fail.
                mArchiveManager.switchWiki(context.getQuery().get("wikiName"));
            }

            if (context.getAction().equals("confirm")) {
                // Copy stuff we need out because context isn't threadsafe.
                mName = context.getPath();
                mContainerPrefix = context.getString("container_prefix", null);
                if (mContainerPrefix == null) {
                    throw new RuntimeException("Assertion Failure: mContainerPrefix == null");
                }

                mLoadedUri = mArchiveManager.getParentUri();
                // So I don't need null checks.
                if (mLoadedUri == null) { mLoadedUri = ""; }

                mKnownHeadUris = mArchiveManager.getKnownHeadUris();

                startTask();
                try {
                    Thread.sleep(1000); // Hack. Give task thread a chance to finish.
                } catch (InterruptedException ioe) {
                    /* NOP */
                }
                sendRedirect(context, context.getPath());
                return "unreachable code";
            }

            boolean showBuffer = false;
            String confirmTitle = null;
            String cancelTitle = null;
            String title = null;
            switch (getState()) {
            case STATE_WORKING:
                showBuffer = true;
                title = "Loading Wiki Version Info from Board";
                cancelTitle = "Cancel";
                break;
            case STATE_WAITING:
                // Shouldn't hit this state.
                showBuffer = false;
                title = "Load Wiki Version Info from Board";
                confirmTitle = "Load";
                cancelTitle = "Cancel";
                break;
            case STATE_SUCCEEDED:
                showBuffer = true;
                title = "Loaded Wiki Version Info from Board";
                confirmTitle = null;
                cancelTitle = "Done";
                break;
            case STATE_FAILED:
                showBuffer = true;
                title = "Full Read of Wiki Version Info Failed";
                confirmTitle = "Reload";
                cancelTitle = "Done";
                break;
            }

            setTitle(title);

            // Hmmm... why did I do this before?
            // body.println("TD{font-family: Arial; font-size: 7pt;}\n");

            StringWriter buffer = new StringWriter();
            PrintWriter body = new PrintWriter(buffer);

            body.println("<h3>" + escapeHTML(title) + "</h3>");
            body.println(String.format("wikiname: %s<br>board: %s<p>",
                                    escapeHTML(context.getString("wikiname", "NOT_SET")),
                                    escapeHTML(context.getString("fms_group", "NOT_SET"))));

            if (showBuffer) {
                body.println(getListHtml());
                body.println("<hr>");
                body.println("<pre>");
                body.print(escapeHTML(getOutput()));
                body.println("</pre>");
            }
            body.println("<hr>");
            addButtonsHtml(context, body, confirmTitle, cancelTitle);
            body.close();
            return buffer.toString();
        } catch (IOException ioe) {
            context.logError("LoadingVersionList", ioe);
            return "Error LoadingVersionList";
        }
    }

    // Doesn't need escaping.
    public static String trustString(int value) {
        if (value == -1) {
            return "null";
        }
        return Integer.toString(value);
    }

    // LATER: move and document better.
    // uri format:
    // <sha1_hash_of_manifest><underbar><sha1_hash_of_parent_uri>[<underbar><sha1_hash_of_rebase_uri>]
    //
    // First is parent version, optional second is rebase version.
    public static String[] getParentVersions(FMSUtil.BISSRecord record) {
        if (record.mKey == null) {
            return new String[] {UNKNOWN_VERSION};
        }
        String[] fields = record.mKey.split("/");
        if (fields.length != 2) {
            return new String[] {UNKNOWN_VERSION};
        }

        fields = fields[1].split("_");
        if (fields.length < 2) { // LATER. handle multiple parents
            if (fields.length == 1 && fields[0].length() == 16) {
                // Assume the entry is the first version.
                return new String[] {BASE_VERSION};
            }

            return new String[] {UNKNOWN_VERSION};
        }

        // LATER: tighten up.
        if (fields.length > 2) {
            return new String[] {fields[1], fields[2]};
        }

        return new String[] {fields[1]};
    }


    final static class DAGData implements Comparable<DAGData> {
        public final int mSize;
        public final long mEpochMs;
        public final List<GraphLog.DAGNode> mDag;
        DAGData(int size, long epochMs, List<GraphLog.DAGNode> dag) {
            mSize = size;
            mEpochMs = epochMs;
            mDag = dag;
        }

        public int compareTo(DAGData o) {
            if (o == null) { throw new NullPointerException(); }
            if (o == this) { return 0; }
            if (o.mSize - mSize != 0) { // first by descending size.
                return o.mSize - mSize;
            }
            if (o.mEpochMs - mEpochMs != 0) { // then by descending date.
                return (o.mEpochMs - mEpochMs) > 0 ? 1: -1;
            }
            return 0;
        }

        // Hmmmm... not sure these are required.
        public boolean equals(Object obj) {
            if (obj == this) { return true; }

            if (obj == null || (!(obj instanceof DAGData))) {
                return false;
            }

            DAGData other = (DAGData)obj;
            return mSize == other.mSize &&
                mEpochMs == other.mEpochMs &&
                mDag.equals(other.mDag);
        }

        public int hashCode() {
            int result = 17;
            result = 37 * mSize;
            result = 37 * (int)(mEpochMs ^ (mEpochMs >>> 32));
            result = 37 * mDag.hashCode();
            return result;
        }
    }

    // Wed, 02 Mar 11 02:57:38 -0000
    private final static DateFormat sDateFormat = new java.text.SimpleDateFormat("EEE, dd MMM yy HH:mm:ssZ");
    private static void sortBySizeAndDate(List<List<GraphLog.DAGNode>> dags, Map<String, List<FMSUtil.BISSRecord>> lut) {
        List<DAGData> dagData = new ArrayList<DAGData>();
        for (List<GraphLog.DAGNode> dag : dags) {
            long epochMs = 0;
            for (GraphLog.DAGNode node : dag) {
                for (FMSUtil.BISSRecord record : lut.get(node.mTag)) {
                    try {
                        long zuluMs = sDateFormat.parse(record.mDate).getTime();
                        if (zuluMs > epochMs) {
                            epochMs = zuluMs;
                        }
                    } catch (ParseException pe) {
                        System.err.println("Parse of date failed: " + record.mDate);
                    }
                }
            }
            dagData.add(new DAGData(dag.size(), epochMs, dag));
        }
        Collections.sort(dagData);
        dags.clear();
        for (DAGData data : dagData) {
            dags.add(data.mDag);
        }
    }

    // LATER: Move this into FMSUtils?
    // i.e. Same private key was used to post the message and
    // insert the archive update.
    private static boolean isInserter(FMSUtil.BISSRecord record) {
        int keyStart = record.mKey.indexOf("@");
        int keyEnd = record.mKey.indexOf(",");
        if (keyStart == -1 || keyEnd == -1 || keyStart >= keyEnd) {
            return false;
        }
        String keyPubHash = record.mKey.substring(keyStart + 1,
                                                  keyEnd - keyStart + 3);


        int idStart = record.mFmsId.indexOf("@");
        if (idStart == -1 || idStart == record.mFmsId.length() - 1) {
            return false;
        }
        String idPubHash = record.mFmsId.substring(idStart + 1);

        // Special case code for FreeTalk.
        if (idPubHash.endsWith(".freetalk") &&
            idPubHash.length() > ".freetalk".length()) {
            // djk20111016: I don't run FreeTalk. This code path is untested.
            idPubHash = idPubHash.substring(0, idPubHash.length() - ".freetalk".length());
        }
        return idPubHash.equals(keyPubHash);
    }

    // ASSUMES: all refer to the same archive SSK!
    // Filter out redundant values, keeping the ones which appear
    // first in the list. This implictly keeps the most recent
    // by arrival date, at least for FMS.
    private static List<FMSUtil.BISSRecord>
        removeRedundantEntries(List<FMSUtil.BISSRecord> references) {
        Set<String> known = new HashSet<String>();
        List<FMSUtil.BISSRecord> pruned = new ArrayList<FMSUtil.BISSRecord>();
        for (FMSUtil.BISSRecord reference : references) {
            // LATER: Clean up C&P, factor out into getPubKeyHash() helper
            int idStart = reference.mFmsId.indexOf("@");
            if (idStart == -1 || idStart == reference.mFmsId.length() - 1) {
                continue;
            }
            String idPubHash = reference.mFmsId.substring(idStart + 1);
            // Special case code for FreeTalk.
            if (idPubHash.endsWith(".freetalk") &&
                idPubHash.length() > ".freetalk".length()) {
                // djk20111016: I don't run FreeTalk. This code path is untested.
                idPubHash = idPubHash.substring(0, idPubHash.length() - ".freetalk".length());
            }
            if (known.contains(idPubHash)) {
                continue;
            }

            known.add(idPubHash);
            pruned.add(reference);
        }
        return pruned;
    }

    public String versionCssClass(String uri) {
        if (mHeadUris.contains(uri)) {
            if (mLoadedUri.equals(uri)) {
                // Our version is head.
                return CSS_LOADED_HEAD;
            }
            if (mKnownHeadUris.contains(uri)) {
                return CSS_NON_LOADED_HEAD;
            }
            // LATER: Think this through. You only see this once. ok?
            return CSS_NEW_HEAD;
        } else {
            if (mLoadedUri.equals(uri)) {
                // Our version is not head.
                return CSS_LOADED_NON_HEAD;
            }
            return CSS_NON_HEAD;
        }
    }

    public synchronized String getRevisionGraphHtml(PrintStream textLog, List<FMSUtil.BISSRecord> records)
        throws IOException {

        textLog.println("Building graph...");
        // Build a list of revision graph edges from the NNTP notification records.
        List<GraphLog.GraphEdge> edges = new ArrayList<GraphLog.GraphEdge>();
        Map<String, List<FMSUtil.BISSRecord>> lut = new HashMap<String, List<FMSUtil.BISSRecord>>();
        for (FMSUtil.BISSRecord record : records) {
            String child = getVersionHex(record.mKey);
            String[] parents = getParentVersions(record);
            if (child.equals(UNKNOWN_VERSION) || parents[0].equals(UNKNOWN_VERSION)) {
                System.err.println(String.format("Skipping: (%s, %s)", child, parents[0]));
                System.err.println("  " + record.mKey);
                continue;
            }

            if (child.equals(BASE_VERSION)) {
                // INTENT: cycles in the DAG (i.e. non-dag) break the drawing code. Catch sleazy stuff.
                //         Remove this? 1) The attacker has to break break SHA1 to make the bad link.
                //                      2) No attacker will make a cylce with a single link.
                System.err.println(String.format("Attempted attack? Skipping: (%s, %s)", child, parents[0]));
                System.err.println("  " + record.mKey);
                continue;
            }

            List<FMSUtil.BISSRecord> recordsEntry = lut.get(child);
            if (recordsEntry == null) {
                recordsEntry = new ArrayList<FMSUtil.BISSRecord>();
                lut.put(child, recordsEntry);
            }
            if (!lut.get(child).contains(record)) {
                // Can have multiple records from the same fmsid.
                lut.get(child).add(record);
            }

            for (String parent : parents) { // add edges for both parent and rebase versions
                // Think of the graph as going from the bottom "up".
                // Edges point from parent version to child version.
                GraphLog.GraphEdge edge = new GraphLog.GraphEdge(parent, child);
                if (!edges.contains(edge)) { // hmmmm.... O(n) search. Dude, that's the least of your worries.
                    edges.add(edge);
                }
            }
        }

        // Passing BASE_VERSION keep the drawing code from drawing '|'
        // below root nodes.
        List<List<GraphLog.DAGNode>> dags = GraphLog.build_dags(edges, BASE_VERSION);
        sortBySizeAndDate(dags, lut);
        Set<String> headUris = getHeadUrisFromDags(dags, lut);
        synchronized(this) {
            mHeadUris = headUris;
        }

        // Draw the revision graph(s).
        StringWriter out = new StringWriter();
        out.write("<pre>\n");
        for (List<GraphLog.DAGNode> dag : dags) {
            out.write("<hr>\n");
            List<Integer> seen = new ArrayList<Integer>();
            GraphLog.AsciiState state = GraphLog.asciistate();
            for (GraphLog.DAGNode value : dag) {
                List<FMSUtil.BISSRecord> references = lut.get(value.mTag);
                // Filter out redundant references, keeping the most "recent"
                // ones.
                references = removeRedundantEntries(references);

                List<String> lines = new ArrayList<String>();
                String verClass = versionCssClass(references.get(0).mKey); // All the same.
                String versionLink = getShortVersionLink(mContainerPrefix, "/jfniki/loadarchive",
                                                         references.get(0).mKey,
                                                         verClass); // All the same.

                String rebaseLink = getRebaseLink(mContainerPrefix, "/jfniki/loadarchive",
                                                  references.get(0).mKey, "finished",
                                                  "[rebase]", false);


                lines.add(versionLink + " " + rebaseLink);

                for (FMSUtil.BISSRecord reference : references) {
                    String symbol = "+";
                    String cssClass = "versionStaker";
                    if (isInserter(reference)) {
                        symbol = "&exist;";
                        cssClass = "versionCreator";
                    }

                    // LATER: Sort by date
                    lines.add(String.format("%s <span class=\"%s\">%s</span> (%s, %s, %s, %s)",
                                            symbol,
                                            cssClass,
                                            escapeHTML(reference.mFmsId),
                                            trustString(reference.msgTrust()),
                                            trustString(reference.trustListTrust()),
                                            trustString(reference.peerMsgTrust()),
                                            trustString(reference.peerTrustListTrust())
                                            ));
                    lines.add(String.format("  %s", reference.mDate)); // Reliable?
                }
                String[] parentsAgain  = getParentVersions(references.get(0));

                lines.add(escapeHTML(String.format("parent: [%s]", parentsAgain[0])));
                if (parentsAgain.length == 2) {
                    lines.add(escapeHTML(String.format("rebased: [%s] (UNVERIFIED!)", parentsAgain[1])));
                }

                lines.add("");

                GraphLog.ascii(out, state, "o", lines, GraphLog.asciiedges(seen, value.mId, value.mParentIds));
            }
        }
        out.write("</pre>\n");
        out.flush();
        textLog.println("Finished. (It may take ~=15 seconds for the page to update.)");

        return out.toString();
    }

    // REVISIT: Simple quick and dirty implementation.
    // LATER: Look back over code above / GraphLog.java
    // and figure out how to do this without *another*
    // iteration over dags.
    public Set<String> getHeadUrisFromDags(List<List<GraphLog.DAGNode>> dags,
                                           Map<String, List<FMSUtil.BISSRecord>> lut) {

        // NOTE: The DagNode.mTag values are jfniki hex version ids.
        Set<String> uris = new HashSet<String>();
        for (List<GraphLog.DAGNode> dag : dags) {
            Set<Integer> parents = new HashSet<Integer>();
            for (GraphLog.DAGNode node : dag) {
                parents.addAll(node.mParentIds);
            }
            // Hmmm two iterations over dag.
            for (GraphLog.DAGNode node : dag) {
                // No other node references the node
                // as a parent so it must be a leaf
                // node.
                if (!parents.contains(node.mId)) {
                    if (lut.containsKey(node.mTag) &&
                        lut.get(node.mTag).size() > 0) {
                        // There can be multiple entries but they
                        // should all have the same key values.
                        uris.add(lut.get(node.mTag).get(0).mKey);
                    }
                }
            }
        }
        return uris;
    }

    public boolean doWork(PrintStream out) throws Exception {
        synchronized (this) {
            mListHtml = new StringBuilder();
        }
        try {
            String graphHtml = getRevisionGraphHtml(out, mArchiveManager.getRecentWikiVersions(out));
            synchronized (this) {
                mListHtml.append(graphHtml);
            }
            return true;
        } catch (IOException ioe) {
            out.println("Error reading log: " + ioe.getMessage());
            return false;
        }
    }

    public void exited() {
        if (getState() == STATE_SUCCEEDED) {
            // Tell the archive manager the latest head versions we
            // have seen for this wiki.
            mArchiveManager.updateLastKnownHeads(mHeadUris);
        }
        // Reset.
        mSwitchedWiki = false;
        mHeadUris = null;
        mLoadedUri = null;
        mKnownHeadUris = null;
        super.exited();
    }

}
