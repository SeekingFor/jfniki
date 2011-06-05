/* Utility class for rendering snippets of HTML.
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
import java.net.URI;
import java.net.URISyntaxException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import name.fraser.neil.plaintext.diff_match_patch; // <-- This is a class.

import static ys.wikiparser.Utils.*;

import wormarc.FileManifest;
import wormarc.IOUtil;

public class HtmlUtils {

    public static String makeHref(String fullPath,
                                  String actionValue, String titleValue,
                                  String uriValue, String gotoValue,
                                  String secondaryValue) {

        String query = "";
        if (actionValue != null) {
            query += "action=" + actionValue;
        }

        if (titleValue != null) {
            if (query.length() > 0) { query += "&"; }
            query += "title=" + titleValue;
        }

        if (uriValue != null) {
            if (query.length() > 0) { query += "&"; }
            query += "uri=" + uriValue;
        }

        if (gotoValue != null) {
            if (query.length() > 0) { query += "&"; }
            query += "goto=" + gotoValue;
        }

        if (secondaryValue != null) {
            if (query.length() > 0) { query += "&"; }
            query += "secondary=" + secondaryValue;
        }

        if (query.length() == 0) {
            query = null;
        }

        try {
            return new URI(null,
                           null,
                           fullPath,
                           query,
                           null).toString();
        } catch (URISyntaxException se) {
            System.err.println("HtmlUtils.makeHref failed: " +
                               fullPath + " : " + query);
            return "HTML_UTILS_MAKE_HREF_FAILED";
        }
    }

    public static String makeHref(String fullPath) {
        return makeHref(fullPath, null, null, null, null, null);
    }

    public static String makeFproxyHref(String fproxyPrefix, String freenetUri) {
        try {
            return new URI(fproxyPrefix + freenetUri).toString();
        } catch (URISyntaxException se) {
            return "HTML_UTILS_MAKE_FPROXY_HREF_FAILED";
        }
    }

    public static void appendPageLink(String prefix, StringBuilder sb, String name, String action, boolean asTitle) {
        String title = name;
        if (asTitle) {
            title = title.replace("_", " ");
        }

        String href = makeHref(prefix + '/' + name,
                               action, null, null, name, null);

        sb.append("<a href=\"" + href + "\">" + escapeHTML(title) + "</a>");
    }


    public static void appendChangesSet(String prefix, StringBuilder sb, String label, Set<String> values) {
        if (values.size() == 0) {
            return;
        }
        sb.append(escapeHTML(label));
        sb.append(": ");
        List<String> sorted = new ArrayList<String>(values);
        Collections.sort(sorted);
        // No join in Java? wtf?
        for (int index = 0; index < sorted.size(); index++) {
            appendPageLink(prefix, sb, sorted.get(index), "finished", false);
            if (index < sorted.size() - 1) {
                sb.append(", ");
            }
        }
        sb.append("<br>\n");
    }

    public static void appendChangesHtml(FileManifest.Changes changes, String prefix, StringBuilder sb) {
        appendChangesSet(prefix, sb, "Deleted", changes.mDeleted);
        appendChangesSet(prefix, sb, "Added", changes.mAdded);
        appendChangesSet(prefix, sb, "Modified", changes.mModified);
    }

    private static final String opName(int op) {
        switch (op) {
        case RebaseStatus.OP_ADDED: return "Added";
        case RebaseStatus.OP_DELETED: return "Missing";
        case RebaseStatus.OP_MODIFIED: return "Modified";
        default: throw new IllegalArgumentException();
        }
    }

    private static final String statusName(int status) {
        switch (status) {
        case RebaseStatus.LOCALLY_MODIFIED: return "Locally Modified";
        case RebaseStatus.PARENT: return "Parent";
        case RebaseStatus.REBASE: return "Rebase";
        default: throw new IllegalArgumentException();
        }
    }

    public static void appendRebaseStatusHtml(List<RebaseStatus.Record> records, String prefix, StringBuilder sb) {
        sb.append("<table border=\"1\">\n");
        sb.append("<tr> <th>Page</th> <th>Rebase Change</th> <th>Local Copy</th> </tr> \n");
        for (RebaseStatus.Record record : records) {
            sb.append("<tr> <td><a href=\"");
            sb.append(makeHref(prefix + "/" + record.mName, "view", null, null, null, null));
            sb.append("\">");
            sb.append(escapeHTML(record.mName)); // Paranoid.
            sb.append("</a></td> <td>");
            sb.append(escapeHTML(opName(record.mDiffOp))); // Paranoid.
            sb.append("</td> <td>");
            sb.append(escapeHTML(statusName(record.mStatus))); // Paranoid.
            sb.append("</td> </tr>\n");
        }
        sb.append("</table>\n");
    }

    // Path is the ony variable that has potentially dangerous data.
    public static String buttonHtml(String fullPath, String label, String action) {
        final String fmt =
            "<form method=\"get\" action=\"%s\" accept-charset=\"UTF-8\">" +
            "   <input type=submit value=\"%s\">" +
            "   <input type=hidden name=\"action\" value=\"%s\">" +
            "</form>";
        return String.format(fmt, makeHref(fullPath), escapeHTML(label), escapeHTML(action));
    }

    public static String getVersionHex(String uri) {
        try {
            if (uri == null) {
                return "???";
            }
            return IOUtil.getFileDigest(IOUtil.toStreamAsUtf8(uri)).hexDigest(8);
        } catch (IOException ioe) {
            return "???";
        }
    }

    // DCI: Change name: getLoadVersionLink
    public static String getVersionLink(String prefix, String name, String uri, String action,
                                        boolean hexLabel) {
        String label = uri;
        if (hexLabel) {
            label = getVersionHex(uri);
        }
        String href = makeHref(prefix + name, action, null, uri, null, null);

        return String.format("<a href=\"%s\">%s</a>", href, escapeHTML(label));
    }

    // DCI: Think through arguments
    public static String getRebaseLink(String prefix, String name, String uri, String action, String label,
                                       boolean hexLabel) {

        if (label == null) { label = uri; }

        if (hexLabel) {
            label = getVersionHex(uri);
        }
        String href = makeHref(prefix + name, action, null, uri, null, "true");

        return String.format("<a href=\"%s\">%s</a>", href, escapeHTML(label));
    }

    public static String getVersionLink(String prefix, String name, String uri, String action) {
        return  getVersionLink(prefix, name, uri, action, false);
    }

    // Hmmmm...
    public static String getVersionLink(String prefix, String name, String uri) {
        return getVersionLink(prefix, name, uri, "finished");
    }

    public static String getShortVersionLink(String prefix, String name, String uri) {
        return getVersionLink(prefix, name, uri, "finished", true);
    }

    public static String gotoPageFormHtml(String basePath, String defaultPage) {
        final String fmt =
            "<form method=\"get\" action=\"%s\" accept-charset=\"UTF-8\"> \n" +
            "   <input type=submit value=\"Goto or Create Page\"/> \n" +
            "   <input type=\"text\" name=\"title\" value=\"%s\"/> \n" +
            "</form> \n";
        return String.format(fmt, makeHref(basePath), defaultPage);
    }

    // Returns a pretty html diff of the wikitext.
    public static String getDiffHtml(String fromWikiText, String toWikiText) {
        if (fromWikiText == null) {
            return "Nothing to diff.";
        }
        if (toWikiText == null) {
            return "Nothing to diff.";
        }

        if (fromWikiText.equals(toWikiText)) {
            return "No changes.";
        }

        diff_match_patch dmp = new diff_match_patch();
        LinkedList<diff_match_patch.Diff> diff = dmp.diff_main(fromWikiText, toWikiText);
        dmp.diff_cleanupSemantic(diff);
        dmp.diff_cleanupEfficiency(diff);
        return dmp.diff_prettyHtmlUsingCss(diff, "diffins", "diffdel", "diffequal");
    }
}