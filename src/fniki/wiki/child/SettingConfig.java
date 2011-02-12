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

import java.io.UnsupportedEncodingException;
import static ys.wikiparser.Utils.*;

import fniki.wiki.ArchiveManager;
import fniki.wiki.ChildContainerException;
import fniki.wiki.Configuration;
import static fniki.wiki.HtmlUtils.*;
import fniki.wiki.ModalContainer;
import fniki.wiki.Query;
import fniki.wiki.WikiContext;

public class SettingConfig implements ModalContainer {
    private final static String UTF8 = "UTF-8"; // DCI: import?

    // Used by export.
    private final static String CONFIG_NAME = "jfniki.cfg";
    private final static String CONFIG_TYPE = "application/octet-stream";

    private boolean mFinished = false;
    private Configuration mConfig;
    private String mPrivateSSK = "";
    private String mPublicFmsId = "???";
    private String mMsg = "";

    // Doesn't validate.
    private Configuration parseConfigFromPost(WikiContext context, Query query,
                                              int listenPort, String oldSSK)  {
        boolean allowImages = false;
        if (query.containsKey("images")) {
            allowImages = true;
        }
        int fcpPort = -1;
        int fmsPort = -1;
        try {
            fcpPort = Integer.parseInt(query.get("fcpport"));
        } catch (NumberFormatException nfe) {
            // NOP
        }

        try {
            fmsPort = Integer.parseInt(query.get("fmsport"));
        } catch (NumberFormatException nfe) {
            // NOP
        }

        String newSSK = query.get("fmsssk");
        if (newSSK == null || newSSK.equals("")) {
            newSSK = oldSSK;
        } else {
            mPublicFmsId = context.getPublicFmsId(query.get("fmsid"), mPrivateSSK);
        }

        Configuration config = new Configuration(listenPort,
                                                 query.get("fcphost"),
                                                 fcpPort,
                                                 query.get("fpprefix"),
                                                 allowImages,
                                                 query.get("fmshost"),
                                                 fmsPort,
                                                 query.get("fmsid"),
                                                 newSSK,
                                                 query.get("fmsgroup"),
                                                 query.get("wikiname"));
        return config;
    }

    private void handlePost(WikiContext context) throws ChildContainerException {
        Query query = context.getQuery();

        if (query.containsKey("export")) {
            // Pop a save-as dialog for configuration.
            try {
                // Save any changes the user made.
                mConfig = parseConfigFromPost(context, query, context.getInt("listen_port", 8083), mPrivateSSK);
                mConfig.validate();

                // Force browser to save the config file to disk.
                context.raiseDownload(mConfig.toStringRep().getBytes(UTF8),
                                      CONFIG_NAME, CONFIG_TYPE);
            } catch (Configuration.ConfigurationException cfe) {
                mMsg = "Refused to export: " + cfe.getMessage();
                return;
            } catch (UnsupportedEncodingException uee) {
                mMsg = "Export failed: " + uee.getMessage();
                return;
            }
        }

        if (query.containsKey("import")) {
            // Read imported config.
            if (!query.containsKey("upload")) {
                mMsg = "Set the file you want to import from!";
                return;
            }

            try {
                Configuration config = Configuration.fromStringRep(query.get("upload"));
                config.validate();
                mConfig = config;
                mPrivateSSK = mConfig.mFmsSsk;
                mPublicFmsId = context.getPublicFmsId(mConfig.mFmsId, mConfig.mFmsSsk);
                mMsg = "Imported configuration!";
                return;
            } catch (Configuration.ConfigurationException cfe) {
                mMsg = "Refused to import: " + cfe.getMessage();
                return;
            }
        }

        if (query.containsKey("defaults")) {
            mConfig = context.getDefaultConfiguration();
            try {
                mConfig.validate();
                mMsg = "Reset to default values."; // Won't see this because fms config not set.
            } catch (Configuration.ConfigurationException cfe) {
                // Handle invalid parameters;
                mMsg = cfe.getMessage();
                return;
            }
            return;
        }

        if (!query.containsKey("done")) {
            return;
        }

        mConfig = parseConfigFromPost(context, query, context.getInt("listen_port", 8083), mPrivateSSK);
        
        try {
            mConfig.validate();
            context.setConfiguration(mConfig);
            mMsg = "Saved configuration changes.";
        } catch (Configuration.ConfigurationException cfe) {
            // Handle invalid parameters;
            mMsg = cfe.getMessage();
            return;
        }

        if (query.containsKey("done")) {
            // Causes the routing logic in WikiApp.routeRequest to transition
            // out of this modal ui state.
            String redirectHref = makeHref(context.makeLink("/fniki/config"),
                                           "finished", null, null, null);
            context.raiseRedirect(redirectHref, "Redirecting...");
        }
    }

