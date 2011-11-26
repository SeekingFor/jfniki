/* A class to describe things that can be stored as Archive.RootObject's.
 *
 *  Copyright (C) 2010, 2011 Darrell Karbott
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.0 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 *  Author: djk@isFiaD04zgAgnrEC5XJt1i4IE7AkNPqhBG5bONi6Yks
 *
 *  This file was developed as component of
 * "fniki" (a wiki implementation running over Freenet).
 */

package wormarc;

import java.io.IOException;
import java.util.Set;
import java.util.HashSet;

// DCI: better name. RootObject, and pull that out of Archive?
public class RootObjectKind {
    public final static int ARCHIVE_MANIFEST = 1;
    public final static int FILE_MANIFEST = 2;
    // Use a manifest if there are more than few files.
    // Otherwise the root objects don't fit in a topkey.
    public final static int SINGLE_FILE = 3;
    public final static int PARENT_REFERENCES = 4;
    public final static int REBASE_REFERENCES = 5; // hmmm application specfic code creeping into wormarc.

    public static LinkContainer getContainer(Archive archive,
                                             Archive.RootObject obj) throws IOException {
        // Hmmmm... consider using polymorphism instead of switch if there are
        // many more cases.
        switch (obj.mKind) {
        case FILE_MANIFEST:
            return FileManifest.fromBytes(archive.getFile(obj.mDigest));

        // The Archive already adds the links for the files these are persisted in.
        case ARCHIVE_MANIFEST: // Drop through on purpose.
        case SINGLE_FILE:
        case PARENT_REFERENCES:
        default:
            return new LinkContainer() {
                public Set<LinkDigest> getReferencedLinks(HistoryLinkMap linkMap) {
                    return new HashSet<LinkDigest>();
                }
            };
        }
    }
}