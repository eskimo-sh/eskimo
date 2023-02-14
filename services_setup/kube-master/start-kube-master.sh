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

echo " - Starting K8s Eskimo Master"

take_lock k8s_master_management_lock /var/lib/kubernetes/
if [[ $? != 0 ]]; then
    echo " !!! Couldn't get /var/lib/kubernetes/k8s_master_management_lock in 30 seconds. crashing !"
    exit 150
fi
export lock_handle=$LAST_LOCK_HANDLE


export HOME=/root

echo "   + Mounting Eskimo Gluster share for docker registry"
/usr/local/sbin/setupK8sGlusterShares.sh > /var/log/kubernetes/start_k8s_master.log 2>&1
if [[ $? != 0 ]]; then
    echo "   + Failed to mount gluster shares"
    cat /var/log/kubernetes/start_k8s_master.log 2>&1
    exit 41
fi

echo "   + Sourcing kubernetes environment"
. /etc/k8s/env.sh

echo "   + Setup runtime kubectl"
/etc/k8s/runtime_config/setup-runtime-kubectl.sh  >> /var/log/kubernetes/start_k8s_master.log 2>&1
if [[ $? != 0 ]]; then
     echo "   + Failed to setup runtme kubectl"
     cat /var/log/kubernetes/start_k8s_master.log 2>&1
     exit 42
fi

echo "   + Register kubernetes registry"
/usr/local/sbin/register-kubernetes-registry.sh  >> /var/log/kubernetes/start_k8s_master.log 2>&1
if [[ $? != 0 ]]; then
    echo "   + Failed to register kubernetes registry"
    cat /var/log/kubernetes/start_k8s_master.log 2>&1
    exit 43
fi


echo "   + Starting kube-api server"
/bin/bash -c '. /etc/k8s/kubeapi.env.sh && /usr/local/bin/kube-apiserver \
  --admission-control=$ESKIMO_KUBE_ADMISSION_CONTROL \
  --advertise-address=$ESKIMO_KUBE_API_ADVERTISE_ADDRESS \
  --bind-address=$ESKIMO_KUBE_API_BIND_ADDRESS \
  --authorization-mode=$ESKIMO_KUBE_AUTHORIZATION_MODE \
  --runtime-config=$ESKIMO_KUBE_RUNTIME_CONFIG \
  --enable-bootstrap-token-auth \
  --token-auth-file=$ESKIMO_KUBE_TOKEN_AUTH_FILE \
  --service-cluster-ip-range=$ESKIMO_KUBE_SERVICE_ADDRESSES \
  --service-node-port-range=$ESKIMO_KUBE_SERVICE_NODE_PORT_RANGE \
  --service-account-key-file=$ESKIMO_KUBE_SERVICE_ACCOUNT_KEYFILE \
  --service-account-signing-key-file=$ESKIMO_KUBE_SERVICE_ACCOUNT_SIGNING_KEYFILE \
  --service-account-issuer=$ESKIMO_KUBE_SERVUCE_ACCOUNT_ISSUER \
  --tls-cert-file=$ESKIMO_KUBE_TLS_CERT_FILE \
  --tls-private-key-file=$ESKIMO_KUBE_TLS_PRIVATE_KEY \
  --client-ca-file=$ESKIMO_KUBE_CLIENT_CA_FILE \
  --etcd-servers=$ESKIMO_KUBE_ETCD_SERVER \
  --audit-log-maxage=$ESKIMO_KUBE_AUDIT_LOG_MAXAGE \
  --audit-log-maxbackup=$ESKIMO_KUBE_AUDIT_LOG_MAXBACKUP \
  --audit-log-maxsize=$ESKIMO_KUBE_AUDIT_LOG_MAXSIZE \
  --audit-log-path=$ESKIMO_KUBE_AUDIT_LOG_PATH \
  --audit-policy-file=$ESKIMO_KUBE_AUDIT_POLICY_FILE \
  --v=$ESKIMO_KUBE_LOG_LEVEL \
  --allow-privileged=$ESKIMO_ALLOW_PRIVILEGED \
  --event-ttl=$ESKIMO_KUBE_EVENT_TTL > /var/log/kubernetes/kubeapi.log 2>&1' &
kubeapi_pid=$!
# not using ssl for etcs for now
#--etcd-cafile=$ESKIMO_KUBE_ETCD_CAFILE \
#--etcd-certfile=$ESKIMO_KUBE_ETCD_CERTFILE \
#--etcd-keyfile=$ESKIMO_KUBE_ETCD_KEYFILE \

# this one is unsupprted as it seems
#--kubelet-https=$ESKIMO_KUBE_KUBELET_HTTPS \

sleep 4
if ! kill -0 $kubeapi_pid > /dev/null 2>&1; then
    echo "   + Failed to start Kubernetes API server"
    cat /var/log/kubernetes/kubeapi.log 2>&1
    exit 44
fi


echo "   + Setup runtime kubectrl"
/bin/bash /etc/k8s/runtime_config/setup-runtime-kubectrl.sh  >> /var/log/kubernetes/start_k8s_master.log 2>&1
if [[ $? != 0 ]]; then
    echo "   + Failed to setup runtime kubectrl"
    cat /var/log/kubernetes/start_k8s_master.log 2>&1
    exit 45
fi

