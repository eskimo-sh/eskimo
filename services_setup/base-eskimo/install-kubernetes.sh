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

if [[ $TEST_MODE != "true" ]]; then
    . /etc/eskimo_topology.sh
else
    # use co-located dummy topology file in test mode
    . ./eskimo_topology.sh
fi


function fail_if_error(){
    if [[ $1 != 0 ]]; then
        echo " -> failed !!"
        cat $2
        exit $3
    fi
}

echo "-- INSTALLING KUBERNETES ------------------------------------------------------"

saved_dir=$(pwd)
function returned_to_saved_dir() {
     cd $saved_dir || return
}
trap returned_to_saved_dir 15
trap returned_to_saved_dir EXIT
trap returned_to_saved_dir ERR


echo " - Changing to temp directory"
sudo rm -Rf /tmp/kube_setup > /tmp/kube_install_log 2>&1
mkdir -p /tmp/kube_setup

echo " - Getting eskimo Kube package version"
export ESKIMO_KUBE_PACKAGE=$(/bin/ls -1 eskimo_kube_*.tar.gz)
export K8S_VERSION=$(basename -s .tar.gz $ESKIMO_KUBE_PACKAGE  | awk -F"_" '{ print $3 }')

if [[ "$K8S_VERSION" == "" ]]; then
    echo "Failed to get Eskimo Kube package version"
    exit 101
else
    echo "  + Eskimo Kube package version is $K8S_VERSION"
fi

mv eskimo_kube_$K8S_VERSION*.tar.gz /tmp/kube_setup/eskimo_kube_$K8S_VERSION.tar.gz

if [[ -z $TEST_MODE ]]; then
    cd /tmp/kube_setup
fi


echo " - Extracting kube_$K8S_VERSION"
tar -zxf eskimo_kube_$K8S_VERSION.tar.gz > /tmp/kube_install_log 2>&1
fail_if_error $? "/tmp/kube_install_log" -2

echo " - Removing any previous Kubernetes installation"
sudo rm -Rf /usr/local/lib/k8s-$K8S_VERSION
sudo rm -Rf /usr/local/lib/k8s

echo " - Installing Kubernetes"
sudo chown root.root -R k8s
sudo mv k8s /usr/local/lib/k8s-$K8S_VERSION

echo " - Symlinking /usr/local/lib/k8s-$K8S_VERSION to /usr/local/lib/k8s"
sudo ln -s /usr/local/lib/k8s-$K8S_VERSION /usr/local/lib/k8s

set -e

echo " - Simlinking k8s config to /usr/local/etc/k8s"
sudo mkdir -p /usr/local/etc/k8s
if [[ ! -L /usr/local/lib/k8s/etc ]]; then
    sudo ln -s /usr/local/etc/k8s /usr/local/lib/k8s/etc
fi

echo " - Simlinking etcd config to /usr/local/etc/etcd"
sudo mkdir -p /usr/local/etc/etcd
if [[ ! -L /usr/local/lib/k8s/etcd_etc ]]; then
    sudo ln -s /usr/local/etc/etcd /usr/local/lib/k8s/etcd_etc
fi

echo " - Simlinking K8s binaries to /usr/local/bin"

echo "   + cfssl"
if [[ ! -f /usr/local/bin/cfssl ]]; then
    sudo ln -s /usr/local/lib/k8s/cfssl/bin/cfssl /usr/local/bin/cfssl
fi
if [[ ! -f /usr/local/bin/cfssljson ]]; then
    sudo ln -s /usr/local/lib/k8s/cfssl/bin/cfssljson /usr/local/bin/cfssljson
fi

echo "   + etcd"
for i in $(ls -1 /usr/local/lib/k8s/etcd/bin/); do
    if [[ ! -f /usr/local/bin/$i ]]; then
        sudo ln -s /usr/local/lib/k8s/etcd/bin/$i /usr/local/bin/$i
    fi
done

echo "   + Kubernetes client"
for i in $(ls -1 /usr/local/lib/k8s/kubernetes/client/bin/); do
    if [[ ! -f /usr/local/bin/$i ]]; then
        sudo ln -s /usr/local/lib/k8s/kubernetes/client/bin/$i /usr/local/bin/$i
    fi
done

echo "   + Kube-router"
for i in $(ls -1 /usr/local/lib/k8s/kube-router/bin/); do
    if [[ ! -f /usr/local/bin/$i ]]; then
        sudo ln -s /usr/local/lib/k8s/kube-router/bin/$i /usr/local/bin/$i
    fi
done

echo "   + Kubernetes server"
for i in $(ls -1 /usr/local/lib/k8s/kubernetes/server/bin/); do
    if [[ ! -f /usr/local/bin/$i ]]; then
        sudo ln -s /usr/local/lib/k8s/kubernetes/server/bin/$i /usr/local/bin/$i
    fi
done

echo "   + Installing cri-dockerd"
for i in $(ls -1 /usr/local/lib/k8s/cri-dockerd/); do
    if [[ ! -f /usr/local/bin/$i ]]; then
        sudo ln -s /usr/local/lib/k8s/cri-dockerd/$i /usr/local/bin/$i
    fi
done

echo "   + Installing cni plugins"
sudo mkdir -p /opt/cni/
if [[ ! -f /opt/cni/bin ]]; then
    sudo ln -s /usr/local/lib/k8s/cni-plugins /opt/cni/bin
fi

if [[ -z $TEST_MODE ]]; then
    echo " - Create / update eskimo K8S version file"
    sudo bash -c "echo K8S_VERSION=$(find /usr/local/lib/ -mindepth 1 -maxdepth 1 ! -type l | grep 'k8s-*.*' | cut -d '-' -f 2) > /etc/eskimo_k8s_environment"

    echo " - Checking eskimo K8S version file"
    if [[ ! -f /etc/eskimo_k8s_environment ]]; then
        echo "Could not create /etc/eskimo_k8s_environment"
        exit 21
    fi
    . /etc/eskimo_k8s_environment

    if [[ ! -d /usr/local/lib/k8s-$K8S_VERSION ]]; then
        echo "/etc/eskimo_k8s_environment doesn't point to valid mesos version"
        exit 21
    fi
fi

echo " - Cleaning build folder"
cd $saved_dir
sudo rm -Rf /tmp/kube_setup > /tmp/kube_install_log 2>&1
fail_if_error $? "/tmp/kube_install_log" -10


returned_to_saved_dir




