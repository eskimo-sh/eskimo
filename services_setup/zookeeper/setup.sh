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

echoerr() { echo "$@" 1>&2; }

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $SCRIPT_DIR/common.sh "$@"

# CHange current folder to script dir (important !)
cd $SCRIPT_DIR || exit 199


# Loading topology
loadTopology

# Defining topology variables
if [[ $SELF_NODE_NUMBER == "" ]]; then
    echo " - No Self Node Number found in topology"
    exit 1
fi

if [[ $SELF_IP_ADDRESS == "" ]]; then
    echo " - No Self IP address found in topology for node $SELF_NODE_NUMBER"
    exit 2
fi

export MASTER_IP_ADDRESS=$MASTER_ZOOKEEPER_1
if [[ $MASTER_IP_ADDRESS == "" ]]; then
    echo " - No zookeeper master found in topology"
    exit 3
fi

# reinitializing log
sudo rm -f zk_install_log

# build
echo " - Building docker container"
build_container zookeeper zookeeper zk_install_log
#save tag
CONTAINER_TAG=$CONTAINER_NEW_TAG

# Create shared dir
sudo mkdir -p /var/log/zookeeper
sudo chmod 777 /var/log/zookeeper
sudo mkdir -p /var/lib/zookeeper
sudo chmod 777 /var/lib/zookeeper

# create and start container
echo " - Running docker container"
docker run \
        -v $PWD:/scripts \
        -v $PWD/../common:/common \
        -p 2181:2181 \
        -p 2888:2888 \
        -p 3888:3888 \
        -v /var/log/zookeeper:/var/log/zookeeper \
        -v /var/run/zookeeper:/var/run/zookeeper \
        -v /var/lib/zookeeper:/var/lib/zookeeper \
        --mount type=bind,source=/etc/eskimo_topology.sh,target=/etc/eskimo_topology.sh \
        -d --name zookeeper \
        -i \
        -t eskimo/zookeeper:$CONTAINER_TAG bash >> zk_install_log 2>&1
fail_if_error $? "zk_install_log" -2

echo " - Configuring zookeeper container"
docker exec zookeeper bash /scripts/inContainerSetupZookeeper.sh | tee -a zk_install_log 2>&1
check_in_container_config_success zk_install_log

echo " - Handling Eskimo Base Infrastructure"
handle_eskimo_base_infrastructure zookeeper zk_install_log

echo " - Handling topology infrastructure"
handle_topology_infrastructure zookeeper zk_install_log

echo " - Committing changes to local template and exiting container zookeeper"
commit_container zookeeper $CONTAINER_TAG zk_install_log

echo " - Copying zookeeper command line programs docker wrappers to /usr/local/bin"
for i in $(find ./zookeeper_wrappers -mindepth 1); do
    sudo cp $i /usr/local/bin
    filename=$(echo $i | cut -d '/' -f 3)
    sudo chmod 755 /usr/local/bin/$filename
done

echo " - Linking zookeeperCli.sh to zkCli.sh"
sudo ln -s /usr/local/bin/zkCli.sh /usr/local/bin/zookeeperCli.sh

echo " - Handling zookeeper systemd file"
install_and_check_service_file zookeeper zk_install_log