echo "   + Starting kube Controler Manager"
/bin/bash -c '. /etc/k8s/kubectrl.env.sh && /usr/local/bin/kube-controller-manager \
  --master=$ESKIMO_KUBE_CTRL_API_SERVER_MASTER \
  --bind-address=$ESKIMO_KUBE_CTRL_BIND_ADDRESS \
  --allocate-node-cidrs=$ESKIMO_KUBE_CTRL_ALLOCATE_NODE_CIDRS \
  --service-cluster-ip-range=$ESKIMO_KUBE_CTRL_SERVICE_CLUSTER_IPD \
  --cluster-cidr=$ESKIMO_KUBE_CTRL_CLUSTER_CIDR \
  --cluster-signing-cert-file=$ESKIMO_KUBE_CTRL_CLUSTER_SIGNING_CERT_FILE \
  --cluster-signing-key-file=$ESKIMO_KUBE_CTRL_CLUSTER_SIGNING_KEY_FILE \
  --service-account-private-key-file=$ESKIMO_KUBE_CTRL_SERVICE_ACCOUNT_PRIVATE_KEY_FILE \
  --root-ca-file=$ESKIMO_KUBE_CTRL_ROOT_CA_FILE \
  --cluster-name=$ESKIMO_KUBE_CTRL_CLUSTER_NAME \
  --leader-elect=$ESKIMO_KUBE_CTRL_LEADER_ELECT \
  --kubeconfig=/etc/k8s/kubectrl.kubeconfig \
  --v=$ESKIMO_KUBE_LOG_LEVEL > /var/log/kubernetes/kubectrl.log 2>&1' &
kubectrl_pid=$!

sleep 4
if ! kill -0 $kubectrl_pid > /dev/null 2>&1; then
    echo "   + Failed to start Kubernetes Controller-Manager"
    cat /var/log/kubernetes/kubectrl.log 2>&1
    exit 46
fi


echo "   + Setup runtime kubesched"
/etc/k8s/runtime_config/setup-runtime-kubesched.sh >> /var/log/kubernetes/start_k8s_master.log 2>&1
if [[ $? != 0 ]]; then
    echo "   + Failed to setup runtime kubesched"
    cat /var/log/kubernetes/start_k8s_master.log 2>&1
    exit 47
fi

echo "   + Starting Kube Scheduler"
/bin/bash -c '. /etc/k8s/kubesched.env.sh && /usr/local/bin/kube-scheduler \
   --bind-address=$ESKIMO_KUBE_SCHED_BIND_ADDRESS \
   --master $ESKIMO_KUBE_SCHED_API_SERVER_MASTER \
   --leader-elect=$ESKIMO_KUBE_SCHED_LEADER_ELECT \
   --kubeconfig=/etc/k8s/kubesched.kubeconfig \
   --v=$ESKIMO_KUBE_LOG_LEVEL > /var/log/kubernetes/kubesched.log 2>&1' &
kubesched_pid=$!

sleep 4
if ! kill -0 $kubesched_pid > /dev/null 2>&1; then
    echo "   + Failed to start Kubernetes Scheduler"
    cat /var/log/kubernetes/kubesched.log 2>&1
    exit 48
fi


# Keep this one last
echo "   + Setup runtime kubectl poststart (MASTER)"
/etc/k8s/runtime_config/setup-runtime-kubectl-poststart-master.sh >> /var/log/kubernetes/start_k8s_master.log 2>&1
if [[ $? != 0 ]]; then
    echo "   + Failed to setup runtime kubectrl poststart"
    cat /var/log/kubernetes/start_k8s_master.log 2>&1
    exit 49
fi

echo "   + Starting Kube Proxy (through kubectl)"
/bin/bash --login -c '/usr/local/bin/kubectl proxy \
    --address=0.0.0.0 \
    --accept-hosts=.* \
    --kubeconfig=/root/.kube/config \
    > /var/log/kubernetes/kubectlproxy.log 2>&1' &
kubectlproxy_pid=$!

sleep 4
if ! kill -0 $kubectlproxy_pid > /dev/null 2>&1; then
    echo "   + Failed to start Kube Proxy (through Kubectl)"
    cat /var/log/kubernetes/kubectlproxy.log 2>&1
    exit 50
fi


echo "   + Deploying Kubernetes services"
/etc/k8s/runtime_config/setup-runtime-kube-services.sh >> /var/log/kubernetes/start_k8s_master.log 2>&1
if [[ $? != 0 ]]; then
    echo "   + Failed to deploy runtime kube services"
    cat /var/log/kubernetes/start_k8s_master.log 2>&1
    exit 49
fi


echo "   + Releasing lock"
release_lock $lock_handle


echo "   + Entering monitoring / remediation loop"
while : ; do

    if ! kill -0 $kubeapi_pid > /dev/null 2>&1; then
        echo "   + Failed to run Kubernetes API server"
        tail -n 50 /var/log/kubernetes/kubeapi.log 2>&1
        exit 44
    fi

    if ! kill -0 $kubectrl_pid > /dev/null 2>&1; then
        echo "   + Failed to run Kubernetes Controller-Manager"
        tail -n 50 /var/log/kubernetes/kubectrl.log 2>&1
        exit 46
    fi

    if ! kill -0 $kubesched_pid > /dev/null 2>&1; then
        echo "   + Failed to run Kubernetes Scheduler"
        tail -n 50 /var/log/kubernetes/kubesched.log 2>&1
        exit 49
    fi

    if ! kill -0 $kubectlproxy_pid > /dev/null 2>&1; then
        echo "   + Failed to run Kube Proxy (through Kubectl)"
        cat /var/log/kubernetes/kubectlproxy.log 2>&1
        exit 50
    fi

    sleep 10

    /etc/k8s/runtime_config/setup-and-check-runtime-kube-gluster-shares.sh
    if [[ $? != 0 ]]; then
        exit 52
    fi

done





