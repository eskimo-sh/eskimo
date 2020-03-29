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


echo "-- INSTALLING ES-HADOOP (Spark to ES connector) -------------------------------"

if [ -z "$ES_VERSION" ]; then
    echo "Need to set ES_VERSION environment variable before calling this script !"
    exit 1
fi
if [ -z "$SPARK_VERSION" ]; then
    echo "Need to set SPARK_VERSION environment variable before calling this script !"
    exit 1
fi

saved_dir=`pwd`
function returned_to_saved_dir() {
     cd $saved_dir
}
trap returned_to_saved_dir 15
trap returned_to_saved_dir EXIT

echo " - Changing to temp directory"
mkdir -p /tmp/eshadoop_setup
cd /tmp/eshadoop_setup

echo " - Downloading elasticsearch-hadoop-$ES_VERSION"
wget https://artifacts.elastic.co/downloads/elasticsearch-hadoop/elasticsearch-hadoop-$ES_VERSION.zip > /tmp/esh_install_log 2>&1
if [[ $? != 0 ]]; then
    echo " -> Failed to downolad elasticsearch-hadoop-$ES_VERSION from http://download.elastic.co/hadoop/. Trying to download from niceideas.ch"
    exit -1
    #wget http://niceideas.ch/mes/elasticsearch-hadoop-$ES_VERSION.zip >> /tmp/esh_install_log 2>&1
    #fail_if_error $? "/tmp/esh_install_log" -1
fi

echo " - Extracting elasticsearch-hadoop-$ES_VERSION"
unzip -f elasticsearch-hadoop-$ES_VERSION.zip > /tmp/spark_install_log 2>&1
fail_if_error $? "/tmp/spark_install_log" -2

echo " - Installing eshadoop spark (to spark jar folder)"
sudo chown root.staff -R elasticsearch-hadoop-$ES_VERSION
sudo cp elasticsearch-hadoop-$ES_VERSION/dist/elasticsearch-spark-20_2.11-$ES_VERSION.jar /usr/local/lib/spark-$SPARK_VERSION/jars/
if [[ $? != 0 ]]; then
    echo " -> Failed to find jar elasticsearch-spark-20_2.11-$ES_VERSION.jar - name must have change ..."
    exit -2
fi

sudo rm -Rf /tmp/spark_setup
returned_to_saved_dir




# Caution : the in container setup script must mandatorily finish with this log"
echo " - In container install SUCCESS"