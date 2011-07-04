// LATER: fix this to use WikiHtmlExporter.

/* Command line utility to dump a jfniki wiki as html.
 *
 * Copyright (C) 2011 sethcg
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
 *
 * This is a derived work based on ServeHttp.java
 * written by djk@isFiaD04zgAgnrEC5XJt1i4IE7AkNPqhBG5bONi6Yks
 *
 *  This file was developed as component of
 * "fniki" (a wiki implementation running over Freenet).
 */
package fniki.standalone;

import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.File;

import java.util.List;

import static fniki.wiki.HtmlUtils.*;
import static fniki.wiki.Validations.*;
import static ys.wikiparser.Utils.*;

import wormarc.FileManifest;
import wormarc.IOUtil;

import fniki.wiki.ArchiveManager;
import fniki.wiki.FreenetWikiTextParser;
import fniki.wiki.WikiParserDelegate;
import fniki.wiki.HtmlExportParserDelegate;

public class DumpWiki {

    private final static String HELP_TEXT =
        "SUMMARY:\n" +
        "Dumps a wiki version in a format suitable for inserting as a Freesite.\n" +
        "This is experimental code. Use it at your own peril.\n\n" +
        "USAGE:\n" +
        "java fniki.standalone.DumpWiki <dump_path> SSK@/XXX...XXX/0123456789abcdef \\\n" +
        "     [[template_file] [[fcp_port] [fcp_host]]]\n\n" +
        "NOTE:\nfreenet.jar and jfniki.jar MUST be in your classpath.\n\n" +
        "The <dump_path> directory must already exist and any files in it will be overwritten.\n\n" +
        "The template_file should contain 4 %s place holders. The first two will be replaced \n"+
        "with the title. The third will be replaced by the href value for the discusion page.\n" +
        "The fourth will be replaced with the wiki content.\n"+
        "You can use the literal value 'default' to get the built in template file.\n\n"+
        "DumpWiki was written as part of the fniki Freenet Wiki project\n\n";

    ////////////////////////////////////////////////////////////

    public static void main(String[] args) throws Exception {
        if (args.length > 5 || args.length < 2) {
            System.err.println(HELP_TEXT);
            System.exit(-1);
        }

        int fcpPort = 9481;
        if (args.length > 3) {
            fcpPort = Integer.parseInt(args[3]);
        }
        String fcpHost = "127.0.0.1";
        if (args.length > 4) {
            fcpHost = args[4];
        }

        ArchiveManager archiveManager = new ArchiveManager();
        archiveManager.setFcpHost(fcpHost);
        archiveManager.setFcpPort(fcpPort);

        FreenetWikiTextParser.ParserDelegate mParserDelegate = new HtmlExportParserDelegate(archiveManager);

        String ouputDirectory = args[0];
        System.out.println("Preparing to dump archive to " + ouputDirectory + "...");

        File dir = new File(ouputDirectory);
        if (!(dir.exists() && dir.isDirectory() && dir.canWrite() )) {
            throw new IOException("Need " + ouputDirectory + " to be an existant, writeable directory.");
        }

        String wikiTemplate;
        if ( args.length >= 3 && (!args[2].equals("default")) && new File(args[2]).exists()) {
            System.err.println("Using template!");
            wikiTemplate = IOUtil.readUtf8StringAndClose(new FileInputStream(args[2]));
        } else {
            wikiTemplate = IOUtil.readUtf8StringAndClose(DumpWiki.class.getResourceAsStream("/wiki_dump_template.html"));
        }

        // Dump this archive and quit
        System.out.println("Loading archive...");
        archiveManager.load(args[1]);

        List<String> pages = archiveManager.getStorage().getNames();
        if ( !archiveManager.getStorage().hasPage("PageDoesNotExist") ) {
            pages.add("PageDoesNotExist");
        }

        String cleanName;
        for (String name : pages) {
            try {
                // sethcg: Do wiki page names already have bad characters stripped out? I'm assuming so.
                FileOutputStream out = new FileOutputStream(ouputDirectory + "/" + name + ".html");
                PrintStream p = new PrintStream(out);
                cleanName = unescapeHTML(name.replace("_", " "));
                String talkName = (archiveManager.getStorage().hasPage("Talk_" + name)) ? "Talk_" + name  + ".html":
                    "PageDoesNotExist.html";
                p.printf(wikiTemplate, cleanName, cleanName,
                         talkName,
                        (archiveManager.getStorage().hasPage(name)) ?
                        new FreenetWikiTextParser(archiveManager.getStorage().getPage(name), mParserDelegate).toString() :
                        "Page doesn't exist in the wiki yet."
                        );
                p.close();
            } catch ( IOException e) {
                System.out.println("Error writing " + name + " to file.");
            }
        }

        System.out.println("Successfully dumped wiki.");
    }
}
