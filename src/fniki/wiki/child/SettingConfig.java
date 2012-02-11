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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static ys.wikiparser.Utils.*;

import wormarc.IOUtil;

import fniki.wiki.ChildContainerException;
import fniki.wiki.ChildContainerResult;
import fniki.wiki.Configuration;
import fniki.wiki.FmsIdentityExtractor;
import fniki.wiki.HtmlResultFactory;
import static fniki.wiki.HtmlUtils.*;
import fniki.wiki.ModalContainer;
import fniki.wiki.Query;
import fniki.wiki.WikiContext;

public class SettingConfig implements ModalContainer {
    private final static String UTF8 = "UTF-8"; // DCI: import?

    // Used by export.
    private final static String CONFIG_NAME = "jfniki.cfg";
    private final static String CONFIG_TYPE = "application/octet-stream";

    // Time to wait before giving up on reading exported identity
    // xml from FMS.
    private final static int FMS_READ_TIMEOUT_MS = 10 * 1000;

    private final static String DEFAULT_FMS_PREFIX = "http://127.0.0.1:8080/";

    private boolean mFinished = false;
    private Configuration mConfig;

    // State which is stored outside of the Configuration.
    private String mFmsPrefix = DEFAULT_FMS_PREFIX; // Only used by fms read "Wizard"
    private String mPrivateSSK = ""; // Never want to display this value. Possibly set by fmsread

    // Derived from the private key.
    private String mPublicFmsId = "???";
    // Keep track of these so we know if we need to update
    // mPublicFmsId.
    private String mPreviousSSK = "";
    private String mPreviousFmsId = "";

    // Identities extracted from FMS.
    private List<FmsIdentityExtractor.FmsIdentity> mFmsIdentities = new ArrayList<FmsIdentityExtractor.FmsIdentity>();

    // Transient message which appears at the top of the screen.
    private String mMsg = "";

