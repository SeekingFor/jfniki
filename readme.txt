20110122
djk@isFiaD04zgAgnrEC5XJt1i4IE7AkNPqhBG5bONi6Yks

WARNING:
THIS IS RAW ALPHA CODE.

I'm releasing this so that other developers in the Freenet community
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
Manually edit plugin/src/fniki/plugin/Fniki.java to include your FMS_ID and PRIVATE_FMS_SSK.

ant plugin
load the jar file from ./build/jar/jfniki-plugin.jar


