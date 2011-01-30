------------------------------------------------------------
This is a my personal work repo. Unless you pull a version
tagged as a release (none yet), you can assume that code
may be buggy / broken.

-- djk

------------------------------------------------------------

20110130
djk@isFiaD04zgAgnrEC5XJt1i4IE7AkNPqhBG5bONi6Yks

WARNING:
THIS IS RAW ALPHA CODE.

I'm publishing this so that other developers in the Freenet community
can audit the source code.

DON'T USE IT if violation of your anonymity would put you at risk.

ABOUT:
* jfniki is an experimental serverless wiki implementation which runs over Freenet / FMS.
* It is written in Java and has no external build dependencies.
* jfniki is INCOMPATIBLE with the existing server based python fniki implementation.

REQUIREMENTS:
ant
java 1.5 or better
Access to a running Freenet Node and FMS daemon.

BUILD:
ant jar

RUN:
Edit the script/jfniki.sh to set PRIVATE_FMS_SSK and FMS_ID correctly and comment out the warning lines.

./script/jfniki.sh

Look at http://127.0.0.1:8083 with your web browser.

BUILD FREENET PLUGIN:
ant jar
load the jar file from ./build/jar/jfniki.jar

Click on the "View Configuration" link and set the "FMS Private SSK" and "FMS ID fields".


KNOWN ISSUES:
o Pages don't auto-refresh. You need to manually reload to see status changes.
  [Freenet ContentFilter is eating meta-refresh???]


------------------------------------------------------------
Dev notes:
------------------------------------------------------------
Stopped in the middle of implementing config ui state
- figure out how to make private ssk wrap
- implement update msgs (mMsg)
- implement import / export
- test in plugin (probably broken at the moment)

------------------------------------------------------------




