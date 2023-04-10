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


echo "-- INSTALLING KAFKA ----------------------------------------------------"

if [ -z "$KAFKA_VERSION" ]; then
    echo "Need to set KAFKA_VERSION environment variable before calling this script !"
    exit 1
fi
if [ -z "$SCALA_VERSION" ]; then
    echo "Need to set SCALA_VERSION environment variable before calling this script !"
    exit 2
fi

saved_dir=$(pwd)
function returned_to_saved_dir() {
     cd $saved_dir || return
}
trap returned_to_saved_dir 15
trap returned_to_saved_dir EXIT
trap returned_to_saved_dir ERR

echo " - Changing to temp directory"
rm -Rf /tmp/kafka_setup
mkdir -p /tmp/kafka_setup
cd /tmp/kafka_setup || (echo "Couldn't change to /tmp/kafka_setup" && exit 11)

echo " - Downloading kafka-$KAFKA_VERSION"
wget https://archive.apache.org/dist/kafka/$KAFKA_VERSION/kafka_$SCALA_VERSION-$KAFKA_VERSION.tgz > /tmp/kafka_install_log 2>&1
if [[ $? != 0 ]]; then
    echo " -> Failed to downolad kafka-$KAFKA_VERSION from https://www.apache.org/. Trying to download from niceideas.ch"
    wget https://niceideas.ch/mes/kafka_$SCALA_VERSION-$KAFKA_VERSION.tgz  >> /tmp/kafka_install_log 2>&1
    fail_if_error $? "/tmp/kafka_install_log" -1
fi


echo " - Extracting kafka-$KAFKA_VERSION"
tar -xvf kafka_$SCALA_VERSION-$KAFKA_VERSION.tgz > /tmp/kafka_install_log 2>&1
fail_if_error $? "/tmp/kafka_install_log" -2

echo " - Installing kafka"
sudo chown root.staff -R kafka_$SCALA_VERSION-$KAFKA_VERSION
sudo mv kafka_$SCALA_VERSION-$KAFKA_VERSION /usr/local/lib

sudo mkdir -p /usr/local/lib/kafka_$SCALA_VERSION-$KAFKA_VERSION/data
sudo chmod -R 777 /usr/local/lib/kafka_$SCALA_VERSION-$KAFKA_VERSION/data
sudo mkdir -p /usr/local/lib/kafka_$SCALA_VERSION-$KAFKA_VERSION/logs
sudo chmod -R 777 /usr/local/lib/kafka_$SCALA_VERSION-$KAFKA_VERSION/logs
sudo chmod -R 777 /usr/local/lib/kafka_$SCALA_VERSION-$KAFKA_VERSION/config/

echo " - Installing kafka python client"
sudo pip install kafka-python > /tmp/kafka_install_log 2>&1

echo " - symlinking /usr/local/lib/kafka/ to /usr/local/lib/kafka_$SCALA_VERSION-$KAFKA_VERSION/"
sudo ln -s /usr/local/lib/kafka_$SCALA_VERSION-$KAFKA_VERSION /usr/local/lib/kafka

echo " - Cleanup unused files"
sudo rm -Rf /usr/local/lib/kafka/site-docs/*

echo " - Checking Kafka Installation"
/usr/local/lib/kafka/bin/kafka-server-start.sh /usr/local/lib/kafka/config/server.properties > /tmp/kafka_run_log 2>&1 &
EXAMPLE_PID=$!
fail_if_error $? "/tmp/kafka_run_log" -3
sleep 5
if ! kill -0 $EXAMPLE_PID > /dev/null 2>&1; then
    echo "Kafka process not started successfully !"
    exit 10
fi


echo " - Cleaning build directory"
cd $saved_dir || (echo "Couldn't change to $saved_dir" && exit 12)
rm -Rf /tmp/kafka_setup


# Caution : the in container setup script must mandatorily finish with this log"
echo "$IN_CONTAINER_INSTALL_SUCESS_MESSAGE"