""" Code to send fms messages pillaged from hg infocalypse codebase.

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

import nntplib
import StringIO

def get_connection(fms_host, fms_port, user_name):
    """ Create an fms NNTP connection. """
    return nntplib.NNTP(fms_host, fms_port, user_name)

MSG_TEMPLATE = """From: %s
Newsgroups: %s
Subject: %s

%s"""

# Please use this function for good and not evil.
def send_msgs(server, msg_tuples, send_quit=False):
    """ Send messages via fms.
    msg_tuple format is: (sender, group, subject, text, send_callback)

    send_callback is optional.

    If it is present and not None send_callback(message_tuple)
    is invoked after each message is sent.

    It is legal to include additional client specific fields.
    """

    for msg_tuple in msg_tuples:
        raw_msg = MSG_TEMPLATE % (msg_tuple[0],
                                  msg_tuple[1],
                                  msg_tuple[2],
                                  msg_tuple[3])

        in_file = StringIO.StringIO(raw_msg)
        try:
            server.post(in_file)

            if len(msg_tuple) > 4 and not msg_tuple[4] is None:
                # Sent notifier
                msg_tuple[4](msg_tuple)

            if send_quit:
                server.quit()
        finally:
            in_file.close()

