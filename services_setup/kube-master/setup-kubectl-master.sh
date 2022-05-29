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

echoerr() { echo "$@" 1>&2; }

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $SCRIPT_DIR/common.sh "$@"

# CHange current folder to script dir (important !)
cd $SCRIPT_DIR || exit 199

if [[ ! -f /etc/k8s/env.sh ]]; then
    echo "Could not find /etc/k8s/env.sh"
    exit 1
fi

. /etc/k8s/env.sh

sudo rm -Rf /tmp/kube_basemaster_setup
mkdir /tmp/kube_basemaster_setup
cd /tmp/kube_basemaster_setup

# Defining topology variables
if [[ $SELF_NODE_NUMBER == "" ]]; then
    echo " - No Self Node Number found in topology"
    exit 1
fi

if [[ $SELF_IP_ADDRESS == "" ]]; then
    echo " - No Self IP address found in topology for node $SELF_NODE_NUMBER"
    exit 2
fi



set -e


echo " - Creating / checking eskimo kubernetes base config (master only)"


echo "   + Create and install kubernetes-csr.json"
cat > kubernetes-csr.json <<EOF
{
  "CN": "kubernetes",
  "hosts": [
    "127.0.0.1",
    "${SELF_IP_ADDRESS}",
    "${MASTER_URL}",
    "${CLUSTER_KUBERNETES_SVC_IP}",
    "kubernetes",
    "kubernetes.default",
    "kubernetes.default.svc",
    "kubernetes.default.svc.cluster",
    "kubernetes.default.svc.cluster.local",
    "eskimo",
    "eskimo.default",
    "eskimo.default.svc",
    "eskimo.default.svc.cluster",
    "eskimo.default.svc.cluster.local",
    "eskimo.eskimo",
    "eskimo.eskimo.svc",
    "eskimo.eskimo.svc.cluster",
    "eskimo.eskimo.svc.cluster.local"
  ],
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

sudo mv kubernetes-csr.json /etc/k8s/ssl/kubernetes-csr.json
sudo chown kubernetes /etc/k8s/ssl/kubernetes-csr.json
sudo chmod 755 /etc/k8s/ssl/kubernetes-csr.json

# Generate certificates
echo "   + (Re-)Generate kubernetes certificates"

sudo /usr/local/bin/cfssl gencert -ca=/etc/k8s/ssl/ca.pem \
  -ca-key=/etc/k8s/ssl/ca-key.pem \
  -config=/etc/k8s/ssl/ca-config.json \
  -profile=kubernetes /etc/k8s/ssl/kubernetes-csr.json | cfssljson -bare kubernetes

echo "   + (Re-)Install kubernetes certificates"
sudo mv kubernetes*.pem /etc/k8s/ssl/
sudo chown kubernetes /etc/k8s/ssl/kubernetes*.pem
sudo mv kubernetes*csr* /etc/k8s/ssl/
sudo chown kubernetes /etc/k8s/ssl/kubernetes*csr*


set +e

rm -Rf /tmp/kube_basemaster_setup