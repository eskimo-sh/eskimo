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

sudo rm -Rf /tmp/cerebro_install_log

echo "-- INSTALLING CEREBRO ---------------------------------------------------------"

if [ -z "$ES_VERSION" ]; then
    echo "Need to set ES_VERSION environment variable before calling this script !"
    exit 1
fi
if [ -z "$CEREBRO_VERSION" ]; then
    echo "Need to set CEREBRO_VERSION environment variable before calling this script !"
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
sudo rm -Rf /tmp/cerebro_setup/
mkdir -p /tmp/cerebro_setup/
cd /tmp/cerebro_setup || (echo "Couldn't change to /tmp/cerebro_setup" && exit 200)

echo " - Downloading cerebro-$CEREBRO_VERSION"
wget https://github.com/lmenezes/cerebro/releases/download/v$CEREBRO_VERSION/cerebro-$CEREBRO_VERSION.tgz > /tmp/cerebro_install_log 2>&1
if [[ $? != 0 ]]; then
    echo " -> Failed to downolad cerebro-$CEREBRO_VERSION from https://github.com/lmenezes/cerebro. Trying to download from niceideas.ch"
    wget https://niceideas.ch/mes/cerebro-$CEREBRO_VERSION.tgz > /tmp/cerebro_install_log 2>&1
    fail_if_error $? "/tmp/cerebro_install_log" -1
fi

echo " - Extracting cerebro-$CEREBRO_VERSION"
tar -xvf cerebro-$CEREBRO_VERSION.tgz > /tmp/cerebro_install_log 2>&1
fail_if_error $? "/tmp/cerebro_install_log" -2

echo " - Installing Cerebro"
sudo chown root.staff -R cerebro-$CEREBRO_VERSION
sudo mv cerebro-$CEREBRO_VERSION /usr/local/lib/cerebro-$CEREBRO_VERSION
sudo chmod 777 /usr/local/lib/cerebro-$CEREBRO_VERSION/

echo " - symlinking /usr/local/lib/cerebro/ to /usr/local/lib/cerebro-$CEREBRO_VERSION/"
sudo ln -s /usr/local/lib/cerebro-$CEREBRO_VERSION /usr/local/lib/cerebro

echo " - Registering test cleaning traps"
export ES_PROC_ID=-1
export CEREBRO_PROC_ID=-1
function check_stop_es_cerebro(){
    if [[ $ES_PROC_ID != -1 ]]; then
        echo " - Stopping ES !!"
        kill -15 $ES_PROC_ID
    fi
    if [[ $CEREBRO_PROC_ID != -1 ]]; then
        echo " - Stopping Cerebro !!"
        kill -15 $CEREBRO_PROC_ID
    fi
}
trap check_stop_es_cerebro 15
trap check_stop_es_cerebro EXIT
trap check_stop_es_cerebro ERR

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

echo " - Starting Cerebro"
/usr/local/lib/cerebro-$CEREBRO_VERSION/bin/cerebro > /tmp/cerebro_run_log 2>&1 &
export CEREBRO_PROC_ID=$!

echo " - Checking Cerebro startup"
sleep 10
if ! kill -0 $CEREBRO_PROC_ID > /dev/null 2>&1; then
    echo " !! Failed to start Cerebro !!"
    exit 8
fi

echo " - Stopping Cerebro"
kill -15 $CEREBRO_PROC_ID
export CEREBRO_PROC_ID=-1

#echo " - Stopping Elasticsearch"
#kill -15 $ES_PROC_ID
#export ES_PROC_ID=-1

returned_to_saved_dir
sudo rm -Rf /tmp/cerebro_setup/



#Download from https://github.com/lmenezes/cerebro/releases
#Extract files
#Run bin/cerebro(or bin/cerebro.bat if on Windows)
#Access on http://localhost:9000


# Caution : the in container setup script must mandatorily finish with this log"
echo "$IN_CONTAINER_INSTALL_SUCESS_MESSAGE"
