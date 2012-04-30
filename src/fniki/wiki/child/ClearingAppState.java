/* A UI subcomponent which clears the stored app state.
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

import static ys.wikiparser.Utils.*;

import java.io.IOException;
import java.io.PrintStream;

import fniki.wiki.ChildContainer;
import fniki.wiki.ChildContainerException;
import fniki.wiki.ChildContainerResult;
import fniki.wiki.HtmlResultFactory;
import fniki.wiki.WikiContext;

public class ClearingAppState implements ChildContainer {
    public ChildContainerResult handle(WikiContext context) throws ChildContainerException {
        String title = "Clearing stored app state.";
        String msg = "Failed. Note that this is not implemented for the stand alone app.";
        try {
            // Sleazy but good enough. Blocking call.
            context.clearStoredAppState();
            msg = "Succeeded. You will need to unload and reload " +
                "the plugin to see any effect.";
        } catch (IOException ioe) {
            context.logError("WikiContext.clearStoredAppState() raised", ioe);
        }

        String href = context.makeLink("/" +
                                       context.getString("default_page",
                                                         "Front_Page"));
        return HtmlResultFactory.
            makeResult(title,
                       context.
                       fillInTemplate("message.html",
                                      escapeHTML(title),
                                      escapeHTML(msg),
                                      href,
                                      escapeHTML("Continue")),
                       context.isCreatingOuterHtml());
    }
}