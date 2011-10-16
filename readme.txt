20111016
djk@isFiaD04zgAgnrEC5XJt1i4IE7AkNPqhBG5bONi6Yks

WARNING:
This is beta code.  I've taken reasonable precautions to make sure it is
safe, including running the Freenet content filter over the final output
to trap dangerous HTML. However, this is new code that was written fast and
hasn't been audited by *anyone*.

DON'T USE IT if violation of your anonymity would put you at risk.

ABOUT:
* jfniki is an experimental serverless wiki implementation which runs over Freenet / FMS.
* It is written in Java and has no external build dependencies except for freenet.jar.
* jfniki is INCOMPATIBLE with the existing server based python fniki implementation.
* It can run either as a standalone web app or as a Freenet Plugin.

REQUIREMENTS:
ant
java 1.5 or better
Access to a running Freenet Node and FMS daemon.
A reasonably recent version of freenet.jar

FMS CONFIGURATION:
By default the prototype uses the "biss.test000" FMS group.
Make sure FMS is configured to save messages to this group.

On the FMS Boards maintenance page:
http://127.0.0.1:18080/boards.htm

Search for "biss.test000" and make sure "Save Received Messages" is checked.

If you can't find the group, add it.

BUILD:
Copy freenet.jar into ./alien/libs

run:
ant jar

RUN STAND ALONE:
./script/jfniki.sh 8083

Look at http://127.0.0.1:8083 with your web browser.

Click on the "View" Configuration link and set the "FMS Private SSK" and "FMS name" fields.
If you want you can use the "Export" button on the Configuration page to export your
configuration to a file.

Once you've done that you can start the script with the saved configuration. i.e.:

./script/jfniki.sh path_to_your_saved_config

will start the stand alone app on the same port you used before.

RUNNING STAND ALONE ON WINDOWS:
I don't have access to a Windows machine to test on so I didn't write a .bat.

TheSeeker from FMS reports that you can run stand alone under Windows by copying freenet.jar
and jfniki.jar into the same directory and running:

java(w) -cp freenet.jar;jfniki.jar fniki.standalone.ServeHttp [jfniki.cfg|port]

RUN AS A FREENET PLUGIN:
Load the jar file from ./build/jar/jfniki.jar

Click on the "View" Configuration link and set the "FMS Private SSK" and "FMS ID" fields.

OTHER DOC:
See quickstart.txt in the doc directory (The default page when an empty wiki is displayed).
It has some notes on experimental support for Freetalk implemented by sethcg.

KNOWN ISSUES:
o "Cancel" sometimes fails. [WORKAROUND: load and unload the plugin / kill restart the stand alone app.]
o FMS Id displays "???" when importing config with non-default FCP host and/or port.
  [WORKAROUND: Click "Done", then click view again and the FMS Id should be correctly displayed.]

CONTRIBUTORS:
---
sethcg@a-tin0kMl1I~8xn5lkQDqYZRExKLzJITrxcNsr4T~fY
 o patch to make jfniki work with Freetalk.
 o patch to dump jfniki to html for insertion as a freesite (DumpWiki)

---
Dev notes
---
*BUG: Fix dumpwiki to set CSS class for "Discussion" links.
BUG: fix version hash generation code to ignore the nym part of the URL
     i.e. version inserted by different nyms with the same info
     after the slash are CRYPTOGRAPHICALLY VERIFIED to be the same version. 
BUG: wikitext should use unix line terminators not DOS (+1 byte per line)
**BUG: MUST show in the UI when edited wikitext has been truncated because it's too big. [horrible.]
BUG: Make Freetalk configuration keys work like fms configuration? i.e. no need for public key.
---
CHORE: Fix release script to automagically truncate the latest_version file at a sentinel line.
       e.g.:__RELEASE_SCRIPT_IGNORES_PAST_THIS_LINE__
CHORE: Fix references to "library" in file headers.
CHORE: Fix references to "FMS" to reflect the fact that either Freetalk or FMS may be used.
CHORE: Add links to previous pages to release page. [encode head and date into tag?]
CHORE: Pillage code out of hg infocalypse to update infocalypse repo version in cut_release.py
CHORE: Backout the code in ArchiveManager that allows old format uris without parents.
CHORE: Get rid of output spewed to stderr.
CHORE: Fix sethcg's dump template stuff to use named variables?
CHORE: Fix crappy code: fix places where I am using signed int values from DataInputStreams to rep unsigned values.
CHORE: Fix cut_release.py to use USK insertion for site so hints are inserted.
       Write stand alone tool?
---
**IDEA: Fix automatic "freenet:..." link detection.  legit scheme://rest parsing is
        built into creole parser, but freenet links don't use a standard scheme url. grrrrrr...
        0) Preprocess? 1) hack creole parser [Not sure this is worth it.]
[PUNT: SFA provided toadlet code.] **IDEA: Toadlet based plugin that redirects to
                                   jfniki (i.e. to get on menu) [not sure this is possible]
*IDEA: Support links to other wikis. e.g.:b fniki://nntp/group/name[/SSK@some_version]
      [Not quite. Should be able to support mutliple transports (fms, ft, freemail?, fproxy usk?) in same url]
      [See notes below on ft fms interop. one NNTP to rule them all]
