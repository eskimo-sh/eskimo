#!/bin/bash

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


if [[ ! -f /etc/eskimo_topology.sh ]]; then
    echo "Could not find /etc/eskimo_topology.sh"
    exit -1
fi

. /etc/eskimo_topology.sh

# expecting from topology
#export ALL_NODES_LIST_k8s_slave=192.168.56.21,192.168.56.22,192.168.56.23


# IP and port of etcd cluster
#export ETCD1_IP="192.168.1.102"
#export ETCD2_IP="192.168.1.103"
#export ETCD3_IP="192.168.1.101"
export EKIMO_ETCD_PORT=2379

# IPs of master nodes
# TODO
export MASTER1_IP=$MASTER_K8S_MASTER
#export MASTER2_IP="192.168.1.102"
#export MASTER3_IP="192.168.1.103"

# TLS Bootstrapping Token
# $(< /dev/urandom tr -dc _A-Z-a-z-0-9 | head -c32;)
export BOOTSTRAP_TOKEN=khFZp2U5yWIgcjl0UsWU-cmeh05PQHXB

# Use unused netword ip range to define service ip and pod ip
# (Service CIDR)
export SERVICE_CIDR="10.254.0.0/16"

# Pod Cluster CIDR
export CLUSTER_CIDR="172.30.0.0/16"

# (NodePort Range)
export NODE_PORT_RANGE="30000-32766"

# etcd cluster addresses
export EKIMO_ETCD_ENDPOINTS=
for i in ${ALL_NODES_LIST_k8s_slave//,/ }; do
    # call your procedure/other scripts here below
    if [[ $EKIMO_ETCD_ENDPOINTS == "" ]]; then
        export EKIMO_ETCD_ENDPOINTS="http://$i:$EKIMO_ETCD_PORT"
    else
        export EKIMO_ETCD_ENDPOINTS="$EKIMO_ETCD_ENDPOINTS,http://$i:$EKIMO_ETCD_PORT"
    fi
done
#export EKIMO_ETCD_ENDPOINTS="https://$ETCD1_IP:$EKIMO_ETCD_PORT,https://$ETCD2_IP:$EKIMO_ETCD_PORT,https://$ETCD3_IP:$EKIMO_ETCD_PORT"

# flanneld etcd prefix
export FLANNEL_EKIMO_ETCD_PREFIX="/kubernetes/network"

# kubernetes service IP (normarlly the first IP in SERVICE_CIDR)
export CLUSTER_KUBERNETES_SVC_IP="10.254.0.1"

#  DNS IP for the cluster (assigned from SERVICE_CIDR)
export CLUSTER_DNS_SVC_IP="10.254.0.2"

#  DNS domain name
export CLUSTER_DNS_DOMAIN="cluster.eskimo"

# MASTER API Server
export MASTER_URL="k8s-api.virtual.eskimo"