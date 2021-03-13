#!/usr/bin/env bash

#
# This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
# well to this individual file than to the Eskimo Project as a whole.
#
# Copyright 2019 - 2021 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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
cd $SCRIPT_DIR

# Loading topology
loadTopology

# Defining topology variables
if [[ $SELF_NODE_NUMBER == "" ]]; then
    echo " - No Self Node Number foundsetupCommon.sh in topology"
    exit -1
fi

if [[ $SELF_IP_ADDRESS == "" ]]; then
    echo " - No Self IP address found in topology for node $SELF_NODE_NUMBER"
    exit -2
fi


# reinitializing log
sudo rm -f flink_worker_install_log

# build

# Initially this was a Hack for BTRFS support :
#   - need to unmount gluster shares otherwise cp command goes nuts
#   - https://github.com/moby/moby/issues/38252
# But eventually I need to do this in anyway to make sure everything is preoperly re-installed
# I need to make sure I'm doing this before attempting to recreate the directories
preinstall_unmount_gluster_share /var/lib/flink/data
preinstall_unmount_gluster_share /var/lib/flink/completed_jobs

echo " - Configuring host flink common part"
. ./setupCommon.sh
if [[ $? != 0 ]]; then
    echo "Common configuration part failed !"
    exit -20
fi

echo " - Installing setupFlinkGlusterShares.sh to /usr/local/sbin"
sudo cp setupFlinkGlusterShares.sh /usr/local/sbin/
sudo chmod 755 /usr/local/sbin/setupFlinkGlusterShares.sh


echo " - Building docker container for flink worker"
build_container flink-worker flink flink_worker_install_log

# create and start container
echo " - Running docker container to configure flink worker"
docker run \
        -v $PWD:/scripts \
        -v $PWD/../common:/common \
        -d \
        -v /var/log/flink:/var/log/flink \
        -v /var/lib/flink:/var/lib/flink \
        --mount type=bind,source=/etc/eskimo_topology.sh,target=/etc/eskimo_topology.sh \
        --name flink-worker \
        -i \
        -t eskimo:flink-worker bash >> flink_worker_install_log 2>&1
fail_if_error $? "flink_worker_install_log" -2

# connect to container
#docker exec -it flink-worker bash

echo " - Configuring flink-worker container (config script)"
docker exec flink-worker bash /scripts/inContainerSetupFlinkCommon.sh $flink_user_id \
        | tee -a flink_worker_install_log 2>&1
if [[ `tail -n 1 flink_worker_install_log` != " - In container config SUCCESS" ]]; then
    echo " - In container setup script ended up in error"
    cat flink_worker_install_log
    exit -100
fi

echo " - Configuring flink-worker container"
docker exec flink-worker bash /scripts/inContainerSetupFlinkWorker.sh  $SELF_IP_ADDRESS | tee -a flink_worker_install_log 2>&1
if [[ `tail -n 1 flink_worker_install_log` != " - In container config SUCCESS" ]]; then
    echo " - In container setup script ended up in error"
    cat flink_worker_install_log
    exit -101
fi

#echo " - TODO"
#docker exec -it flink-worker TODO/tmp/logstash_install_log


echo " - Copying Topology Injection Script (common)"
docker_cp_script inContainerInjectTopology.sh sbin flink-worker flink_worker_install_log

echo " - Copying settingsInjector.sh Script"
docker_cp_script settingsInjector.sh sbin flink-worker flink_worker_install_log


echo " - Committing changes to local template and exiting container flink"
commit_container flink-worker flink_worker_install_log

# TODO
#echo " - Copying flink command line programs docker wrappers to /usr/local/bin"
#for i in `find ./flink_wrappers -mindepth 1`; do
#    sudo cp $i /usr/local/bin
#    filename=`echo $i | cut -d '/' -f 3`
#    sudo chmod 755 /usr/local/bin/$filename
#done

echo " - Installing and checking systemd service file"
install_and_check_service_file flink-worker flink_worker_install_log

echo " - Creating flink-workers containers cleaner"
sudo bash -c 'echo "for i in \`docker ps -aq --no-trunc -f status=exited -f ancestor=eskimo:flink-worker\` ; do docker rm --force \$i; done" > /usr/local/sbin/clean-flink-worker-containers.sh'
sudo chmod 755 /usr/local/sbin/clean-flink-worker-containers.sh


if [[ `sudo crontab -u root -l 2>/dev/null | grep clean-flink-worker-containers.sh` == "" ]]; then
    echo " - Scheduling flink-workers containers cleaner"
    sudo rm -f /tmp/crontab
    sudo bash -c "crontab -u root -l >> /tmp/crontab 2>/dev/null"
    sudo bash -c "echo \"* * * * * /bin/bash /usr/local/sbin/clean-flink-worker-containers.sh >> /var/log/flink/clean-flink-worker-containers.log 2>&1\" >> /tmp/crontab"
    sudo crontab -u root /tmp/crontab
fi
