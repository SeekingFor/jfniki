/* A ParserDelegate implementation for dumping wikitext as html.
 *
 * INTENT: I did this so code could be shared between the live wiki and html dumping.
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
import static fniki.wiki.HtmlUtils.*;
import static ys.wikiparser.Utils.*; // DCI: clean up

public class HtmlExportParserDelegate extends WikiParserDelegate {
    public HtmlExportParserDelegate(ArchiveManager archiveManager) {
        super(archiveManager);
    }

    // Implement base class abstract methods to supply the functionality
    // specific to dumping a wiki as html.
    protected String getContainerPrefix() { return ""; }
    protected boolean getFreenetLinksAllowed() { return true; }
    protected boolean getImagesAllowed() { return true; }

    protected String makeLink(String containerRelativePath) {
        while (containerRelativePath.startsWith("/")) {
            containerRelativePath = containerRelativePath.substring(1);
        }
        try {
            if (!mArchiveManager.getStorage().hasPage(containerRelativePath)) {
                containerRelativePath = "PageDoesNotExist";
            }
        } catch (IOException ioe) {
            throw new RuntimeException("ArchiveManager.getStorage() failed???", ioe);
        }
        return containerRelativePath + ".html";
    }

    protected String makeFreenetLink(String uri) {
        if (!uri.startsWith("freenet:")) {
            throw new RuntimeException("uri doesn't start with 'freenet:'");
        }
        return "/" + uri.substring("freenet:".length());
    }

    // Override one pesky macro that requires a different implementation.
    protected boolean processedTitleIndexMacro(StringBuilder sb, String text) {
        try {
            for (String name : mArchiveManager.getStorage().getNames()) {
                sb.append("<a href=\"" + makeHref(name) + ".html\">" + escapeHTML(name.replace("_", " ")) + "</a>");
                sb.append("<br />");
            }
        } catch (IOException ioe) {
            sb.append("{ERROR PROCESSING TITLEINDEX MACRO}");
        }
        return true;
    }
}
