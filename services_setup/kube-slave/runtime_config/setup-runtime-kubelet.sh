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

# CHange current folder to script dir (important !)
cd $SCRIPT_DIR

if [[ ! -f /etc/k8s/env.sh ]]; then
    echo "Could not find /etc/k8s/env.sh"
    exit 1
fi

. /etc/k8s/env.sh

echo " - Creating / checking eskimo kubelet config"

if [[ `/usr/local/bin/kubectl get clusterrolebinding kubelet-bootstrap | grep system:node-bootstrapper` == "" ]]; then

    echo "   + Create cluster role system:node-bootstrapper in the cluster for kubelet-bootstrap"

    kubectl create clusterrolebinding kubelet-bootstrap \
     --clusterrole=system:node-bootstrapper \
     --user=kubelet-bootstrap
fi

echo "   + create temp folder"
tmp_dir=$(mktemp -d -t ci-XXXXXXXXXX)
cd $tmp_dir

echo "   + Configure the cluster parameters"
kubectl config set-cluster eskimo \
  --certificate-authority=/etc/k8s/ssl/ca.pem \
  --embed-certs=true \
  --server=${ESKIMO_KUBE_APISERVER} \
  --kubeconfig=bootstrap.kubeconfig

echo "   + Configure authentication parameters"
kubectl config set-credentials kubelet-bootstrap \
  --token=${BOOTSTRAP_TOKEN} \
  --client-certificate=/etc/k8s/ssl/kubernetes.pem \
  --client-key=/etc/k8s/ssl/kubernetes-key.pem \
  --kubeconfig=bootstrap.kubeconfig

echo "   + Configure the context"
kubectl config set-context eskimo \
  --cluster=eskimo \
  --user=kubelet-bootstrap \
  --kubeconfig=bootstrap.kubeconfig

echo "   + Use the default context"
kubectl config use-context eskimo --kubeconfig=bootstrap.kubeconfig

echo "   + installing new configuration"
sudo mv bootstrap.kubeconfig /etc/k8s/bootstrap.kubeconfig
sudo chown root /etc/k8s/bootstrap.kubeconfig
sudo chmod 755 /etc/k8s/bootstrap.kubeconfig

echo "   + Deleting runtime configuration for recreation"
rm -Rf /etc/k8s/kubelet.kubeconfig

echo "   + removing temp folder"
rm -Rf $tmp_dir

