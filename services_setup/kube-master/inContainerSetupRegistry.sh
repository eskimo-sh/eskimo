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

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $SCRIPT_DIR/common.sh "$@"


KUBERNETES_USER_ID=$1
if [[ $KUBERNETES_USER_ID == "" ]]; then
    echo " - Didn't get KUBERNETES User ID as argument"
    exit 2
fi

# Loading topology
. /etc/eskimo_topology.sh

# Defining topology variables
if [[ $SELF_NODE_NUMBER == "" ]]; then
    echo " - No Self Node Number found in topology"
    exit 3
fi

if [[ $SELF_IP_ADDRESS == "" ]]; then
    echo " - No Self IP address found in topology for node $SELF_NODE_NUMBER"
    exit 4
fi


echo " - Creating kubernetes user (if not exist) in container"
set +e
kubernetes_user_id=$(id -u kubernetes 2>/dev/null)
set -e
if [[ $kubernetes_user_id == "" ]]; then
    useradd -u $KUBERNETES_USER_ID kubernetes
elif [[ $kubernetes_user_id != $KUBERNETES_USER_ID ]]; then
    echo "Docker KUBERNETES USER ID is $KUBERNETES_USER_ID while requested USER ID is $KUBERNETES_USER_ID"
    exit 5
fi

echo " - Creating user kubernetes home directory"
mkdir -p /home/kubernetes
chown -R kubernetes /home/kubernetes

echo " - Updating kubernetes registry config"
sed -i s/"rootdirectory: \/var\/lib\/docker_registry"/"rootdirectory: \/var\/lib\/kubernetes\/docker_registry"/g /etc/docker_registry/config.yml
#rootdirectory: /var/lib/docker_registry

echo " - disabling TLS for kubernetes.registry in regctl"
# for user kubernetes
regctl registry set --tls disabled kubernetes.registry:5000
cp -R /root/.regctl /home/kubernetes/
chown -R kubernetes /home/kubernetes/.regctl

# Caution : the in container setup script must mandatorily finish with this log"
echo "$IN_CONTAINER_CONFIG_SUCESS_MESSAGE"
