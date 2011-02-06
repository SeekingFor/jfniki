20110205
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

BUILD:
ant jar

RUN STAND ALONE:
./script/jfniki.sh 8083

Look at http://127.0.0.1:8083 with your web browser.

Click on the "View" Configuration link and set the "FMS Private SSK" and "FMS ID" fields.
If you want you can use the "Export" button on the Configuration page to export your
configuration to a file.

Once you've done that you can start the script with the saved configuration. i.e.:

./script/jfniki.sh path_to_your_saved_config

will start the stand alone app on the same port you used before.

RUN AS A FREENET PLUGIN:
Load the jar file from ./build/jar/jfniki.jar

Click on the "View" Configuration link and set the "FMS Private SSK" and "FMS ID" fields.

OTHER DOC:
See quickstart.txt in this directory (The default page when an empty wiki is displayed).

KNOWN ISSUES:
o Pages don't auto-refresh. You need to manually reload to see status changes.
  [Freenet ContentFilter is eating meta-refresh??? Fixed now with WikiContentFilter.EXCEPTIONS hack]
o "Cancel" sometimes fails. [WORKAROUND: load and unload the plugin / kill restart the stand alone app.]


