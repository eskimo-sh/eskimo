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

# CHange current folder to script dir (important !)
cd $SCRIPT_DIR

if [[ ! -f /etc/k8s/env.sh ]]; then
    echo "Could not find /etc/k8s/env.sh"
    exit 1
fi

. /etc/k8s/env.sh

echo " - Creating kube config file for user eskimo"

echo "   + create temp folder"
tmp_dir=$(mktemp -d -t ci-XXXXXXXXXX)
cd $tmp_dir

echo "   + Configure the cluster and the certificates"
kubectl config set-cluster eskimo \
  --certificate-authority=/etc/k8s/ssl/ca.pem \
  --embed-certs=true \
  --server=$ESKIMO_KUBE_APISERVER \
  --kubeconfig=temp.kubeconfig

export ADMIN_USER=`cat /etc/eskimo_user`

echo "   + Configure client side user and certificates"
kubectl config set-credentials $ADMIN_USER \
  --client-certificate=/etc/k8s/ssl/$ADMIN_USER.pem \
  --client-key=/etc/k8s/ssl/$ADMIN_USER-key.pem \
  --token=$BOOTSTRAP_TOKEN \
  --kubeconfig=temp.kubeconfig

echo "   + Create context"
kubectl config set-context eskimo \
  --cluster=eskimo \
  --user=$ADMIN_USER \
  --kubeconfig=temp.kubeconfig

echo " - Set the context eskimo as default context"
kubectl config use-context eskimo --kubeconfig=temp.kubeconfig

echo "   + removing previous configuration"
rm -f /home/$ADMIN_USER/.kube/config

echo "   + installing new configuration"
mkdir -p /home/$ADMIN_USER/.kube/
sudo chown $ADMIN_USER.$ADMIN_USER /home/$ADMIN_USER/.kube
mv temp.kubeconfig /home/$ADMIN_USER/.kube/config
sudo chown $ADMIN_USER.$ADMIN_USER /home/$ADMIN_USER/.kube/config

sudo mkdir -p /root/.kube/
sudo cp /home/$ADMIN_USER/.kube/config /root/.kube/config
sudo chown root /root/.kube/config

echo "   + Checking kube config file"
if [[ ! -f /home/$ADMIN_USER/.kube/config ]]; then
    echo "Couldn't find file /home/"$ADMIN_USER"/.kube/config"
    exit 101
fi

kube_config_file=/home/$ADMIN_USER/.kube/config
if [[ `cat $kube_config_file | grep "name: eskimo" | wc -l` -lt 2 ]]; then
    echo "Missing config in file /home/"$ADMIN_USER"/.kube/config"
    exit 101
fi

echo "   + removing temp folder"
rm -Rf $tmp_dir