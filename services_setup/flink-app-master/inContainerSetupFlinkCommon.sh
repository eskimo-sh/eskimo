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

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $SCRIPT_DIR/common.sh "$@"

FLINK_USER_ID=$1
if [[ $FLINK_USER_ID == "" ]]; then
    echo " - Didn't get FLINK User ID as argument"
    exit -2
fi


echo "-- SETTING UP FLINK (COMMON PART) --------------------------------------"

echo " - Creating flink user (if not exist) in container"
set +e
flink_user_id=`id -u flink 2>/dev/null`
set -e
if [[ $flink_user_id == "" ]]; then
    useradd -u $FLINK_USER_ID flink
elif [[ $flink_user_id != $FLINK_USER_ID ]]; then
    echo "Docker FLINK USER ID is $flink_user_id while requested USER ID is $FLINK_USER_ID"
    exit -2
fi

echo " - Creating user flink home directory"
mkdir -p /home/flink
chown flink /home/flink


echo " - Simlinking flink binaries to /usr/local/bin"
for i in `ls -1 /usr/local/lib/flink/bin`; do
    create_binary_wrapper /usr/local/lib/flink/bin/$i /usr/local/bin/$i
done

echo " - Simlinking flink log to /var/log/"
sudo rm -Rf /usr/local/lib/flink/log
sudo ln -s /var/log/flink/log /usr/local/lib/flink/log


# The default directory used for storing the data files and meta data of checkpoints in a Flink supported
# filesystem.
# The storage path must be accessible from all participating processes/nodes(i.e. all TaskManagers and JobManagers).
sed -i s/"# state.savepoints.dir: hdfs:\/\/namenode-host:port\/flink-checkpoints"/"state.savepoints.dir: file:\/\/\/var\/lib\/flink\/data\/savepoints"/g \
        /usr/local/lib/flink/conf/flink-conf.yaml

# The default directory for savepoints.
# Used by the state backends that write savepoints to file systems (MemoryStateBackend, FsStateBackend, RocksDBStateBackend).
sed -i s/"# state.checkpoints.dir: hdfs:\/\/namenode-host:port\/flink-checkpoints"/"state.checkpoints.dir: file:\/\/\/var\/lib\/flink\/data\/ckeckpoints"/g \
        /usr/local/lib/flink/conf/flink-conf.yaml

# Directory to upload completed jobs to.
sed -i s/"#jobmanager.archive.fs.dir: hdfs:\/\/\/completed-jobs\/"/"jobmanager.archive.fs.dir: file:\/\/\/var\/lib\/flink\/completed_jobs\/"/g \
        /usr/local/lib/flink/conf/flink-conf.yaml

# Comma separated list of directories to monitor for completed jobs.
sed -i s/"#historyserver.archive.fs.dir: hdfs:\/\/\/completed-jobs\/"/"historyserver.archive.fs.dir: file:\/\/\/var\/lib\/flink\/completed_jobs\/"/g \
        /usr/local/lib/flink/conf/flink-conf.yaml

# uncomment
sed -i s/"#rest.port: 8081"/"rest.port: 8081"/g /usr/local/lib/flink/conf/flink-conf.yaml


sudo bash -c "echo -e \"\n\n\"  >> /usr/local/lib/flink/conf/flink-conf.yaml"
sudo bash -c "echo -e \"#==============================================================================\"  >> /usr/local/lib/flink/conf/flink-conf.yaml"
sudo bash -c "echo -e \"# Eskimo Mesos Configuration part \"  >> /usr/local/lib/flink/conf/flink-conf.yaml"
sudo bash -c "echo -e \"#==============================================================================\"  >> /usr/local/lib/flink/conf/flink-conf.yaml"

sudo bash -c "echo -e \"\n# setting containierization type \"  >> /usr/local/lib/flink/conf/flink-conf.yaml"
sudo bash -c "echo -e \"mesos.resourcemanager.tasks.container.type: docker\"  >> /usr/local/lib/flink/conf/flink-conf.yaml"

sudo bash -c "echo -e \"\n# specifying image name \"  >> /usr/local/lib/flink/conf/flink-conf.yaml"
sudo bash -c "echo -e \"mesos.resourcemanager.tasks.container.image.name: eskimo:flink-worker\"  >> /usr/local/lib/flink/conf/flink-conf.yaml"

sudo bash -c "echo -e \"\n# specifying FLINK_HOME (workaround for https://github.com/mesosphere/dcos-flink-service/issues/54) \"  >> /usr/local/lib/flink/conf/flink-conf.yaml"
sudo bash -c "echo -e \"mesos.resourcemanager.tasks.bootstrap-cmd: export FLINK_HOME=/usr/local/lib/flink/\"  >> /usr/local/lib/flink/conf/flink-conf.yaml"

sudo bash -c "echo -e \"\n# A comma separated list of [host_path:]container_path[:RO|RW]. \"  >> /usr/local/lib/flink/conf/flink-conf.yaml"
sudo bash -c "echo -e \"# This allows for mounting additional volumes into your container.. \"  >> /usr/local/lib/flink/conf/flink-conf.yaml"
sudo bash -c "echo -e \"mesos.resourcemanager.tasks.container.volumes: /var/log/flink:/var/log/flink:RW,/var/lib/flink:/var/lib/flink:RW,/etc:/host_etc:RO\"  >> /usr/local/lib/flink/conf/flink-conf.yaml"




# temporary debug logs
#sed -i s/"log4j.rootLogger=INFO, file"/"log4j.rootLogger=DEBUG, file"/g \
#        /usr/local/lib/flink/conf/log4j.properties



# This hack was required with FLink 1.9 when a killed task manager made is to that flink was never anymore able to
# recover some resources from mesos !
## temporary increasing timeout to workaround
## https://stackoverflow.com/questions/58537199/apache-flink-resource-manager-app-master-fails-allocating-new-task-managers-af
## https://issues.apache.org/jira/browse/FLINK-14074
#
#sudo bash -c "echo -e \"\n# FIXME emporary increasing timeout to workaround \"  >> /usr/local/lib/flink/conf/flink-conf.yaml"
#sudo bash -c "echo -e \"\n# https://stackoverflow.com/questions/58537199/apache-flink-resource-manager-app-master-fails-allocating-new-task-managers-af \"  >> /usr/local/lib/flink/conf/flink-conf.yaml"
#sudo bash -c "echo -e \"\n# https://issues.apache.org/jira/browse/FLINK-14074 \"  >> /usr/local/lib/flink/conf/flink-conf.yaml"
#sudo bash -c "echo -e \"resourcemanager.taskmanager-timeout: 1800000\"  >> /usr/local/lib/flink/conf/flink-conf.yaml"



echo " - Enabling flink to change configuration at runtime"
chown -R flink. "/usr/local/lib/flink/conf/"

# Caution : the in container setup script must mandatorily finish with this log"
echo " - In container config SUCCESS"
