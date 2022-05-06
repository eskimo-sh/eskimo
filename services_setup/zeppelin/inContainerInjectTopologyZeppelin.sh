#!/usr/bin/env bash

#
# This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
# well to this individual file than to the Eskimo Project as a whole.
#
# Copyright 2019 - 2022 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

echo " - Loading Topology"
. /etc/eskimo_topology.sh

export ZOOKEEPER_IP_ADDRESS=$MASTER_ZOOKEEPER_1
if [[ $ZOOKEEPER_IP_ADDRESS == "" ]]; then
    echo " - No zookeeper master found in topology"
    exit 2
fi

export KUBERNETES_API_MASTER=$MASTER_KUBE_MASTER_1
if [[ $KUBERNETES_API_MASTER == "" ]]; then
    echo " - No Kubernetes API master found in topology"
    exit 3
fi

echo " - Adapting configuration files and scripts"

sed -i -n '1h;1!H;${;g;s/'\
'          \"name\": \"elasticsearch.host\",\n'\
'          \"value\": \"localhost\",'\
'/'\
'          \"name\": \"elasticsearch.host\",\n'\
'          \"value\": \"elasticsearch.default.svc.cluster.eskimo\",'\
'/g;p;}' /usr/local/lib/zeppelin/conf/interpreter.json

# zeppelin pre-0.9-final
#sed -i -n '1h;1!H;${;g;s/'\
#'          \"name\": \"master\",\n'\
#'          \"value\": \"local\[\*\]\",'\
#'/'\
#'          \"name\": \"master\",\n'\
#'          \"value\": \"'"mesos:\/\/zk:\/\/$ZOOKEEPER_IP_ADDRESS:2181\/mesos"'\",'\
#'/g;p;}' /usr/local/lib/zeppelin/conf/interpreter.json

sed -i -n '1h;1!H;${;g;s/'\
'          \"name\": \"spark.master\",\n'\
'          \"value\": \"local\[\*\]\",'\
'/'\
'          \"name\": \"spark.master\",\n'\
'          \"value\": \"'"k8s:\/\/https:\/\/$KUBERNETES_API_MASTER:6443"'\",'\
'/g;p;}' /usr/local/lib/zeppelin/conf/interpreter.json

sed -i -n '1h;1!H;${;g;s/'\
'          \"name\": \"flink.execution.remote.host\",\n'\
'          \"value\": \"\",'\
'/'\
'          \"name\": \"flink.execution.remote.host\",\n'\
'          \"value\": \"flink.default.svc.cluster.eskimo\",'\
'/g;p;}' /usr/local/lib/zeppelin/conf/interpreter.json


if [[ $MEMORY_ZEPPELIN != "" ]]; then
    echo " - Applying eskimo memory settings from topology in jvm.options"

    sed -i s/"# export ZEPPELIN_MEM"/"export ZEPPELIN_MEM\"=-Xmx"$MEMORY_ZEPPELIN"m "/g /usr/local/lib/zeppelin/conf/zeppelin-env.sh
fi