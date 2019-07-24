#!/usr/bin/env bash

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

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $SCRIPT_DIR/common.sh "$@"


echo "-- INSTALLING GRAFANA ----------------------------------------------------"

if [ -z "$GRAFANA_VERSION" ]; then
    echo "Need to set $GRAFANA_VERSION environment variable before calling this script !"
    exit 1
fi

saved_dir=`pwd`
function returned_to_saved_dir() {
     cd $saved_dir
}
trap returned_to_saved_dir 15
trap returned_to_saved_dir EXIT

echo " - Changing to temp directory"
mkdir -p /tmp/grafana_setup
cd /tmp/grafana_setup

echo " - Downloading grafana-$GRAFANA_VERSION"
wget https://dl.grafana.com/oss/release/grafana-$GRAFANA_VERSION.linux-amd64.tar.gz   > /tmp/grafana_install_log 2>&1
if [[ $? != 0 ]]; then
    echo " -> Failed to downolad grafana-$GRAFANA_VERSION from https://www.apache.org/. Trying to download from niceideas.ch"
    exit -1
    #wget http://niceideas.ch/mes/grafana-$GRAFANA_VERSION.tar.gz >> /tmp/grafana_install_log 2>&1
    #fail_if_error $? "/tmp/grafana_install_log" -1
fi


echo " - Extracting grafana-$GRAFANA_VERSION"
tar -xvf grafana-$GRAFANA_VERSION.linux-amd64.tar.gz  > /tmp/grafana_install_log 2>&1
fail_if_error $? "/tmp/grafana_install_log" -2

echo " - Installing grafana"
sudo chown root.staff -R grafana-$GRAFANA_VERSION
sudo mv grafana-$GRAFANA_VERSION /usr/local/lib

#sudo mkdir -p /usr/local/lib/grafana_$SCALA_VERSION-$GRAFANA_VERSION/data
#sudo chmod -R 777 /usr/local/lib/grafana_$SCALA_VERSION-$GRAFANA_VERSION/data
#sudo mkdir -p /usr/local/lib/grafana_$SCALA_VERSION-$GRAFANA_VERSION/logs
#sudo chmod -R 777 /usr/local/lib/grafana_$SCALA_VERSION-$GRAFANA_VERSION/logs
#sudo chmod -R 777 /usr/local/lib/grafana_$SCALA_VERSION-$GRAFANA_VERSION/config/

echo " - symlinking /usr/local/lib/grafana/ to /usr/local/lib/grafana-$GRAFANA_VERSION"
sudo ln -s /usr/local/lib/grafana-$GRAFANA_VERSION /usr/local/lib/grafana


echo " - Registering test cleaning traps"
export ES_PROC_ID=-1
export GRAFANA_PROC_ID=-1
function check_stop_es_grafana(){
    if [[ $ES_PROC_ID != -1 ]]; then
        echo " - Stopping ES !!"
        kill -15 $ES_PROC_ID
    fi
    if [[ $GRAFANA_PROC_ID != -1 ]]; then
        echo " - Stopping Grafana !!"
        kill -15 $GRAFANA_PROC_ID
    fi
}
trap check_stop_es_grafana 15
trap check_stop_es_grafana EXIT


echo " - Starting Grafana"
/usr/local/lib/grafana-$GRAFANA_VERSION/bin/grafana-server \
        -homepath /usr/local/lib/grafana-$GRAFANA_VERSION > /tmp/grafana_run_log 2>&1 &
export GRAFANA_PROC_ID=$!

echo " - Checking Grafana startup"
sleep 10
if [[ `ps -e | grep $GRAFANA_PROC_ID` == "" ]]; then
    echo " !! Failed to start Grafana !!"
    cat /tmp/grafana_run_log
    exit -8
fi

echo " - Stopping Grafana"
kill -15 $GRAFANA_PROC_ID
export GRAFANA_PROC_ID=-1


# Caution : the in container setup script must mandatorily finish with this log"
echo " - In container install SUCCESS"