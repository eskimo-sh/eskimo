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

# Loading topology
loadTopology

# Defining topology variables
if [[ $SELF_NODE_NUMBER == "" ]]; then
    echo " - No Self Node Number found in topology"
    exit 1
fi

if [[ $SELF_IP_ADDRESS == "" ]]; then
    echo " - No Self IP address found in topology for node $SELF_NODE_NUMBER"
    exit 2
fi


# reinitializing log
sudo rm -f k8s_install_log

echo " - Creating shared directory"
if [[ ! -d /var/lib/kubernetes ]]; then
    sudo mkdir -p /var/lib/kubernetes
    sudo chmod -R 777 /var/lib/kubernetes
    sudo chown -R kubernetes /var/lib/kubernetes
fi
if [[ ! -d /var/run/kubernetes ]]; then
    sudo mkdir -p /var/run/kubernetes
    sudo chown -R kubernetes /var/run/kubernetes
fi
if [[ ! -d /var/log/kubernetes ]]; then
    sudo mkdir -p /var/log/kubernetes
    sudo chmod -R 777 /var/log/kubernetes
    sudo chown -R kubernetes /var/log/kubernetes
fi


if [[ -f /usr/local/sbin/register-kubernetes-registry.sh ]]; then
    echo " - Removing existing register-kubernetes-registry.sh (will be rewritten)"
    sudo rm -f /usr/local/sbin/register-kubernetes-registry.sh
fi

echo " - Copying register-kubernetes-registry.sh script"
sudo cp $SCRIPT_DIR/register-kubernetes-registry.sh /usr/local/sbin/
sudo chmod 754 /usr/local/sbin/register-kubernetes-registry.sh



# TODO Create Kubernetes environment and SystenD unit files

echo " - Linking /etc/k8s to /usr/local/lib/k8s/etc"
if [[ ! -L /etc/k8s ]]; then
    sudo ln -s /usr/local/lib/k8s/etc /etc/k8s
fi

echo " - Copying kubernetes env files to /etc/k8s"
for i in `find ./etc_k8s -mindepth 1`; do
    sudo cp $i /etc/k8s/
    filename=`echo $i | cut -d '/' -f 3`
    sudo chmod 755 /etc/k8s/$filename
done

echo " - Copying runtime configuration scripts to /etc/k8s/runtime_config"
sudo mkdir -p /etc/k8s/runtime_config
for i in `find ./runtime_config -mindepth 1`; do
    sudo cp $i /etc/k8s/runtime_config/
    filename=`echo $i | cut -d '/' -f 3`
    sudo chmod 755 /etc/k8s/runtime_config/$filename
done

echo " - Copying SystemD unit files to /lib/systemd/system"
for i in `find ./service_files -mindepth 1`; do
    sudo cp $i /lib/systemd/system/
    filename=`echo $i | cut -d '/' -f 3`
    sudo chmod 755 /lib/systemd/system/$filename
done

echo " - Creating eskimo_user file"
export ESKIMO_USER=$USER
sudo bash -c "echo $USER > /etc/eskimo_user"


# Setup all individual services
echo " - Installing setupK8sGlusterShares.sh to /usr/local/sbin"
sudo cp setupK8sGlusterShares.sh /usr/local/sbin/
sudo chmod 755 /usr/local/sbin/setupK8sGlusterShares.sh

bash ./setup-kubectl.sh
fail_if_error $? /dev/null 301

bash /etc/k8s/runtime_config/setup-runtime-kubectl.sh
fail_if_error $? /dev/null 302

bash ./setup-etcd.sh
fail_if_error $? /dev/null 303

# TODO Not for now, let's see if I really need that
#bash ./setup-flannel.sh

bash ./setup-kubelet.sh
fail_if_error $? /dev/null 304

bash ./setup-kubeproxy.sh
fail_if_error $? /dev/null 305




# TODO
# ----------------------------------------------------------------------------------------------------------------------


echo " - Copying k8s-slave process file to /usr/local/sbin"
sudo cp run-k8s-slave.sh /usr/local/sbin/
sudo chmod 755 /usr/local/sbin/run-k8s-slave.sh


echo " - Create / update eskimo K8S version file"
sudo bash -c "echo K8S_VERSION=`find /usr/local/lib/ -mindepth 1 -maxdepth 1 ! -type l | grep \"k8s-*.*\" | cut -d '-' -f 2` > /etc/eskimo_k8s_environment"

echo " - Checking eskimo KUBERNETES version file"
if [[ -z $TEST_MODE && ! -f /etc/eskimo_k8s_environment ]]; then
    echo "Could not create /etc/eskimo_k8s_environment"
    exit 21
fi
. /etc/eskimo_k8s_environment

if [[ -z $TEST_MODE && ! -d /usr/local/lib/k8s-$K8S_VERSION ]]; then
    echo "/etc/eskimo_k8s_environment doesn't point to valid Kubernetes version"
    exit 21
fi

echo " - Installing and checking systemd service file"
install_and_check_service_file k8s-slave k8s_install_log



#ACZUALLY IT?S MORE COMPLICATED THAT THAN
#- I need to start etcd (create partial function of install_and_check_service_file above for this)
#- then configure flannel witin etcd
#- then start flannel
#- and finall start k8s.slave
#(same in master)

# TODO setup flannel