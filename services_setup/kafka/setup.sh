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

export ZOOKEEPER_IP_ADDRESS=$MASTER_ZOOKEEPER_1
if [[ $ZOOKEEPER_IP_ADDRESS == "" ]]; then
    echo " - No zookeeper master found in topology"
    exit 3
fi

# reinitializing log
sudo rm -f kafka_install_log

echo " - Configuring host kafka common part"
. ./setupCommon.sh
if [[ $? != 0 ]]; then
    echo "Common configuration part failed !"
    exit 5
fi

echo " - Building container kafka"
build_container kafka kafka kafka_install_log

# create and start container
echo " - Running docker container"
docker run \
        -v $PWD:/scripts \
        -v $PWD/../common:/common \
        -v /var/log/kafka:/var/log/kafka \
        -v /var/lib/kafka:/var/lib/kafka \
        --mount type=bind,source=/etc/eskimo_topology.sh,target=/etc/eskimo_topology.sh \
        -p 9092:9092 \
        -p 9093:9093 \
        -p 9999:9999 \
        -d --name kafka \
        -i \
        -t eskimo:kafka bash >> kafka_install_log 2>&1
fail_if_error $? "kafka_install_log" -2

# connect to container
#docker exec -it kafka bash

echo " - Configuring kafka container (common part)"
docker exec kafka bash /scripts/inContainerSetupKafkaCommon.sh $kafka_user_id | tee kafka_install_log 2>&1
check_in_container_config_success kafka_install_log

echo " - Configuring kafka container (wrappers part)"
docker exec kafka bash /scripts/inContainerSetupKafkaWrappers.sh  | tee kafka_install_log 2>&1
check_in_container_config_success kafka_install_log

echo " - Configuring kafka container"
docker exec kafka bash /scripts/inContainerSetupKafka.sh  | tee kafka_install_log 2>&1
check_in_container_config_success kafka_install_log

echo " - Copying inContainerMountGluster.sh script"
docker_cp_script inContainerMountGluster.sh sbin kafka kafka_install_log

echo " - Copying glusterMountChecker.sh Script"
docker_cp_script glusterMountChecker.sh sbin kafka kafka_install_log

echo " - Copying glusterMountCheckerPeriodic.sh Script"
docker_cp_script glusterMountCheckerPeriodic.sh sbin kafka kafka_install_log

echo " - Handling topology and setting injection"
handle_topology_settings kafka kafka_install_log

echo " - Committing changes to local template and exiting container kafka"
commit_container kafka kafka_install_log

echo " - Starting Kubernetes deployment"
deploy_kubernetes kafka kafka_install_log

