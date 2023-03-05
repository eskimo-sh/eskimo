#
# This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
# well to this individual file than to the Eskimo Project as a whole.
#
# Copyright 2019 - 2023 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

from http.server import BaseHTTPRequestHandler, HTTPServer
import socketserver
import logging
import sys
import signal
import subprocess
import tempfile

PORT = 28999

# configure logging
root = logging.getLogger()
root.setLevel(logging.INFO)

handler = logging.StreamHandler(sys.stdout)
handler.setLevel(logging.INFO)
formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')
handler.setFormatter(formatter)
root.addHandler(handler)

LOG = logging.getLogger(__name__)


class RequestHandler(BaseHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        BaseHTTPRequestHandler.__init__(
            self, *args, **kwargs)

    def _set_error_headers(self):
        self.send_response(500)
        self.send_header('Content-type', 'text/plain')
        self.end_headers()

    def do_GET(self):
        LOG.info('- Got GET request: %s (UNSUPPORTED)', self.path)

        self._set_error_headers()
        self.wfile.write("GET requests are not supported by logstash command server".encode("UTF-8"))

    def do_POST(self):
        LOG.info('- Got POST request: %s', self.path)

        full_args = self.rfile.read(int(self.headers['Content-Length']))

        LOG.info('- fullArgs raw: %s', full_args.decode('utf-8'))

        if 'stdin_file' in self.headers:
            command_line = "/usr/local/sbin/call_logstash.sh --std_in {0} {1} ".format(self.headers['stdin_file'],
                                                                                       full_args.decode('utf-8'))
        else:
            command_line = "/usr/local/sbin/call_logstash.sh {0} ".format(full_args)

        LOG.info('- Command Line : %s', command_line)

        # execute command
        try:
            # stdout = subprocess.PIPE lets you redirect the output
            LOG.info('   + Calling process')
            with tempfile.TemporaryFile() as fp:
                res = subprocess.Popen(command_line.strip().split(), bufsize=40000000, stdout=fp,
                                       stderr=subprocess.STDOUT)

                LOG.info('   + Waiting for completion')
                res.wait()  # wait for process to finish; this also sets the returncode variable inside 'res'

                fp.seek(0)
                command_log = fp.read()

            LOG.info('   + Completed!')
            if res.returncode != 0:
                LOG.error("subprocess.wait:exit status != 0\n")
                self.send_response(500)
            else:
                LOG.debug("subprocess.wait:({},{})".format(res.pid, res.returncode))
                self.send_response(200)

            self.send_header('Content-type', 'text/plain')
            self.end_headers()

            # LOG.info (" - result is \n : %s", command_log)
            # print ("after read: {}".format(result))

            # Send the html message
            self.wfile.write(command_log)

        except OSError:
            LOG.error("error: popen")

            self._set_error_headers()
            self.wfile.write("logstash command failed to execute (got OSError)".encode("UTF-8"))

        return


socketserver.TCPServer.allow_reuse_address = True
# httpd = socketserver.TCPServer(("", PORT), RequestHandler)
httpd = HTTPServer(('', PORT), RequestHandler)


def signal_handler(sig, frame):
    httpd.server_close()
    sys.exit(0)


signal.signal(signal.SIGINT, signal_handler)
signal.signal(signal.SIGHUP, signal_handler)

print("serving at port {0}".format(PORT))
httpd.serve_forever()
