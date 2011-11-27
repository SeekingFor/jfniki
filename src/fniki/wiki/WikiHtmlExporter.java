/* Export a wiki version as HTML.
 *
 * Copyright (C) 2011 sethcg (derived from DumpWiki), djk
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
 * Author: sethcg@a-tin0kMl1I~8xn5lkQDqYZRExKLzJITrxcNsr4T~fY
 * Author: djk@isFiaD04zgAgnrEC5XJt1i4IE7AkNPqhBG5bONi6Yks
 *
 *  This file was developed as component of
 * "fniki" (a wiki implementation running over Freenet).
 */

package fniki.wiki;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.StringWriter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import wormarc.IOUtil;

import static ys.wikiparser.Utils.*;

public class WikiHtmlExporter {
    private final ArchiveManager mArchiveManager;
    private final WikiParserDelegate mParserDelegate;
    private final String mTemplate;
    private final Iterable<FileInfo> mStaticFiles;

    public WikiHtmlExporter(ArchiveManager manager, String template,
                            Iterable<FileInfo> staticFiles) throws IOException {
        mArchiveManager = manager;
        mParserDelegate = new HtmlExportParserDelegate(manager);
        mTemplate = template;
        mStaticFiles = staticFiles;
    }

    private String dumpPage(String name) throws IOException {
        if (!mArchiveManager.getStorage().hasPage(name)) {
            return "Page doesn't exist in the wiki yet.";
        }
        return new FreenetWikiTextParser(mArchiveManager.getStorage().getPage(name), mParserDelegate).toString();
    }

    private String getTalkName(String name) throws IOException {
        if (!mArchiveManager.getStorage().hasPage("Talk_" + name)) {
            return "PageDoesNotExist.html";
        }
        return "Talk_" + name  + ".html";
    }

    private String getTalkCssClass(String name) throws IOException {
        if (mArchiveManager.getStorage().hasPage("Talk_" + name)) {
            return "talktitle";
        }
        return "notalktitle";
    }

    public Iterable<FileInfo> export() throws IOException {
        List<String> pages = mArchiveManager.getStorage().getNames();
        if ( !mArchiveManager.getStorage().hasPage("PageDoesNotExist")) {
            pages.add("PageDoesNotExist");
        }

        List<FileInfo> infos = new ArrayList<FileInfo>();
        for (Iterator<FileInfo> itr = mStaticFiles.iterator(); itr.hasNext(); ) {
            infos.add(itr.next()); // Hmmmm really not better way?
        }

        for (String name : pages) {
            StringWriter writer = new StringWriter();
            String cleanName = unescapeHTML(name.replace("_", " "));
            String talkName = getTalkName(name);
            writer.write(String.format(mTemplate,
                                       cleanName,            // %1$s
                                       cleanName,            // %2$s Historical, should fix
                                       talkName,             // %3$s
                                       dumpPage(name),       // %4$s
                                       getTalkCssClass(name) // %5$s
                                       ));
            writer.flush();
            // LATER: back to this.
            // Yeah. I really am storing every single page in RAM.
            FileInfo info = FcpTools.
                makeFileInfo(name + ".html", writer.toString().getBytes(IOUtil.UTF8), "text/html");
            infos.add(info);
        }
        return infos;
    }
}
