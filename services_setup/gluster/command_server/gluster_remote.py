#
# This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
# well to this individual file than to the Eskimo Project as a whole.
#
# Copyright 2019 eskimo.sh / https://www.eskimo.sh - All rights reserved.
# Author : eskimo.sh / https://www.eskimo.sh
#
# Eskimo is available under a dual licensing model : commercial and GNU AGPL.
# If you did not acquire a commercial licence for Eskimo, you can still use it and consider it free software under the
# terms of the GNU Affero Public License. You can redistribute it and/or modify it under the terms of the GNU Affero
# Public License  as published by the Free Software Foundation, either version 3 of the License, or (at your option)
# any later version.
# Compliance to each and every aspect of the GNU Affero Public License is mandatory for users who did no acquire a
# commercial license.
#
# Eskimo is distributed as a free software under GNU AGPL in the hope that it will be useful, but WITHOUT ANY
# WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
# Affero Public License for more details.
#
# You should have received a copy of the GNU Affero Public License along with Eskimo. If not,
# see <https://www.gnu.org/licenses/> or write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
# Boston, MA, 02110-1301 USA.
#
# You can be released from the requirements of the license by purchasing a commercial license. Buying such a
# commercial license is mandatory as soon as :
# - you develop activities involving Eskimo without disclosing the source code of your own product, software, 
#   platform, use cases or scripts.
# - you deploy eskimo as part of a commercial product, platform or software.
# For more information, please contact eskimo.sh at https://www.eskimo.sh
#
# The above copyright notice and this licensing notice shall be included in all copies or substantial portions of the
# Software.
#

# requires :
# apt-get install python-pip
# pip install furl
# called with :
# curl 'http://localhost:18999/?command=peer&subcommand=probe&options=192.168.10.13'
# curl 'http://localhost:18999/?command=pool&subcommand=list&options='

import SimpleHTTPServer
import SocketServer
import logging
import sys
import os
import signal
import subprocess
from furl import furl

PORT = 18999

# configure logging
root = logging.getLogger()
root.setLevel(logging.INFO)

handler = logging.StreamHandler(sys.stdout)
handler.setLevel(logging.INFO)
formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')
handler.setFormatter(formatter)
root.addHandler(handler)

LOG = logging.getLogger(__name__)

class RequestHandler(SimpleHTTPServer.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        SimpleHTTPServer.SimpleHTTPRequestHandler.__init__(
            self, *args, **kwargs)

    def do_GET(self):
        LOG.info('Got GET request: %s', self.path)

        f = furl(self.path)
        command = f.args["command"]
        subcommand = f.args["subcommand"]
        if "options" in f.args:
            options = f.args["options"]
        else:
            options = ""

        LOG.info(command)
        LOG.info(subcommand)
        LOG.info(options)

        if command == "force-remove-peer":
            command_line = "/usr/local/sbin/__force-remove-peer.sh {0}".format(options)
        elif command == "force-remove-brick":
            command_line = "/usr/local/sbin/__force-remove-brick.sh {0} {1}".format(subcommand, options)
        else:
            command_line = "/usr/sbin/gluster {0} {1} {2}".format(command, subcommand, options)

        LOG.info('About to execute command : %s', command_line)

        # execute command
        try:
            # stdout = subprocess.PIPE lets you redirect the output
            res = subprocess.Popen(command_line.strip().split(" "), stdout=subprocess.PIPE, stderr=subprocess.PIPE)

            res.wait() # wait for process to finish; this also sets the returncode variable inside 'res'
            if res.returncode != 0:
                LOG.error("os.wait:exit status != 0\n")
                self.send_response(500)
            else:
                LOG.debug ("os.wait:({},{})".format(res.pid, res.returncode))
                self.send_response(200)

            self.send_header('Content-type','text/plain')
            self.end_headers()

            # access the output from stdout and stderr
            result = "{0}\n{1}".format (res.stdout.read(), res.stderr.read())
            LOG.info ("result is \n : %s", result)
            #print ("after read: {}".format(result))

            # Send the html message
            self.wfile.write(result)

        except OSError:
            LOG.error ("error: popen")

            self.send_response(500)
            self.send_header('Content-type', 'text/plain')
            self.end_headers()

            self.wfile.write("gluster command execution failed")

        return


    def do_POST(self):
        LOG.info('Got POST request: %s', self.path)

SocketServer.TCPServer.allow_reuse_address = True
httpd = SocketServer.TCPServer(("", PORT), RequestHandler)

def signal_handler(sig, frame):
    httpd.server_close()
    sys.exit(0)

signal.signal(signal.SIGINT, signal_handler)
signal.signal(signal.SIGHUP, signal_handler)

print ("serving at port", PORT)
httpd.serve_forever()