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


if [[ "$ESKIMO_POD_NAME" != "" ]]; then
    export BROKER_ID=$(echo $ESKIMO_POD_NAME | cut -d '-' -f 2)
    if [[ $BROKER_ID == "" ]]; then
        echo " - No broker ID found could be exracted from $ESKIMO_POD_NAME"
        exit 3
    fi

    sed -i s/"broker.id=0"/"broker.id=$BROKER_ID"/g /usr/local/etc/kafka/server.properties

    sed -i s/"log.dirs=\/tmp\/kafka-logs"/"log.dirs=\/var\/lib\/kafka\/$ESKIMO_POD_NAME"/g /usr/local/etc/kafka/server.properties

    sed -i s/"#advertised.listeners=PLAINTEXT:\/\/your.host.name:9092"/"advertised.listeners=PLAINTEXT:\/\/$ESKIMO_POD_NAME.kafka.eskimo.svc.cluster.eskimo:9092"/g /usr/local/etc/kafka/server.properties

    echo "   + Using advertised.listeners=$ESKIMO_POD_NAME.kafka.eskimo.svc.cluster.eskimo"
fi

# silent
#echo " - Adapting configuration in file server.properties"

sed -i s/"zookeeper.connect=localhost:2181"/"zookeeper.connect=$ZOOKEEPER_IP_ADDRESS:2181"/g /usr/local/etc/kafka/server.properties

#sed -i s/"#advertised.listeners=PLAINTEXT:\/\/your.host.name:9092"/"advertised.listeners=PLAINTEXT:\/\/$SELF_IP_ADDRESS:9092"/g /usr/local/etc/kafka/server.properties


if [[ $MEMORY_KAFKA != "" ]]; then
    echo " - Applying eskimo memory settings from topology as KAFKA_HEAP_OPTS Environment variable"
    export KAFKA_HEAP_OPTS="-Xmx"$MEMORY_KAFKA"m -Xms"$MEMORY_KAFKA"m"
else
    echo " - Applying default memory settings as KAFKA_HEAP_OPTS Environment variable"
    export KAFKA_HEAP_OPTS="-Xmx1024m -Xms1024m"
fi