/* A base class for common parts of FreenetWikiTextParser.ParserDelegate implementations.
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

import static ys.wikiparser.Utils.*; // DCI: clean up

import java.io.IOException;

import wormarc.FileManifest;

import static fniki.wiki.HtmlUtils.*;
import static fniki.wiki.Validations.*;

public abstract class WikiParserDelegate implements FreenetWikiTextParser.ParserDelegate {
    final protected ArchiveManager mArchiveManager;

    public WikiParserDelegate(ArchiveManager archiveManager) {
        mArchiveManager = archiveManager;
    }

    protected abstract String getContainerPrefix();
    protected abstract boolean getFreenetLinksAllowed();
    protected abstract boolean getImagesAllowed();
    protected abstract String makeLink(String containerRelativePath);
    protected abstract String makeFreenetLink(String uri);

    protected boolean processedLocalChangesMacro(StringBuilder sb, String text) {
        try { // Should never happen while dumping and existing archive to html.
            FileManifest.Changes changes =  mArchiveManager.getLocalChanges();
            if (changes.isUnmodified()) {
                sb.append("<br>No local changes.<br>");
            }
            appendChangesHtml(changes, getContainerPrefix(), sb);
        } catch (IOException ioe) {
            sb.append("{ERROR PROCESSING LOCALCHANGES MACRO}");
        }
        return true;
    }

    protected boolean processedTitleIndexMacro(StringBuilder sb, String text) {
        try {
            for (String name : mArchiveManager.getStorage().getNames()) {
                appendPageLink(getContainerPrefix(), sb, name, null, true);
                sb.append("<br>");
            }
        } catch (IOException ioe) {
            sb.append("{ERROR PROCESSING TITLEINDEX MACRO}");
            return true;
        }
        return true;
    }

    public boolean processedMacro(StringBuilder sb, String text) {
        if (text.equals("LocalChanges")) {
            return processedLocalChangesMacro(sb, text);
        } else if (text.equals("TitleIndex")) {
            return processedTitleIndexMacro(sb, text);
        }
        return false;
    }

    // CHK, SSK, USK freenet links.
    public void appendLink(StringBuilder sb, String text) {
        String[] link=split(text, '|');
        if (getFreenetLinksAllowed() &&
            isValidFreenetUri(link[0])) {
            sb.append("<a href=\""+ makeFreenetLink(link[0].trim()) +"\">");
            sb.append(escapeHTML(unescapeHTML(link.length>=2 && !isEmpty(link[1].trim())? link[1]:link[0])));
            sb.append("</a>");
            return;
        }

        if (isAlphaNumOrUnder(link[0])) {
            // Link to an internal wiki page.
            sb.append("<a href=\""+ makeHref(makeLink("/" + link[0].trim())) +"\">");
            sb.append(escapeHTML(unescapeHTML(link.length>=2 && !isEmpty(link[1].trim())? link[1]:link[0])));
            sb.append("</a>");
            return;
        }

        sb.append("<a href=\"" + makeHref(makeLink("/ExternalLink")) +"\">");
        sb.append(escapeHTML(unescapeHTML(link.length>=2 && !isEmpty(link[1].trim())? link[1]:link[0])));
        sb.append("</a>");
    }

    // Only CHK and SSK freenet links.
    public void appendImage(StringBuilder sb, String text) {
        if (!getImagesAllowed()) {
            sb.append("{IMAGES DISABLED. IMAGE WIKITEXT IGNORED}");
            return;
        }

        if (!getFreenetLinksAllowed()) {
            // Hmmm... A little wonky. But this is ok.
            sb.append("{FPROXY PREFIX NOT SET. IMAGE WIKITEXT IGNORED}");
            return;
        }

        String[] link=split(text, '|');
        if (isValidFreenetUri(link[0]) &&
            !link[0].startsWith("freenet:USK@")) {
            String alt=escapeHTML(unescapeHTML(link.length>=2 && !isEmpty(link[1].trim())? link[1]:link[0]));
            sb.append("<img src=\"" + makeFreenetLink(link[0].trim())
                      + "\" alt=\""+alt+"\" title=\""+alt+"\" />");
            return;
        }
        sb.append("{ERROR PROCESSING IMAGE WIKITEXT}");;
    }
}

