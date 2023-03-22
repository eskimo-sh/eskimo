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

set -e

# silent
#echo " - Loading Topology"
. /etc/eskimo_topology.sh

export ZOOKEEPER_IP_ADDRESS=$MASTER_ZOOKEEPER_1
if [[ $ZOOKEEPER_IP_ADDRESS == "" ]]; then
    echo " - No zookeeper master found in topology"
    exit 2
fi

echo " - Mounting flink gluster shares"
echo "   + (Taking the opportunity to do it in inContainerInjectTopology.sh since it's used everywhere)"
sudo /bin/bash /usr/local/sbin/inContainerMountGluster.sh flink_data /var/lib/flink/data flink
sudo /bin/bash /usr/local/sbin/inContainerMountGluster.sh flink_completed_jobs /var/lib/flink/completed_jobs flink

#sudo /bin/chmod 777 /var/lib/flink/completed_jobs
#sudo /bin/chmod 777 /var/lib/flink/data


bash -c "echo -e \"kubernetes.jobmanager.cpu: $ESKIMO_KUBE_REQUEST_FLINK_RUNTIME_CPU\"  >> /usr/local/lib/flink/conf/flink-conf.yaml"
bash -c "echo -e \"kubernetes.taskmanager.cpu: $ESKIMO_KUBE_REQUEST_FLINK_RUNTIME_CPU\"  >> /usr/local/lib/flink/conf/flink-conf.yaml"

if [[ $MEMORY_FLINK_RUNTIME != "" ]]; then
    bash -c "sed -i s/\"jobmanager.memory.process.size: 1600m\"/\"jobmanager.memory.process.size: ${MEMORY_FLINK_RUNTIME}m\"/g /usr/local/lib/flink/conf/flink-conf.yaml"
    bash -c "sed -i s/\"taskmanager.memory.process.size: 1728m\"/\"taskmanager.memory.process.size: ${MEMORY_FLINK_RUNTIME}m\"/g /usr/local/lib/flink/conf/flink-conf.yaml"
else
    bash -c "sed -i s/\"jobmanager.memory.process.size: 1600m\"/\"jobmanager.memory.process.size: 1100m\"/g /usr/local/lib/flink/conf/flink-conf.yaml"
    bash -c "sed -i s/\"taskmanager.memory.process.size: 1728m\"/\"taskmanager.memory.process.size: 1200m\"/g /usr/local/lib/flink/conf/flink-conf.yaml"
fi

# The external address of the host on which the JobManager runs and can be
# reached by the TaskManagers and any clients which want to connect
#sed -i s/"jobmanager.rpc.address: localhost"/"jobmanager.rpc.address: $MASTER_FLINK_APP_MASTER_1"/g /usr/local/lib/flink/conf/flink-conf.yaml

bash -c "sed -i s/\"taskmanager.host: localhost\"/\"taskmanager.host: $POD_IP_ADDRESS\"/g /usr/local/lib/flink/conf/flink-conf.yaml"
bash -c "sed -i s/\"jobmanager.bind-host: localhost\"/\"jobmanager.bind-host: 0.0.0.0\"/g /usr/local/lib/flink/conf/flink-conf.yaml"
bash -c "sed -i s/\"taskmanager.bind-host: localhost\"/\"taskmanager.bind-host: 0.0.0.0\"/g /usr/local/lib/flink/conf/flink-conf.yaml"