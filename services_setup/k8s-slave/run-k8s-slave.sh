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


echo " - Starting K8s Eskimo Slave"

function delete_k8s_slave_lock_file() {
   # Not in gluster share !!!
   rm -Rf /etc/k8s/k8s_slave_management_lock
}

# From here we will be messing with gluster and hence we need to take a lock
counter=0
while [[ -f /etc/k8s/k8s_slave_management_lock ]] ; do
    echo "   + /etc/k8s/ssl/k8s_slave_management_lock exist. waiting 2 secs ... "
    sleep 2
    let counter=counter+1
    if [[ $counter -ge 15 ]]; then
        echo " !!! Couldn't get /etc/k8s/k8s_slave_management_lock in 30 seconds. crashing !"
        exit 150
    fi
done

echo "   + Taking startup lock"
touch /etc/k8s/k8s_slave_management_lock

trap delete_k8s_slave_lock_file 15
trap delete_k8s_slave_lock_file EXIT

echo "   + Mounting Kubernetes Eskimo Gluster shares"
/usr/local/sbin/setupK8sGlusterShares.sh > /tmp/start_k8s_slave.log 2>&1
if [[ $? != 0 ]]; then
    echo "   + Failed to mount gluster shares"
    cat /tmp/start_k8s_slave.log 2>&1
    exit 41
fi

echo "   + Setup runtime kubectl"
/etc/k8s/runtime_config/setup-runtime-kubectl.sh  > /tmp/start_k8s_slave.log 2>&1
if [[ $? != 0 ]]; then
     echo "   + Failed to setup runtme kubectl"
     cat /tmp/start_k8s_slave.log 2>&1
     exit 42
fi

echo "   + Register kubernetes registry"
/usr/local/sbin/register-kubernetes-registry.sh  > /tmp/start_k8s_slave.log 2>&1
if [[ $? != 0 ]]; then
    echo "   + Failed to register kubernetes registry"
    cat /tmp/start_k8s_slave.log 2>&1
    exit 43
fi

echo "   + Setup runtime kubelet"
/bin/bash /etc/k8s/runtime_config/setup-runtime-kubelet.sh  > /tmp/start_k8s_slave.log 2>&1
if [[ $? != 0 ]]; then
    echo "   + Failed to setup runtime kubelet"
    cat /tmp/start_k8s_slave.log 2>&1
    exit 45
fi

echo "   + Starting Kubelet"
/bin/bash -c '. /etc/k8s/kubelet.env.sh && /usr/local/bin/kubelet \
  --address=$ESKIMO_KUBELET_ADDRESS \
  --hostname-override=$ESKIMO_KUBELET_HOSTNAME \
  --cluster-dns=$ESKIMO_CLUSTER_DNS \
  --cluster-domain=$ESKIMO_CLUSTER_DNS_DOMAIN \
  --logtostderr=$ESKIMO_KUBE_LOGTOSTDERR \
  --tls-cert-file=$ESKIMO_KUBE_TLS_CERT_FILE \
  --tls-private-key-file=$ESKIMO_KUBE_TLS_PRIVATE_KEY \
  --client-ca-file=$ESKIMO_KUBE_CLIENT_CA_FILE \
  --v=$ESKIMO_KUBE_LOG_LEVEL \
  --cgroup-driver $ESKIMO_KUBELET_CGROUP_DRIVER \
  --fail-swap-on=$ESKIMO_KUBELET_FAIL_SWAP_ON \
  --bootstrap-kubeconfig=$ESKIMO_BOOTSTRAP_KUBECONFIG \
  --kubeconfig=$ESKIMO_KUBELET_KUBECONFIG > /var/log/kubernetes/kubelet.log 2>&1' &
kubelet_pid=$!

#--port=$ESKIMO_KUBELET_PORT

sleep 4
if [[ `ps -e | grep $kubelet_pid ` == "" ]]; then
    echo "   + Failed to start Kubernetes Kubelet"
    cat /var/log/kubernetes/kubelet.log 2>&1
    exit 46
fi

echo "   + Setup runtime kubeproxy"
/bin/bash /etc/k8s/runtime_config/setup-runtime-kubeproxy.sh  > /tmp/start_k8s_slave.log 2>&1
if [[ $? != 0 ]]; then
    echo "   + Failed to setup runtime kubeproxy"
    cat /tmp/start_k8s_slave.log 2>&1
    exit 47
fi


echo "   + Starting Kubeproxy"
/bin/bash -c '. /etc/k8s/kubeproxy.env.sh && /usr/local/bin/kube-proxy \
  --bind-address=$ESKIMO_BIND_ADDRESS \
  --hostname-override=$ESKIMO_HOST_NAME_OVERRIDE \
  --cluster-cidr=$ESKIMO_CLUSTER_CIDR \
  --kubeconfig=/etc/k8s/kubeproxy.kubeconfig \
  --logtostderr=$ESKIMO_KUBE_LOGTOSTDERR \
  --v=$ESKIMO_KUBE_LOG_LEVEL > /var/log/kubernetes/kubeproxy.log 2>&1' &
kubeproxy_pid=$!

#--port=$ESKIMO_KUBELET_PORT

sleep 4
if [[ `ps -e | grep $kubeproxy_pid ` == "" ]]; then
    echo "   + Failed to start Kubernetes Proxy"
    cat /var/log/kubernetes/kubeproxy.log 2>&1
    exit 48
fi


# Keep this one last
echo "   + Setup runtime kubectl poststart"
/etc/k8s/runtime_config/setup-runtime-kubectl-poststart.sh > /tmp/start_k8s_slave.log 2>&1
if [[ $? != 0 ]]; then
    echo "   + Failed to setup runtime kubectrl poststart"
    cat /tmp/start_k8s_slave.log 2>&1
    exit 49
fi



echo "   + Deleting lock file"
delete_k8s_slave_lock_file

set -e

echo "   + Entering monitoring loop"
while : ; do

    sleep 10

    if [[ `ps -e | grep $kubelet_pid ` == "" ]]; then
        echo "   + Failed to run Kubernetes Kubelet"
        tail -n 50 /var/log/kubernetes/kubelet.log 2>&1
        exit 44
    fi

    if [[ `ps -e | grep $kubeproxy_pid ` == "" ]]; then
        echo "   + Failed to run Kubernetes Proxy"
        cat /var/log/kubernetes/kubeproxy.log 2>&1
        exit 48
    fi

done

