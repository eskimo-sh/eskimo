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

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $SCRIPT_DIR/common.sh "$@"

FLINK_USER_ID=$1
if [[ $FLINK_USER_ID == "" ]]; then
    echo " - Didn't get FLINK User ID as argument"
    exit -2
fi

SELF_IP_ADDRESS=$2
if [[ SELF_IP_ADDRESS == "" ]]; then
    echo " - Didn't get Self IP Address as argument"
    exit -2
fi


echo "-- SETTING UP FLINK (COMMON PART) --------------------------------------"

echo " - Creating flink user (if not exist) in container"
set +e
flink_user_id=`id -u flink 2> /tmp/es_install_log`
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

echo " - Simlinking flink logs to /var/log/"
sudo rm -Rf /usr/local/lib/flink/log
sudo ln -s /var/log/flink/log /usr/local/lib/flink/log



# TODO find out which is to add and which is to replace (see file on desktop)


# The external address of the host on which the JobManager runs and can be
# reached by the TaskManagers and any clients which want to connect
jobmanager.rpc.address: $SELF_IP_ADDRESS

# setting containierization type
mesos.resourcemanager.tasks.container.type: docker

# specifying image name
mesos.resourcemanager.tasks.container.image.name: eskimo:flink-worker

# A comma separated list of [host_path:]container_path[:RO|RW].
# This allows for mounting additional volumes into your container.
mesos.resourcemanager.tasks.container.volumes: /var/lib/flink/data:/var/lib/flink/data:RW,/var/lib/flink/completed_jobs:/var/lib/flink/completed_jobs:RW

# CPUs to assign to the Mesos workers.
mesos.resourcemanager.tasks.cpus: 1

# Memory to assign to the Mesos workers in MB.
mesos.resourcemanager.tasks.mem: 1024

# The default directory used for storing the data files and meta data of checkpoints in a Flink supported
# filesystem.
# The storage path must be accessible from all participating processes/nodes(i.e. all TaskManagers and JobManagers).
state.savepoints.dir: /var/lib/flink/data/savepoints

# The default directory for savepoints.
# Used by the state backends that write savepoints to file systems (MemoryStateBackend, FsStateBackend, RocksDBStateBackend).
state.checkpoints.dir: /var/lib/flink/data/ckeckpoints

# JVM heap size for the JobManager.
jobmanager.heap.size: 1024m

# JVM heap size for the TaskManagers, which are the parallel workers of the system.
# On YARN setups, this value is automatically configured to the size of the TaskManager's YARN container, minus a certain tolerance value.
taskmanager.heap.size :  1024m

# Default parallelism for jobs.
parallelism.default: 1

# Directory to upload completed jobs to.
jobmanager.archive.fs.dir: /var/lib/flink/completed_jobs

# Comma separated list of directories to monitor for completed jobs.
historyserver.archive.fs.dir: /var/lib/flink/completed_jobs





# Caution : the in container setup script must mandatorily finish with this log"
echo " - In container config SUCCESS"
