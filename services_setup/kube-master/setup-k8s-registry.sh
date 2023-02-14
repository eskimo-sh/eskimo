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
loadTopology

sudo rm -Rf k8s-registry_install_log

echo " - Getting kubernetes user"
export kubernetes_user_id=$(id -u kubernetes 2>> k8s-registry_install_log)
if [[ $kubernetes_user_id == "" ]]; then
    echo "User kubernetes should have been added by eskimo-base-system setup script"
    exit 4
fi


echo " - Installing kubernetes docker registry"

echo "   + Registering kubernetes registry"
sudo /usr/local/sbin/register-kubernetes-registry.sh

sudo mkdir -p /var/log/kubernetes/docker_registry
sudo chown -R kubernetes /var/log/kubernetes/docker_registry
sudo mkdir -p /var/lib/kubernetes/docker_registry
sudo chown -R kubernetes /var/lib/kubernetes/docker_registry

echo "   + Installing setupKubernetesRegistryGlusterShares.sh to /usr/local/sbin"
sudo cp setupKubernetesRegistryGlusterShares.sh /usr/local/sbin/
sudo chmod 755 /usr/local/sbin/setupKubernetesRegistryGlusterShares.sh

echo "   + Building container k8s-registry"
build_container k8s-registry kube-master k8s-registry_install_log
#save tag
CONTAINER_TAG=$CONTAINER_NEW_TAG

#sudo mkdir -p /usr/local/etc/kubernetes
#sudo chown -R kubernetes/usr/local/etc/kubernetes

# create and start container
echo "   + Running docker container"
docker run \
        -v $PWD:/scripts \
        -v $PWD/../common:/common \
        -d --name k8s-registry \
        -v /var/log/kubernetes:/var/log/kubernetes \
        -v /var/run/kubernetes:/var/run/kubernetes \
        -v /var/lib/kubernetes:/var/lib/kubernetes \
        -v /var/lib/docker_registry:/var/lib/docker_registry \
        -v /var/log/docker_registry:/var/log/docker_registry \
        --mount type=bind,source=/etc/eskimo_topology.sh,target=/etc/eskimo_topology.sh \
        -it \
        "eskimo/k8s-registry:$CONTAINER_TAG" \
            bash >> k8s-registry_install_log 2>&1
fail_if_error $? "k8s-registry_install_log" -2


# connect to container
#docker exec -it kubernetes bash

echo "   + Configuring Registry in container"
docker exec k8s-registry bash /scripts/inContainerSetupRegistry.sh $kubernetes_user_id $SELF_IP_ADDRESS | tee -a k8s-registry_install_log 2>&1
check_in_container_config_success k8s-registry_install_log

echo " - Handling Eskimo Base Infrastructure"
handle_eskimo_base_infrastructure k8s-registry k8s-registry_install_log

echo "   + Handling topology infrastructure"
handle_topology_infrastructure k8s-registry k8s-registry_install_log

echo "   + Committing changes to local template and exiting container kubernetes"
commit_container k8s-registry $CONTAINER_TAG k8s-registry_install_log

echo "   + Installing and checking systemd service file"
install_and_check_service_file k8s-registry k8s_install_log SKIP_COPY,RESTART