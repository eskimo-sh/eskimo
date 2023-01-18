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
# kubernetes system config
#
# The following values are used to configure the kube-controller-manager
###

# The IP address on which to listen for the --secure-port port
# The associated interface(s) must be reachable by the rest of the cluster, and by CLI/web clients
# This value is set to 127.0.0.1  because API Server and Controller Manager are expected on the same machine
# This argument is deprecated in 1.10 and is replaced by --bind-address. --bind-address is not available before 1.10
export ESKIMO_KUBE_CTRL_BIND_ADDRESS="0.0.0.0"
echo "   + Using ESKIMO_KUBE_CTRL_BIND_ADDRESS=$ESKIMO_KUBE_CTRL_BIND_ADDRESS"

# CIDR Range for Services. Requires --allocate-node-cidrs to be true
export ESKIMO_KUBE_CTRL_ALLOCATE_NODE_CIDRS=true
echo "   + Using ESKIMO_KUBE_CTRL_ALLOCATE_NODE_CIDRS=$ESKIMO_KUBE_CTRL_ALLOCATE_NODE_CIDRS"

export ESKIMO_KUBE_CTRL_SERVICE_CLUSTER_IPD=$SERVICE_CIDR
echo "   + Using ESKIMO_KUBE_CTRL_SERVICE_CLUSTER_IPD=$ESKIMO_KUBE_CTRL_SERVICE_CLUSTER_IPD"

# CIDR Range for Pods in cluster. Requires --allocate-node-cidrs to be true
export ESKIMO_KUBE_CTRL_CLUSTER_CIDR="$CLUSTER_CIDR"
echo "   + Using ESKIMO_KUBE_CTRL_CLUSTER_CIDR=$ESKIMO_KUBE_CTRL_CLUSTER_CIDR"

export ESKIMO_KUBE_CTRL_CLUSTER_SIGNING_CERT_FILE="/etc/k8s/shared/ssl/ca.pem"
echo "   + Using ESKIMO_KUBE_CTRL_CLUSTER_SIGNING_CERT_FILE=$ESKIMO_KUBE_CTRL_CLUSTER_SIGNING_CERT_FILE"

export ESKIMO_KUBE_CTRL_CLUSTER_SIGNING_KEY_FILE="/etc/k8s/shared/ssl/ca-key.pem"
echo "   + Using ESKIMO_KUBE_CTRL_CLUSTER_SIGNING_KEY_FILE=$ESKIMO_KUBE_CTRL_CLUSTER_SIGNING_KEY_FILE"

export ESKIMO_KUBE_CTRL_SERVICE_ACCOUNT_PRIVATE_KEY_FILE="/etc/k8s/shared/ssl/kubernetes-key.pem"
echo "   + Using ESKIMO_KUBE_CTRL_SERVICE_ACCOUNT_PRIVATE_KEY_FILE=$ESKIMO_KUBE_CTRL_SERVICE_ACCOUNT_PRIVATE_KEY_FILE"

export ESKIMO_KUBE_CTRL_ROOT_CA_FILE="/etc/k8s/shared/ssl/ca.pem"
echo "   + Using ESKIMO_KUBE_CTRL_ROOT_CA_FILE=$ESKIMO_KUBE_CTRL_ROOT_CA_FILE"

# How the controller-manager, scheduler, and proxy find the apiserver
export ESKIMO_KUBE_CTRL_API_SERVER_MASTER=$ESKIMO_KUBE_MASTER
echo "   + Using ESKIMO_KUBE_CTRL_API_SERVER_MASTER=$ESKIMO_KUBE_CTRL_API_SERVER_MASTER"

export ESKIMO_KUBE_CTRL_CLUSTER_NAME=$ESKIMO_CLUSTER_NAME
echo "   + Using ESKIMO_KUBE_CTRL_CLUSTER_NAME=$ESKIMO_KUBE_CTRL_CLUSTER_NAME"

export ESKIMO_KUBE_CTRL_LEADER_ELECT=true
echo "   + Using ESKIMO_KUBE_CTRL_LEADER_ELECT=$ESKIMO_KUBE_CTRL_LEADER_ELECT"


# from master env.sh
echo "   + Using ESKIMO_KUBE_LOG_LEVEL=$ESKIMO_KUBE_LOG_LEVEL"