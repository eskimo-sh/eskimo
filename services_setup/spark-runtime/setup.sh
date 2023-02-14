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
sudo rm -f spark_executor_install_log

# build

# Initially this was a Hack for BTRFS support :
#   - need to unmount gluster shares otherwise cp command goes nuts
#   - https://github.com/moby/moby/issues/38252
# But eventually I need to do this in anyway to make sure everything is preoperly re-installed
# I need to make sure I'm doing this before attempting to recreate the directories
preinstall_unmount_gluster_share /var/lib/spark/eventlog
preinstall_unmount_gluster_share /var/lib/spark/data

echo " - Configuring host spark common part"
. ./setupCommon.sh
if [[ $? != 0 ]]; then
    echo "Common configuration part failed !"
    exit 20
fi


# spark common template part
# ----------------------------------------------------------------------------------------------------------------------
echo " ---> Spark common template part ----------------------------------------------------"

echo " - Building docker container for spark"
build_container spark spark spark_executor_install_log
#save tag
export COMMON_CONTAINER_TAG=$CONTAINER_NEW_TAG

# create and start container
echo " - Running docker container to configure spark"
docker run \
        -v $PWD:/scripts \
        -v $PWD/../common:/common \
        --mount type=bind,source=/etc/eskimo_topology.sh,target=/etc/eskimo_topology.sh \
        -d \
        --name spark \
        -it \
        eskimo/spark:$COMMON_CONTAINER_TAG bash >> spark_executor_install_log 2>&1
fail_if_error $? "spark_executor_install_log" -2

echo " - Getting in advance next spark-runtime tag"
NEXT_SPARK_RUNTIME_TAG=$(($(get_last_tag spark-runtime) + 1))

echo " - Configuring spark container (config script)"
docker exec spark bash /scripts/inContainerSetupSparkCommon.sh $spark_user_id $NEXT_SPARK_RUNTIME_TAG \
        | tee -a spark_executor_install_log 2>&1
check_in_container_config_success spark_executor_install_log

echo " - Configuring spark container"
docker exec spark bash /scripts/inContainerSetupSpark.sh | tee -a spark_executor_install_log 2>&1
check_in_container_config_success spark_executor_install_log

echo " - Copying Spark entrypoint script"
docker_cp_script eskimo-spark-entrypoint.sh sbin spark spark_executor_install_log

echo " - Handling Eskimo Base Infrastructure"
handle_eskimo_base_infrastructure spark spark_executor_install_log

echo " - Copying Topology Injection Script (common)"
docker_cp_script inContainerInjectTopology.sh sbin spark spark_executor_install_log

echo " - Committing changes to local template and exiting container spark"
commit_container spark $COMMON_CONTAINER_TAG spark_executor_install_log

echo " - Deploying spark Service in docker registry for kubernetes"
docker tag eskimo/spark:$COMMON_CONTAINER_TAG kubernetes.registry:5000/spark:$COMMON_CONTAINER_TAG >> spark_executor_install_log 2>&1
if [[ $? != 0 ]]; then
    echo "   + Could not re-tag kubernetes container image for spark"
    exit 4
fi

docker push kubernetes.registry:5000/spark:$COMMON_CONTAINER_TAG >> spark_executor_install_log 2>&1
if [[ $? != 0 ]]; then
    echo "Image push in docker registry failed !"
    exit 5
fi


# spark executor part
# ----------------------------------------------------------------------------------------------------------------------
echo " ---> Spark executor part ----------------------------------------------------"

set -e

echo " - Creating sub-setup dir for spark-executor"
/bin/mkdir spark_executor_setup

echo " - Copying specific docker file to spark-executor setup"
envsubst < Dockerfile.spark-runtime > spark_executor_setup/Dockerfile

echo " - Changing dir to spark-executor setup"
cd spark_executor_setup/

set +e

echo " - Building docker container for spark runtime (specific)"
build_container spark-runtime spark spark_executor_install_log
#save tag
RUNTIME_CONTAINER_TAG=$CONTAINER_NEW_TAG

echo " - Deploying spark runtime in docker registry for kubernetes"
deploy_registry spark-runtime $RUNTIME_CONTAINER_TAG spark_executor_install_log

cd ..

echo " - removing local image for common part"
docker image rm eskimo/spark:$COMMON_CONTAINER_TAG  >> spark_executor_install_log 2>&1
if [[ $? != 0 ]]; then
    echo "local image removal failed !"
    exit 6
fi

