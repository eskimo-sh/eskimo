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

# CHange current folder to script dir (important !)
cd $SCRIPT_DIR || exit 199

if [[ ! -f /etc/k8s/env.sh ]]; then
    echo "Could not find /etc/k8s/env.sh"
    exit 1
fi

. /etc/k8s/env.sh


echo " - Deploying Kubernetes services"

echo "   + create temp folder"
tmp_dir=$(mktemp -d -t ci-XXXXXXXXXX)
cd $tmp_dir || (echo "Couldn't cd to $tmp_dir" && exit 1)

# Making /root/.kube/config available
export HOME=/root

echo " - Deploying CoreDNS"

echo "   + Creating runtime resolv.conf"

# copy resolve.conf somewhere else and remove 127.0.0.x (and add 8.8.8.8 if it now has no nameserve ranymore)
# and use that one if it contains 12.0.0.X and other stuff
# Copy it to /etc/k8s/resolv.conf
cat /etc/resolv.conf | sed s/'nameserver 127.0.0.*'//g > /etc/k8s/shared/resolv.conf

# if no nameserver remains, add 8.8.8.8
if [[ $(grep nameserver /etc/k8s/shared/resolv.conf) == "" ]]; then
  echo "nameserver 8.8.8.8" >> /etc/k8s/shared/resolv.conf
fi

echo "   + Creating runtime core-dns file"
/bin/cp -f /etc/k8s/services/core-dns.yaml /var/lib/kubernetes/core-dns.yaml

echo "   + Injecting runtime properties"

sed -i "s/CLUSTER_DNS_SVC_IP/$CLUSTER_DNS_SVC_IP/g" /var/lib/kubernetes/core-dns.yaml
sed -i "s/CLUSTER_DNS_DOMAIN/$CLUSTER_DNS_DOMAIN/g" /var/lib/kubernetes/core-dns.yaml

echo "   + Deploying core-dns"
kubectl apply -f /var/lib/kubernetes/core-dns.yaml

echo " - Deploying kube-state-metrics"

echo "   + Creating runtime kube-state-metrics file"
/bin/cp -f /etc/k8s/services/kube-state-metrics.yaml /var/lib/kubernetes/kube-state-metrics.yaml

echo "   + Deploying kube-state-metrics"
kubectl apply -f /var/lib/kubernetes/kube-state-metrics.yaml



echo "   + removing temp folder"
rm -Rf $tmp_dir