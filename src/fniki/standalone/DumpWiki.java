package fniki.standalone;

import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.File;

import java.util.List;

import static fniki.wiki.HtmlUtils.*;
import static fniki.wiki.Validations.*;
import static ys.wikiparser.Utils.*;

import wormarc.FileManifest;
import wormarc.IOUtil;

import fniki.wiki.ArchiveManager;
import fniki.wiki.FreenetWikiTextParser;

public class DumpWiki {

    private final static String HELP_TEXT =
        "DumpWiki: Experimental distributed anonymous wiki over Freenet + FMS\n" +
        "written as part of the fniki Freenet Wiki project\n\n" +
        "SUMMARY:\n" +
        "Dumps a wiki version into a format suitable for inserting as a Freesite.\n" +
        "This is experimental code. Use it at your own peril.\n\n" +
        "USAGE:\n" +
        "java -jar jfniki.jar <dump_path> SSK@/XXX...XXX/0123456789abcdef [template_file]\n\n" +
        "NOTE:\nfreenet.jar MUST be in your classpath.\n\n" +
        "The <dump_path> directory must already exist and any files in it will be overwritten.\n\n" +
        "The template_file should contain 3 %s place holders. The first two will be replaced \n"+
        "with the title and the third will be replaced with the wiki content.\n\n";

    ////////////////////////////////////////////////////////////
    private static class LocalParserDelegate implements FreenetWikiTextParser.ParserDelegate {
        // Pedantic.  Explictly copy references instead of making this class non-static
        // so that the code uses well defined interfaces.
        final ArchiveManager mArchiveManager;

        LocalParserDelegate(ArchiveManager archiveManager) {
            mArchiveManager = archiveManager;
        }

        public boolean processedMacro(StringBuilder sb, String text) {
            if (text.equals("LocalChanges")) {
                try {
                    FileManifest.Changes changes  = mArchiveManager.getLocalChanges();
                    if (changes.isUnmodified()) {
                        sb.append("<br />No local changes.<br />");
                        return true;
                    }
                    appendChangesHtml(changes, "", sb);
                    return true;
                } catch (IOException ioe) {
                    sb.append("{ERROR PROCESSING LOCALCHANGES MACRO}");
                    return true;
                }
            } else if (text.equals("TitleIndex")) {
                try {
                    for (String name : mArchiveManager.getStorage().getNames()) {
                        sb.append("<a href=\"" + makeHref(name) + ".html\">" + escapeHTML(name.replace("_", " ")) + "</a>");
                        sb.append("<br />");
                    }
                } catch (IOException ioe) {
                    sb.append("{ERROR PROCESSING TITLEINDEX MACRO}");
                    return true;
                }
                return true;
            }

            return false;
        }

        // CHK, SSK, USK freenet links.
        public void appendLink(StringBuilder sb, String text) {

            String[] link=split(text, '|');
            if ( isValidFreenetUri(link[0])) {
                sb.append("<a href=\"/"+makeHref(link[0].trim().substring("freenet:".length()))+"\">");
                sb.append(escapeHTML(unescapeHTML(link.length>=2 && !isEmpty(link[1].trim())? link[1]:link[0])));
                sb.append("</a>");
                return;
            }
            if (isAlphaNumOrUnder(link[0])) {
                // Link to an internal wiki page.
                String pageName = link[0].trim();
                try {
                    if ( ! mArchiveManager.getStorage().hasPage(pageName) ) {
                        pageName = "PageDoesNotExist";
                    }
                } catch (IOException ioe) {

                    sb.append("{ERROR VALIDATING INTERNAL LINK}");
                    return;
                }
                sb.append("<a href=\""+makeHref(pageName)+".html\">");
                sb.append(escapeHTML(unescapeHTML(link.length>=2 && !isEmpty(link[1].trim())? link[1]:link[0])));
                sb.append("</a>");
                return;
            }

            sb.append("<a href=\"ExternalLink.html\">");
            sb.append(escapeHTML(unescapeHTML(link.length>=2 && !isEmpty(link[1].trim())? link[1]:link[0])));
            sb.append("</a>");
        }

        // Only CHK and SSK freenet links.
        public void appendImage(StringBuilder sb, String text) {

            String[] link=split(text, '|');
            if ( isValidFreenetUri(link[0]) && !link[0].startsWith("freenet:USK@")) {
                String alt=escapeHTML(unescapeHTML(link.length>=2 && !isEmpty(link[1].trim())? link[1]:link[0]));
                sb.append("<img src=\"/" + makeHref(link[0].trim().substring("freenet:".length())) + "\" alt=\""+alt+"\" title=\""+alt+"\" />");
                return;
            }
            sb.append("{ERROR PROCESSING IMAGE WIKITEXT}");
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 3 || args.length < 2) {
            System.err.println(HELP_TEXT);
            System.exit(-1);
        }

        ArchiveManager archiveManager = new ArchiveManager();
        FreenetWikiTextParser.ParserDelegate mParserDelegate = new LocalParserDelegate(archiveManager);

        String ouputDirectory = args[0];
        System.out.println("Preparing to dump archive to " + ouputDirectory + "...");

        File dir = new File(ouputDirectory);
        if (!(dir.exists() && dir.isDirectory() && dir.canWrite() )) {
            throw new IOException("Need " + ouputDirectory + " to be an existant, writeable directory.");
        }

        String wikiTemplate;
        if ( args.length == 3 && new File(args[2]).exists() ) {
            wikiTemplate = IOUtil.readUtf8StringAndClose(new FileInputStream(args[2]));
        } else {
            wikiTemplate = IOUtil.readUtf8StringAndClose(ServeHttp.class.getResourceAsStream("/wiki_dump_template.html"));
        }

        // Dump this archive and quit
        System.out.println("Loading archive...");
        archiveManager.load(args[1]);

        List<String> pages = archiveManager.getStorage().getNames();
        if ( !archiveManager.getStorage().hasPage("PageDoesNotExist") ) {
            pages.add("PageDoesNotExist");
        }

        String cleanName;
        for (String name : pages) {
            try {
                // sethcg: Do wiki page names already have bad characters stripped out? I'm assuming so.
                FileOutputStream out = new FileOutputStream(ouputDirectory + "/" + name + ".html");
                PrintStream p = new PrintStream(out);
                cleanName = unescapeHTML(name.replace("_", " "));
                p.printf(wikiTemplate, cleanName, cleanName, 
                        ( archiveManager.getStorage().hasPage(name) ) ?
                        new FreenetWikiTextParser(archiveManager.getStorage().getPage(name), mParserDelegate).toString() :
                        "Page doesn't exist in the wiki yet."
                        );
                p.close();
            } catch ( IOException e) {
                System.out.println("Error writing " + name + " to file.");
            }
        }

        System.out.println("Successfully dumped wiki.");
    }
}
