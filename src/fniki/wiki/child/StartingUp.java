/* A UI subcomponent which redirects to the "Discover" page.
 *
 * Copyright (C) 2010, 2011, 2012 Darrell Karbott
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

import fniki.wiki.ArchiveManager;
import fniki.wiki.ChildContainer;
import fniki.wiki.ChildContainerException;
import fniki.wiki.ChildContainerResult;
import fniki.wiki.Configuration;
import fniki.wiki.WikiContext;

public class StartingUp implements ChildContainer {
    private final ArchiveManager mArchiveManager;
    public StartingUp(ArchiveManager archiveManager) {
        mArchiveManager = archiveManager;
    }

    public ChildContainerResult handle(WikiContext context) throws ChildContainerException {
        try {
            // Force validation exception.
            context.getConfiguration().validate();
            if (mArchiveManager.hasLoadedCurrentWiki()) {
                // action=confirm force read to start immediately.
                context.raiseRedirect(context.makeLink("/fniki/getversions") +
                                      "?action=confirm",
                                      "Redirecting...");
                // Don't get here because line above throws.
            }
        } catch (Configuration.ConfigurationException ce) {
            // Configuration not valid.
        }

        context.raiseRedirect(context.makeLink("/" + context.getString("default_page", "Front_Page")),
                              "Redirecting...");
        return null;
    }
}