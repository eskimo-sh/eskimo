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

. /usr/local/sbin/eskimo-utils.sh


echo " - Starting K8s Eskimo Slave"

take_lock k8s_slave_management_lock /var/lib/kubernetes/
if [[ $? != 0 ]]; then
    echo " !!! Couldn't get /var/lib/kubernetes/k8s_slave_management_lock in 30 seconds. crashing !"
    exit 150
fi
export lock_handle=$LAST_LOCK_HANDLE


echo "   + Sourcing kubernetes environment"
. /etc/k8s/env.sh

export HOME=/root

echo "   + Mounting Kubernetes Eskimo Gluster shares"
/usr/local/sbin/setupK8sGlusterShares.sh > /var/log/kubernetes/start_k8s_slave.log 2>&1
if [[ $? != 0 ]]; then
    echo "   + Failed to mount gluster shares"
    cat /var/log/kubernetes/start_k8s_slave.log 2>&1
    exit 41
fi

echo "   + Setup runtime kubectl"
/etc/k8s/runtime_config/setup-runtime-kubectl.sh  >> /var/log/kubernetes/start_k8s_slave.log 2>&1
if [[ $? != 0 ]]; then
     echo "   + Failed to setup runtme kubectl"
     cat /var/log/kubernetes/start_k8s_slave.log 2>&1
     exit 42
fi

echo "   + Register kubernetes registry"
/usr/local/sbin/register-kubernetes-registry.sh  >> /var/log/kubernetes/start_k8s_slave.log 2>&1
if [[ $? != 0 ]]; then
    echo "   + Failed to register kubernetes registry"
    cat /var/log/kubernetes/start_k8s_slave.log 2>&1
    exit 43
fi

echo "   + Setup runtime kubelet"
/bin/bash /etc/k8s/runtime_config/setup-runtime-kubelet.sh  >> /var/log/kubernetes/start_k8s_slave.log 2>&1
if [[ $? != 0 ]]; then
    echo "   + Failed to setup runtime kubelet"
    cat /var/log/kubernetes/start_k8s_slave.log 2>&1
    exit 45
fi


echo "   + Starting cri-dockerd"
/bin/bash -c '. /etc/k8s/cri-dockerd.env.sh && /usr/local/bin/cri-dockerd \
  --cni-bin-dir $ESKIMO_CRID_NETWORK_PLUGIN_DIR \
  --cni-conf-dir $ESKIMO_CRID_CNI_CONF_DIR \
  --container-runtime-endpoint $ESKIMO_CONTAINER_RUNTIME_ENDPOINT \
  --network-plugin $ESKIMO_CRID_NETWORK_PLUGIN \
  --pod-infra-container-image $ESKIMO_POD_INFRA_CONTAINER_IMAGE \
  --runtime-request-timeout $ESKIMO_RUNTIME_REQUEST_TIMEOUT > /var/log/kubernetes/cri-dockerd.log 2>&1' &
cri_dockerd_pid=$!

sleep 4
if ! kill -0 $cri_dockerd_pid > /dev/null 2>&1; then
    echo "   + Failed to start cri-dockerd"
    cat /var/log/kubernetes/cri-dockerd.log 2>&1
    exit 61
fi


