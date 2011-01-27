/* Glue code to run WikiApp to run from within HTTPServer.
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
import java.io.ByteArrayOutputStream;
import net.freeutils.httpserver.HTTPServer;

import fniki.wiki.Query;
import fniki.wiki.Request;
import fniki.wiki.WikiApp;

import fniki.wiki.AccessDeniedException;
import fniki.wiki.NotFoundException;
import fniki.wiki.RedirectException;
import fniki.wiki.ChildContainerException;

// Adapter class to run WikiApp from HTTPServer
public class FnikiContextHandler implements HTTPServer.ContextHandler {
    private final WikiApp mApp;
    private final String mContainerPrefix;

    private static class WikiQuery implements Query {
        private final HTTPServer.Request mParent;
        private final String mSaveText;
        private final String mSavePage;

        // Hmmmm... can't figure out any other way to know when part is done.
        private final String readAsUtf8(HTTPServer.MultipartIterator.Part part) throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            while (part.body.available() > 0) { // Do better? Does it matter?
                int oneByte = part.body.read();
                if (oneByte == -1) {
                    throw new IOException("Unexpected EOF???");
                }
                baos.write(oneByte);
            }

            return new String(baos.toByteArray(), "utf8");
        }


        WikiQuery(HTTPServer.Request parent) throws IOException {
            mParent = parent;

            String saveText = null;
            String savePage = null;
            if (parent.getHeaders().getParams("Content-Type").
                containsKey("multipart/form-data")) {
                HTTPServer.MultipartIterator iter = new HTTPServer.MultipartIterator(parent);
                while (iter.hasNext()) {
                    HTTPServer.MultipartIterator.Part part = iter.next();
                    if (part.name.equals("savetext")) {
                        saveText = readAsUtf8(part);
                    } else if (part.name.equals("savepage")) {
                        savePage = readAsUtf8(part);
                    }
                }
                parent.consumeBody();
            }
            mSaveText = saveText;
            mSavePage = savePage;
        }
        public boolean containsKey(String paramName) {
            try {
                if (paramName.equals("savetext")) {
                    return mSaveText != null;
                } else if (paramName.equals("savepage")) {
                    return mSavePage != null;
                }
                return mParent.getParams().containsKey(paramName);
            } catch (IOException ioe) {
                return false;
            }
        }

        public String get(String paramName) {
            try {
                if (paramName.equals("savetext")) {
                    return mSaveText;
                } else if (paramName.equals("savepage")) {
                    return mSavePage;
                }
                return mParent.getParams().get(paramName);
            } catch (IOException ioe) {
                return null;
            }
        }
    }

    private static class WikiRequest implements Request {
        private final Query mQuery;
        private final String mPath;

        WikiRequest(HTTPServer.Request parent, String containerPrefix) throws IOException {
            mQuery = new WikiQuery(parent);
            for (String key : parent.getParams().keySet()) {
                String value = parent.getParams().get(key);
                if (value.length() > 128) {
                    value = value.substring(0, 128) + "...";
                }
                System.err.println(String.format("[%s] => [%s]", key, value));
            }

            String path = parent.getPath();
            if (!path.startsWith(containerPrefix)) {
                // This should be impossible because of the way HTTPServer routes requests.
                throw new RuntimeException("Request doesn't start with: " + containerPrefix);
            }

            System.err.println("Raw path: " + path);

            path = path.substring(containerPrefix.length());
            while(path.startsWith("/")) {
                path = path.substring(1).trim();
            }

            // DCI: not sure that this stuff belongs here.
            String title = path;
            if (mQuery.containsKey("title")) {
                title = mQuery.get("title");
            } else {
                parent.getParams().put("title", title);
            }

            // DCI: validate title here

            String action = "view";
            if (mQuery.containsKey("action")) {
                action = mQuery.get("action");
            } else {
                parent.getParams().put("action", action);
            }
            mPath = path;



        }

        public String getPath() { return mPath; }
        public Query getQuery() { return mQuery; }
    }

    public FnikiContextHandler(WikiApp app) {
        mApp = app;
        mContainerPrefix = mApp.getString("container_prefix", null);
        if (mContainerPrefix == null) {
            throw new IllegalArgumentException("mContainerPrefix == null");
        }
    }

    /**
     * Serves the given request using the given response.
     * 
     * @param req the request to be served
     * @param resp the response to be filled
     * @return an HTTP status code, which will be used in returning
     *         a default response appropriate for this status. If this
     *         method invocation already sent anything in the response
     *         (headers or content), it must return 0, and no further
     *         processing will be done
     * @throws IOException if an IO error occurs
     */
    public int serve(HTTPServer.Request req, HTTPServer.Response resp) throws IOException {
        synchronized (mApp) { // Only allow one thread to touch app at a time.
            mApp.setRequest(new WikiRequest(req, mContainerPrefix));
            try {
                String html = mApp.handle(mApp);
                resp.send(200, html);
                return 0;
            } catch(AccessDeniedException accessDenied) {
                resp.sendError(403, accessDenied.getMessage());
                return 0;
            } catch(NotFoundException notFound) {
                resp.sendError(404, notFound.getMessage());
                return 0;
            } catch(RedirectException redirected) {
                resp.redirect(redirected.getLocation(), false);
                return 0;
            } catch(ChildContainerException serverError) {
                // This also handles ServerErrorException.
                resp.sendError(500, serverError.getMessage());
                return 0;
            }
        }
    }
}
