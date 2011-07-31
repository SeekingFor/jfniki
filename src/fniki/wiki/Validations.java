/* Validation utility functions
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

public class Validations {
    // empty is allowed
    public static boolean isAlphaNumOrUnder(String value) {
        for (int index = 0; index < value.length(); index++) {
            char c  = value.charAt(index);
            if ((c >= '0' && c <= '9') ||
                (c >= 'a' && c <= 'z') ||
                (c >= 'A' && c <= 'Z') ||
                c == '_') {
                continue;
            }
            return false;
        }
        return true;
    }

    public static boolean isValidLocalLink(String value) {
        // Allow links to files stored in the jar.
        if (value.startsWith("static_files/")) {
            value = value.substring("static_files/".length());
        }

        for (int index = 0; index < value.length(); index++) {
            char c  = value.charAt(index);
            if ((c >= '0' && c <= '9') ||
                (c >= 'a' && c <= 'z') ||
                (c >= 'A' && c <= 'Z') ||
                c == '_') {
                continue;
            }
            return false;
        }
        return true;
    }

    // empty is allowed
    public static boolean isLowerCaseAlpha(String value) {
        for (int index = 0; index < value.length(); index++) {
            char c  = value.charAt(index);
            if (c >= 'a' && c <= 'z') {
                continue;
            }
            return false;
        }
        return true;
    }

    public static boolean isValidFreenetUri(String link) {
        // DCI: do much better!
        return (link.startsWith("freenet:CHK@") ||
                link.startsWith("freenet:SSK@") ||
                link.startsWith("freenet:USK@"));
    }

    // INTENT: I added this because I've seen freesites with '-' in the human readable name.
    //         I wanted people to be able to create theme names to match.
    public static boolean isValidThemeName(String value) {
        for (int index = 0; index < value.length(); index++) {
            char c  = value.charAt(index);
            if ((c >= '0' && c <= '9') ||
                (c >= 'a' && c <= 'z') ||
                (c >= 'A' && c <= 'Z') ||
                c == '_' ||
                c == '-') {
                continue;
            }
            return false;
        }
        return true;
    }


}