echo "   + Starting Kubelet"
/bin/bash -c '. /etc/k8s/kubelet.env.sh && /usr/local/bin/kubelet \
  --address=$ESKIMO_KUBELET_ADDRESS \
  --hostname-override=$ESKIMO_KUBELET_HOSTNAME \
  --cluster-dns=$ESKIMO_CLUSTER_DNS \
  --cluster-domain=$ESKIMO_CLUSTER_DNS_DOMAIN \
  --tls-cert-file=$ESKIMO_KUBE_TLS_CERT_FILE \
  --tls-private-key-file=$ESKIMO_KUBE_TLS_PRIVATE_KEY \
  --client-ca-file=$ESKIMO_KUBE_CLIENT_CA_FILE \
  --v=$ESKIMO_KUBE_LOG_LEVEL \
  --cgroup-driver $ESKIMO_KUBELET_CGROUP_DRIVER \
  --fail-swap-on=$ESKIMO_KUBELET_FAIL_SWAP_ON \
  --bootstrap-kubeconfig=$ESKIMO_BOOTSTRAP_KUBECONFIG \
  --pod-infra-container-image $ESKIMO_POD_INFRA_CONTAINER_IMAGE \
  --runtime-request-timeout $ESKIMO_RUNTIME_REQUEST_TIMEOUT \
  --container-runtime-endpoint $ESKIMO_CONTAINER_RUNTIME_ENDPOINT \
  --kubeconfig=$ESKIMO_KUBELET_KUBECONFIG > /var/log/kubernetes/kubelet.log 2>&1' &
kubelet_pid=$!

#--port=$ESKIMO_KUBELET_PORT

sleep 4
if ! kill -0 $kubelet_pid > /dev/null 2>&1; then
    echo "   + Failed to start Kubernetes Kubelet"
    cat /var/log/kubernetes/kubelet.log 2>&1
    exit 46
fi

echo "   + Setup runtime kuberouter"
/bin/bash /etc/k8s/runtime_config/setup-runtime-kuberouter.sh  >> /var/log/kubernetes/start_k8s_slave.log 2>&1
if [[ $? != 0 ]]; then
    echo "   + Failed to setup runtime kube-router"
    cat /var/log/kubernetes/start_k8s_slave.log 2>&1
    exit 47
fi


echo "   + Starting Kube-router"
/bin/bash -c '. /etc/k8s/kuberouter.env.sh && /usr/local/bin/kube-router \
  --master=$ESKIMO_KUBEROUTER_APISERVER \
  --hostname-override=$ESKIMO_KUBEROUTER_HOST_NAME_OVERRIDE \
  --kubeconfig=$ESKIMO_KUBEROUTER_KUBECONFIG \
  --run-firewall=$ESKIMO_KUBEROUTER_RUN_FIREWALL \
  --run-service-proxy=true \
  --run-router=true \
  > /var/log/kubernetes/kuberouter.log 2>&1' &
kuberouter_pid=$!

#--port=$ESKIMO_KUBELET_PORT

sleep 4
if ! kill -0 $kuberouter_pid > /dev/null 2>&1; then
    echo "   + Failed to start Kubernetes Router"
    cat /var/log/kubernetes/kuberouter.log 2>&1
    exit 48
fi


# Keep this one last
echo "   + Setup runtime kubectl poststart"
/etc/k8s/runtime_config/setup-runtime-kubectl-poststart.sh >> /var/log/kubernetes/start_k8s_slave.log 2>&1
if [[ $? != 0 ]]; then
    echo "   + Failed to setup runtime kubectrl poststart"
    cat /var/log/kubernetes/start_k8s_slave.log 2>&1
    exit 49
fi



echo "   + Releasing lock"
release_lock $lock_handle

echo "   + Entering monitoring loop"
while : ; do

    if ! kill -0 $cri_dockerd_pid > /dev/null 2>&1; then
        echo "   + Failed to run cri-dockerd"
        tail -n 50 /var/log/kubernetes/cri-dockerd.log 2>&1
        exit 62
    fi

    if ! kill -0 $kubelet_pid > /dev/null 2>&1; then
        echo "   + Failed to run Kubernetes Kubelet"
        tail -n 50 /var/log/kubernetes/kubelet.log 2>&1
        exit 44
    fi

    if ! kill -0 $kuberouter_pid > /dev/null 2>&1; then
        echo "   + Failed to run Kubernetes Kube-Router"
        cat /var/log/kubernetes/kuberouter.log 2>&1
        exit 48
    fi

    sleep 10

    /etc/k8s/runtime_config/setup-and-check-runtime-kube-gluster-shares.sh
    if [[ $? != 0 ]]; then
        exit 52
    fi


done

