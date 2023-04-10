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

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $SCRIPT_DIR/common.sh "$@"


echo "-- INSTALLING PROMETHEUS NODE EXPORTER ---------------------------------------"

if [ -z "$PROMETHEUS_VERSION" ]; then
    echo "Need to set PROMETHEUS_VERSION environment variable before calling this script !"
    exit 1
fi

if [ -z "$PROMETHEUS_NODE_EXPORTER_VERSION" ]; then
    echo "Need to set PROMETHEUS_NODE_EXPORTER_VERSION environment variable before calling this script !"
    exit 1
fi

saved_dir=$(pwd)
function returned_to_saved_dir() {
     cd $saved_dir || return
}
trap returned_to_saved_dir 15
trap returned_to_saved_dir EXIT
trap returned_to_saved_dir ERR

echo " - Changing to temp directory"
rm -Rf /tmp/prometheus_ne_setup
mkdir -p /tmp/prometheus_ne_setup
cd /tmp/prometheus_ne_setup || (echo "Couldn't change to /tmp/prometheus_ne_setup" && exit 200)

sudo mkdir -p /usr/local/lib/prometheus-$PROMETHEUS_VERSION.linux-amd64/
sudo ln -s /usr/local/lib/prometheus-$PROMETHEUS_VERSION.linux-amd64 /usr/local/lib/prometheus

echo " - Downloading node_exporter-$PROMETHEUS_NODE_EXPORTER_VERSION"
wget https://github.com/prometheus/node_exporter/releases/download/v$PROMETHEUS_NODE_EXPORTER_VERSION/node_exporter-$PROMETHEUS_NODE_EXPORTER_VERSION.linux-amd64.tar.gz > /tmp/prometheus_install_log 2>&1
if [[ $? != 0 ]]; then
    echo " -> Failed to download ode_exporter-$PROMETHEUS_NODE_EXPORTER_VERSION from github. Trying to download from niceideas.ch"
    wget https://niceideas.ch/mes/node_exporter-$PROMETHEUS_NODE_EXPORTER_VERSION.linux-amd64.tar.gz >> /tmp/prometheus_install_log 2>&1
    fail_if_error $? "/tmp/prometheus_install_log" -1
fi


echo " - Extracting node_exporter-$PROMETHEUS_VERSION"
tar -xvf node_exporter-$PROMETHEUS_NODE_EXPORTER_VERSION.linux-amd64.tar.gz > /tmp/prometheus_install_log 2>&1
fail_if_error $? "/tmp/prometheus_install_log" -2

echo " - Installing node_exporter"
sudo chown -R root.staff node_exporter-$PROMETHEUS_NODE_EXPORTER_VERSION.linux-amd64/
sudo mkdir -p /usr/local/lib/prometheus-$PROMETHEUS_VERSION.linux-amd64/exporters/
sudo mv node_exporter-$PROMETHEUS_NODE_EXPORTER_VERSION.linux-amd64 /usr/local/lib/prometheus-$PROMETHEUS_VERSION.linux-amd64/exporters/

#sudo mkdir -p /usr/local/lib/prometheus_$SCALA_VERSION-$PROMETHEUS_VERSION/data
#sudo chmod -R 777 /usr/local/lib/prometheus_$SCALA_VERSION-$PROMETHEUS_VERSION/data
#sudo mkdir -p /usr/local/lib/prometheus_$SCALA_VERSION-$PROMETHEUS_VERSION/logs
#sudo chmod -R 777 /usr/local/lib/prometheus_$SCALA_VERSION-$PROMETHEUS_VERSION/logs
#sudo chmod -R 777 /usr/local/lib/prometheus_$SCALA_VERSION-$PROMETHEUS_VERSION/config/

echo " - symlinking node_exporter to /usr/local/lib/prometheus/exporters/node_exporter"
sudo ln -s /usr/local/lib/prometheus-$PROMETHEUS_VERSION.linux-amd64/exporters/node_exporter-$PROMETHEUS_NODE_EXPORTER_VERSION.linux-amd64 \
        /usr/local/lib/prometheus/exporters/node_exporter

echo " - Registering test cleaning traps"
export ES_PROC_ID=-1
export NODE_EXPORTER_PROC_ID=-1
function check_stop_node_exporter(){
    if [[ $ES_PROC_ID != -1 ]]; then
        echo " - Stopping ES !!"
        kill -15 $ES_PROC_ID
    fi
    if [[ $NODE_EXPORTER_PROC_ID != -1 ]]; then
        echo " - Stopping Prometheus Node Exporter !!"
        kill -15 $NODE_EXPORTER_PROC_ID
    fi
}
trap check_stop_node_exporter 15
trap check_stop_node_exporter EXIT
trap check_stop_node_exporter ERR


echo " - Starting Prometheus Node Exporter"
/usr/local/lib/prometheus/exporters/node_exporter/node_exporter > /tmp/prometheus_run_log 2>&1 &
export NODE_EXPORTER_PROC_ID=$!

echo " - Checking Node Exporter startup"
sleep 10
if ! kill -0 $NODE_EXPORTER_PROC_ID > /dev/null 2>&1; then
    echo " !! Failed to start Node Exporter !!"
    cat /tmp/prometheus_run_log
    exit 8
fi

echo " - Stopping Node Exporter"
kill -15 $NODE_EXPORTER_PROC_ID
export NODE_EXPORTER_PROC_ID=-1


echo " - Cleaning build directory"
rm -Rf /tmp/prometheus_ne_setup
returned_to_saved_dir


# Caution : the in container setup script must mandatorily finish with this log"
echo "$IN_CONTAINER_INSTALL_SUCESS_MESSAGE"