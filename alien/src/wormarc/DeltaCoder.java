/* An abstract interface for a delta encoder / decoder.
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

import java.io.InputStream;
import java.io.IOException;

public interface DeltaCoder {
    // Make a new HistoryLink to store the incremental changes to a file.
    // parent is the LinkDigest for the latest link in the HistoryLink
    // chain representing the previous version. oldData is the previous file data.

    // oldData can be null. This forces a full reinsert.
    // The parent value is set in the returned link even when oldData is null.
    // This allows you to follow the history of shortened chains.
    //
    // disableCompress is currently unimplemented. IT MUST BE false.
    HistoryLink makeDelta(LinkDataFactory linkDataFactory,
                          LinkDigest parent,
                          InputStream oldData,
                          InputStream newData,
                          boolean disableCompression
                          ) throws IOException;


    // Reconstruct a file from a list of HistoryLinks.
    InputStream applyDeltas(Iterable<HistoryLink> history) throws IOException;
}