    // DCI: back over this. escape  quotes.
    public String handle(WikiContext context) throws ChildContainerException {
        handlePost(context);

        String href = makeHref(context.makeLink("/fniki/config"),
                               null, null, null, null);

        return String.format(formTemplate(),
                             getMsgHtml(),
                             href,
                             mConfig.mFcpHost,
                             mConfig.mFcpPort,
                             mConfig.mFproxyPrefix,
                             mConfig.mFmsHost,
                             mConfig.mFmsPort,
                             mConfig.mFmsId,
                             mPublicFmsId,
                             mConfig.mFmsGroup,
                             mConfig.mWikiName,
                             mConfig.mAllowImages ? "checked" : "",
                             // IMPORTANT: Won't work as a plugin without this.
                             context.getString("form_password", "FORM_PASSWORD_NOT_SET"));
    }

    public String getMsgHtml() {
        if (mMsg == null || mMsg.trim().equals("")) {
            return "";
        }
        return String.format("<hr>%s<hr>\n", escapeHTML(mMsg));
    }

    public boolean isFinished() {return mFinished; }
    public void cancel() { mFinished = true; }
    public void entered(WikiContext context) {
        mFinished = false;
        mConfig = context.getConfiguration();
        mPrivateSSK = mConfig.mFmsSsk;
        mPublicFmsId = context.getPublicFmsId(mConfig.mFmsId, mConfig.mFmsSsk);
        mMsg = "";
    }

    public void exited() {}

    //////////////////////////////////////////////////

    // All fields used by the template must be in QueryBase.PARAMS:
    // "fcphost", "fcpport", "fpprefix", "fmshost", "fmsport",
    // "fmsssk", "fmsid", "wikiname", "images", "fmsbase", "formPassword", "defaults", "done"

    // READ the comment above before modifiy the template!
    private static String formTemplate() {
        StringBuilder sb = new StringBuilder();
        sb.append("<html>\n");
        sb.append("<head>\n");
        sb.append("<title>\n");
        sb.append("  Configuration\n");
        sb.append("</title>\n");
        sb.append("</head>\n");
        sb.append("<body>\n");
        sb.append("%s\n");
        sb.append("<h1>Configuration</h1>\n");
        sb.append("<form method=\"post\" action=\"%s\" enctype=\"multipart/form-data\" accept-charset=\"UTF-8\">\n");
        sb.append("<table>\n");
        sb.append("  <tr>\n");
        sb.append("    <td>Fcp Host</td>\n");
        sb.append("    <td><input type=\"text\" name=\"fcphost\" value=\"%s\" /></td>\n");
        sb.append("  </tr>\n");
        sb.append("  <tr>\n");
        sb.append("    <td>Fcp Port</td>\n");
        sb.append("    <td><input type=\"text\" name=\"fcpport\" value=\"%d\" /></td>\n");
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
        sb.append("    <td><input type=\"text\" name=\"fmsport\" value=\"%d\" /> </td>\n");
        sb.append("  </tr>\n");
        sb.append("  <tr>\n");
        sb.append("    <td>FMS Private SSK</td>\n");
        sb.append("    <td><input type=\"text\" name=\"fmsssk\" size=\"128\" value=\"\" /> </td>\n");
        sb.append("  </tr>\n");
        sb.append("  <tr>\n");
        sb.append("    <td>FMS name</td>\n");
        sb.append("    <td><input type=\"text\" name=\"fmsid\" value=\"%s\" /> </td>\n");
        sb.append("  </tr>\n");
        sb.append("  <tr>\n");
        sb.append("    <td>Full FMS ID</td>\n");
        sb.append("    <td><input type=\"text\" name=\"fmsfull\" size=\"64\" readonly=\"readonly\" value=\"%s\" /> </td>\n");
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
        sb.append("    <td><input type=\"checkbox\" name=\"images\" %s/>Enable Images</td>\n");
        sb.append("  </tr>\n");
        sb.append("  <input type=\"hidden\" name=\"formPassword\" value=\"%s\"/>\n");
        sb.append("</table>\n");
        sb.append("<input name=\"defaults\" type=\"submit\" value=\"Reset Defaults\"/>\n");
        sb.append("<input name=\"done\" type=\"submit\" value=\"Done\"/>\n");
        sb.append("<hr>\n");
        sb.append("<input name=\"import\" type=\"submit\" value=\"Import Configuration\"/>\n");
        sb.append("<input type=\"file\" name=\"upload\" size=\"64\">\n");
        sb.append("<hr>\n");
        sb.append("<input name=\"export\" type=\"submit\" value=\"Export Configuration\"/>\n");
        sb.append("<hr>\n");
        sb.append("</form>\n");
        sb.append("\n");
        sb.append("</body>\n");
        sb.append("</html>\n");

        return sb.toString();
    }
}

// DCI: include form  html and script in comments.
