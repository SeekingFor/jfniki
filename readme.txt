20110529
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
BUG: Default FCP port wrong for CLI client. [requested by a real user]
BUG: fix the discover UI to correctly handle posts from a different nym than the insert
BUG: wikitext should use unix line terminators not DOS (+1 byte per line)
BUG: MUST show in the UI when edited wikitext has been truncated because it's too big.
*BUG: Make Freetalk configuration work like fms configuration. i.e. no need for public key.
---
CHORE: Fix references to "FMS" to reflect the fact that either Freetalk or FMS may be used.

---
IDEA: shrink blocks by using a token map?  use short token in binary rep, fixup to full 20byte hash on read / write?
*IDEA: Support links to other wikis. e.g.:b fniki://nntp/group/name[/SSK@some_version]
      [Not quite. Should be able to support mutliple transports (fms, ft, freemail?, fproxy usk?) in same url]
      [See notes below on ft fms interop. one NNTP to rule them all]
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
      let n = 1000, m = 1000,  o == 1000 => ???? [NOT QUITE. LOOK UP Combinatorics]
*IDEA: Staking.  add a biss message that says "I say this version is good"
      not "I published this version".  Add feedback in UI to show how many nyms staked a given version.
IDEA: Wikibot ng. Just uses its FMS trust info to decide which version is the latest and
      send a "Stake" biss message for it.
IDEA: Freetalk vs Freenet interop
      0) Group naming convention. anythingbutmul.foo.bar.baz -> mul.anythingbutmul.foo.bar.baz in freetalk
      1) fniki://groupname/wikiname/[optional SSK] -- same for both. Freetalk nntp code prefixes mul. to group
      2) In config UI. add freetalk config and enable checkboxes for freeetalk and fms
      3) Convention or config to choose which private key is used for SSK insertion.
      Hmmm... not sure if people would use this feature because of the correlation of ids.
---
Fixed bugs:
710d700bc7a1: BUG: Header links to discussion pages. [requested by a real user]
2ce3a4499a2c: BUG: No way to create an empty wiki from the UI. [requested by a real user]
cab9533f4cb8: BUG: Can the <<<TOC>>> macro be made to play nice with the ContentFilter? [suggestion from sethcg]

Added features:
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
cce3742a46d6: CHORE: Write a script to cut releases and insert them into freeneet.
