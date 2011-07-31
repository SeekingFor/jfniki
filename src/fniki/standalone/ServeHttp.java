/* Stand alone client to serve WikiApp on localhost.
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
package fniki.standalone;

import java.io.IOException;
import java.io.FileInputStream;

import net.freeutils.httpserver.HTTPServer;

import wormarc.IOUtil;
import fniki.wiki.Configuration;
import fniki.wiki.WikiApp;
import fniki.wiki.ArchiveManager;

public class ServeHttp {
    private final static int DEFAULT_PORT = 8080;

    private final static String HELP_TEXT =
        "ServeHttp: Experimental distributed anonymous wiki over Freenet + FMS\n" +
        "written as part of the fniki Freenet Wiki project\n" +
        "Copyright (C) 2010, 2011 Darrell Karbott, GPL2 (or later)\n\n" +
        "SUMMARY:\n" +
        "Launch a wiki viewer / editor on localhost.\n" +
        "This is experimental code. Use it at your own peril.\n\n" +
        "USAGE:\n" +
        "java -jar jfniki.jar <listen_port> [SSK@/XXX...XXX/0123456789abcdef]\n" +
        "or\n" +
        "java -jar jfniki.jar <config_file> [SSK@/XXX...XXX/0123456789abcdef]\n\n" +
        "NOTE:\nfreenet.jar MUST be in your classpath.\n" +
        "If you include an archive SSK on the command line, the web UI doesn't\n" +
        "start until it is loaded.\n\n" +
        "EXAMPLES:\n" +
        "java -jar jfniki.jar ~/saved_jfniki.cfg\n\n" +
        "java -jar jfniki.jar 8099\n\n";


    public static void main(String[] args) throws Exception {
        if (args.length > 2 || args.length < 1) {
            System.err.println(HELP_TEXT);
            System.exit(-1);
        }

        ArchiveManager archiveManager = new ArchiveManager();
        WikiApp wikiApp = new WikiApp(archiveManager, true);

        if(wikiApp.getContext().getString("container_prefix", null) == null) {
            throw new RuntimeException("Assertion Failure: container_prefix not set!");
        }

        try {
            // Try to parse the argument as an integer listen port.
            wikiApp.setListenPort(Integer.parseInt(args[0]));

        } catch (NumberFormatException nfe) {
            System.out.println("Reading configuration from: " + args[0]);
            Configuration config =
                Configuration.fromStringRep(IOUtil.readUtf8StringAndClose(new FileInputStream(args[0])));
            wikiApp.getContext().setConfiguration(config);
        }

        if (args.length == 1) {
            archiveManager.createEmptyArchive();
        } else {
            // Needs to happen after the config is loaded.
            archiveManager.load(args[1]);
        }

        int listenPort = wikiApp.getContext().getInt("listen_port", WikiApp.LISTEN_PORT);
        final String containerPrefix = wikiApp.getContext().getString("container_prefix", null);
        // Redirect non-routed requests to the wiki app.
        HTTPServer.ContextHandler defaultRedirect = new HTTPServer.ContextHandler() {
                public int serve(HTTPServer.Request req, HTTPServer.Response resp) throws IOException {
                    resp.redirect(containerPrefix, false);
                    return 0;
                }
            };

        HTTPServer server = new HTTPServer(listenPort);
        HTTPServer.VirtualHost host = server.getVirtualHost(null); // default host
        host.setAllowGeneratedIndex(false);
        host.setDirectoryIndex("/"); // Keep from sending default "index.html"
        host.addContext(containerPrefix, new FnikiContextHandler(wikiApp));
        host.addContext("/", defaultRedirect);
        server.start();

        System.err.println(String.format("Serving wiki on: http://127.0.0.1:%d%s",
                                         listenPort, containerPrefix));
    }
}
