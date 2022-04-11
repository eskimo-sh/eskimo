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

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $SCRIPT_DIR/common.sh "$@"


echo "-- PACKAGING K8S ------------------------------------------------------"

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
mkdir -p /tmp/k8s_setup
cd /tmp/k8s_setup


# Reference
# https://github.com/shawnsong/kubernetes-handbook

echo " - Downloading cfssl_"$CFSSL_VERSION"_linux_amd64"
wget https://github.com/cloudflare/cfssl/releases/download/v$CFSSL_VERSION/cfssl_"$CFSSL_VERSION"_linux_amd64  >> /tmp/k8s_install_log 2>&1
if [[ $? != 0 ]]; then
    echo " -> Failed to download cfssl_$CFSSL_VERSION_linux_amd64 from https://github.com/. Trying to download from niceideas.ch"
    wget http://niceideas.ch/mes/cfssl_$CFSSL_VERSION_linux_amd64 >> /tmp/k8s_install_log 2>&1
    fail_if_error $? "/tmp/k8s_install_log" -1
fi

echo " - Installing cfssl_"$CFSL_VERSION_linux_amd64

set -e # failing on errors
mkdir -p /usr/local/lib/k8s/cfssl/bin
sudo mv cfssl_"$CFSSL_VERSION"_linux_amd64 /usr/local/lib/k8s/cfssl/bin
chmod +x /usr/local/lib/k8s/cfssl/bin/cfssl_"$CFSSL_VERSION"_linux_amd64
ln -s cfssl_"$CFSSL_VERSION"_linux_amd64 /usr/local/lib/k8s/cfssl/bin/cfssl
set +e


echo " - Downloading cfssljson_"$CFSSL_VERSION"_linux_amd64"
wget https://github.com/cloudflare/cfssl/releases/download/v$CFSSL_VERSION/cfssljson_"$CFSSL_VERSION"_linux_amd64  >> /tmp/k8s_install_log 2>&1
if [[ $? != 0 ]]; then
    echo " -> Failed to download cfssljson_$CFSSL_VERSION_linux_amd64 from https://github.com/. Trying to download from niceideas.ch"
    wget http://niceideas.ch/mes/cfssljson_$CFSSL_VERSION_linux_amd64 >> /tmp/k8s_install_log 2>&1
    fail_if_error $? "/tmp/k8s_install_log" -1
fi

echo " - Installing cfssljson_"$CFSL_VERSION_linux_amd64

set -e # failing on errors
mkdir -p /usr/local/lib/k8s/cfssl/bin
sudo mv cfssljson_"$CFSSL_VERSION"_linux_amd64 /usr/local/lib/k8s/cfssl/bin
chmod +x /usr/local/lib/k8s/cfssl/bin/cfssljson_"$CFSSL_VERSION"_linux_amd64
ln -s cfssljson_"$CFSSL_VERSION"_linux_amd64 /usr/local/lib/k8s/cfssl/bin/cfssljson
set +e

echo " - Downloading etcd-v${ETCD_VERSION}-linux-amd64"
wget https://github.com/etcd-io/etcd/releases/download/v${ETCD_VERSION}/etcd-v${ETCD_VERSION}-linux-amd64.tar.gz  >> /tmp/k8s_install_log 2>&1
if [[ $? != 0 ]]; then
    echo " -> Failed to download etcd-v${ETCD_VERSION}-linux-amd64 from https://github.com/. Trying to download from niceideas.ch"
    wget http://niceideas.ch/mes/etcd-v${ETCD_VERSION}-linux-amd64.tar.gz  >> /tmp/k8s_install_log 2>&1
    fail_if_error $? "/tmp/k8s_install_log" -1
fi

echo " - Installing etcd-v${ETCD_VERSION}-linux-amd64."

