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

echo "-- INSTALLING KIBANA ---------------------------------------------------------"

if [ -z "$ES_VERSION" ]; then
    echo "Need to set ES_VERSION environment variable before calling this script !"
    exit 1
fi


saved_dir=`pwd`
function returned_to_saved_dir() {
     cd $saved_dir
}
trap returned_to_saved_dir 15
trap returned_to_saved_dir EXIT

echo " - Changing to temp directory"
mkdir -p /tmp/kb_setup
cd /tmp/kb_setup

echo " - Downloading kibana-$ES_VERSION"
wget https://artifacts.elastic.co/downloads/kibana/kibana-$ES_VERSION-linux-x86_64.tar.gz > /tmp/kb_install_log 2>&1
if [[ $? != 0 ]]; then
    echo " -> Failed to downolad kibana-$ES_VERSION from https://artifacts.elastic.co/downloads/. Trying to download from niceideas.ch"
    wget http://niceideas.ch/mes/kibana-$ES_VERSION-linux-x86_64.tar.gz >> /tmp/kb_install_log 2>&1
    fail_if_error $? "/tmp/kb_install_log" -1
fi

echo " - Extracting kibana-$ES_VERSION"
tar -xvf kibana-$ES_VERSION-linux-x86_64.tar.gz > /tmp/kb_install_log 2>&1
fail_if_error $? "/tmp/kb_install_log" -2

echo " - Installing Kibana"
sudo chown root.staff -R kibana-$ES_VERSION-linux-x86_64
sudo mv kibana-$ES_VERSION-linux-x86_64 /usr/local/lib/kibana-$ES_VERSION
sudo mkdir -p /usr/local/lib/kibana-$ES_VERSION/optimize/
sudo chmod -R 777 /usr/local/lib/kibana-$ES_VERSION/optimize/
sudo mkdir -p /usr/local/lib/kibana-$ES_VERSION/data
sudo chmod -R 777 /usr/local/lib/kibana-$ES_VERSION/data

echo " - symlinking /usr/local/lib/kibana/ to /usr/local/lib/kibana-$ES_VERSION/"
sudo ln -s /usr/local/lib/kibana-$ES_VERSION /usr/local/lib/kibana

echo " - Registering test cleaning traps"
export ES_PROC_ID=-1
export KIBANA_PROC_ID=-1
function check_stop_es_kibana(){
    if [[ $ES_PROC_ID != -1 ]]; then
        echo " - Stopping ES !!"
        kill -15 $ES_PROC_ID
    fi
    if [[ $KIBANA_PROC_ID != -1 ]]; then
        echo " - Stopping Kibana !!"
        kill -15 $KIBANA_PROC_ID
    fi
}
trap check_stop_es_kibana 15
trap check_stop_es_kibana EXIT

#echo " - Starting ElasticSearch"
#export ES_JAVA_OPTS="-Dlog4j2.disable.jmx=true -Xmx1g -Xms800m"
#/usr/local/lib/elasticsearch-$ES_VERSION/bin/elasticsearch > /tmp/es_run_log 2>&1 &
#export ES_PROC_ID=$!

#echo " - Checking Elasticsearch startup"
#sleep 10
#if [[ `ps -e | grep $ES_PROC_ID` == "" ]]; then
#    echo " !! Failed to start Elasticsearch !!"
#    exit -8
#fi

echo " - Starting Kibana"
/usr/local/lib/kibana-$ES_VERSION/bin/kibana > /tmp/kibana_run_log 2>&1 &
export KIBANA_PROC_ID=$!

echo " - Checking Kibana startup"
sleep 10
if [[ `ps -e | grep $KIBANA_PROC_ID` == "" ]]; then
    echo " !! Failed to start Kibana !!"
    cat /tmp/kibana_run_log
    exit -8
fi

echo " - Stopping Kibana"
kill -15 $KIBANA_PROC_ID
export KIBANA_PROC_ID=-1

#echo " - Stopping Elasticsearch"
#kill -15 $ES_PROC_ID
#export ES_PROC_ID=-1

sudo rm -Rf /tmp/kb_setup
returned_to_saved_dir




# Caution : the in container setup script must mandatorily finish with this log"
echo " - In container install SUCCESS"