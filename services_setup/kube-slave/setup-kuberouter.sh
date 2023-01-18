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

echoerr() { echo "$@" 1>&2; }

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $SCRIPT_DIR/common.sh "$@"

# CHange current folder to script dir (important !)
cd $SCRIPT_DIR || exit 199

# Loading topology
if [[ ! -f /etc/k8s/env.sh ]]; then
    echo "Could not find /etc/k8s/env.sh"
    exit 1
fi

. /etc/k8s/env.sh

sudo rm -Rf /tmp/kuberouter_setup
mkdir /tmp/kuberouter_setup
cd /tmp/kuberouter_setup

# Defining topology variables
if [[ $SELF_NODE_NUMBER == "" ]]; then
    echo " - No Self Node Number found in topology"
    exit 1
fi

if [[ $SELF_IP_ADDRESS == "" ]]; then
    echo " - No Self IP address found in topology for node $SELF_NODE_NUMBER"
    exit 2
fi




echo " - Installing / configuring / checking kube-router "

if [[ ! -d /var/lib/kuberouter ]]; then
    echo "   + Creating /var/lib/kuberouter"
    sudo mkdir -p /var/lib/kuberouter
    sudo chown -R kubernetes /var/lib/kuberouter
    sudo chmod 744 /var/lib/kuberouter
fi

set -e

if [[ ! -f /etc/k8s/shared/ssl/kuberouter-csr.json ]]; then
    echo "   + Create and install kuberouter-csr.json"
    cat > kuberouter-csr.json <<EOF
{
  "CN": "system:kuberouter",
  "hosts": [],
  "key": {
    "algo": "rsa",
    "size": 2048
  },
   "names": [
      {
        "C": "SH",
        "ST": "Eskimo",
        "L": "Eskimo",
        "O": "system:masters",
        "OU": "System"
      }
    ]
}
EOF

    sudo mv kuberouter-csr.json /etc/k8s/shared/ssl/kuberouter-csr.json
    sudo chown kubernetes /etc/k8s/shared/ssl/kuberouter-csr.json
    sudo chmod 755 /etc/k8s/shared/ssl/kuberouter-csr.json
fi


if [[ ! -f /etc/k8s/shared/ssl/kuberouter.pem ]]; then

    # Generate certificates
    echo "   + Generate kubernetes certificates"

    sudo /usr/local/bin/cfssl gencert -ca=/etc/k8s/shared/ssl/ca.pem \
      -ca-key=/etc/k8s/shared/ssl/ca-key.pem \
      -config=/etc/k8s/shared/ssl/ca-config.json \
      -profile=kubernetes /etc/k8s/shared/ssl/kuberouter-csr.json | cfssljson -bare kuberouter

    echo "   + Install kubernetes certificates"
    sudo mv kuberouter*.pem /etc/k8s/shared/ssl/
    sudo chown kubernetes /etc/k8s/shared/ssl/kuberouter*.pem
    sudo mv kuberouter*csr* /etc/k8s/shared/ssl/
    sudo chown kubernetes /etc/k8s/shared/ssl/kuberouter*csr*
fi

if [[ ! -f /etc/cni/net.d/10-kuberouter.conf ]]; then

    echo "   + Create and install 10-kuberouter.conf"
    cat > 10-kuberouter.conf <<EOF
{
    "cniVersion": "0.3.0",
    "name":"eskimo",
    "type":"bridge",
    "bridge":"kube-bridge",
    "isDefaultGateway":true,
    "ipam": {
        "type":"host-local"
     }
}
EOF

    sudo mkdir -p /etc/cni/net.d/
    sudo mv 10-kuberouter.conf /etc/cni/net.d/10-kuberouter.conf
    sudo chmod 755 /etc/cni/net.d/10-kuberouter.conf
fi

set +e


sudo rm -Rf /tmp/kuberouter_setup