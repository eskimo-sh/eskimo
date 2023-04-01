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

echo "-- INSTALLING ZEPPELIN ---------------------------------------------------------"

if [ -z "$ZEPPELIN_VERSION" ]; then
    echo "Need to set ZEPPELIN_VERSION environment variable before calling this script !"
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
rm -Rf /tmp/zeppelin_setup/
mkdir -p /tmp/zeppelin_setup/
cd /tmp/zeppelin_setup/ || (echo "Couldn't change to /tmp/zeppelin_setup" && exit 200)

echo " - Downloading zeppelin-$ZEPPELIN_VERSION"
export FROM_COMPLETE="1"
wget https://downloads.apache.org/zeppelin/zeppelin-$ZEPPELIN_VERSION_FULL/zeppelin-$ZEPPELIN_VERSION_FULL-bin-all.tgz > /tmp/zeppelin_install_log 2>&1
if [[ $? != 0 ]]; then
    echo " -> Failed to downolad zeppelin-$ZEPPELIN_VERSION from apache. Trying to download from niceideas.ch"
    wget --no-check-certificate  https://www.niceideas.ch/mes/zeppelin-$ZEPPELIN_VERSION_FULL.tar.gz >> /tmp/zeppelin_install_log 2>&1
    fail_if_error $? "/tmp/zeppelin_install_log" -1
fi

echo " - Extracting zeppelin-$ZEPPELIN_VERSION"
tar -xvf zeppelin-$ZEPPELIN_VERSION*gz > /tmp/zeppelin_install_log 2>&1
fail_if_error $? "/tmp/zeppelin_install_log" -2

rm -f zeppelin-$ZEPPELIN_VERSION*gz

echo " - Installing Zeppelin"
sudo chown root.staff -R zeppelin-$ZEPPELIN_VERSION*
sudo mv zeppelin-$ZEPPELIN_VERSION* /usr/local/lib/

echo " - symlinking /usr/local/lib/zeppelin/ to /usr/local/lib/zeppelin-$ZEPPELIN_VERSION/"
sudo ln -s /usr/local/lib/zeppelin-$ZEPPELIN_VERSION* /usr/local/lib/zeppelin

if [[ $FROM_COMPLETE == "1" ]]; then

    echo " - Removing unused interpreters"
    rm -Rf /usr/local/lib/zeppelin/interpreter/alluxio
    rm -Rf /usr/local/lib/zeppelin/interpreter/beam
    rm -Rf /usr/local/lib/zeppelin/interpreter/bigquery
    rm -Rf /usr/local/lib/zeppelin/interpreter/cassandra
    rm -Rf /usr/local/lib/zeppelin/interpreter/geode
    rm -Rf /usr/local/lib/zeppelin/interpreter/hazelcastjet
    rm -Rf /usr/local/lib/zeppelin/interpreter/hbase
    rm -Rf /usr/local/lib/zeppelin/interpreter/ignite
    rm -Rf /usr/local/lib/zeppelin/interpreter/kylin
    rm -Rf /usr/local/lib/zeppelin/interpreter/lens
    rm -Rf /usr/local/lib/zeppelin/interpreter/livy
    rm -Rf /usr/local/lib/zeppelin/interpreter/neo4j
    rm -Rf /usr/local/lib/zeppelin/interpreter/pig
    rm -Rf /usr/local/lib/zeppelin/interpreter/sap
    rm -Rf /usr/local/lib/zeppelin/interpreter/scalding
    rm -Rf /usr/local/lib/zeppelin/interpreter/scio
    rm -Rf /usr/local/lib/zeppelin/interpreter/submarine
    rm -Rf /usr/local/lib/zeppelin/interpreter/sparql
    rm -Rf /usr/local/lib/zeppelin/interpreter/kotlin
    rm -Rf /usr/local/lib/zeppelin/interpreter/jupyter
    rm -Rf /usr/local/lib/zeppelin/interpreter/influxdb

else
    echo " - Installing required interpreters"
    sudo /usr/local/lib/zeppelin/bin/install-interpreter.sh --name md,shell,jdbc,python,angular,elasticsearch,flink,mongodb \
            > /tmp/zeppelin_install_log 2>&1
    fail_if_error $? "/tmp/zeppelin_install_log" -1
fi

echo " - Removing Angular version"
rm -Rf /usr/local/lib/zeppelin/zeppelin-web-angular-*

echo " - Registering test cleaning traps"
export ZEPPELIN_PROC_ID=-1
function check_stop_zeppelin(){
    if [[ $ZEPPELIN_PROC_ID != -1 ]]; then
        echo " - Stopping Zeppelin !!"
        kill -15 $ZEPPELIN_PROC_ID
    fi
}
trap check_stop_zeppelin 15
trap check_stop_zeppelin EXIT
trap check_stop_zeppelin ERR


echo " - Starting Zeppelin"
/usr/local/lib/zeppelin/bin/zeppelin.sh > /tmp/zeppelin_install_log 2>&1 &
export ZEPPELIN_PROC_ID=$!

echo " - Checking Zeppelin startup"
sleep 30
if ! kill -0 $ZEPPELIN_PROC_ID > /dev/null 2>&1; then
    echo " !! Failed to start Zeppelin !!"
    exit 8
fi

echo " - Stopping Zeppelin"
kill -15 $ZEPPELIN_PROC_ID
export ZEPPELIN_PROC_ID=-1

sleep 6

echo " - Cleaning build directory"
returned_to_saved_dir
sudo rm -Rf /tmp/zeppelin_setup/


if [[ ! -f /usr/local/lib/zeppelin/conf/interpreter.json ]]; then
   echo "PROBLEM : interpreter.json was not created !"
   exit 50
fi


# Caution : the in container setup script must mandatorily finish with this log"
echo "$IN_CONTAINER_INSTALL_SUCESS_MESSAGE"






