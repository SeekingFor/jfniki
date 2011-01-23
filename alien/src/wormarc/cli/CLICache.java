/* An implementation helper class for the CLI client.
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

package wormarc.cli;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import wormarc.Archive;
import wormarc.ArchiveResolver;
import wormarc.IOUtil;
import wormarc.FileManifest;
import wormarc.ExternalRefs;
import wormarc.io.ArchiveCache;
import wormarc.io.DirectoryIO;

class CLICache extends ArchiveCache implements ArchiveResolver {
    public CLICache(String directory) throws IOException {
        super(directory);
    }

    protected static String namePart(String name) {
        int pos = name.lastIndexOf(".");
        if (pos == -1) {
            return name;
        }

        if (pos > 0 && pos < name.length() - 1) {
            return name.substring(0, pos);
        }
        throw new IllegalArgumentException("Couldn't parse <name><dot><ordinal> version name.");
    }

    protected static int ordinalPart(String name) {
        int pos = name.lastIndexOf(".");
        if (pos > 0 && pos < name.length() - 1) {
            try {
                return Integer.parseInt(name.substring(pos + 1));
            } catch (NumberFormatException nfe) {
            }
        }
        throw new IllegalArgumentException("Couldn't parse <name><dot><ordinal> version name.");
    }

    // Override
    public void setName(String name) {
        if (name.equals(namePart(name))) {
            name = name + ".0";
        }

        // Catch illegal values.
        namePart(name);
        ordinalPart(name);

        super.setName(name);
    }

    public void bumpOrdinal() {
        if (getName() == null) {
            throw new IllegalStateException("Name not set.");
        }
        String name = namePart(getName());
        int ordinal = ordinalPart(getName());
        while ((new File(mDirectory, String.format("%s.%d", name, ordinal))).exists()) {
            ordinal++;
        }
        setName(String.format("%s.%d", name, ordinal));
    }

    public File headFile() throws IOException {
        return new File(mDirectory, readHead());
    }

    public boolean headFileExists() {
        try {
            return headFile().exists();
        } catch (IOException ioe) {
            return false;
        }
    }

    public String cloneHead(String name) throws IOException {
        if (namePart(name).equals(name)) {
            name = name + ".0";
        }

        File copy = new File(mDirectory, name);
        if (copy.exists()) {
            throw new IOException("Branch already exists.");
        }

        IOUtil.copyAndClose(new FileInputStream(headFile()),
                            new FileOutputStream(copy));

        return name;
    }


    public void saveValue(String key, String value) throws IOException {
        IOUtil.copyAndClose(new ByteArrayInputStream(value.getBytes(IOUtil.UTF8)),
                            new FileOutputStream(new File(mDirectory, key)));
    }

    public String readValue(String key) throws IOException {
        return IOUtil.readUtf8StringAndClose(new FileInputStream(new File(mDirectory, key)));
    }

    public void deleteValue(String key) throws IOException {
        (new File(mDirectory, key)).delete();
    }

    public void saveHead(String name) throws IOException { saveValue("head", name); }
    public String readHead() throws IOException { return readValue("head"); }


    public FileManifest.IO getManifestIO() throws IOException {
        DirectoryIO dirIO = new DirectoryIO(mDirectory.getParent());
        dirIO.ignore(".wormarc"); // DCI: really hard code?
        dirIO.ignore(".hg");
        dirIO.ignore(".git");
        dirIO.ignore(".svn");
        return dirIO;
    }

    public String toString() {
        return mDirectory.toString();
    }

    // DCI: errr. what if someone sets the name?
    // Hack to test auditing.
    public ExternalRefs.Reference getHeadRef() {
        if (!headFileExists()) {
            return ExternalRefs.CURRENT_ARCHIVE;
        }
        return new ExternalRefs.Reference(ExternalRefs.KIND_LOCAL, getName());
    }


    // DCI: get rid of this local resolver impl now that we have Freenet IO?
    public Archive resolve(ExternalRefs.Reference fromReference) throws IOException {
        if (fromReference.mKind != ExternalRefs.KIND_LOCAL) {
            throw new FileNotFoundException("Only KIND_LOCAL references are supported.");
        }
        String previousName = getName();
        try {
            setName(fromReference.mExternalKey);
            return Archive.load(this);
        } finally {
            if (previousName != null) {
                setName(previousName);
            }
        }
    }

    // somename_archivename.0 -> return somename
    public String getNym(ExternalRefs.Reference fromReference) throws IOException {
        if (fromReference.mExternalKey.equals(ExternalRefs.CURRENT_ARCHIVE.mExternalKey)) {
            return "CURRENT_ARCHIVE"; // HACK on top of a hack.
        }

        return fromReference.mExternalKey;

        // int pos = fromReference.mExternalKey.indexOf("_");
        // if (pos == -1 || pos == fromReference.mExternalKey.length() - 1) {
        //     return "UNKNOWN";
        // }

        // return fromReference.mExternalKey.substring(0, pos);
    }
}
