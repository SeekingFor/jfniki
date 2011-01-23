/* Freenet Plugin to run WikiApp.
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

package fniki.plugin;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import freenet.pluginmanager.AccessDeniedPluginHTTPException;
import freenet.pluginmanager.FredPlugin;
import freenet.pluginmanager.FredPluginHTTP;
import freenet.pluginmanager.FredPluginThreadless;
import freenet.pluginmanager.NotFoundPluginHTTPException;
import freenet.pluginmanager.PluginHTTPException;
import freenet.pluginmanager.PluginRespirator;
import freenet.pluginmanager.RedirectPluginHTTPException;
import freenet.support.api.HTTPRequest;

import fniki.wiki.ArchiveManager;
import fniki.wiki.Query;
import fniki.wiki.Request;
import fniki.wiki.WikiApp;

import fniki.wiki.AccessDeniedException;
import fniki.wiki.NotFoundException;
import fniki.wiki.RedirectException;
import fniki.wiki.ChildContainerException;

public class Fniki implements FredPlugin, FredPluginHTTP, FredPluginThreadless {
    private WikiApp mWikiApp;

    public void terminate() {
        System.err.println("terminating...");
    }

    public void runPlugin(PluginRespirator pr) {
        try {
            ArchiveManager archiveManager = new ArchiveManager();

            // DCI: Parameter handling?
            archiveManager.setFcpHost("127.0.0.1");
            archiveManager.setFcpPort(9481);

            archiveManager.setFmsHost("127.0.0.1");
            archiveManager.setFmsPort(1119);

            // YOU MUST SET THESE OR THE PLUGIN WON'T LOAD.
            archiveManager.setPrivateSSK("FMS_PRIVATE_SSK");
            archiveManager.setFmsId("FMS_ID");

            archiveManager.setFmsGroup("biss.test000");
            archiveManager.setBissName("testwiki");

            String fproxyPrefix = "http://127.0.0.1:8888/";
            boolean enableImages = true;

            archiveManager.createEmptyArchive();

            WikiApp wikiApp = new WikiApp(archiveManager);
            final String containerPrefix = wikiApp.getString("container_prefix", null);
            if (containerPrefix == null) {
                throw new RuntimeException("Assertion Failure: container_prefix not set!");
            }
            wikiApp.setFproxyPrefix(fproxyPrefix);
            wikiApp.setAllowImages(enableImages);

            // IMPORTANT:
            // HTTP POSTS will be rejected without any useful error message if your form
            // doesn't contain a hidden field with the freenet per boot form password.
            wikiApp.setFormPassword(pr.getNode().clientCore.formPassword);

            // I couldn't get application/x-www-form-urlencoded forms to work.
            wikiApp.setUseMultiPartForms(true);

            mWikiApp = wikiApp;

        } catch (IOException ioe) {
            System.err.println("EPIC FAIL!");
            ioe.printStackTrace();
        }
    }

    private static class ServerPluginHTTPException extends PluginHTTPException {
	private static final long serialVersionUID = -1;

	public static final short code = 500; // Bad Request
	public ServerPluginHTTPException(String errorMessage, String location) {
            super(errorMessage, location);
	}
    }

    private static class PluginQuery implements Query {
        private final HTTPRequest mParent;
        private final String mTitle;
        private final String mAction;
        private final String mSaveText;
        private final String mSavePage;

        PluginQuery(HTTPRequest parent, String path) {
            mParent = parent;

            String title = path;
            if (parent.isParameterSet("title")) {
                title = parent.getParam("title");
            }
            mTitle = title;

            // DCI: validate title here

            String action = "view";
            if (parent.isParameterSet("action")) {
                action = parent.getParam("action");
            }
            mAction = action;

            // Handle multipart form parameters.
            System.err.println("Dumping list of parts...");
            String saveText = "";
            String savePage = "";
            try {
                for (String part : parent.getParts()) {
                    if (part.equals("savetext")) {
                        // DCI: magic numbers
                        saveText = new String(parent.getPartAsBytesFailsafe(part, 64 * 1024), "utf-8");
                        continue;
                    }
                    if (part.equals("savepage")) {
                        savePage = new String(parent.getPartAsBytesFailsafe(part, 64 * 1024), "utf-8");
                    }
                }
            } catch (UnsupportedEncodingException ue) {
                // Shouldn't happen.
                ue.printStackTrace();
            }
            mSaveText = saveText;
            mSavePage = savePage;

            parent.freeParts(); // DCI: test!, put in finally?

        }

        public boolean containsKey(String paramName) {
            if (paramName.equals("title") || paramName.equals("action") ||
                paramName.equals("savetext") || paramName.equals("savepage")) {
                return true;
            }
            return mParent.isParameterSet(paramName);
        }

        public String get(String paramName) {
            if (paramName.equals("title")) {
                return mTitle;
            }
            if (paramName.equals("action")) {
                return mAction;
            }
            if (paramName.equals("savetext")) {
                return mSaveText;
            }
            if (paramName.equals("savepage")) {
                return mSavePage;
            }
            if (!containsKey(paramName)) {
                return null;
            }
            return mParent.getParam(paramName);
        }
    }

    private static class PluginRequest implements Request {
        private final Query mQuery;
        private final String mPath;
        PluginRequest(HTTPRequest parent, String containerPrefix) { // DCI throws IOException {
            for (String key : parent.getParameterNames()) {
                String value = parent.getParam(key);
                if (value.length() > 128) {
                    value = value.substring(0, 128) + "...";
                }
                System.err.println(String.format("[%s] => [%s]", key, value));
            }

            String path = parent.getPath();
            if (!path.startsWith(containerPrefix)) {
                // This should be impossible because of the way plugin requests are routed.
                throw new RuntimeException("Request doesn't start with: " + containerPrefix);
            }

            System.err.println("Raw path: " + path);
            path = path.substring(containerPrefix.length());

            while(path.startsWith("/")) {
                path = path.substring(1).trim();
            }
            mQuery = new PluginQuery(parent, path);
            mPath = path;
        }

        public String getPath() { return mPath; }
        public Query getQuery() { return mQuery; }
    }

    public String handle(HTTPRequest request) throws PluginHTTPException {
        // DCI: cleanup container_prefix usage

        try {
            mWikiApp.setRequest(new PluginRequest(request, mWikiApp.getString("container_prefix", null)));
            return mWikiApp.handle(mWikiApp);
        } catch(AccessDeniedException accessDenied) {
            throw new AccessDeniedPluginHTTPException(accessDenied.getMessage(),
                                                      mWikiApp.getString("container_prefix", null));
        } catch(NotFoundException notFound) {
            throw new NotFoundPluginHTTPException(notFound.getMessage(),
                                                  mWikiApp.getString("container_prefix", null));
        } catch(RedirectException redirected) {
            throw new RedirectPluginHTTPException(redirected.getMessage(),
                                              redirected.getLocation());
        } catch(ChildContainerException serverError) {
            throw new ServerPluginHTTPException(serverError.getMessage(),
                                                mWikiApp.getString("container_prefix", null));
        }
    }

    public String handleHTTPGet(HTTPRequest request) throws PluginHTTPException {
        return handle(request);
    }

    public String handleHTTPPost(HTTPRequest request) throws PluginHTTPException {
        return handle(request);
    }
}

