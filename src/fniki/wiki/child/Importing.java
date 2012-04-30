/* A UI subcomponent which imports a wiki from a file.
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
import java.io.PrintStream;

import fniki.wiki.ArchiveManager;
import fniki.wiki.ChildContainer;
import fniki.wiki.ChildContainerException;
import fniki.wiki.ChildContainerResult;
import fniki.wiki.ServerErrorException;
import fniki.wiki.WikiContext;

public class Importing implements ChildContainer {
    private final ArchiveManager mArchiveManager;
    public Importing(ArchiveManager archiveManager) {
        mArchiveManager = archiveManager;
    }

    public ChildContainerResult handle(WikiContext context) throws ChildContainerException {
        try {
            if (!context.getAction().toLowerCase().equals("sentfile")) {
                context.raiseAccessDenied("Illegal parameters");
            }

            byte[] blob = context.getQuery().getBytes("upload");
            if (blob == null) {
                context.raiseAccessDenied("Illegal parameters");
            }

            boolean secondary = false;
            if (context.getQuery().get("secondary") != null) {
                secondary = context.getQuery().get("secondary").toLowerCase().equals("true");
            }
            String[] metaData = mArchiveManager.loadArchiveFromBlob(blob, secondary);
            String group = metaData[1];
            String name = metaData[2];
            if (!(group.equals("")) || name.equals("")) {
                //System.err.println("Set group and wiki name from imported archive:");
                //System.err.println(group + ":" + name);
                mArchiveManager.setFmsGroup(group);
                mArchiveManager.setBissName(name);
            }

            context.
                raiseRedirect(context.makeLink("/" +
                                               context.getString("default_page",
                                                                 "Front_Page")),
                              "Redirecting...");
        } catch (IOException ioe)  {
            // DCI: Later, do better.
            throw new ServerErrorException("Unexpected error calling mArchiveManager.loadArchiveFromBlob()");
        }
        return null; // Ureachable
    }
}