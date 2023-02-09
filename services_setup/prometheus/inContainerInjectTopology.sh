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

echo " - Loading Topology"
. /etc/eskimo_topology.sh


echo " - Adapting Configuration file"


echo " - Adding node exporter scrap config to prometheus"
bash -c "echo -e \"\n  - job_name: 'node'\n    static_configs:\" >> /usr/local/lib/prometheus/prometheus.yml"

if [[ "$SELF_IP_ADDRESS" == "$MASTER_PROMETHEUS_1" ]]; then

    echo " - fetching from all other node exporters"
    bash -c "echo -e \"    # targets come from /etc/eskimo_topology.sh - ALL_NODES_LIST_prometheus\" >> /usr/local/lib/prometheus/prometheus.yml"
    bash -c "echo -e \"    - targets:\" >> /usr/local/lib/prometheus/prometheus.yml"
    for i in $(echo "$ALL_NODES_LIST_prometheus"  | tr "," "\n"); do
        bash -c "echo -e \"      - $i:9100\" >> /usr/local/lib/prometheus/prometheus.yml"
    done
else
    bash -c "echo -e \"    # (not any node configured since this node is not master)\" >> /usr/local/lib/prometheus/prometheus.yml"
fi


#echo " - Adding mesos-agent scrap config to prometheus"
#bash -c "echo -e \"\n  - job_name: mesos-slave\n    static_configs:\" >> /usr/local/lib/prometheus/prometheus.yml"
#
#if [[ "$SELF_IP_ADDRESS" == "$MASTER_PROMETHEUS_1" ]]; then
#
#    echo " - fetching from all mesos-agent nodes"
#    bash -c "echo -e \"    # targets come from /etc/eskimo_topology.sh - ALL_NODES_LIST_prometheus\" >> /usr/local/lib/prometheus/prometheus.yml"
#    bash -c "echo -e \"    - targets:\" >> /usr/local/lib/prometheus/prometheus.yml"
#    for i in `echo "$ALL_NODES_LIST_prometheus"  | tr "," "\n"`; do
#        bash -c "echo -e \"      - $i:9106\" >> /usr/local/lib/prometheus/prometheus.yml"
#    done
#
#else
#    bash -c "echo -e \"    # (not any mesos-agent node configured since this node is not master)\" >> /usr/local/lib/prometheus/prometheus.yml"
#fi
#
#
#echo " - Adding mesos-master scrap config to prometheus"
#bash -c "echo -e \"\n  - job_name: mesos-master\n    static_configs:\" >> /usr/local/lib/prometheus/prometheus.yml"
#
#if [[ "$SELF_IP_ADDRESS" == "$MASTER_PROMETHEUS_1" ]]; then
#
#    echo " - fetching from mesos-master node"
#    bash -c "echo -e \"    # targets come from /etc/eskimo_topology.sh - MASTER_MESOS_MASTER_1\" >> /usr/local/lib/prometheus/prometheus.yml"
#    bash -c "echo -e \"    - targets:\" >> /usr/local/lib/prometheus/prometheus.yml"
#    bash -c "echo -e \"      - $MASTER_MESOS_MASTER_1:9105\" >> /usr/local/lib/prometheus/prometheus.yml"
#
#else
#    bash -c "echo -e \"    # (not any mesos-agent node configured since this node is not master)\" >> /usr/local/lib/prometheus/prometheus.yml"
#fi