set -e # failing on errors
tar xzvf etcd-v${ETCD_VERSION}-linux-amd64.tar.gz >> /tmp/k8s_install_log 2>&1
mkdir /usr/local/lib/k8s/etcd-v${ETCD_VERSION}
ln -s etcd-v${ETCD_VERSION} /usr/local/lib/k8s/etcd
mkdir /usr/local/lib/k8s/etcd/bin
mv etcd-v${ETCD_VERSION}-linux-amd64/* /usr/local/lib/k8s/etcd/bin/
set +e


echo " - Downloading kubernetes-client-linux-amd64 v$K8S_VERSION"
wget https://dl.k8s.io/v$K8S_VERSION/kubernetes-client-linux-amd64.tar.gz >> /tmp/k8s_install_log 2>&1
if [[ $? != 0 ]]; then
    echo " -> Failed to download kubernetes-client-linux-amd64 v$K8S_VERSION from https://github.com/. Trying to download from niceideas.ch"
    wget http://niceideas.ch/mes/kubernetes-client-linux-amd64.tar.gz  >> /tmp/k8s_install_log 2>&1
    fail_if_error $? "/tmp/k8s_install_log" -1
fi

echo " - Installing kubernetes-client-linux-amd64 v$K8S_VERSION"

set -e
tar xvfz kubernetes-client-linux-amd64.tar.gz >> /tmp/k8s_install_log 2>&1
mkdir /usr/local/lib/k8s/kubernetes-v$K8S_VERSION
ln -s kubernetes-v$K8S_VERSION /usr/local/lib/k8s/kubernetes
mkdir -p /usr/local/lib/k8s/kubernetes/client/bin
mv kubernetes/client/bin/* /usr/local/lib/k8s/kubernetes/client/bin/
set +e

# Deprecated
#echo " - Downloading flannel v$FLANNEL_VERSION"
#wget https://github.com/flannel-io/flannel/releases/download/v$FLANNEL_VERSION/flannel-v"$FLANNEL_VERSION"-linux-amd64.tar.gz >> /tmp/k8s_install_log 2>&1
#if [[ $? != 0 ]]; then
#    echo " -> Failed to download flannel v$FLANNEL_VERSION from https://github.com/. Trying to download from niceideas.ch"
#    wget http://niceideas.ch/mes/flannel-v"$FLANNEL_VERSION"-linux-amd64.tar.gz  >> /tmp/k8s_install_log 2>&1
#    fail_if_error $? "/tmp/k8s_install_log" -1
#fi

#
#echo " - Installing flannel v$FLANNEL_VERSION"
#
#set -e
#tar xvfz flannel-v0.17.0-linux-amd64.tar.gz  >> /tmp/k8s_install_log 2>&1
#mkdir /usr/local/lib/k8s/flannel-v$FLANNEL_VERSION
#ln -s flannel-v$FLANNEL_VERSION /usr/local/lib/k8s/flannel
#mkdir -p /usr/local/lib/k8s/flannel/bin
#mv flanneld mk-docker-opts.sh README.md /usr/local/lib/k8s/flannel/bin/
#set +e
#
#
#echo " - HACK - back-porting flannel 0.14 since all following versions have incompatibility with latest glibc"
#wget https://github.com/cucker0/docker/raw/main/resources/flannel/flannel_v0.14.0/flanneld >> /tmp/k8s_install_log 2>&1
#fail_if_error $? "/tmp/k8s_install_log" -1
#
#set -e
#mv flanneld /usr/local/lib/k8s/flannel/bin/
#chmod 755  /usr/local/lib/k8s/flannel/bin/flanneld
#set +e


#https://objects.githubusercontent.com/github-production-release-asset-2e65be/23059575/05b0a200-5f01-11eb-9d61-d2441ade97e7?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=AKIAIWNJYAX4CSVEH53A%2F20220411%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Date=20220411T065139Z&X-Amz-Expires=300&X-Amz-Signature=15b9308fc0f80df0f194dac495228653edfe004920a309fcfc2988e79acaead4&X-Amz-SignedHeaders=host&actor_id=0&key_id=0&repo_id=23059575&response-content-disposition=attachment%3B%20filename%3Dweave&response-content-type=application%2Foctet-stream



echo " - Downloading kube-router v$K8S_ROUTER_VERSION"
wget https://github.com/cloudnativelabs/kube-router/releases/download/v$K8S_ROUTER_VERSION/kube-router_"$K8S_ROUTER_VERSION"_linux_amd64.tar.gz >> /tmp/k8s_install_log 2>&1
if [[ $? != 0 ]]; then
    echo " -> Failed to download kube-router v$K8S_ROUTER_VERSION from https://github.com/. Trying to download from niceideas.ch"
    wget http://niceideas.ch/mes/kube-router_"$K8S_ROUTER_VERSION"_linux_amd64.tar.gz  >> /tmp/k8s_install_log 2>&1
    fail_if_error $? "/tmp/k8s_install_log" -1
fi

echo " - Installing kube-router v$K8S_ROUTER_VERSION"

set -e
tar xvfz kube-router_"$K8S_ROUTER_VERSION"_linux_amd64.tar.gz  >> /tmp/k8s_install_log 2>&1
mkdir /usr/local/lib/k8s/kube-router-v$K8S_ROUTER_VERSION
ln -s kube-router-v$K8S_ROUTER_VERSION /usr/local/lib/k8s/kube-router
mkdir -p /usr/local/lib/k8s/kube-router/bin
mv kube-router README.md /usr/local/lib/k8s/kube-router/bin
set +e



echo " - Downloading cni-plugins v$K8S_CNI_PLUGINS_VERSION"
wget https://github.com/containernetworking/plugins/releases/download/v$K8S_CNI_PLUGINS_VERSION/cni-plugins-linux-amd64-v$K8S_CNI_PLUGINS_VERSION.tgz >> /tmp/k8s_install_log 2>&1
if [[ $? != 0 ]]; then
    echo " -> Failed to download kube-router v$K8S_ROUTER_VERSION from https://github.com/. Trying to download from niceideas.ch"
    wget http://niceideas.ch/mes/kube-router_"$K8S_ROUTER_VERSION"_linux_amd64.tar.gz  >> /tmp/k8s_install_log 2>&1
    fail_if_error $? "/tmp/k8s_install_log" -1
fi

echo " - Installing cni-plugins v$K8S_CNI_PLUGINS_VERSION"
set -e
mkdir cni-plugins
mv cni-plugins-linux-amd64-v$K8S_CNI_PLUGINS_VERSION.tgz cni-plugins/
cd cni-plugins
tar xvfz cni-plugins-linux-amd64-v$K8S_CNI_PLUGINS_VERSION.tgz >> /tmp/k8s_install_log 2>&1
mkdir /usr/local/lib/k8s/cni-plugins-v$K8S_CNI_PLUGINS_VERSION
ln -s cni-plugins-v$K8S_CNI_PLUGINS_VERSION /usr/local/lib/k8s/cni-plugins
rm -f cni-plugins-linux-amd64-v$K8S_CNI_PLUGINS_VERSION.tgz
mv * /usr/local/lib/k8s/cni-plugins
cd ..
set +e



echo " - Downloading kubernetes-server-linux-amd64 v$K8S_VERSION"

wget https://dl.k8s.io/v$K8S_VERSION/kubernetes-server-linux-amd64.tar.gz >> /tmp/k8s_install_log 2>&1
if [[ $? != 0 ]]; then
    echo " -> Failed to download kubernetes-server-linux-amd64 v$K8S_VERSION from https://github.com/. Trying to download from niceideas.ch"
    wget http://niceideas.ch/mes/kubernetes-server-linux-amd64.tar.gz  >> /tmp/k8s_install_log 2>&1
    fail_if_error $? "/tmp/k8s_install_log" -1
fi

echo " - Installing kubernetes-server-linux-amd64 v$K8S_VERSION"

set -e
tar xvfz kubernetes-server-linux-amd64.tar.gz >> /tmp/k8s_install_log 2>&1
mkdir -p /usr/local/lib/k8s/kubernetes/server/bin
mv kubernetes/server/bin/* /usr/local/lib/k8s/kubernetes/server/bin/
set +e




echo " - Cleaning build folder"
cd $saved_dir
sudo rm -Rf /tmp/k8s_setup >> /tmp/k8s_install_log 2>&1
fail_if_error $? "/tmp/k8s_install_log" -10

echo " - Creating tar.gz k8s archive"
cd /usr/local/lib/
tar cvfz k8s-$K8S_VERSION.tar.gz k8s >> /tmp/k8s_install_log 2>&1
fail_if_error $? "/tmp/k8s_install_log" -9

returned_to_saved_dir



