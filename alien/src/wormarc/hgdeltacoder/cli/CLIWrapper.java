/* An ancient file used during the initial testing of the java bdiff and mpatch code.
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

package wormarc.hgdeltacoder.cli;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.ArrayList;

import wormarc.IOUtil;
import wormarc.hgdeltacoder.ported.BDiff;
import wormarc.hgdeltacoder.ported.MDiff;

// Not really meant for public consumption.
// I wrote this so that I could run my python unit tests against
// the Java implementation.
public class CLIWrapper {
    // Skips the first and last file.
    final static List<byte[]> readInputFiles(String[] nameList) throws IOException{
        ArrayList<byte[]> byteBlocks = new ArrayList<byte[]>();
        for (int index = 0; index < nameList.length - 2; index++) {
            byteBlocks.add(IOUtil.readFully(nameList[index + 1]));
        }
        return byteBlocks;
    }

    final static String[] shiftArgsLeft(String[] args) {
        String[] shifted = new String[args.length - 1];
        System.arraycopy(args, 1, shifted, 0, args.length -1);
        return shifted;
    }

    public final static void main(String[] args) {
        try {
            // diff old_file new_file out_file
            if (args[0].equals("diff")) {
                args = shiftArgsLeft(args);
                IOUtil.writeFully(BDiff.bdiff(IOUtil.readFully(args[0]),
                                              IOUtil.readFully(args[1])),
                                  args[2]);
            // patch orig_file patch0 patch1 ... patchn out_file
            } else if (args[0].equals("patch")) {
                args = shiftArgsLeft(args);
                ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
                MDiff.patches(IOUtil.readFully(args[0]), readInputFiles(args), bytesOut);
                IOUtil.writeFully(bytesOut.toByteArray(),
                                  args[args.length -1]);
            } else {
                throw new IllegalArgumentException("Only 'diff' and 'patch' are supported.");
            }
            System.exit(0);
            //System.out.println("SUCCEEDED");
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
            //System.out.println("FAILED");
        }
    }
}
