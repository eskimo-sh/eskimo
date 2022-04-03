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


if [[ ! -f /etc/k8s/env.sh ]]; then
    echo "Could not find /etc/k8s/env.sh"
    exit 1
fi

. /etc/k8s/env.sh


###
# etcd config
#
# The following values are used to configure the secured etcd cluster
###

# The name of the node in the cluster
export NODE_ID=`eval echo "\$"$(echo NODE_NBR_K8S_SLAVE_$SELF_IP_ADDRESS | tr -d .)`
export ESKIMO_ETCD_NODE_NAME=node$NODE_ID
echo "   + Using ESKIMO_ETCD_NODE_NAME=$ESKIMO_ETCD_NODE_NAME"

# List of this member's peer URLs to advertise to the rest of the cluster
export EKIMO_ETCD_INITIAL_ADVERTISE_PEER_URLS=
for i in ${ALL_NODES_LIST_k8s_slave//,/ }; do
    if [[ "$EKIMO_ETCD_INITIAL_ADVERTISE_PEER_URLS" == "" ]]; then
        export EKIMO_ETCD_INITIAL_ADVERTISE_PEER_URLS="http://$i:2380"
    else
        export EKIMO_ETCD_INITIAL_ADVERTISE_PEER_URLS="$EKIMO_ETCD_INITIAL_ADVERTISE_PEER_URLS,http://$i:2380"
    fi
done
echo "   + Using EKIMO_ETCD_INITIAL_ADVERTISE_PEER_URLS=$EKIMO_ETCD_INITIAL_ADVERTISE_PEER_URLS"

# This flag tells the etcd to accept incoming requests from its peers on the specified scheme://IP:port combinations.
# If 0.0.0.0 is specified as the IP, etcd listens to the given port on all interfaces.
# If an IP address is given as well as a port, etcd will listen on the given port and interface.
# Multiple URLs may be used to specify a number of addresses and ports to listen on.
# The etcd will respond to requests from any of the listed addresses and ports.
export EKIMO_ETCD_LISTEN_PEER_URLS="http://$SELF_IP_ADDRESS:2380"
echo "   + Using EKIMO_ETCD_LISTEN_PEER_URLS=$EKIMO_ETCD_LISTEN_PEER_URLS"

# Similary to peer urls, but this flag is to accept incoming requests from clients
export EKIMO_ETCD_LISTEN_CLIENT_URLS="http://$SELF_IP_ADDRESS:2379,http://127.0.0.1:2379"
echo "   + Using EKIMO_ETCD_LISTEN_CLIENT_URLS=$EKIMO_ETCD_LISTEN_CLIENT_URLS"

# List of this member's client URLs to advertise to the rest of the cluster. These URLs can contain domain names.
# Example: https://10.0.0.1:2379,https://etcd-cluster.com:2379
export EKIMO_ETCD_ADVERTISE_CLIENT_URLS="https://$SELF_IP_ADDRESS:2379,https://localhost:2379"
echo "   + Using EKIMO_ETCD_ADVERTISE_CLIENT_URLS=$EKIMO_ETCD_ADVERTISE_CLIENT_URLS"

# The initial unique token of the cluster during bootstrap
export EKIMO_ETCD_INITIAL_CLUSTER_TOKEN="etcd-cluster-eskimo"
echo "   + Using EKIMO_ETCD_INITIAL_CLUSTER_TOKEN=$EKIMO_ETCD_INITIAL_CLUSTER_TOKEN"

# The URLs specified in initial-cluster are the advertised peer URLs.
# i.e. they should match the value of initial-advertise-peer-urls on the respective nodes.
export EKIMO_ETCD_INITIAL_CLUSTER=
for i in ${ALL_NODES_LIST_k8s_slave//,/ }; do
    export NODE_ID=`eval echo "\$"$(echo NODE_NBR_K8S_SLAVE_$SELF_IP_ADDRESS | tr -d .)`
    if [[ "$EKIMO_ETCD_INITIAL_CLUSTER" == "" ]]; then
        export EKIMO_ETCD_INITIAL_CLUSTER="node$NODE_ID=http://$i:2380"
    else
        export EKIMO_ETCD_INITIAL_CLUSTER="$EKIMO_ETCD_INITIAL_CLUSTER,node$NODE_ID=http://$i:2380"
    fi
done

#counter=1
#for i in ${ALL_NODES_LIST_k8s_slave//,/ }; do
#    if [[ "$EKIMO_ETCD_INITIAL_CLUSTER" == "" ]]; then
#        export EKIMO_ETCD_INITIAL_CLUSTER="etcd$counter=http://$i:2380"
#    else
#        export EKIMO_ETCD_INITIAL_CLUSTER="$EKIMO_ETCD_INITIAL_CLUSTER,etcd$counter=http://$i:2380"
#    fi
#    let "counter=counter+1"
#done

echo "   + Using EKIMO_ETCD_INITIAL_CLUSTER=$EKIMO_ETCD_INITIAL_CLUSTER"

# The initial cluster state
export EKIMO_ETCD_INITIAL_CLUSTER_STATE="new"
echo "   + Using EKIMO_ETCD_INITIAL_CLUSTER_STATE=$EKIMO_ETCD_INITIAL_CLUSTER_STATE"

#SSL security disabled for now
# TLS certificates
#EKIMO_ETCD_CERT_FILE="/etc/etcd/ssl/server.pem"
#EKIMO_ETCD_KEY_FILE="/etc/etcd/ssl/server-key.pem"

# Enable client auth
#EKIMO_ETCD_CLIENT_CERT_AUTH="true"

# Root ca file
#EKIMO_ETCD_TRUSTED_CA_FILE="/etc/etcd/ssl/etcd-root-ca.pem"

# Peer certificates
#EKIMO_ETCD_PEER_CERT_FILE="/etc/etcd/ssl/etcd3.pem"
#EKIMO_ETCD_PEER_KEY_FILE="/etc/etcd/ssl/etcd3-key.pem"
#EKIMO_ETCD_PEER_CLIENT_CERT_AUTH="true"
#EKIMO_ETCD_PEER_TRUSTED_CA_FILE="/etc/etcd/ssl/etcd-root-ca.pem"

export EKIMO_ETCD_DATA_DIR="/var/lib/etcd"
echo "   + Using EKIMO_ETCD_DATA_DIR=$EKIMO_ETCD_DATA_DIR"