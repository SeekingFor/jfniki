/* Customization information for a wiki freesite.
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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import wormarc.IOUtil;

// LATER: Revisit serialization HACKs?
public class SiteTheme implements Serializable {
    static final long serialVersionUID = 8369808699611171904L;
    // Historical Note: These used to be public final, but
    // I had to make mStaticFiles transient to get the
    // serialization to work, which precludes it from
    // being final (to the best of my knowledge).
    private String mTemplate;
    private String mDefaultPage;
    private transient Iterable<FileInfo> mStaticFiles;

    public String getTemplate() { return mTemplate; }
    public String getDefaultPage() { return mDefaultPage; }
    public Iterable<FileInfo> getStaticFiles() { return mStaticFiles; }

    public SiteTheme(String template, String defaultPage, Iterable<FileInfo> staticFiles) {
        mTemplate = template;
        mDefaultPage = defaultPage;
        mStaticFiles = staticFiles;
    }

    // Read a site them from a zip file stream.
    //
    // Format:
    // theme/config/template.html -- the html template
    // theme/config/default_page -- a flat ascii file containing a single line with the default page name.
    // theme/static/  -- a directory containing zero or more static files to insert in addition to the exported wiki html.
    public static SiteTheme fromZipStream(InputStream data) throws IOException {
        ZipInputStream zin = new ZipInputStream(data);
        try {
            String template = null;
            String defaultPage = null;
            List<FileInfo> staticFiles = new ArrayList<FileInfo>();
            while (true) {
                ZipEntry entry = zin.getNextEntry();
                if (entry == null) {
                    break;
                }

                String name = entry.getName();

                if (entry.isDirectory()) {
                    // Only care about files.
                    zin.closeEntry();
                    continue;
                }

                // Read a single file from the zip.
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                inner_while: while (true) {
                    // Really read one byte at a time. These files should be small.
                    // Revisit later if this is a problem.
                    int byteValue = zin.read();
                    if (byteValue == -1) {
                        buffer.flush(); // Cargo cult?
                        if (name.equals("theme/config/template.html")) {
                            template = new String(buffer.toByteArray(), IOUtil.UTF8);
                        } else if (name.equals("theme/config/default_page.txt")) {
                            defaultPage = new String(buffer.toByteArray(), IOUtil.UTF8).trim();
                        } else if (name.startsWith("theme/static/")) {
                            String fileName = name.substring("theme/static/".length()).trim();
                            if (fileName.length() < 1) {
                                throw new IOException("Unexpected entry in theme zip file: " + name);
                            }
                            staticFiles.add(FcpTools.makeFileInfo(fileName, buffer.toByteArray(), null));
                        } else {
                            throw new IOException("Unexpected entry in theme zip file: " + name);
                        }
                        break inner_while;
                    }
                    buffer.write(byteValue);
                } // inner while
                zin.closeEntry();
            } // outer while
            if (template == null) {
                throw new IOException("Theme zip file has no theme/config/template.html entry.");
            }
            if (template == null) {
                throw new IOException("Theme zip file has no theme/config/default_page entry.");
            }
            return new SiteTheme(template, defaultPage, staticFiles);
        } finally {
            if (zin != null) {
                zin.close();
            }
        }
    }
    public void debugDump(String msg) {
        System.err.println("--- SiteTheme: " + msg + " ---");
        System.err.println("mTemplate: " + mTemplate);
        System.err.println("mDefaultPage: " + mDefaultPage);
        System.err.println("FileInfos:");
        for (FileInfo info : mStaticFiles) {
            System.err.println("   name: " + info.getName());
            System.err.println("   mimeType: " + info.getMimeType());
            System.err.println("   length: " + info.getLength());
            try {
                System.err.println("   SHA1: " + IOUtil.getFileDigest(info.getInputStream()));
            } catch (IOException ioe) {
                System.err.println("   SHA1: EPIC FAIL! IOException");
            }
        }
        System.err.println("---");
    }

    // Horrible HACK to make serialization work.  I'm not proud...
    // Save FileInfos as RAMFileInfos, LOSING the original type.
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        ArrayList<RAMFileInfo> list = new ArrayList<RAMFileInfo>();
        for (FileInfo info : mStaticFiles) {
            list.add(new RAMFileInfo(info));
        }
        out.writeObject(list);
    }

    private void readObject(ObjectInputStream in) throws IOException {
        try {
            in.defaultReadObject();
            // Dunno how to "downcast" generic lists.
            // If this is obvious to some java hacker out there,
            // please school me.
            List<FileInfo> list = new ArrayList<FileInfo>();

            for (RAMFileInfo info : (List<RAMFileInfo>)in.readObject()) {
                list.add(info);
            }
            mStaticFiles = list; // List implements Iterable.
        } catch (ClassNotFoundException cnfe) {
            throw new IOException(cnfe);
        }
    }
}


