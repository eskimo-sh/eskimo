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

sudo rm -Rf /tmp/grafana_install_log

echo "-- INSTALLING GRAFANA ----------------------------------------------------"

if [ -z "$GRAFANA_VERSION" ]; then
    echo "Need to set $GRAFANA_VERSION environment variable before calling this script !"
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
rm -Rf /tmp/grafana_setup
mkdir -p /tmp/grafana_setup
cd /tmp/grafana_setup || (echo "Couldn't change to /tmp/grafana_setup" && exit 200)

echo " - Downloading grafana-$GRAFANA_VERSION"
wget https://dl.grafana.com/oss/release/grafana-$GRAFANA_VERSION.linux-amd64.tar.gz  > /tmp/grafana_install_log 2>&1
if [[ $? != 0 ]]; then
    echo " -> Failed to downolad grafana-$GRAFANA_VERSION from https://www.apache.org/. Trying to download from niceideas.ch"
    wget https://niceideas.ch/mes/grafana-$GRAFANA_VERSION.tar.gz >> /tmp/grafana_install_log 2>&1
    fail_if_error $? "/tmp/grafana_install_log" -1
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
trap check_stop_es_grafana ERR


echo " - Installing required Grafana plugins"

echo "   + grafana-piechart-panel"
sudo /usr/local/lib/grafana/bin/grafana-cli plugins install grafana-piechart-panel > /tmp/grafana_install_log 2>&1
fail_if_error $? "/tmp/grafana_install_log" -2

echo "   + grafana-clock-panel"
sudo /usr/local/lib/grafana/bin/grafana-cli plugins install grafana-clock-panel > /tmp/grafana_install_log 2>&1
fail_if_error $? "/tmp/grafana_install_log" -2

echo "   + grafana-worldmap-panel"
sudo /usr/local/lib/grafana/bin/grafana-cli plugins install grafana-worldmap-panel > /tmp/grafana_install_log 2>&1
fail_if_error $? "/tmp/grafana_install_log" -2

echo "   + ryantxu-ajax-panel"
sudo /usr/local/lib/grafana/bin/grafana-cli plugins install ryantxu-ajax-panel > /tmp/grafana_install_log 2>&1
fail_if_error $? "/tmp/grafana_install_log" -2

echo "   + digrich-bubblechart-panel"
sudo /usr/local/lib/grafana/bin/grafana-cli plugins install digrich-bubblechart-panel > /tmp/grafana_install_log 2>&1
fail_if_error $? "/tmp/grafana_install_log" -2

echo "   + aidanmountford-html-panel"
sudo /usr/local/lib/grafana/bin/grafana-cli plugins install aidanmountford-html-panel > /tmp/grafana_install_log 2>&1
fail_if_error $? "/tmp/grafana_install_log" -2

echo "   + natel-plotly-panel"
sudo /usr/local/lib/grafana/bin/grafana-cli plugins install natel-plotly-panel > /tmp/grafana_install_log 2>&1
fail_if_error $? "/tmp/grafana_install_log" -2

echo "   + snuids-radar-panel"
sudo /usr/local/lib/grafana/bin/grafana-cli plugins install snuids-radar-panel > /tmp/grafana_install_log 2>&1
fail_if_error $? "/tmp/grafana_install_log" -2

echo "   + marcuscalidus-svg-panel"
sudo /usr/local/lib/grafana/bin/grafana-cli plugins install marcuscalidus-svg-panel > /tmp/grafana_install_log 2>&1
fail_if_error $? "/tmp/grafana_install_log" -2


echo " - Starting Grafana"
/usr/local/lib/grafana/bin/grafana-server -homepath /usr/local/lib/grafana/ > /tmp/grafana_run_log 2>&1 &
export GRAFANA_PROC_ID=$!

echo " - Checking Grafana startup"
sleep 10
if ! kill -0 $GRAFANA_PROC_ID > /dev/null 2>&1; then
    echo " !! Failed to start Grafana !!"
    cat /tmp/grafana_run_log
    exit 8
fi

echo " - Stopping Grafana"
kill -15 $GRAFANA_PROC_ID
export GRAFANA_PROC_ID=-1

echo " - Moving grafana plugins to grafana directory"
sudo mv /var/lib/grafana/plugins /usr/local/lib/grafana/

echo " - Cleaning build directory"
cd $saved_dir
rm -Rf /tmp/grafana_setup


# Caution : the in container setup script must mandatorily finish with this log"
echo "$IN_CONTAINER_INSTALL_SUCESS_MESSAGE"