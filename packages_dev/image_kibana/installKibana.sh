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

echo "-- INSTALLING KIBANA ---------------------------------------------------------"

if [ -z "$ES_VERSION" ]; then
    echo "Need to set ES_VERSION environment variable before calling this script !"
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
rm -Rf /tmp/kb_setup
mkdir -p /tmp/kb_setup
cd /tmp/kb_setup || (echo "Couldn't change to /tmp/kb_setup" && exit 200)

echo " - Downloading kibana-$ES_VERSION"
wget https://artifacts.elastic.co/downloads/kibana/kibana-$ES_VERSION-linux-x86_64.tar.gz > /tmp/kb_install_log 2>&1
if [[ $? != 0 ]]; then
    echo " -> Failed to downolad kibana-$ES_VERSION from https://artifacts.elastic.co/downloads/. Trying to download from niceideas.ch"
    wget https://niceideas.ch/mes/kibana-$ES_VERSION-linux-x86_64.tar.gz >> /tmp/kb_install_log 2>&1
    fail_if_error $? "/tmp/kb_install_log" -1
fi

echo " - Extracting kibana-$ES_VERSION"
tar -xvf kibana-$ES_VERSION-linux-x86_64.tar.gz > /tmp/kb_install_log 2>&1
fail_if_error $? "/tmp/kb_install_log" -2

echo " - Installing Kibana"
sudo chown root.staff -R kibana-$ES_VERSION
sudo mv kibana-$ES_VERSION /usr/local/lib/kibana-$ES_VERSION
sudo mkdir -p /usr/local/lib/kibana-$ES_VERSION/optimize/
sudo chmod -R 777 /usr/local/lib/kibana-$ES_VERSION/optimize/
sudo mkdir -p /usr/local/lib/kibana-$ES_VERSION/data
sudo chmod -R 777 /usr/local/lib/kibana-$ES_VERSION/data

echo " - symlinking /usr/local/lib/kibana/ to /usr/local/lib/kibana-$ES_VERSION/"
sudo ln -s /usr/local/lib/kibana-$ES_VERSION /usr/local/lib/kibana

#echo " - Installing nodejs and npm (required to build plugins)"
#
#echo " - Configuring nodejs repo"
#curl -sL https://deb.nodesource.com/setup_10.x | sudo -E bash -  > /tmp/kb_install_log 2>&1
#fail_if_error $? "/tmp/kb_install_log" -2
#
#echo " - Installing nodejs"
#apt-get -y install nodejs  > /tmp/kb_install_log 2>&1
#fail_if_error $? "/tmp/kb_install_log" -2
#
#echo " - Downloading sankey plugin"
#sudo rm -Rf /tmp/installsankey
#mkdir /tmp/installsankey
#cd /tmp/installsankey
#wget https://github.com/uniberg/kbn_sankey_vis/archive/$ES_VERSION.zip > /tmp/kb_install_log 2>&1
#fail_if_error $? "/tmp/kb_install_log" -2
#
#echo " - Extracing sankey plugin"
#unzip $ES_VERSION.zip > /tmp/kb_install_log 2>&1
#fail_if_error $? "/tmp/kb_install_log" -2
#
#cd kbn_sankey_vis-$ES_VERSION
#
#echo " - Building sankey plugin"
#npm install --production > /tmp/kb_install_log 2>&1
#fail_if_error $? "/tmp/kb_install_log" -2
#
#echo " - Installing sankey plugin"
#rm -Rf test
#sudo cp -R ../kbn_sankey_vis-$ES_VERSION /usr/local/lib/kibana/plugins/kbn_sankey_vis > /tmp/kb_install_log 2>&1
#fail_if_error $? "/tmp/kb_install_log" -2
#
#echo " - HACK - Fixing version in sankey plugin"
#sudo sed -i s/"\"version\": \"7.3.0\""/"\"version\": \"$ES_VERSION\""/g /usr/local/lib/kibana/plugins/kbn_sankey_vis/package.json
## FIXME HACK - use a REGEXP
#sudo sed -i s/"\"version\": \"7.6.3\""/"\"version\": \"$ES_VERSION\""/g /usr/local/lib/kibana/plugins/kbn_sankey_vis/package.json

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
trap check_stop_es_kibana ERR

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

# ES 6.x
#/usr/local/lib/kibana-$ES_VERSION/bin/kibana  > /tmp/kibana_run_log 2>&1 &

# ES 7.x
/usr/local/lib/kibana-$ES_VERSION/bin/kibana --allow-root > /tmp/kibana_run_log 2>&1 &

export KIBANA_PROC_ID=$!

echo " - Checking Kibana startup"
sleep 10
if ! kill -0 $KIBANA_PROC_ID > /dev/null 2>&1; then
    echo " !! Failed to start Kibana !!"
    cat /tmp/kibana_run_log
    exit 8
fi

echo " - Stopping Kibana"
kill -15 $KIBANA_PROC_ID
export KIBANA_PROC_ID=-1

#echo " - Stopping Elasticsearch"
#kill -15 $ES_PROC_ID
#export ES_PROC_ID=-1

echo " - Cleaning build directory"
sudo rm -Rf /tmp/kb_setup
returned_to_saved_dir


# Caution : the in container setup script must mandatorily finish with this log"
echo "$IN_CONTAINER_INSTALL_SUCESS_MESSAGE"