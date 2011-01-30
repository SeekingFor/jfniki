/* A UI subcomponent to display and set the configuration.
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

import fniki.wiki.ArchiveManager;
import fniki.wiki.ChildContainerException;
import static fniki.wiki.HtmlUtils.*;
import fniki.wiki.ModalContainer;
import fniki.wiki.Query;
import fniki.wiki.WikiApp;
import fniki.wiki.WikiContext;

public class SettingConfig implements ModalContainer {
    private final WikiApp mWikiApp;
    private final ArchiveManager mArchiveManager;

    private boolean mFinished = false;
    private String mMsg = "";

    public SettingConfig(WikiApp wikiApp, ArchiveManager archiveManager) {
        mWikiApp = wikiApp;
        mArchiveManager = archiveManager;
    }

    private void handlePost(WikiContext context) throws ChildContainerException {
        Query query = context.getQuery();
        if (query.containsKey("discarded")) {
            mMsg = "Discarded changes.";
            return;
        }

        if (query.containsKey("done")) {
            // Causes the routing logic in WikiApp.routeRequest to transition
            // out of this modal ui state.
            String redirectHref = makeHref(context.makeLink("/fniki/config"),
                                           "finished", null, null, null);
            context.raiseRedirect(redirectHref, "Redirecting...");
        }

        if (!query.containsKey("saved")) {
            return; // Not sure how this would happen
        }

        for (String key : FORM_FIELDS) {
            if (!query.containsKey(key)) {
                continue;
            }
            if (key.equals("fcphost")) { mArchiveManager.setFcpHost(query.get(key)); }
            if (key.equals("fcpport")) { mArchiveManager.setFcpPort(Integer.parseInt(query.get(key))); }

            if (key.equals("fpprefix")) { mWikiApp.setFproxyPrefix(query.get(key)); }

            if (key.equals("fmshost")) { mArchiveManager.setFmsHost(query.get(key)); }
            if (key.equals("fmsport")) { mArchiveManager.setFmsPort(Integer.parseInt(query.get(key))); }

            if (key.equals("fmsssk")) { mArchiveManager.setPrivateSSK(query.get(key)); }
            if (key.equals("fmsid")) { mArchiveManager.setFmsId(query.get(key)); }

            if (key.equals("wikiname")) { mArchiveManager.setBissName(query.get(key)); }
        }

        // Don't "images" not set in query params when box unchecked.
        if (query.containsKey("images")) {
            System.err.println("images: SET");
            mWikiApp.setAllowImages(true);
        } else {
            System.err.println("images: CLEARED");
            mWikiApp.setAllowImages(false);
        }
    }

    private static String noNulls(String text) {
        if (text == null) {
            return "";
        }
        return text;
    }

    // DCI: back over this. escape  quotes.
    public String handle(WikiContext context) throws ChildContainerException {
        handlePost(context);
        // DCI: Hmmm... it would be more pedantic to use the context interface for all of these.

        String href = makeHref(context.makeLink("/fniki/config"),
                               null, null, null, null);

        System.err.println("images allowd: " + context.getInt("allow_images", 0));
        return String.format(formTemplate(),
                             href,
                             noNulls(mArchiveManager.getFcpHost()),
                             Integer.toString(mArchiveManager.getFcpPort()),
                             context.getString("fproxy_prefix", "http://127.0.0.1:8888/"),
                             noNulls(mArchiveManager.getFmsHost()),
                             Integer.toString(mArchiveManager.getFmsPort()),
                             noNulls(mArchiveManager.getPrivateSSK()),
                             noNulls(mArchiveManager.getFmsId()),
                             noNulls(mArchiveManager.getFmsGroup()),
                             noNulls(mArchiveManager.getBissName()),
                             (context.getInt("allow_images", 0) == 1) ? "checked" : "",
                             // IMPORTANT: Won't work as a plugin without this.
                             context.getString("form_password", "FORM_PASSWORD_NOT_SET"));
    }

    public boolean isFinished() {return mFinished; }
    public void cancel() { mFinished = true; }
    public void entered(WikiContext context) {
        mFinished = false;
        mMsg = "";
    }
    public void exited() {}

    //////////////////////////////////////////////////
    private static final String FORM_FIELDS[] = new String[] {
        "fcphost", "fcpport", "fpprefix", "fmshost", "fmsport",
        "fmsssk", "fmsid", "wikiname", "images",
    };

    private static String formTemplate() {
        StringBuilder sb = new StringBuilder();

sb.append("<html>\n");
sb.append("<head>\n");
sb.append("<title>\n");
sb.append("  Configuration\n");
sb.append("</title>\n");
sb.append("</head>\n");
sb.append("<body>\n");
sb.append("<h1>Configuration</h1>\n");
sb.append("<form method=\"post\" action=\"%s\" enctype=\"multipart/form-data\" accept-charset=\"UTF-8\">\n");
sb.append("<table>\n");
sb.append("  <tr>\n");
sb.append("    <td>Fcp Host</td>\n");
sb.append("    <td><input type=\"text\" name=\"fcphost\" value=\"%s\" /></td>\n");
sb.append("  </tr>\n");
sb.append("  <tr>\n");
sb.append("    <td>Fcp Port</td>\n");
sb.append("    <td><input type=\"text\" name=\"fcpport\" value=\"%s\" /></td>\n");
sb.append("  </tr>\n");
sb.append("  <tr>\n");
sb.append("    <td>Fproxy Prefix</td>\n");
sb.append("    <td><input type=\"text\" name=\"fpprefix\" size=\"64\" value=\"%s\" /></td>\n");
sb.append("  </tr>\n");
sb.append("  <tr>\n");
sb.append("    <td>FMS Host</td>\n");
sb.append("    <td><input type=\"text\" name=\"fmshost\" value=\"%s\" /></td>\n");
sb.append("  </tr>\n");
sb.append("  <tr>\n");
sb.append("    <td>FMS Port</td>\n");
sb.append("    <td><input type=\"text\" name=\"fmsport\" value=\"%s\" /> </td>\n");
sb.append("  </tr>\n");
sb.append("  <tr>\n");
sb.append("    <td>FMS Private SSK</td>\n");
sb.append("    <td><input type=\"text\" name=\"fmsssk\" size=\"128\" value=\"%s\" /> </td>\n");
sb.append("  </tr>\n");
sb.append("  <tr>\n");
sb.append("    <td>FMS ID</td>\n");
sb.append("    <td><input type=\"text\" name=\"fmsid\" value=\"%s\" /> </td>\n");
sb.append("  </tr>\n");
sb.append("  <tr>\n");
sb.append("    <td>FMS Group</td>\n");
sb.append("    <td><input type=\"text\" name=\"fmsgroup\" value=\"%s\" /> </td>\n");
sb.append("  </tr>\n");
sb.append("  <tr>\n");
sb.append("    <td>Wiki Name</td>\n");
sb.append("    <td><input type=\"text\" name=\"wikiname\" value=\"%s\"/> </td>\n");
sb.append("  </tr>\n");
sb.append("  <tr>\n");
sb.append("    <td>Enable Images: <input type=\"checkbox\" name=\"images\" %s/></td>\n");
sb.append("  </tr>\n");
sb.append("  <input type=\"hidden\" name=\"formPassword\" value=\"%s\"/>\n");
sb.append("</table>\n");
sb.append("<input name=\"saved\" type=\"submit\" value=\"Save Changes\"/>\n");
sb.append("<input name=\"discarded\" type=\"submit\" value=\"Discard Changes\"/>\n");
sb.append("<input name=\"done\" type=\"submit\" value=\"Done\"/>\n");
sb.append("</form>\n");
sb.append("\n");
sb.append("</body>\n");
sb.append("</html>\n");

        return sb.toString();
    }
}