    // Doesn't validate.
    // Only parses values out of post params. Other stateful values. e.g. fmsId, private SSK
    // are passed in.
    // Static because it should be *stateless* i.e. just parses.
    private static Configuration parseConfigFromPost(WikiContext context, Query query,
                                                     int listenPort,
                                                     String oldSSK)  {

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
            // Hack to handle the fact that we don't display the
            // private key
            newSSK = oldSSK;
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

    private static void throwRedirectException(WikiContext context) throws ChildContainerException {
        context.raiseRedirect(context.getPath(), "Re-render form data...");
    }

    private void updatePublicFmsId(WikiContext context) {
        if (mPreviousSSK.equals(mConfig.mFmsSsk) && mPreviousFmsId.equals(mConfig.mFmsId)) {
            return;
        }
        mPublicFmsId = context.getPublicFmsId(mConfig.mFmsId, mConfig.mFmsSsk);
        mPreviousSSK = mConfig.mFmsSsk;
        mPreviousFmsId = mConfig.mFmsId;
    }

    // This doesn't try to render changes.  It just redirects back to itself.
    private void handleFmsIdentityPosts(WikiContext context) throws ChildContainerException {
        Query query = context.getQuery();

        // Save the state of the FMS prefix. It's not stored in the config.
        if (query.containsKey("fmsprefix")) {
            mFmsPrefix = query.get("fmsprefix");
        }

        if (!(query.containsKey("fmsread") || query.containsKey("fmsuse"))) {
            // Nothing to do.
            return;
        }

        if (query.containsKey("fmsread")) {
            if (!query.containsKey("fmsprefix")) {
                mMsg = "Set the FMS HTTP prefix!";
                throwRedirectException(context);
            }
            try {
                mMsg = "Tried to read FMS identities from " + query.get("fmsprefix") +
                " but failed!";

                String prefix = query.get("fmsprefix").trim();
                if (!prefix.endsWith("/")) {
                    prefix = prefix + "/";
                }

                mFmsIdentities = FmsIdentityExtractor.readIdentities(prefix,
                                                                     FMS_READ_TIMEOUT_MS);
                // Nice order for list box.
                Collections.sort(mFmsIdentities);

                if (mFmsIdentities.size() == 0) {
                    mMsg = "Read succeeded, but there were no identities!";
                }
                mMsg = "";
            } catch (IOException ioe) {
                // No useful information to report.
            }
        } else if (query.containsKey("fmsuse")) {
            if (!query.containsKey("fmsident")) {
                mMsg = "No identity selected!";
                throwRedirectException(context);
            }
            int index = -1;
            try {
                index = Integer.parseInt(query.get("fmsident"));
            } catch (NumberFormatException nfe) {
                // NOP
            }
            if (index < 0 || index  >= mFmsIdentities.size()) {
                mMsg = "Unknown identity selected!";
                throwRedirectException(context);
            }
            FmsIdentityExtractor.FmsIdentity identity = mFmsIdentities.get(index);

            // First, parse any other changes they made to the form.
            Configuration config =
                parseConfigFromPost(context, query,
                                    context.getInt("listen_port", 8083),
                                    identity.mPrivateKey);

            mConfig = new Configuration(config.mListenPort,
                                        config.mFcpHost,
                                        config.mFcpPort,
                                        config.mFproxyPrefix,
                                        config.mAllowImages,
                                        config.mFmsHost,
                                        config.mFmsPort,
                                        identity.mName,
                                        identity.mPrivateKey,
                                        config.mFmsGroup,
                                        config.mWikiName);

            mPrivateSSK = identity.mPrivateKey;
            updatePublicFmsId(context);
        }
        throwRedirectException(context);
    }

    private void handlePost(WikiContext context) throws ChildContainerException {
        handleFmsIdentityPosts(context);

        Query query = context.getQuery();

        if (query.containsKey("export")) {
            // Pop a save-as dialog for configuration.
            try {
                // Double check the form password before allowing export.
                context.checkFormPassword();
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
                mPreviousSSK = "";
                mPreviousFmsId = "";
                updatePublicFmsId(context);
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
                mFinished = false;
                mPrivateSSK = "";
                mPublicFmsId = "???";
                mMsg = "";
                mFmsIdentities = new ArrayList<FmsIdentityExtractor.FmsIdentity>();
                mFmsPrefix = DEFAULT_FMS_PREFIX;
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
                                           "finished", null, null, null, null);
            context.raiseRedirect(redirectHref, "Redirecting...");
        }
    }

    public ChildContainerResult handle(WikiContext context) throws ChildContainerException {
        handlePost(context);

        String href = makeHref(context.makeLink("/fniki/config"),
                               null, null, null, null, null);

        // Html escape CDATA
        // http://www.w3.org/TR/html401/types.html#type-cdata
        String html = String.format(formTemplate(),
                             getMsgHtml(), // Already escaped
                             href, // Not escaped
                             escapeHTML(mConfig.mFcpHost),
                             mConfig.mFcpPort, // Integer
                             escapeHTML(mConfig.mFproxyPrefix),
                             escapeHTML(mConfig.mFmsHost),
                             mConfig.mFmsPort, // Integer
                             escapeHTML(mConfig.mFmsId),
                             escapeHTML(mPublicFmsId),
                             escapeHTML(mConfig.mFmsGroup),
                             escapeHTML(mConfig.mWikiName),
                             mConfig.mAllowImages ? "checked" : "", // Not escaped
                             getFmsIdentityHtml(),
                             // IMPORTANT: Won't work as a plugin without this.
                             context.getString("form_password", "FORM_PASSWORD_NOT_SET"), // Not escaped
                             mFmsPrefix
                             );

        // LATER: fix hard coded title
        return HtmlResultFactory.makeResult("Configuration", html, context.isCreatingOuterHtml());
    }

    public String getMsgHtml() {
        if (mMsg == null || mMsg.trim().equals("")) {
            return "";
        }
        return String.format("<hr>%s<hr>\n", escapeHTML(mMsg));
    }

    // LATER: Fix. Complicates changing UI. i.e.
    // can't do it in the template.
    //
    // Choice list of available identities.
    public String getFmsIdentityHtml() {
        if (mFmsIdentities.size() == 0) {
            // Nothing to show.
            return "<td><em>Click the button above to read identities from FMS.</em></td>";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<td>\n");
        sb.append("<select name=\"fmsident\">\n");
        int index = 0;
        for (FmsIdentityExtractor.FmsIdentity identity: mFmsIdentities) {
            sb.append("<option value=\"" + index +"\">");
            sb.append(escapeHTML(identity.mName));
            sb.append("</option>\n");
            index++;
        }
        sb.append("</select>\n");
        sb.append("</td>\n");
        sb.append("<td>\n");
        sb.append("<input name=\"fmsuse\" type=\"submit\" value=\"Use Selected Identity!\"/>\n");
        sb.append("</td>\n");

        return sb.toString();
    }

    public boolean isFinished() {return mFinished; }
    public void cancel() { mFinished = true; }
    public void entered(WikiContext context) {
        mFinished = false;
        mConfig = context.getConfiguration();
        mPrivateSSK = mConfig.mFmsSsk;
        mMsg = "";
        mFmsIdentities = new ArrayList<FmsIdentityExtractor.FmsIdentity>();
        mPreviousSSK = "";
        mPreviousFmsId = "";
        updatePublicFmsId(context);
    }

    public void exited() {}

    //////////////////////////////////////////////////

    // All fields used by the template must be in QueryBase.PARAMS:
    // "fcphost", "fcpport", "fpprefix", "fmshost", "fmsport",
    // "fmsssk", "fmsid", "wikiname", "images", "fmsbase", "formPassword", "defaults", "done"

    // READ the comment above before modifing the template!
    private static String formTemplate() {
        try {
            return IOUtil.readUtf8StringAndClose(SettingConfig.class.getResourceAsStream("/config_form.html"));
        } catch (IOException ioe) {
            return "Couldn't load template from jar???";
        }
    }
}

