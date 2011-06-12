#!/usr/bin/env python

""" Script to insert jfniki releases into Freenet.

    Copyright (C) 2011 Darrell Karbott

    This library is free software; you can redistribute it and/or
    modify it under the terms of the GNU General Public
    License as published by the Free Software Foundation; either
    version 2.0 of the License, or (at your option) any later version.

    This library is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
    General Public License for more details.

    You should have received a copy of the GNU General Public
    License along with this library; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA

    Author: djk@isFiaD04zgAgnrEC5XJt1i4IE7AkNPqhBG5bONi6Yks
"""

# This script isn't really for public consumption.
# It is brittle and not fully debugged.
# It assumes you have hg infocalypse installed and configured.
#
# BUG: ANONYMITY ISSUE: This script currently leaks the *nix user id
#      into the inserted .tgz and .jar files.
#      DO NOT RUN IT if this concerns you.
#
import os
import shutil
import subprocess
import tarfile

from binascii import hexlify

from mercurial import ui, hg, commands

from insert_files import insert_files
from minimalfms import get_connection, send_msgs

############################################################

# CAUTION: This directory is recursively deleted!
STAGING_DIR = '/tmp/staging'

FCP_HOST = '127.0.0.1'
FCP_PORT = 19481

FMS_HOST = '127.0.0.1'
FMS_PORT =  11119

FMS_ID = 'djk'
FMS_GROUP = 'sites'

# REQUIRES: must match name in freesite.cfg. LATER: fix.
SITE_NAME = 'jfniki_releases'

PUBLIC_SITE = "USK@kRM~jJVREwnN2qnA8R0Vt8HmpfRzBZ0j4rHC2cQ-0hw," + \
              "2xcoQVdQLyqfTpF2DpkdUIbHFCeL4W~2X1phUYymnhM,AQACAAE/%s/%%d/" % \
              SITE_NAME

############################################################
# Indexes of refereneced USK sites

FREENET_DOC_WIKI_IDX = 40
FNIKI_IDX = 84
REPO_IDX = 17

############################################################

THIS_FILES_DIR = os.path.abspath(os.path.dirname(__file__))

REPO_DIR = os.path.abspath(os.path.join(THIS_FILES_DIR, '..'))
FREENET_JAR = os.path.abspath(os.path.join(THIS_FILES_DIR, '../alien/libs/freenet.jar'))
RELEASE_NOTES = os.path.abspath(os.path.join(THIS_FILES_DIR, '../doc/latest_release.txt'))
INDEX_HTML = os.path.abspath(os.path.join(THIS_FILES_DIR, 'generated_freesite/index.html'))
INDEX_HTML_TEMPLATE = os.path.abspath(os.path.join(THIS_FILES_DIR, 'index_template.html'))
FMS_MESSAGE_TEMPLATE = os.path.abspath(os.path.join(THIS_FILES_DIR, 'fms_message_template.txt'))

############################################################

def stage_release():
    # LATER: check for uncommitted changes
    ui_ = ui.ui()
    repo = hg.repository(ui_, REPO_DIR)

    # Get current head.
    heads = [hexlify(repo[head].node())[:12] for head in repo.heads()]
    assert len(heads) == 1 # Don't try to handle multiple heads
    head = heads[0]

    jar_name = "jfniki.%s.jar" % head
    tgz_name = "jfniki.%s.tgz" % head
    export_dir_name = "jfniki.%s" % head

    tgz_file_name = "%s/%s" % (STAGING_DIR, tgz_name)
    jar_file_name = "%s/%s" % (STAGING_DIR, jar_name)

    # scrub staging directory
    try:
        shutil.rmtree(STAGING_DIR)
    except:
        pass

    os.makedirs(STAGING_DIR)

    # dump clean source to staging
    dest =  "%s/%s" % (STAGING_DIR, export_dir_name)

    # TRICKY: Had to put a print in the implementation of command.archive to figure
    #         out required default opts.
    # {'rev': '', 'no_decode': None, 'prefix': '', 'exclude': [], 'include': [], 'type': ''}
    commands.archive(ui_, repo, dest,
                     rev='', no_decode=None, prefix='', exclude=[], include=[], type='')


    # remove origin tarballs to save space
    shutil.rmtree("%s/alien/origins/" % dest)

    # tar up source
    tgz_file = tarfile.open(tgz_file_name, 'w:gz')

    #def reset(tarinfo):
    #    tarinfo.uid = tarinfo.gid = 0
    #    tarinfo.uname = tarinfo.gname = "root"
    #    return tarinfo
    # LATER: Use line after upgrading python. Keeps uid, gid, uname out of tar.
    # tgz_file.add("%s/%s" % (STAGING_DIR, export_dir_name), arcname=export_dir_name, filter=reset) # python 2.7
    tgz_file.add("%s/%s" % (STAGING_DIR, export_dir_name), arcname=export_dir_name)
    tgz_file.close()

    # cp freenet.jar required for build
    os.makedirs("%s/%s/%s" % (STAGING_DIR, export_dir_name, "alien/libs"))
    shutil.copyfile(FREENET_JAR, "%s/%s/%s" % (STAGING_DIR, export_dir_name, "alien/libs/freenet.jar"))

    # build jar
    result = subprocess.check_call(["/usr/bin/ant",
                                    "-buildfile",
                                    "%s/%s/build.xml" % (STAGING_DIR, export_dir_name)])
    print "ant result code: %d" % result

    # copy jar with name including the hg rev.
    shutil.copyfile("%s/%s/%s" % (STAGING_DIR, export_dir_name, "build/jar/jfniki.jar"), jar_file_name)

    print
    print "SUCCESSFULLY STAGED:"
    print jar_file_name
    print tgz_file_name
    print
    return (head, jar_file_name, tgz_file_name)


