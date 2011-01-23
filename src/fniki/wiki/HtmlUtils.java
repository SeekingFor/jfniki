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

import java.net.URI;
import java.net.URISyntaxException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static ys.wikiparser.Utils.*;

import wormarc.FileManifest;

public class HtmlUtils {

    public static String makeHref(String fullPath,
                                  String actionValue, String titleValue,
                                  String uriValue, String gotoValue) {

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
        return makeHref(fullPath, null, null, null, null);
    }

    public static String makeFproxyHref(String fproxyPrefix, String freenetUri) {
        // DCI: url encode? use key=?
        return fproxyPrefix + freenetUri;
    }

    // DCI: option to convert '_' -> ' '
    public static void appendPageLink(String prefix, StringBuilder sb, String name, String action, boolean asTitle) {
        String title = name;
        if (asTitle) {
            title = title.replace("_", " ");
        }

        String href = makeHref(prefix + '/' + name,
                               action, null, null, name);

        sb.append("<a href=\"" + href + "\">" + escapeHTML(title) + "</a>");
    }

    public static void appendChangesSet(String prefix, StringBuilder sb, String label, Set<String> values) {
        if (values.size() == 0) {
            return;
        }
        sb.append(label);
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

    // Path is the ony variable that has potentially dangerous data.
    public static String buttonHtml(String fullPath, String label, String action) {
        final String fmt =
            "<form method=\"get\" action=\"%s\" accept-charset=\"UTF-8\">" +
            "   <input type=submit value=\"%s\">" +
            "   <input type=hidden name=\"action\" value=\"%s\">" +
            "</form>";
        return String.format(fmt, makeHref(fullPath), label, action);
    }

    public static String getVersionLink(String prefix, String name, String uri, String action) {
        String href = makeHref(prefix + name, action, null, uri, null);

        return String.format("<a href=\"%s\">%s</a>", href, escapeHTML(uri));
    }

    // Hmmmm...
    public static String getVersionLink(String prefix, String name, String uri) {
        return getVersionLink(prefix, name, uri, "finished");
    }

    public static boolean isValidFreenetUri(String link) {
        // DCI: do much better!
        return (link.startsWith("freenet:CHK@") ||
                link.startsWith("freenet:SSK@") ||
                link.startsWith("freenet:USK@"));
    }

    public static boolean isValidLocalLink(String link) {
        for (int index = 0; index < link.length(); index++) {
            char c  = link.charAt(index);
            if ((c >= '0' && c <= '9') ||
                (c >= 'a' && c <= 'z') ||
                (c >= 'A' && c <= 'Z') ||
                c == '_') {
                continue;
            }
            return false;
        }
        return true;
    }
}