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


if [[ ! -f /etc/k8s/env.sh ]]; then
    echo "Could not find /etc/k8s/env.sh"
    exit 1
fi

. /etc/k8s/env.sh



###
# kubernetes kubelet (minion) config

# The address for the info server to serve on (set to 0.0.0.0 or "" for all interfaces)
export ESKIMO_KUBELET_ADDRESS="$SELF_IP_ADDRESS"
echo "   + Using ESKIMO_KUBELET_ADDRESS=$ESKIMO_KUBELET_ADDRESS"

# The port for the info server to serve on
# ESKIMO_KUBELET_PORT="10250"
echo "   + Using ESKIMO_KUBELET_PORT=$ESKIMO_KUBELET_PORT"

# You may leave this blank to use the actual hostname
export ESKIMO_KUBELET_HOSTNAME="$SELF_IP_ADDRESS"
echo "   + Using ESKIMO_KUBELET_HOSTNAME=$ESKIMO_KUBELET_HOSTNAME"

# IP Adress of DNS server. This needs to match the CLUSTER_IP defined in DNS addon
# See https://github.com/kubernetes/examples/tree/master/staging/cluster-dns
export ESKIMO_CLUSTER_DNS="$CLUSTER_DNS_SVC_IP"
echo "   + Using ESKIMO_CLUSTER_DNS=$ESKIMO_CLUSTER_DNS"

# Domain name of the cluster
export ESKIMO_CLUSTER_DNS_DOMAIN="$CLUSTER_DNS_DOMAIN"
echo "   + Using ESKIMO_CLUSTER_DNS_DOMAIN=$ESKIMO_CLUSTER_DNS_DOMAIN"

# Configuration foles
export ESKIMO_BOOTSTRAP_KUBECONFIG=/etc/k8s/bootstrap.kubeconfig
echo "   + Using ESKIMO_BOOTSTRAP_KUBECONFIG=$ESKIMO_BOOTSTRAP_KUBECONFIG"

export ESKIMO_KUBELET_KUBECONFIG=/etc/k8s/kubelet.kubeconfig
echo "   + Using ESKIMO_KUBELET_KUBECONFIG=$ESKIMO_KUBELET_KUBECONFIG"

export ESKIMO_KUBELET_CGROUP_DRIVER="systemd"
echo "   + Using ESKIMO_KUBELET_CGROUP_DRIVER=$ESKIMO_KUBELET_CGROUP_DRIVER"

export ESKIMO_KUBELET_FAIL_SWAP_ON="false"
echo "   + Using ESKIMO_KUBELET_FAIL_SWAP_ON=$ESKIMO_KUBELET_FAIL_SWAP_ON"


export ESKIMO_KUBELET_RUNTIME_REQUEST_TIMEOUT="5m"
echo "   + Using ESKIMO_KUBELET_RUNTIME_REQUEST_TIMEOUT=$ESKIMO_KUBELET_RUNTIME_REQUEST_TIMEOUT"

# from master env.sh
echo "   + Using ESKIMO_KUBE_LOG_LEVEL=$ESKIMO_KUBE_LOG_LEVEL"

echo "   + Using ESKIMO_POD_INFRA_CONTAINER_IMAGE=$ESKIMO_POD_INFRA_CONTAINER_IMAGE"
echo "   + Using ESKIMO_RUNTIME_REQUEST_TIMEOUT=$ESKIMO_RUNTIME_REQUEST_TIMEOUT"
echo "   + Using ESKIMO_CONTAINER_RUNTIME_ENDPOINT=$ESKIMO_CONTAINER_RUNTIME_ENDPOINT"

echo "   + Using ESKIMO_KUBE_TLS_CERT_FILE=$ESKIMO_KUBE_TLS_CERT_FILE"
echo "   + Using ESKIMO_KUBE_TLS_PRIVATE_KEY=$ESKIMO_KUBE_TLS_PRIVATE_KEY"
echo "   + Using ESKIMO_KUBE_CLIENT_CA_FILE=$ESKIMO_KUBE_CLIENT_CA_FILE"

# --cert-dir=/etc/k8s/shared/ssl