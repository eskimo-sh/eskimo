#!/bin/bash

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


if [[ ! -f /etc/k8s/env.sh ]]; then
    echo "Could not find /etc/k8s/env.sh"
    exit 1
fi

. /etc/k8s/env.sh



#export ESKIMO_FLANNEL_ETCD_ENDPOINTS=$ESKIMO_ETCD_ENDPOINTS
#echo "   + Using ESKIMO_FLANNEL_ETCD_ENDPOINTS=$ESKIMO_FLANNEL_ETCD_ENDPOINTS"

export ESKIMO_KUBE_ETCD_SERVER="http://$SELF_IP_ADDRESS:$EKIMO_ETCD_PORT"
echo "   + Using ESKIMO_KUBE_ETCD_SERVER=$ESKIMO_KUBE_ETCD_SERVER"

# Directory prefix of flannel settings in etcd
export ESKIMO_FLANNEL_ETCD_PREFIX="$FLANNEL_ETCD_PREFIX"
echo "   + Using ESKIMO_FLANNEL_ETCD_PREFIX=$ESKIMO_FLANNEL_ETCD_PREFIX"

#export ESKIMO_FLANNEL_CA_FILE=/etc/k8s/ssl/ca.pem
#echo "   + Using ESKIMO_FLANNEL_CA_FILE=$ESKIMO_FLANNEL_CA_FILE"
#
#export ESKIMO_FLANNEL_CERT_FILE=/etc/k8s/ssl/flanneld.pem
#echo "   + Using ESKIMO_FLANNEL_CERT_FILE=$ESKIMO_FLANNEL_CERT_FILE"
#
#export ESKIMO_FLANNEL_KEY_FILE=/etc/k8s/ssl/flanneld-key.pem
#echo "   + Using ESKIMO_FLANNEL_KEY_FILE=$ESKIMO_FLANNEL_KEY_FILE"

export ESKIMO_FLANNEL_CLUSTER_CIDR=$CLUSTER_CIDR
echo "   + Using ESKIMO_FLANNEL_CLUSTER_CIDR=$ESKIMO_FLANNEL_CLUSTER_CIDR"