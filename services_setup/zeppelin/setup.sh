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

# reinitializing log
sudo rm -f zeppelin_install_log

# Initially this was a Hack for BTRFS support :
#   - need to unmount gluster shares otherwise cp command goes nuts
#   - https://github.com/moby/moby/issues/38252
# But eventually I need to do this in anyway to make sure everything is preoperly re-installed
# I need to make sure I'm doing this before attempting to recreate the directories
#preinstall_unmount_gluster_share /var/lib/spark/eventlog
#preinstall_unmount_gluster_share /var/lib/spark/data
#preinstall_unmount_gluster_share /var/lib/flink/data
#preinstall_unmount_gluster_share /var/lib/flink/completed_jobs
#preinstall_unmount_gluster_share /var/lib/elasticsearch/logstash/data

echo " - Configuring host spark config part"
. ./setupSparkCommon.sh
if [[ $? != 0 ]]; then
    echo "Spark Common configuration part failed !"
    exit 10
fi

echo " - Configuring host flink config part"
. ./setupFlinkCommon.sh
if [[ $? != 0 ]]; then
    echo "FLink Common configuration part failed !"
    exit 11
fi

echo " - Configuring host kafka config part"
. ./setupKafkaCommon.sh
if [[ $? != 0 ]]; then
    echo "Kafka Common configuration part failed !"
    exit 11
fi

echo " - Configuring host ElasticSearch config part"
. ./setupESCommon.sh
if [[ $? != 0 ]]; then
    echo "ES Common configuration part failed !"
    exit 12
fi

echo " - Configuring host logstash config part"
. ./setupLogstashCommon.sh
if [[ $? != 0 ]]; then
    echo "Logstash Common configuration part failed !"
    exit 13
fi


echo " - Building container zeppelin"
build_container zeppelin zeppelin zeppelin_install_log
#save tag
CONTAINER_TAG=$CONTAINER_NEW_TAG

# create and start container
echo " - Running docker container"
docker run \
        -v $PWD:/scripts \
        -v $PWD/../common:/common \
        -d --name zeppelin \
        -v /var/log/spark:/var/log/spark \
        -v /var/run/spark:/var/run/spark \
        -v /var/lib/spark:/var/lib/spark:rshared \
        -v /usr/local/lib:/usr/local/host_lib:ro \
        --mount type=bind,source=/etc/eskimo_topology.sh,target=/etc/eskimo_topology.sh \
        -it \
        eskimo/zeppelin:$CONTAINER_TAG bash >> zeppelin_install_log 2>&1
fail_if_error $? "zeppelin_install_log" -2

#        -v /var/lib/zeppelin:/var/lib/zeppelin \
#        -v /usr/local/etc/zeppelin:/usr/local/etc/zeppelin \

echo " - Configuring zeppelin container - spark common part"
docker exec zeppelin bash /scripts/inContainerSetupSparkCommon.sh $spark_user_id | tee zeppelin_install_log 2>&1
check_in_container_config_success zeppelin_install_log

echo " - Configuring zeppelin container - flink common part"
docker exec zeppelin bash /scripts/inContainerSetupFlinkCommon.sh $flink_user_id | tee zeppelin_install_log 2>&1
check_in_container_config_success zeppelin_install_log

echo " - Configuring zeppelin container - kafka common part"
docker exec zeppelin bash /scripts/inContainerSetupKafkaCommon.sh $kafka_user_id | tee zeppelin_install_log 2>&1
check_in_container_config_success zeppelin_install_log

echo " - Configuring zeppelin container - kafka wrappers"
docker exec zeppelin bash /scripts/inContainerSetupKafkaWrappers.sh | tee zeppelin_install_log 2>&1
check_in_container_config_success zeppelin_install_log

echo " - Configuring zeppelin container"
docker exec zeppelin bash /scripts/inContainerSetupZeppelin.sh | tee zeppelin_install_log 2>&1
check_in_container_config_success zeppelin_install_log

echo " - Copying Topology Injection Script (Spark)"
docker_cp_script inContainerInjectTopologySpark.sh sbin zeppelin zeppelin_install_log

echo " - Copying Topology Injection Script (Flink)"
docker_cp_script inContainerInjectTopologyFlink.sh sbin zeppelin zeppelin_install_log

echo " - Copying Service Start Script"
docker_cp_script inContainerStartService.sh sbin zeppelin zeppelin_install_log

echo " - Copying Topology Injection Script (Zeppelin)"
docker_cp_script inContainerInjectTopologyZeppelin.sh sbin zeppelin zeppelin_install_log

echo " - Handling Eskimo Base Infrastructure"
handle_eskimo_base_infrastructure zeppelin zeppelin_install_log

# if /usr/local/bin/logstash-cli is found, then copy it to container
if [[ -f /usr/local/bin/logstash-cli ]]; then

    echo " - Copying logstash command client to zeppelin container"
    docker cp /usr/local/bin/logstash-cli zeppelin:/usr/local/bin/logstash-cli >> zeppelin_install_log 2>&1
    fail_if_error $? zeppelin_install_log -31

    docker exec --user root zeppelin bash -c "chmod 755 /usr/local/bin/logstash-cli" >> zeppelin_install_log 2>&1
    fail_if_error $? zeppelin_install_log -24
fi


# XXX Hack required for zeppelin pre-0.9 bug where notebooks imported through APIs are not anymore available after a restart
#echo " - HACK import of raw samples archived in docker container"
#docker cp ./HACK_temp_samples/eskimo_samples.tgz zeppelin:/usr/local/lib/zeppelin/ >> zeppelin_install_log 2>&1
#fail_if_error $? "zeppelin_install_log" -40

#docker exec --user root zeppelin bash -c "chmod 755 /usr/local/lib/zeppelin/eskimo_samples.tgz" >> zeppelin_install_log 2>&1
#fail_if_error $? "zeppelin_install_log" -41

echo " - Import of samples in docker container"
docker cp ./samples zeppelin:/usr/local/lib/zeppelin/eskimo_samples >> zeppelin_install_log 2>&1
fail_if_error $? "zeppelin_install_log" -40


echo " - Committing changes to local template and exiting container zeppelin"
commit_container zeppelin $CONTAINER_TAG zeppelin_install_log


echo " - Starting Kubernetes deployment"
deploy_kubernetes zeppelin $CONTAINER_TAG zeppelin_install_log



