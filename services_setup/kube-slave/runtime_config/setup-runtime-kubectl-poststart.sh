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

. /usr/local/sbin/eskimo-utils.sh

echo " - Postprocessing kube config file for user eskimo"

echo "   + create temp folder"
tmp_dir=$(mktemp -d -t ci-XXXXXXXXXX)
cd $tmp_dir


take_global_lock k8s_poststart_management_lock /etc/k8s/
if [[ $? != 0 ]]; then
    echo " !!! Couldn't get /etc/k8s/k8s_poststart_management_lock in 30 seconds. crashing !"
    exit 150
fi


set -e

export ADMIN_USER=$(cat /etc/eskimo_user)

# Unfortunately, system doesn't pass me any environment variable, so I have to set them here
# I am ensuring I run as root before messing with it
if [[ $(id -u) != 0 ]]; then
    echo "This ExecStartPost script is intented to be executed as root"
    exit 200
fi

# Making /root/.kube/config available
export HOME=/root

if [[ $(kubectl get namespace | grep eskimo) == "" ]]; then
    echo "   + Creating namespace eskimo"
    kubectl create namespace eskimo
    sleep 2
fi

# Not recreating systematically  on slave
if [[ $(kubectl get serviceaccount | grep $ADMIN_USER) == "" ]]; then
    echo "   + Creating serviceaccount-$ADMIN_USER"
    kubectl apply -f /etc/k8s/serviceaccount-$ADMIN_USER.yaml
    sleep 2
fi

if [[ $(kubectl get secret | grep $ADMIN_USER) == "" ]]; then
    echo "   + Creating serviceaccount-$ADMIN_USER-secret"
    kubectl apply -f /etc/k8s/serviceaccount-$ADMIN_USER-secret.yaml
fi

if [[ $(kubectl get ClusterRoleBinding | grep default-$ADMIN_USER) == "" ]]; then
    echo "   + Creating ClusterRoleBinding default-$ADMIN_USER"
    kubectl apply -f /etc/k8s/clusterrolebinding-default-$ADMIN_USER.yaml
fi

if [[ $(kubectl get ClusterRoleBinding | grep kubernetes-dashboard-$ADMIN_USER) == "" ]]; then
    echo "   + Creating ClusterRoleBinding kubernetes-dashboard-$ADMIN_USER"
    kubectl apply -f /etc/k8s/clusterrolebinding-kubernetes-dashboard-$ADMIN_USER.yaml
fi

echo "   + Getting token"
sleep 4
NEW_TOKEN=$(kubectl describe secret $ADMIN_USER-secret | grep token: | sed 's/token: *\(.*\)/\1/')
if [[ "$NEW_TOKEN" == "" ]]; then
    echo " !! Failed to get new token"
    echo "  -> this is the output of kubectl get secrets"
    kubectl get secrets
    exit 51
fi

echo "   + Installing new token"
sudo sed -i s/'token: .*'/"token: $NEW_TOKEN"/ /home/$ADMIN_USER/.kube/config
sudo chown $ADMIN_USER.$ADMIN_USER /home/$ADMIN_USER/.kube/config

echo "   + Checking installation"
if [[ $(grep -F $NEW_TOKEN /home/$ADMIN_USER/.kube/config) == "" ]]; then
    echo "Failed to install new token"
    exit 61
fi

echo "   + Copying kube config file to root"
sudo cp /home/$ADMIN_USER/.kube/config /root/.kube/config
sudo chown root.root /root/.kube/config

echo "   + Copying kube config file to all eskimo users"
for eskuser in $(grep -F eskimoservices /etc/group | cut -d ':' -f 4 | sed 's/,/ /g'); do
    sudo mkdir -p /home/$eskuser/.kube/
    sudo chown -R $eskuser. /home/$eskuser/.kube/
    sudo cp /home/$ADMIN_USER/.kube/config /home/$eskuser/.kube/config
    sudo chown $eskuser. /home/$eskuser/.kube/config
done


set +e


echo "   + removing temp folder"
rm -Rf $tmp_dir
