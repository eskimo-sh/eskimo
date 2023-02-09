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

echo " - Injecting topology"
. /usr/local/sbin/inContainerInjectTopology.sh

echo " - Inject settings"
/usr/local/sbin/settingsInjector.sh kafka

# we rely on settings injector to add overriden memory setting in /usr/local/lib/kafka/config/eskimo-memory.opts
if [[ -f /usr/local/lib/kafka/config/eskimo-memory.opts && $(cat /usr/local/lib/kafka/config/eskimo-memory.opts) != "" ]]; then

    export KAFKA_HEAP_OPTS=""
    for line in $(cat /usr/local/lib/kafka/config/eskimo-memory.opts); do
        export KAFKA_HEAP_OPTS="$KAFKA_HEAP_OPTS -$line"
    done

    echo " - Using overriden memory settings : $KAFKA_HEAP_OPTS"
fi

echo " - Mounting kafka gluster shares"
sudo /bin/bash /usr/local/sbin/inContainerMountGluster.sh kafka_data /var/lib/kafka kafka

echo " - Creating kafka data directory /var/lib/kafka/$ESKIMO_POD_NAME"
sudo /bin/mkdir -p /var/lib/kafka/$ESKIMO_POD_NAME

echo " - Changing rights of folder /var/lib/kafka"
sudo /bin/chown -R kafka /var/lib/kafka
sudo /bin/chmod 755 /var/lib/kafka

if [[ -f /var/log/kafka/server.log ]]; then
    echo " - Archiving log file"
    mv /var/log/kafka/server.log /var/log/kafka/server.log.previous
fi

echo " - Start glusterMountCheckerPeriodic.sh script"
/bin/bash /usr/local/sbin/glusterMountCheckerPeriodic.sh &
export GLUSTER_MOUNT_CHECKER_PID=$!

echo " - Starting service"
/usr/local/lib/kafka/bin/kafka-server-start.sh /usr/local/etc/kafka/server.properties &
KAFKA_PID=$!
echo $KAFKA_PID > /var/run/kafka/Kafka.pid


echo " - Entering corruption and error detection loop"
# trying for 100 seconds
for i in $(seq 1 20); do

    # Ensure process is still up otherwise crash with exit code != 0
    if [[ $(ps -e | grep $KAFKA_PID) == "" ]]; then
        echo " - ! Couldn't successfully start kafka"
        exit 10
    fi

    sleep 5

    if [[ $(tail -800 /var/log/kafka/server.log  | grep -E "Registered broker [0-9]+ at path") != "" ]]; then
        echo " - Kafka started successfully, now waiting on kafka process to exit"
        break
    fi

    if [[ $(tail -800 /var/log/kafka/server.log  | grep -E "doesn't match stored clusterId") != "" ]]; then
        echo " - Detected cluster mismatch. Wiping out kafka folder !!!"
        rm -Rf /var/lib/kafka/$ESKIMO_POD_NAME/*
        mv /var/log/kafka/server.log /var/log/kafka/server.log.backup-post-wipeout

        sleep 1

        echo " - Restarting kafka"
        /usr/local/lib/kafka/bin/kafka-server-start.sh /usr/local/etc/kafka/server.properties &
        KAFKA_PID=$!
        echo $KAFKA_PID > /var/run/kafka/Kafka.pid
    fi

    # Issue a warning if i == 30
   if [[ $i == 20 ]]; then
       echo " - Couldn't successfully find kafka startup marker";
   fi

done

echo " - Launching Watch Dog on glusterMountCheckerPeriodic remote server"
/usr/local/sbin/containerWatchDog.sh $GLUSTER_MOUNT_CHECKER_PID $KAFKA_PID /var/log/kafka/gluster-mount-checker-periodic-watchdog.log &

echo " - Now waiting on main process to exit"
wait $KAFKA_PID
