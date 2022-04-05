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

export K8S_VERSION=1.23.5

function fail_if_error(){
    if [[ $1 != 0 ]]; then
        echo " -> failed !!"
        cat $2
        exit $3
    fi
}

function get_ip_address(){
    export IP_ADDRESS="`cat /etc/network/interfaces | grep address | cut -d ' ' -f 8`"
}

# extract IP address
function get_ip_address(){
    ip_from_ifconfig=`/sbin/ifconfig | grep $SELF_IP_ADDRESS`

    if [[ `echo $ip_from_ifconfig | grep Mask:` != "" ]]; then
      ip=`echo $ip_from_ifconfig  | sed 's/.*inet addr:\([0-9\.]*\).*/\1/'`
    elif [[ `echo $ip_from_ifconfig | grep netmask` != "" ]]; then
      ip=`echo $ip_from_ifconfig  | sed 's/.*inet \([0-9\.]*\).*/\1/'`
    fi

    export IP_ADDRESS=$ip
}

# compute CIDR suffix from network mask
function mask2cdr () {
    # Assumes there's no "255." after a non-255 byte in the mask
    local x=${1##*255.}
    set -- 0^^^128^192^224^240^248^252^254^ $(( (${#1} - ${#x})*2 )) ${x%%.*}
    x=${1%%$3*}
    echo $(( $2 + (${#x}/4) ))
}

function get_ip_root() {
    get_ip_address
    ip_root=`echo $IP_ADDRESS | sed 's/^\([0-9\.]*\)*[0-9]\{2\}/\1/'`
    echo $ip_root
}

function get_ip_CIDR() {
    ip_from_ifconfig=`/sbin/ifconfig | grep $SELF_IP_ADDRESS`

    if [[ `echo $ip_from_ifconfig | grep Mask:` != "" ]]; then
      netmask=`echo $ip_from_ifconfig  | sed 's/.*Mask:\(.*\)/\1/'`
    elif [[ `echo $ip_from_ifconfig | grep netmask` != "" ]]; then
      netmask=`echo $ip_from_ifconfig  | sed 's/.* netmask \([0-9\.]*\).*/\1/'`
    fi
    cdr=`mask2cdr $netmask`

    ip_root=`get_ip_root`

    echo "$ip_root"0/$cdr
}

function get_host_min() {
    ip_root=`get_ip_root`
    echo "$ip_root"1
}



echo "-- INSTALLING KUBERNETES ------------------------------------------------------"

if [ -z "$K8S_VERSION" ]; then
    echo "Need to set K8S_VERSION environment variable before calling this script !"
    exit 1
fi

saved_dir=`pwd`
function returned_to_saved_dir() {
     cd $saved_dir
}
trap returned_to_saved_dir 15
trap returned_to_saved_dir EXIT

echo " - Changing to temp directory"
sudo rm -Rf /tmp/k8s_setup > /tmp/k8s_install_log 2>&1
mkdir -p /tmp/k8s_setup

mv eskimo_k8s_$K8S_VERSION*.tar.gz /tmp/k8s_setup/eskimo_k8s_$K8S_VERSION.tar.gz

cd /tmp/k8s_setup


echo " - Extracting k8s_$K8S_VERSION"
tar -zxf eskimo_k8s_$K8S_VERSION.tar.gz > /tmp/k8s_install_log 2>&1
fail_if_error $? "/tmp/k8s_install_log" -2

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

echo " - Simlinking K8s binaries to /usr/local/bin"

echo "   + cfssl"
sudo ln -s /usr/local/lib/k8s/cfssl/bin/cfssl /usr/local/bin/cfssl
sudo ln -s /usr/local/lib/k8s/cfssl/bin/cfssljson /usr/local/bin/cfssljson

echo "   + etcd"
for i in `ls -1 /usr/local/lib/k8s/etcd/bin/`; do
    if [[ ! -f /usr/local/bin/$i ]]; then
        sudo ln -s /usr/local/lib/k8s/etcd/bin/$i /usr/local/bin/$i
    fi
done

echo "   + Kubernetes client"
for i in `ls -1 /usr/local/lib/k8s/kubernetes/client/bin/`; do
    if [[ ! -f /usr/local/bin/$i ]]; then
        sudo ln -s /usr/local/lib/k8s/kubernetes/client/bin/$i /usr/local/bin/$i
    fi
done

echo "   + Flannel"
for i in `ls -1 /usr/local/lib/k8s/flannel/bin/`; do
    if [[ ! -f /usr/local/bin/$i ]]; then
        sudo ln -s /usr/local/lib/k8s/flannel/bin/$i /usr/local/bin/$i
    fi
done

echo "   + Kubernetes server"
for i in `ls -1 /usr/local/lib/k8s/kubernetes/server/bin/`; do
    if [[ ! -f /usr/local/bin/$i ]]; then
        sudo ln -s /usr/local/lib/k8s/kubernetes/server/bin/$i /usr/local/bin/$i
    fi
done

echo " - Create / update eskimo K8S version file"
sudo bash -c "echo K8S_VERSION=`find /usr/local/lib/ -mindepth 1 -maxdepth 1 ! -type l | grep \"k8s-*.*\" | cut -d '-' -f 2` > /etc/eskimo_k8s_environment"

echo " - Checking eskimo K8S version file"
if [[ -z $TEST_MODE && ! -f /etc/eskimo_k8s_environment ]]; then
    echo "Could not create /etc/eskimo_k8s_environment"
    exit 21
fi
. /etc/eskimo_k8s_environment

if [[ -z $TEST_MODE && ! -d /usr/local/lib/k8s-$K8S_VERSION ]]; then
    echo "/etc/eskimo_k8s_environment doesn't point to valid mesos version"
    exit 21
fi

echo " - Creating network identifcation marker flags"

export ip_CIDR=`get_ip_CIDR`
sudo bash -c "echo $ip_CIDR" > /etc/eskimo_network_cidr

export host_min=`get_host_min`
sudo bash -c "echo $host_min" > /etc/eskimo_network_host_min

echo " - Installation tests"
echo "   + TODO"
# TODO

echo " - Cleaning build folder"
cd $saved_dir
sudo rm -Rf /tmp/k8s_setup > /tmp/k8s_install_log 2>&1
fail_if_error $? "/tmp/k8s_install_log" -10


returned_to_saved_dir




