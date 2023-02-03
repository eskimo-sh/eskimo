#!/bin/bash

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


if [[ ! -f /etc/eskimo_topology.sh ]]; then
    echo "Could not find /etc/eskimo_topology.sh"
    exit 1
fi

. /etc/eskimo_topology.sh

. /usr/local/sbin/eskimo-utils.sh

# Port of etcd cluster
export EKIMO_ETCD_PORT=2379

# IPs of master nodes
export MASTER1_IP=$MASTER_KUBE_MASTER_1


# getting TLS Bootstrapping Token (BOOTSTRAP_TOKEN)
if [[ ! -d /etc/k8s/shared ]]; then
    echo "Couldn't find /etc/k8s/shared"
    exit 1
fi

if [[ ! -f /etc/k8s/shared/token.sh ]]; then

     take_lock k8s_bootstrap_token_lock /etc/k8s/shared/
     if [[ $? != 0 ]]; then
         echo " !!! Couldn't get /etc/k8s/shared/k8s_bootstrap_token_lock in 30 seconds. crashing !"
         exit 150
     fi
     export lock_handle=$LAST_LOCK_HANDLE

     # double check locking
     if [[ ! -f /etc/k8s/shared/token.sh ]]; then
        export TOKEN=$(< /dev/urandom tr -dc _A-Z-a-z-0-9 | head -c32;)

        echo '#!/bin/bash' > /etc/k8s/shared/token.sh
        echo "export BOOTSTRAP_TOKEN=$TOKEN" >> /etc/k8s/shared/token.sh
     fi

     release_lock $lock_handle
fi

. /etc/k8s/shared/token.sh

# Use unused netword ip range to define service ip and pod ip
# (Service CIDR)
export SERVICE_CIDR="10.254.0.0/16"

# Pod Cluster CIDR
export CLUSTER_CIDR="172.30.0.0/16"

# (NodePort Range)
export NODE_PORT_RANGE="30000-32766"

# etcd cluster addresses
export ESKIMO_ETCD_ENDPOINTS=
for i in ${ALL_NODES_LIST_kube_slave//,/ }; do
    # call your procedure/other scripts here below
    if [[ $ESKIMO_ETCD_ENDPOINTS == "" ]]; then
        export ESKIMO_ETCD_ENDPOINTS="http://$i:$EKIMO_ETCD_PORT"
    else
        export ESKIMO_ETCD_ENDPOINTS="$ESKIMO_ETCD_ENDPOINTS,http://$i:$EKIMO_ETCD_PORT"
    fi
done
#export ESKIMO_ETCD_ENDPOINTS="https://$ETCD1_IP:$EKIMO_ETCD_PORT,https://$ETCD2_IP:$EKIMO_ETCD_PORT,https://$ETCD3_IP:$EKIMO_ETCD_PORT"

# kubernetes service IP (normally the first IP in SERVICE_CIDR)
export CLUSTER_KUBERNETES_SVC_IP="10.254.0.1"

#  DNS IP for the cluster (assigned from SERVICE_CIDR)
export CLUSTER_DNS_SVC_IP="10.254.0.2"

#  DNS domain name
export CLUSTER_DNS_DOMAIN="cluster.eskimo"

# MASTER API Server
#export MASTER_URL="k8s-api.virtual.eskimo"
export MASTER_URL="$MASTER_KUBE_MASTER_1"

export ESKIMO_KUBE_APISERVER="https://${MASTER_URL}:6443"

# journal message level, 0 is debug, 2 is INFO
export ESKIMO_KUBE_LOG_LEVEL="2"

export ESKIMO_ALLOW_PRIVILEGED="true"

# The certificates used by API server
export ESKIMO_KUBE_TLS_CERT_FILE=/etc/k8s/shared/ssl/kubernetes.pem

export ESKIMO_KUBE_TLS_PRIVATE_KEY=/etc/k8s/shared/ssl/kubernetes-key.pem

export ESKIMO_KUBE_CLIENT_CA_FILE=/etc/k8s/shared/ssl/ca.pem

# How the controller-manager, scheduler, and proxy find the apiserver
export ESKIMO_KUBE_MASTER="$ESKIMO_KUBE_APISERVER"

export ESKIMO_CLUSTER_NAME=eskimo

export ESKIMO_CONTAINER_RUNTIME_ENDPOINT="unix:///var/run/cri-dockerd.sock"

export ESKIMO_POD_INFRA_CONTAINER_IMAGE="kubernetes.registry:5000/k8s.gcr.io/pause"

export ESKIMO_RUNTIME_REQUEST_TIMEOUT="5m"