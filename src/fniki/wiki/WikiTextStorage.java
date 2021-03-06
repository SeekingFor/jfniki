/* Interface for the wiki's backing store.
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
import java.util.List;

public interface WikiTextStorage {
    boolean hasPage(String name) throws IOException;
    String getPage(String name) throws IOException;
    void putPage(String name, String text) throws IOException;
    List<String> getNames() throws IOException;
    void deletePage(String name) throws IOException;

    // DCI: local changes stuff corrupting original design
    boolean hasLocalChange(String name);
    boolean wasLocallyDeleted(String name);
    // Reverting changes on a page that has no changes is allowed.
    void revertLocalChange(String name);

    boolean hasUnmodifiedPage(String name) throws IOException;
    String  getUnmodifiedPage(String name) throws IOException;
}