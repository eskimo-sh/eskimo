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


###
# etcd config
#
# The following values are used to configure the secured etcd cluster
###

# The name of the node in the cluster
export NODE_ID=`eval echo "\$""$(echo NODE_NBR_ETCD_$SELF_IP_ADDRESS | tr -d .)"`
if [[ $NODE_ID != "" ]]; then
    export ESKIMO_ETCD_NODE_NAME=node$NODE_ID
else
    export ESKIMO_ETCD_NODE_NAME=node1
fi
echo "   + Using ESKIMO_ETCD_NODE_NAME=$ESKIMO_ETCD_NODE_NAME"

export ESKIMO_ETCD_INITIAL_ADVERTISE_PEER_URLS="http://$SELF_IP_ADDRESS:2380"
echo "   + Using ESKIMO_ETCD_INITIAL_ADVERTISE_PEER_URLS=$ESKIMO_ETCD_INITIAL_ADVERTISE_PEER_URLS"

# This flag tells the etcd to accept incoming requests from its peers on the specified scheme://IP:port combinations.
# If 0.0.0.0 is specified as the IP, etcd listens to the given port on all interfaces.
# If an IP address is given as well as a port, etcd will listen on the given port and interface.
# Multiple URLs may be used to specify a number of addresses and ports to listen on.
# The etcd will respond to requests from any of the listed addresses and ports.
export ESKIMO_ETCD_LISTEN_PEER_URLS="http://$SELF_IP_ADDRESS:2380"
echo "   + Using ESKIMO_ETCD_LISTEN_PEER_URLS=$ESKIMO_ETCD_LISTEN_PEER_URLS"

# Similary to peer urls, but this flag is to accept incoming requests from clients
export ESKIMO_ETCD_LISTEN_CLIENT_URLS="http://$SELF_IP_ADDRESS:2379,http://127.0.0.1:2379"
echo "   + Using ESKIMO_ETCD_LISTEN_CLIENT_URLS=$ESKIMO_ETCD_LISTEN_CLIENT_URLS"

# List of this member's client URLs to advertise to the rest of the cluster. These URLs can contain domain names.
# Example: https://10.0.0.1:2379,https://etcd-cluster.com:2379
export ESKIMO_ETCD_ADVERTISE_CLIENT_URLS="https://$SELF_IP_ADDRESS:2379,https://localhost:2379"
echo "   + Using ESKIMO_ETCD_ADVERTISE_CLIENT_URLS=$ESKIMO_ETCD_ADVERTISE_CLIENT_URLS"

# The initial unique token of the cluster during bootstrap
export ESKIMO_ETCD_INITIAL_CLUSTER_TOKEN="etcd-cluster-eskimo"
echo "   + Using ESKIMO_ETCD_INITIAL_CLUSTER_TOKEN=$ESKIMO_ETCD_INITIAL_CLUSTER_TOKEN"

# The URLs specified in initial-cluster are the advertised peer URLs.
# i.e. they should match the value of initial-advertise-peer-urls on the respective nodes.
export ESKIMO_ETCD_INITIAL_CLUSTER=
for i in ${ALL_NODES_LIST_etcd//,/ }; do
    export NODE_ID=`eval echo "\$""$(echo NODE_NBR_ETCD_$i | tr -d .)"`
    if [[ "$ESKIMO_ETCD_INITIAL_CLUSTER" == "" ]]; then
        export ESKIMO_ETCD_INITIAL_CLUSTER="node$NODE_ID=http://$i:2380"
    else
        export ESKIMO_ETCD_INITIAL_CLUSTER="$ESKIMO_ETCD_INITIAL_CLUSTER,node$NODE_ID=http://$i:2380"
    fi
done
# in case slaves are not yet installed
if [[ "$ESKIMO_ETCD_INITIAL_CLUSTER" == "" ]]; then
    ESKIMO_ETCD_INITIAL_CLUSTER="node1=http://$SELF_IP_ADDRESS:2380"
fi

echo "   + Using ESKIMO_ETCD_INITIAL_CLUSTER=$ESKIMO_ETCD_INITIAL_CLUSTER"

# The initial cluster state - existing if there are already etcd nodes running
if [[ $(grep -F ESKIMO_INSTALLED_etcd /etc/eskimo_topology.sh) != "" ]]; then
    export ESKIMO_ETCD_INITIAL_CLUSTER_STATE="existing"
else
    export ESKIMO_ETCD_INITIAL_CLUSTER_STATE="new"
fi
echo "   + Using ESKIMO_ETCD_INITIAL_CLUSTER_STATE=$ESKIMO_ETCD_INITIAL_CLUSTER_STATE"

#SSL security disabled for now
# TLS certificates
#export ESKIMO_ETCD_CERT_FILE="/etc/etcd/ssl/kubernetes.pem"
#echo "   + Using ESKIMO_ETCD_CERT_FILE=$ESKIMO_ETCD_CERT_FILE"
#export ESKIMO_ETCD_KEY_FILE="/etc/etcd/ssl/kubernetes-key.pem"
#echo "   + Using ESKIMO_ETCD_KEY_FILE=$ESKIMO_ETCD_KEY_FILE"

# Enable client auth
#export ESKIMO_ETCD_CLIENT_CERT_AUTH="true"
#echo "   + Using ESKIMO_ETCD_CLIENT_CERT_AUTH=$ESKIMO_ETCD_CLIENT_CERT_AUTH"

# Root ca file
#export ESKIMO_ETCD_TRUSTED_CA_FILE="/etc/k8s/shared/ssl/ca.pem"
#echo "   + Using ESKIMO_ETCD_TRUSTED_CA_FILE=$ESKIMO_ETCD_TRUSTED_CA_FILE"

# Peer certificates
#export #ESKIMO_ETCD_PEER_CERT_FILE="/etc/k8s/shared/ssl/kubernetes.pem"
#echo "   + Using ESKIMO_ETCD_PEER_CERT_FILE=$ESKIMO_ETCD_PEER_CERT_FILE"
#export ESKIMO_ETCD_PEER_KEY_FILE="/etc/k8s/shared/ssl/kubernetes-key.pem"
#echo "   + Using ESKIMO_ETCD_PEER_KEY_FILE=$ESKIMO_ETCD_PEER_KEY_FILE"
#export ESKIMO_ETCD_PEER_CLIENT_CERT_AUTH="true"
#echo "   + Using ESKIMO_ETCD_PEER_CLIENT_CERT_AUTH=$ESKIMO_ETCD_PEER_CLIENT_CERT_AUTH"
#export ESKIMO_ETCD_PEER_TRUSTED_CA_FILE="/etc/k8s/shared/ssl/ca.pem"
#echo "   + Using ESKIMO_ETCD_PEER_TRUSTED_CA_FILE=$ESKIMO_ETCD_PEER_TRUSTED_CA_FILE"

export ESKIMO_ETCD_DATA_DIR="/var/lib/etcd"
echo "   + Using ESKIMO_ETCD_DATA_DIR=$ESKIMO_ETCD_DATA_DIR"
