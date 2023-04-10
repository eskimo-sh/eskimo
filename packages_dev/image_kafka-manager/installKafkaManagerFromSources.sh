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


echo "-- INSTALLING KAFKA MANAGER ----------------------------------------------------"

if [ -z "$KAFKA_MANAGER_VERSION" ]; then
    echo "Need to set KAFKA_MANAGER_VERSION environment variable before calling this script !"
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
rm -Rf /tmp/kafka_manager_source_setup
mkdir -p /tmp/kafka_manager_source_setup
rm -Rf /tmp/kafka_manager_setup
mkdir -p /tmp/kafka_manager_setup
cd /tmp/kafka_manager_source_setup || (echo "Couldn't change to /tmp/kafka_manager_source_setup" && exit 200)

echo " - Downloading kafka-manager-$KAFKA_MANAGER_VERSION"
wget https://github.com/yahoo/CMAK/archive/$KAFKA_MANAGER_VERSION.zip > /tmp/kafka_manager_install_log 2>&1
if [[ $? != 0 ]]; then
    echo " -> Failed to downolad kafka-manager-$KAFKA_MANAGER_VERSION from github. Trying to download from niceideas.ch"
    wget https://niceideas.ch/mes/CMAK-$KAFKA_MANAGER_VERSION.zip  >> /tmp/kafka_manager_install_log 2>&1
    fail_if_error $? "/tmp/kafka_manager_install_log" -1
fi

mv $KAFKA_MANAGER_VERSION.zip CMAK-$KAFKA_MANAGER_VERSION.zip || 0

echo " - Extracting CMAK-$KAFKA_MANAGER_VERSION"
unzip CMAK-$KAFKA_MANAGER_VERSION.zip > /tmp/kafka_manager_install_log 2>&1
fail_if_error $? "/tmp/kafka_manager_install_log" -2

cd ./CMAK-$KAFKA_MANAGER_VERSION/ || (echo "Couldn't cd to ./CMAK-$KAFKA_MANAGER_VERSION/" && exit 1)

echo " - !! HACK : PATCHING build.sbt for zookeeper 3.4.x"
patch < /scripts/patch-for-zookeeper-3.4.diff > /tmp/kafka_manager_install_log 2>&1
fail_if_error $? "/tmp/kafka_manager_install_log" -1

echo " - Building source tree (./sbt clean dist) (this can take a few minutes)"
./sbt clean dist > /tmp/kafka_manager_install_log 2>&1
fail_if_error $? "/tmp/kafka_manager_install_log" -1


mv /tmp/kafka_manager_source_setup/CMAK-$KAFKA_MANAGER_VERSION/target/universal/cmak-$KAFKA_MANAGER_VERSION.zip \
   /tmp/kafka_manager_setup/kafka-manager-$KAFKA_MANAGER_VERSION.zip
cd /tmp/kafka_manager_setup || (echo "Couldn't cd to /tmp/kafka_manager_setup " && exit 1)


echo " - Extracting kafka-manager-$KAFKA_MANAGER_VERSION"
unzip kafka-manager-$KAFKA_MANAGER_VERSION.zip > /tmp/kafka_manager_install_log 2>&1
fail_if_error $? "/tmp/kafka_manager_install_log" -2

echo " - Installing kafka_managser"
sudo chown root.staff -R cmak-$KAFKA_MANAGER_VERSION
sudo mv cmak-$KAFKA_MANAGER_VERSION /usr/local/lib/kafka-manager-$KAFKA_MANAGER_VERSION

echo " - symlinking /usr/local/lib/kafka-manager/ to /usr/local/lib/kafka-manager-$KAFKA_MANAGER_VERSION"
sudo ln -s /usr/local/lib/kafka-manager-$KAFKA_MANAGER_VERSION /usr/local/lib/kafka-manager

echo " - symlinking kadka-manager to cmak exec"
cd /usr/local/lib/kafka-manager/bin/ || (echo "Couldn't cd to /usr/local/lib/kafka-manager/bin/" && exit 1)
sudo ln -s cmak kafka-manager
cd /tmp/kafka_manager_setup || (echo "Couldn't cd to /tmp/kafka_manager_setup" && exit 1)

echo " - Build cleanup"
sudo rm -Rf /root/.ivy2 >> /tmp/kafka_manager_install_log 2>&1
sudo rm -Rf /root/.sbt >> /tmp/kafka_manager_install_log 2>&1

# installation tests

echo " - Registering test cleaning traps"
export KAFKA_MANAGER_PROC_ID=-1
function check_stop_kafka_manager(){
    if [[ $KAFKA_MANAGER_PROC_ID != -1 ]]; then
        echo " - Stopping Kafka Manager !!"
        kill -15 $KAFKA_MANAGER_PROC_ID
    fi
}
trap check_stop_kafka_manager 15
trap check_stop_kafka_manager EXIT
trap check_stop_kafka_manager ERR


echo " - Starting Kafka Manager"
export ZK_HOSTS=localhost:2181

/usr/local/lib/kafka-manager-$KAFKA_MANAGER_VERSION/bin/kafka-manager \
    -Dapplication.home=/usr/local/lib/kafka-manager-$KAFKA_MANAGER_VERSION/ \
    -Dpidfile.path=/tmp/kafka-manager.pid \
    -Dhttp.port=22080 \
    > /tmp/kafka_manager_install_log 2>&1 &
export KAFKA_MANAGER_PROC_ID=$!

echo " - Checking Kafka Manager startup"
sleep 3
if ! kill -0 $KAFKA_MANAGER_PROC_ID > /dev/null 2>&1; then
    echo " !! Failed to start Kafka Manager!!"
    exit 8
fi


echo " - Stopping Kafka Manager"
kill -15 $KAFKA_MANAGER_PROC_ID
export KAFKA_MANAGER_PROC_ID=-1

rm -Rf /tmp/kafka-manager.pid

echo " - Cleaning build folder"
cd $saved_dir

rm -Rf /tmp/kafka_manager_setup > /tmp/kafka_manager_install_log 2>&1
fail_if_error $? "/tmp/kafka_manager_install_log" -10

rm -Rf /tmp/kafka_manager_source_setup > /tmp/kafka_manager_install_log 2>&1
fail_if_error $? "/tmp/kafka_manager_install_log" -10

echo " - Cleaning coursier cache"
sudo rm -Rf /root/.cache/coursier


# Caution : the in container setup script must mandatorily finish with this log"
echo "$IN_CONTAINER_INSTALL_SUCESS_MESSAGE"




