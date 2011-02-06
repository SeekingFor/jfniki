/* Base class for writing Query implmentations for a specific HTTP framework.
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

package fniki.wiki;

import java.io.IOException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public abstract class QueryBase implements Query {
    protected Map<String, String> mParamTable = new HashMap<String, String>();

    // MUST contain every parameter used by any ChildContainer.
    protected final static String PARAMS[] = new String[] {
        "action",
        "uri", "goto",
        "savepage", "savetext",
        "formPassword",
        // Configuration stuff.
        "defaults", "done", "import", "export", "upload",
        "fcphost", "fcpport", "fpprefix", "fmshost", "fmsport",
        "fmsssk", "fmsid", "fmsgroup", "wikiname", "images",
    };

    protected static Set<String> paramsSet() {
        return new HashSet(Arrays.asList(PARAMS));
    }

    public boolean containsKey(String paramName) {
        return mParamTable.containsKey(paramName);
    }

    public String get(String paramName) {
        return mParamTable.get(paramName);
    }

    // Subclass should define this and call it once after construction.
    public abstract void readParams() throws IOException;
}
