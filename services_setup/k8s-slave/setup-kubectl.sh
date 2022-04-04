#!/usr/bin/env bash

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

echoerr() { echo "$@" 1>&2; }

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $SCRIPT_DIR/common.sh "$@"

# CHange current folder to script dir (important !)
cd $SCRIPT_DIR

if [[ ! -f /etc/k8s/env.sh ]]; then
    echo "Could not find /etc/k8s/env.sh"
    exit 1
fi

. /etc/k8s/env.sh

sudo rm -Rf /tmp/kube_base_setup
mkdir /tmp/kube_base_setup
cd /tmp/kube_base_setup

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

if [[ ! -d /etc/k8s/ssl/ ]]; then
    echo " - Creating folder /etc/k8s/ssl/"
    sudo mkdir -p /etc/k8s/ssl/
fi

# attempt to recreate  / remount gluster shares
sudo /bin/bash /usr/local/sbin/setupK8sGlusterShares.sh

echo " - Creating / checking eskimo kubernetes base config"


function delete_ssl_lock_file() {
     rm -Rf /etc/k8s/ssl/ssl_management_lock
}

# From here we will be messing with gluster and hence we need to take a lock
counter=0
while [[ -f /etc/k8s/ssl/ssl_management_lock ]] ; do
    echo "   + /etc/k8s/ssl/ssl_management_lock exist. waiting 2 secs ... "
    sleep 2
    let counter=counter+1
    if [[ $counter -ge 15 ]]; then
        echo " !!! Couldn't get /etc/k8s/ssl/ssl_management_lock in 30 seconds. crashing !"
        exit 150
    fi
done

touch /etc/k8s/ssl/ssl_management_lock

trap delete_ssl_lock_file 15
trap delete_ssl_lock_file EXIT


if [[ ! -f /etc/k8s/ssl/ca-config.json ]]; then
    echo "   + Create and install ca-config.json"
    cat > ca-config.json <<EOF
{
    "signing": {
        "default": {
            "expiry": "43800h"
        },
        "profiles": {
            "server": {
                "expiry": "43800h",
                "usages": [
                    "signing",
                    "key encipherment",
                    "server auth"
                ]
            },
            "client": {
                "expiry": "43800h",
                "usages": [
                    "signing",
                    "key encipherment",
                    "client auth"
                ]
            },
            "peer": {
                "expiry": "43800h",
                "usages": [
                    "signing",
                    "key encipherment",
                    "server auth",
                    "client auth"
                ]
            },
            "kubernetes": {
                "expiry": "43800h",
                "usages": [
                    "signing",
                    "key encipherment",
                    "server auth",
                    "client auth"
                ]
            }
        }
    }
}
EOF

    sudo mv ca-config.json /etc/k8s/ssl/ca-config.json
    sudo chown root /etc/k8s/ssl/ca-config.json
    sudo chmod 755 /etc/k8s/ssl/ca-config.json
fi

if [[ ! -f /etc/k8s/ssl/ca-csr.json ]]; then
    echo "   + Create and install ca-csr.json"
    cat > ca-csr.json <<EOF
{
  "CN": "eskimo",
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

    sudo mv ca-csr.json /etc/k8s/ssl/ca-csr.json
    sudo chown root /etc/k8s/ssl/ca-csr.json
    sudo chmod 755 /etc/k8s/ssl/ca-csr.json
fi


# TODO re-generate cert with the following
# echo "   + generate certificate ca.pen"
# cfssl gencert -initca /etc/k8s/ssl/ca-csr.json | cfssljson -bare ca

if [[ ! -f /etc/k8s/ssl/ca.pem ]]; then
    # Generate certificates
    echo "   + Generate root certificates"
    sudo /usr/local/bin/cfssl gencert -initca /etc/k8s/ssl/ca-csr.json | cfssljson -bare ca

    echo "   + Install root certificates"
    sudo mv ca*.pem /etc/k8s/ssl/
    sudo mv ca*csr* /etc/k8s/ssl/
fi


if [[ ! -f /etc/k8s/ssl/$USER-csr.json ]]; then
    echo "   + Create and install ${user}-csr.json"
    cat > $USER-csr.json <<EOF
{
  "CN": "$USER",
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

    sudo mv $USER-csr.json /etc/k8s/ssl/$USER-csr.json
fi

if [[ ! -f /etc/k8s/ssl/$USER.pem ]]; then
    # Generate certificates
    echo "   + Generate Admin certificates"
    sudo /usr/local/bin/cfssl gencert -ca=/etc/k8s/ssl/ca.pem \
      -ca-key=/etc/k8s/ssl/ca-key.pem \
      -config=/etc/k8s/ssl/ca-config.json \
      -profile=kubernetes /etc/k8s/ssl/`echo $USER`-csr.json | cfssljson -bare `echo $USER`

    echo "   + Install Admin certificates"
    sudo mv `echo $USER`*.pem /etc/k8s/ssl/
    sudo mv `echo $USER`*csr* /etc/k8s/ssl/
fi

delete_ssl_lock_file

set +e

rm -Rf /tmp/kube_base_setup