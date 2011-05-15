/* A UI subcomponent to display and edit wikitext.
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

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static ys.wikiparser.Utils.*;

import fniki.wiki.ChildContainer;
import fniki.wiki.ChildContainerException;
import fniki.wiki.Query;

import wormarc.FileManifest;
import wormarc.IOUtil;

import fniki.wiki.FreenetWikiTextParser;
import static fniki.wiki.HtmlUtils.*;
import static fniki.wiki.Validations.*;
import fniki.wiki.WikiApp;
import fniki.wiki.WikiContext;
import fniki.wiki.WikiTextStorage;

public class WikiContainer implements ChildContainer {
    private final static String ENCODING = "UTF-8";

    public WikiContainer() {}

    public String handle(WikiContext context) throws ChildContainerException {
        try {
            String action = context.getAction();
            if (action.equals("finished")) {
                // Hack: Ignore "finished".
                // This happens when the user hits the back button and picks
                // a link from a finished task page. e.g. changelog.
                action = "view";
            }
            // Convert spaces to '_' so you can type titles with spaces into
            // the "Goto or Create Page" box.
            String title = context.getTitle().trim().replace(" ", "_");
            if (!isAlphaNumOrUnder(title)) {
                // Titles must be legal page names.
                context.raiseAccessDenied("Couldn't work out query.");
            }

            if (!isLowerCaseAlpha(action)) {
                // Illegal action
                context.raiseAccessDenied("Couldn't work out query.");
            }

            Query query = context.getQuery();

            if (action.equals("view")) {
                return handleView(context, title);
            } else if (action.equals("edit")) {
                return handleEdit(context, title);
            } else if (action.equals("delete")) {
                return handleDelete(context, title);
            } else if (action.equals("revert")) {
                return handleRevert(context, title);
            } else if (action.equals("save")) {
                return handleSave(context, query);
            } else  {
                context.raiseAccessDenied("Couldn't work out query.");
            }
        } catch (IOException ioe) {
            context.logError("WikiContainer.handle", ioe);
            context.raiseServerError("Unexpected Error in WikiContainer.handle. Sorry :-(");
        }
        return "unreachable code";
    }

    private String handleView(WikiContext context, String name) throws IOException {
        return getPageHtml(context, name);
    }

    private String handleEdit(WikiContext context, String name) throws IOException {
        return getEditorHtml(context, name);
    }

    private String handleDelete(WikiContext context, String name) throws IOException {
        if (context.getStorage().hasPage(name)) {
            context.getStorage().deletePage(name);
        }

        // LATER: do better.
        return getPageHtml(context, name);
    }

    private String handleRevert(WikiContext context, String name) throws ChildContainerException, IOException {
        context.getStorage().revertLocalChange(name);
        context.raiseRedirect(context.makeLink("/" + name), "Redirecting...");
        return "unreachable code";
    }

    private String handleSave(WikiContext context, Query form) throws ChildContainerException, IOException {
        // Name is included in the query data.
        System.err.println("handleSave -- ENTERED");
        String name = form.get("savepage");
        String wikiText = form.get("savetext");

        System.err.println("handleSave --got params");
        if (name == null || wikiText == null) {
            context.raiseAccessDenied("Couldn't parse parameters from POST.");
        }

        System.err.println("Writing: " + name);
        context.getStorage().putPage(name, unescapeHTML(wikiText));
        System.err.println("Raising redirect!");
        context.raiseRedirect(context.makeLink("/" + name), "Redirecting...");
        System.err.println("SOMETHING WENT WRONG!");
        return "unreachable code";
    }

    private String unescapedTitleFromName(String name) {
        if (name.startsWith("Talk_")) {
            // LATER: Localization.
            name = "Talk:" + name.substring("Talk_".length());
        }
        return name.replace("_", " ");
    }

    private String getTalkPage(WikiContext context, String name) throws IOException {
        if (name.startsWith("Talk_") || (!context.getStorage().hasPage(name))) {
            return null;
        }
        // LATER: Localization
        return "Talk_" + name;
    }

    private String getPageHtml(WikiContext context, String name) throws IOException {
        StringBuilder buffer = new StringBuilder();
        addHeader(context, name, getTalkPage(context, name), buffer);
        if (context.getStorage().hasPage(name)) {
            buffer.append(renderXHTML(context, context.getStorage().getPage(name)));
        } else {
            // Hmmmm... too branchy
            if (name.equals(context.getString("default_page", "Front_Page"))) {
                buffer.append(renderXHTML(context,
                                          context.getString("default_wikitext",
                                                            "Page doesn't exist in the wiki yet.")));
            } else {
                if (name.startsWith("Talk_")) {
                    if (context.getStorage().hasPage("TalkPageDoesNotExist")) {
                        // LATER: Revisit. Also, ExternalLink. Evil submissions can change this to something confusing.
                        buffer.append(renderXHTML(context, context.getStorage().getPage("TalkPageDoesNotExist")));
                    } else {
                        buffer.append("Discussion page doesn't exist in the wiki yet.");
                    }
                } else {
                    if (context.getStorage().hasPage("PageDoesNotExist")) {
                        // LATER: as above.
                        buffer.append(renderXHTML(context, context.getStorage().getPage("PageDoesNotExist")));
                    } else {
                        buffer.append("Page doesn't exist in the wiki yet.");
                    }
                }
            }
        }
        addFooter(context, name, buffer);
        return buffer.toString();
    }

    private void addHeader(WikiContext context, String name, String talkName,
                           StringBuilder buffer) throws IOException {
        buffer.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        buffer.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" " +
                      "\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n");
        buffer.append("<html xmlns=\"http://www.w3.org/1999/xhtml\">\n");
        buffer.append("<head><title>\n");
        buffer.append(escapeHTML(unescapedTitleFromName(name)));
        buffer.append("</title>\n");
        buffer.append("<style type=\"text/css\">\n");
        // CAREFUL: MUST audit .css files built into .jar to make sure they are safe.
        // Load .css snippet from jar. Names can only have 1 '/' and must be globally unique.
        buffer.append(context.getString("/add_header.css", ""));
        buffer.append("</style>\n");
        buffer.append("</head>\n");
        buffer.append("<body>\n");
        buffer.append("<h1 class=\"pagetitle\">\n");
        buffer.append(escapeHTML(unescapedTitleFromName(name)));
        buffer.append("</h1>\n");
        if (talkName != null) {
            String talkClass = context.getStorage().hasPage(talkName) ? "talktitle" : "notalktitle";
            buffer.append(String.format("<h4 class=\"%s\">\n", talkClass));
            String href = makeHref(context.makeLink("/" + talkName), null, talkName, null, null);
            buffer.append(String.format("<a class=\"%s\" href=\"%s\">%s</a>",
                                        talkClass, href, escapeHTML("Discussion")));
            buffer.append("</h4>\n");
        }
        buffer.append("</h1><hr>\n");
    }

    private String makeLocalLink(WikiContext context, String name, String action, String label) {
        String href = makeHref(context.makeLink("/" + name), action, name, null, null);
        return String.format("<a href=\"%s\">%s</a>", href, escapeHTML(label));
    }

    private void addFooter(WikiContext context, String name, StringBuilder buffer) throws IOException {
        buffer.append("<hr>\n");
        buffer.append("Parent Version: ");
        // DCI: css class to make this smaller.
        String version = getVersionHex(context.getString("parent_uri", null));
        buffer.append(escapeHTML(version));
        buffer.append("<hr>\n");

        buffer.append(makeLocalLink(context, name, "edit", "Edit"));
        buffer.append(" this page.<br>");

        buffer.append(makeLocalLink(context, name, "delete", "Delete"));
        buffer.append(" this page without confirmation!<br>");

        if (context.getStorage().hasLocalChange(name)) {
            buffer.append(makeLocalLink(context, name, "revert", "Revert"));
            buffer.append(" local changes to this page without confirmation!<br>");
        }

        buffer.append(makeLocalLink(context, "fniki/submit", null, "Submit"));
        buffer.append(" local changes. <br>");

        buffer.append(makeLocalLink(context, "fniki/changelog", "confirm", "Show"));
        buffer.append(" change history for this version. <br>");

        buffer.append(makeLocalLink(context, "fniki/getversions", "confirm", "Discover"));
        buffer.append(String.format(" other recent version of this wiki (wikiname: [%s], FMS group: [%s])<br>",
                                    escapeHTML(context.getString("wikiname", "NOT_SET")),
                                    escapeHTML(context.getString("fms_group", "NOT_SET"))));

        buffer.append(makeLocalLink(context, "fniki/config", "view", "View"));
        buffer.append(" configuration.<p/>\n");
        buffer.append(gotoPageFormHtml(context.makeLink("/" + name),
                                       context.getString("default_page", "Front_Page")));

        buffer.append("<hr>\n");
        buffer.append(makeLocalLink(context, "fniki/resettoempty", "view", "Create Wiki!"));
        buffer.append(" (<em>careful:</em> This deletes all content and history without confirmation.)<p/>\n");

        // LATER: Quick hack. Clean this up.
        buffer.append(String.format("<p><form method=\"get\" action=\"%s\" accept-charset=\"UTF-8\">\n",
                                    context.makeLink("/fniki/loadarchive"), null, null, null, null));
        buffer.append("   <table><tr>\n");
        buffer.append("   <td><input type=submit value=\"Load Archive\"/></td>\n");
        buffer.append("   <td><input style=\"font-size:60%;\" type=text name=\"uri\" size=\"140\" value=\"\"/></td>\n");
        buffer.append("   </tr></table>\n");
        buffer.append("</form>\n");

        buffer.append("</body></html>\n");
    }

    private String getEditorHtml(WikiContext context, String name) throws IOException {
        String template = null;
        try {
            // IMPORTANT: Only multipart/form-data encoding works in plugins.
            // IMPORTANT: Must be multipart/form-data even for standalone because
            //            the Freenet ContentFilter rewrites the encoding in all forms
            //            to this value.
            template = IOUtil.readUtf8StringAndClose(SettingConfig.class.getResourceAsStream("/edit_form.html"));
        } catch (IOException ioe) {
            return "Couldn't load edit_form.html template from jar???";
        }

        String escapedName = escapeHTML(unescapedTitleFromName(name));
        String href = makeHref(context.makeLink("/" +name),
                               "save", null, null, null);
        String wikiText = "Page doesn't exist in the wiki yet.";
        if (context.getStorage().hasPage(name)) {
            wikiText = context.getStorage().getPage(name);
        }

        return String.format(template,
                             escapedName,
                             escapedName,
                             href,
                             escapeHTML(name), // i.e. with '_' chars
                             escapeHTML(wikiText),
                             // IMPORTANT: Required by Freenet Plugin.
                             // Doesn't need escaping.
                             context.getString("form_password", "FORM_PASSWORD_NOT_SET"));
    }

    public String renderXHTML(WikiContext context, String wikiText) {
        return new FreenetWikiTextParser(wikiText, context.getParserDelegate()).toString();
    }
}
