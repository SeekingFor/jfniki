/* Utility class to extract identities from the FMS http interface.
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

package fniki.wiki;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;

public class FmsIdentityExtractor {
    private final static String EXPORT_PASSWORD_START = "<form name=\"frmexport\" method=\"POST\"><input type=\"hidden\" name=\"formpassword\"";
    private final static String EXPORT_PASSWORD_END = "><input type=\"hidden\"";

    public static final class FmsIdentity implements Comparable<FmsIdentity> {
        public final String mName;
        public final String mPrivateKey;
        public final String mPublicKey;

        FmsIdentity(String name,
                    String privateKey,
                    String publicKey) {
            mName = name;
            mPrivateKey = privateKey;
            mPublicKey = publicKey;
        }


        public int compareTo(FmsIdentity other) {
            int result = mName.compareToIgnoreCase(other.mName);
            if (result != 0) {
                return result;
            }
            result = mPublicKey.compareToIgnoreCase(other.mPublicKey);
            if (result != 0) {
                return result;
            }
            return mPrivateKey.compareToIgnoreCase(other.mPrivateKey);
        }
    }

    //http://127.0.0.1:8080/localidentities.htm
    private static URL getLocalIdentitiesUrl(String httpPrefix) throws IOException {
        if (httpPrefix == null) {
            throw new IllegalArgumentException("httpPrefix is null");
        }
        if (!httpPrefix.endsWith("/")) {
            throw new IllegalArgumentException("httpPrefix must end with '/'");
        }
        return new URL(httpPrefix + "localidentities.htm");
    }

    static String getFormPassword(String httpPrefix) throws IOException {
        URL localIdentitiesUrl = getLocalIdentitiesUrl(httpPrefix);
        URLConnection connection = localIdentitiesUrl.openConnection();

        BufferedReader in =
            new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String html = "";
        try {
            String inputLine = in.readLine();
            while (inputLine != null) {
                html += inputLine;
                inputLine = in.readLine();
            }
        } finally {
            in.close();
        }

        int startPos = html.indexOf(EXPORT_PASSWORD_START);
        if (startPos == -1) {
            throw new IOException("Couldn't find form password.");
        }
        startPos += EXPORT_PASSWORD_START.length();

        int endPos = html.indexOf(EXPORT_PASSWORD_END, startPos);
        if (endPos == -1) {
            throw new IOException("Couldn't find form password(1).");
        }
        String[] fields = html.substring(startPos, endPos).split("=");
        if (fields.length != 2) {
            throw new IOException("Couldn't find form password(2).");
        }
        return fields[1].replaceAll("\"", "");
    }

    // Read the export identities file from FMS.
    static public String getIdentitiesXml(String httpPrefix) throws IOException {

        // HACK. Assumes password doesn't need to be url encoded.
        // (which it doesn't)
        String postBody = "formaction=export&formpassword=" + getFormPassword(httpPrefix);

        URL localIdentitiesUrl = getLocalIdentitiesUrl(httpPrefix);
        URLConnection connection = localIdentitiesUrl.openConnection();

        connection.setDoOutput(true);

        OutputStreamWriter out = new OutputStreamWriter(connection.getOutputStream(), "UTF-8");
        try {
            out.write(postBody);
        } finally {
            out.close();
        }

        BufferedReader in =
            new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
        String xml = "";
        try {
            String inputLine = in.readLine();
            while (inputLine != null) {
                xml += inputLine + "\n"; // Normalize.
                inputLine = in.readLine();
            }
        } finally {
            in.close();
        }

        return xml;
    }

    private static String getTagValue(String sTag, Element eElement) {
        NodeList nlList = eElement.getElementsByTagName(sTag).item(0).getChildNodes();
        Node nValue = (Node) nlList.item(0);
        return nValue.getNodeValue();
    }

    // Parse indentities from and exported FMS xml identity file.
    public static List<FmsIdentity> parseXml(String fmsIdentitiesExportXml)
        throws IOException {
        try {
            byte[] bytes = fmsIdentitiesExportXml.getBytes("UTF-8");
            InputStream xmlStream = new ByteArrayInputStream(bytes);

            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(xmlStream);
            doc.getDocumentElement().normalize();

            NodeList nList = doc.getElementsByTagName("Identity");

            List<FmsIdentity> identities = new ArrayList<FmsIdentity>();
            for (int temp = 0; temp < nList.getLength(); temp++) {
                Node nNode = nList.item(temp);
                if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element eElement = (Element) nNode;
                    identities.
                        add(new FmsIdentity(getTagValue("Name", eElement),
                                            getTagValue("PrivateKey", eElement),
                                            getTagValue("PublicKey", eElement)));
                }
            }
            return identities;
        } catch (ParserConfigurationException pce) {
            throw new IOException("Couldn't set up the XML parser", pce);
        } catch (SAXException se) {
            throw new IOException("Error parsing xml", se);
        }
    }

    // Read identities. Time out up after timeoutMs milliseconds.
    // NOTE: timeoutMs == 0 means wait forever.
    static public List<FmsIdentity> readIdentities(String httpPrefix, int timeoutMs) throws IOException {
        BlockingRequester requester = new BlockingRequester(httpPrefix);
        Thread worker = new Thread(requester, "Background thread to read FMS identities");
        synchronized (requester) {
            try {
                worker.start();
                requester.waitForWorkerToStart();
                requester.wait(timeoutMs);
            } catch (InterruptedException ie) {
                // NOP. Will raise below.
            } finally {
                // Good enough, but not legit.  e.g. might not kill the bg thread
                // if it isn't blocked in an interruptable call.  In practice
                // it will be blocked in a URL read.
                worker.interrupt();
            }
        }
        return requester.getIdentities();
    }

    ////////////////////////////////////////////////////////////
    // Quick and dirty hack to get reliable socket timeouts.
    private final static class BlockingRequester implements Runnable {
        private final String mHttpPrefix;
        private List<FmsIdentity> mIdentities;
        private IOException mException = new IOException("Timed out.");
        private boolean mStarted = false;

        BlockingRequester(String prefix) {
            mHttpPrefix = prefix;
        }

        public void run() {
            try {
                synchronized (BlockingRequester.this) {
                    mStarted = true;
                    BlockingRequester.this.notifyAll();
                }
                String xml = FmsIdentityExtractor.getIdentitiesXml(mHttpPrefix);
                List<FmsIdentity> identities = FmsIdentityExtractor.parseXml(xml);

                synchronized (BlockingRequester.this) {
                    mIdentities = identities;
                    mException = null;
                }
            } catch (IOException ioe) {
                synchronized (BlockingRequester.this) {
                    mException = ioe;
                }
            } finally {
                synchronized (BlockingRequester.this) {
                    BlockingRequester.this.notifyAll();
                }
            }
        }

        // Prevent race condition where we try to interrupt before starting the thread.
        public synchronized void waitForWorkerToStart() throws InterruptedException {
            while (!mStarted) {
                synchronized (BlockingRequester.this) {
                    BlockingRequester.this.wait();
                }
            }
        }

        public synchronized List<FmsIdentity> getIdentities() throws IOException {
            if (mException != null) {
                throw mException;
            }
            return mIdentities;
        }
    }

    // public final static void main(String[] argv) throws IOException {
    //     // Blocks for an unbounded time.
    //     // String xml = getIdentitiesXml("http://127.0.0.1:8080/");
    //     // List<FmsIdentity> identities = parseXml(xml);
    //     List<FmsIdentity> identities = readIdentities("http://127.0.0.1:8080/", 15000);
    //     for (FmsIdentity identity : identities) {
    //         System.out.println("");
    //         System.out.println("Name      : " + identity.mName);
    //         System.out.println("PublicKey : " + identity.mPublicKey);
    //         System.out.println("PrivateKey: " + identity.mPrivateKey);
    //     }
    // }
}

