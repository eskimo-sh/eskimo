#!/usr/bin/env bash

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

echoerr() { echo "$@" 1>&2; }

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $SCRIPT_DIR/common.sh "$@"

# CHange current folder to script dir (important !)
cd $SCRIPT_DIR || exit 199

# Loading topology
loadTopology


# reinitializing log
sudo rm -f prom-node-exporter_install_log

echo " - Building container Prometheus Node Exporter"
build_container prom-node-exporter prom-node-exporter prom-node-exporter_install_log
#save tag
CONTAINER_TAG=$CONTAINER_NEW_TAG

echo " - Creating shared directory"
# TODO

# create and start container
echo " - Running docker container"
docker run \
        -v $PWD:/scripts \
        -v $PWD/../common:/common \
        -v /var/log/prometheus:/var/log/prometheus \
        -d --name prom-node-exporter \
        -i \
        -t eskimo/prom-node-exporter:$CONTAINER_TAG bash >> prom-node-exporter_install_log 2>&1
fail_if_error $? "prom-node-exporter_install_log" -2

echo " - Handling Eskimo Base Infrastructure"
handle_eskimo_base_infrastructure prom-node-exporter prom-node-exporter_install_log

echo " - Handling topology infrastructure"
handle_topology_infrastructure prom-node-exporter prom-node-exporter_install_log

echo " - Committing changes to local template and exiting container prom-node-exporter"
commit_container prom-node-exporter $CONTAINER_TAG prom-node-exporter_install_log

echo " - Installing and checking systemd service file"
install_and_check_service_file prom-node-exporter prom-node-exporter_install_log
