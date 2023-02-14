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

rm -f /tmp/gluster_container_pid

. /usr/local/sbin/eskimo-utils.sh

echo " - Launching docker container"
# capturing docker PID
nohup /usr/bin/docker run \
        -i \
        --name gluster \
        --privileged=true \
         --network host \
        -v /var/lib/gluster:/var/lib/gluster\
        -v /var/log/gluster:/var/log/gluster\
        -v /var/run/gluster:/var/run/gluster\
        --mount type=bind,source=/etc/eskimo_topology.sh,target=/etc/eskimo_topology.sh \
        --mount type=bind,source=/etc/eskimo_services-settings.json,target=/etc/eskimo_services-settings.json \
        -e NODE_NAME=$HOSTNAME \
        "eskimo/gluster:$(get_last_tag gluster)" \
        /usr/local/sbin/inContainerStartService.sh | tee /var/log/gluster/gluster-container-out-log &

#         -p 111:111 \ # RPC Bind is not supported in docker container since in use on the host in anyway
# as a consequence if gluster has to be used through nfs, this is not supported without "network host" I guess

#        -p 28901:28901 \
#        -p 24007:24007 \
#        -p 24008:24008 \
#        -p 38465-38467:38465-38467 \
#        -p 49152:50152 \
#        -p 2049:2049 \

echo " - Giving 20 seconds to container to start as well as gluster and everything"
sleep 20


echo " - Finding docker run process PID"
container_process_PID=$(ps -ef | grep "docker run" | grep "name gluster"  | tr -s ' ' | cut -d ' ' -f 2)

if [[ "$container_process_PID" == "" ]]; then
    echo " - Couldn't not get docker run command PID"
    exit 61
else
    echo " - docker run PID is $container_process_PID"
fi

echo " - Ensuring gluster container is well started"
if [[ $(ps -p $container_process_PID -f | grep "docker run") != "" ]]; then
    echo "   + process has been correctly started"
else
    echo "   + Could not find running PID $container_process_PID"
    exit 62
fi

# After a gluster restart, fix gluster mounts on host
# XXX : this needs to be done offline since otherwise we're stuck here forever on some OSes
# (the ones that wait gluster to be started before allowing to mount)
nohup bash /usr/local/sbin/glusterMountChecker.sh &

echo " - Creating PID file"
echo $container_process_PID > /var/run/gluster/gluster.pid