def simple_templating(text, substitutions):
    for variable in substitutions:
        text = text.replace(variable, str(substitutions[variable]))
    assert text.find("__") == -1 # Catch unresolved variables
    return text

def latest_site_index(repo): # C&P: wikibot.py
    """ Read the latest known freesite index out of the hg changelog. """
    for tag, dummy in reversed(repo.tagslist()):
        if tag.startswith('I_'):
            return int(tag.split('_')[1])
    return -1

def tag_site_index(ui_, repo, index=None): # C&P: wikibot.py
    """ Tag the local repository with a freesite index. """
    if index is None:
        index = latest_site_index(repo) + 1
    commands.tag(ui_, repo, 'I_%i' % index)

############################################################
# ATTRIBUTION:
# http://wiki.python.org/moin/EscapingHtml
HTML_ESCAPE_TABLE = {
    "&": "&amp;",
    '"': "&quot;",
    "'": "&apos;",
    ">": "&gt;",
    "<": "&lt;"}

def html_escape(text):
    """Produce entities within text."""
    return "".join(HTML_ESCAPE_TABLE.get(c,c) for c in text)

############################################################

def update_html(head, jar_chk, tgz_chk):
    ui_ = ui.ui()
    repo = hg.repository(ui_, REPO_DIR)
    site_usk = PUBLIC_SITE % (latest_site_index(repo) + 1)

    html = simple_templating(open(INDEX_HTML_TEMPLATE).read(),
                             {'__HEAD__':head,
                              '__JAR_CHK__': jar_chk,
                              '__SRC_CHK__': tgz_chk,
                              '__RELEASE_NOTES__' : html_escape(open(RELEASE_NOTES).read()),
                              '__SITE_USK__': site_usk,
                              '__INDEX_FDW__': FREENET_DOC_WIKI_IDX,
                              '__INDEX_FNIKI__': FNIKI_IDX,
                              '__INDEX_REPO__': REPO_IDX,
                              })

    updated = open(INDEX_HTML, 'w')
    updated.write(html)
    updated.close()

    commit_msg = "index.html:%s" % head
    commands.commit(ui_, repo, pat = (INDEX_HTML, ),
                    include = [], addremove = None, close_branch = None,
                    user = '', date = '',
                    exclude = [], logfile = '',
                    message = commit_msg)
    tag_site_index(ui_, repo)

# Insert the latest tagged freesite version into freenet.
# NOTE: depends on state set in freesite.cfg
# REQUIRES: 'I_<num>' tag exists in the repo.
# REQUIRES: hg infocalypse is installed and configured.
def insert_freesite():
    print REPO_DIR
    ui_ = ui.ui()
    repo = hg.repository(ui_, REPO_DIR)
    target_index = latest_site_index(repo)
    assert target_index >= 0

    # BUG: There are case when this can fail with error code == 0
    #      e.g. when private key can't be read.
    # DCI: Test. does fn-putsite set error code on failure?
    subprocess.check_call(["/usr/bin/hg",
                           "-R",
                           REPO_DIR,
                           "fn-putsite",
                           "--index",
                           str(target_index)])

    # LATER: Do better. Parse request URI from output.
    return PUBLIC_SITE % target_index, target_index

def send_fms_notification(site_uri, target_index, head, jar_chk, tgz_chk):

    connection = get_connection(FMS_HOST, FMS_PORT, FMS_ID)

    msg = simple_templating(open(FMS_MESSAGE_TEMPLATE).read(),
                             {'__HEAD__':head,
                              '__JAR_CHK__': jar_chk,
                              '__SRC_CHK__': tgz_chk,
                              '__SITE_USK__' : site_uri,
                              '__RELEASE_NOTES__' : open(RELEASE_NOTES).read(),
                              })

    send_msgs(connection,
              ((FMS_ID, FMS_GROUP,
                "jfniki releases #%d" % target_index,
                msg),))

    print "Sent FMS notification to: %s" % FMS_GROUP

def release():
    print
    print "If you haven't read the warning about ANONYMITY ISSUES"
    print "with this script, now might be a good time to hit Ctrl-C."
    print
    print
    print "RELEASE NOTES:"
    print open(RELEASE_NOTES).read()
    print
    print "------------------------------------------------------------"

    head, jar_file, tgz_file = stage_release()
    jar_chk, tgz_chk = insert_files(FCP_HOST, FCP_PORT, [jar_file, tgz_file])
    update_html(head, jar_chk, tgz_chk)
    site_uri, target_index = insert_freesite()
    send_fms_notification(site_uri, target_index, head, jar_chk, tgz_chk)

    print
    print "Success!"


if __name__ == "__main__":
    release()