IDEA: Freetalk vs Freenet interop
      0) Group naming convention. anythingbutmul.foo.bar.baz -> mul.anythingbutmul.foo.bar.baz in freetalk
      1) fniki://groupname/wikiname/[optional SSK] -- same for both. Freetalk nntp code prefixes mul. to group
      2) In config UI. add freetalk config and enable checkboxes for freeetalk and fms
      3) Convention or config to choose which private key is used for SSK insertion.
      Hmmm... not sure if people would use this feature because of the correlation of ids.
IDEA: Add permenant static help page, with quickstart page. Clean up display of empty wiki.
IDEA: Use magic pages for extra header and footers. Add options to disable them in config. emergency link on perm footer
      Completely replace top footer?
IDEA: I KAN HAZ JAVA TEMPLATE ENGIN?
       http://www.source-code.biz/MiniTemplator/

IDEA: <<<SaidBy|public_key|sig>>> -- macro which allows you to drop a cryptographically signed block of text into a page
      0) need to read up on crypto
      1) Is it possible to implement block level macros with t4?
      Intent: signed discussion entries, changlog entries
IDEA: Headers / Footers (not sure how to implement yet. macros? <<<header>>> possibly referencing <<<this_page>>>) [From two different nyms]
IDEA: write code to 0) compile a list of referenced USKS and 1) update them to the latest versions.

IDEA: shrink blocks by using a token map?  use short token in binary rep, fixup to full 20byte hash on read / write?
IDEA: Caching in FreenetIO
      Make LinkCache and interface
      FreenetLinkCache extends LinkCache
        SSK -> topkey byte[] hash
        CHK -> CHK content SHA1
        CHK content SHA1 ->  CHK
        void cacheTopKey(SSK, InputStream)
        FreenetTopKey getTopKey(ssk)
        SHA1 cacheBlock(CHK, InputStream)
        boolean isCached(CHK)


IDEA: Ditch human readable name <--> SSK fixup and generate arbitrary names from
      SSK public key hash (== big number). <n_first>*<m_middle>*<o_last> == big number
      let n = 1000, m = 1000,  o == 1000 => ???? [NOT QUITE. LOOK UP Combinatorics. USABILITY QUESTIONABLE]
      [I think zwister might be working on something similar. rtfs his source before coding this from scratch.]

IDEA: Wikibot ng. Just uses its FMS trust info to decide which version is the latest and
      send a "Stake" biss message for it.
---
Fixed bugs:
68813294196d: BUG: fix the discover UI to correctly handle posts from a different nym than the insert
2cf5cd727366: BUG: Missing insert sitekey parameter causes freesite insert failure for USKs.
4d24ce7d76ef: BUG: Diff coloring missing from jfniki.css.
5686a2328b99: BUG: "Error reading log: XGETTRUST NNTP request failed: 480 Identity not found" in Discover for deleted
              identities on FMS.
238c7dcc5ae3: BUG: incorrect drawing of rebased changes on discover page (since b963650876a7).
              [The plausable commit order code was just wrong. Fixed it I hope.]
8cfb2f3e7c38: BUG: Default FCP port wrong for CLI client. [requested by a real user]
              [The default port is set to 9481 now and can be set via Java System properties. See ./script/wa.sh]
710d700bc7a1: BUG: Header links to discussion pages. [requested by a real user]
2ce3a4499a2c: BUG: No way to create an empty wiki from the UI. [requested by a real user]
cab9533f4cb8: BUG: Can the <<<TOC>>> macro be made to play nice with the ContentFilter? [suggestion from sethcg]

Added features:
68813294196d: IDEA: Staking.  add a biss message that says "I say this version is good"
                   not "I published this version".  Add feedback in UI to show how many nyms staked a given version.
                   [like == stake]
a7fb95669db4: IDEA: linklint. checks version of all USK links and updates them [feature creep.]
f9b66084244d: IDEA: USK insert from inside fniki (feature creep :-( ) [From a real user]
08d1b85d8ddd: IDEA: Pillage glog graph drawing code from hg to improve discover versions UI
      from  http://selenic.com/repo/hg-stable/file/4ec34de8bbb1/hgext/graphlog.py
      ISSUES:
      0) It doesn't draw arbitary DAGs, only DAGS that could have reasonably constructed
         from an ORDERED sequence of commits.
      1) I implemented construction of DAGS from an unsorted list of (child, parent) pairs
         BUT it depends CRITICALLY on:
         a) being able to find the root nodes. (i.e. the nodes for which no parents are available).
         b) being able to find the child nodes. (i.e. nodes without children)
      2) This theoretically leaves the drawing code vulnerable to attack.
         ATTACK: Create a cycle so that there are no root or child nodes.
         PUNT: Don't worry about it. Attacker has to break SHA1 to create a cycle
               because of the way the version is string is generated.

Finished Chores:
7c982a8a1ea9 CHORE: Prune out unused files in alien/src directory.
d29fdea8222e: CHORE: Make cut_release.py use .zip files.  .tgz files are risky to extract.
       http://stackoverflow.com/questions/458436/adding-folders-to-a-zip-file-using-python
       (or just shell execute zip) [just executed in shell]
cce3742a46d6: CHORE: Write a script to cut releases and insert them into freenet.


