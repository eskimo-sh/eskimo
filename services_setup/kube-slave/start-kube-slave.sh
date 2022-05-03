#!/usr/bin/env bash

#
# This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
# well to this individual file than to the Eskimo Project as a whole.
#
# Copyright 2019 - 2022 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

echo "   + Sourcing kubernetes environment"
. /etc/k8s/env.sh

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
  --cni-bin-dir=$ESKIMO_KUBELET_NETWORK_PLUGIN_DIR \
  --network-plugin=$ESKIMO_KUBELET_NETWORK_PLUGIN \
  --cni-conf-dir=$ESKIMO_KUBELET_CNI_CONF_DIR \
  --pod-infra-container-image $ESKIMO_KUBELET_POD_INFRA_CONTAINER_IMAGE \
  --kubeconfig=$ESKIMO_KUBELET_KUBECONFIG > /var/log/kubernetes/kubelet.log 2>&1' &
kubelet_pid=$!

#--port=$ESKIMO_KUBELET_PORT

sleep 4
if [[ `ps -e | grep $kubelet_pid ` == "" ]]; then
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
  --run-firewall=true \
  --run-service-proxy=true \
  --run-router=true \
  > /var/log/kubernetes/kuberouter.log 2>&1' &
kuberouter_pid=$!

#--port=$ESKIMO_KUBELET_PORT

sleep 4
if [[ `ps -e | grep $kuberouter_pid ` == "" ]]; then
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

    if [[ `ps -e | grep $kuberouter_pid ` == "" ]]; then
        echo "   + Failed to run Kubernetes Kube-Router"
        cat /var/log/kubernetes/kuberouter.log 2>&1
        exit 48
    fi

    # don't bother on master node, the master does it better
    if [[ "$MASTER_KUBE_MASTER_1" != "$SELF_IP_ADDRESS" ]]; then

        # ensure DNS is still working alright
        #echo "   + Trying to ping kubernetes.default.svc.$CLUSTER_DNS_DOMAIN" # this is filling up logs
        /bin/ping -c 1 -W 5 -w 10 kubernetes.default.svc.$CLUSTER_DNS_DOMAIN > /var/log/kubernetes/start_k8s_master.log 2>&1
        if [[ $? != 0 ]]; then

            sleep 5

            echo "   + Previous ping failed. Trying AGAIN to ping kubernetes.default.svc.$CLUSTER_DNS_DOMAIN"
            /bin/ping -c 1 -W 5 -w 10 kubernetes.default.svc.$CLUSTER_DNS_DOMAIN > /var/log/kubernetes/start_k8s_master.log 2>&1
            if [[ $? != 0 ]]; then

                echo "   + Failed to ping kubernetes.default.svc.$CLUSTER_DNS_DOMAIN. Checking pod status"

                FIXME Continue with a warning if pod is anything else than running !


                echo "   + Trying to restart Network Manager"

                if [[ -d /lib/systemd/system/ ]]; then
                    export systemd_units_dir=/lib/systemd/system/
                elif [[ -d /usr/lib/systemd/system/ ]]; then
                    export systemd_units_dir=/usr/lib/systemd/system/
                else
                    echo "Couldn't find systemd unit files directory"
                    exit 51
                fi

                if [[ -f $systemd_units_dir/NetworkManager.service ]]; then
                    /bin/systemctl restart NetworkManager
                else
                    /bin/systemctl restart dnsmasq
                fi
                if [[ $? != 0 ]]; then
                    echo "Failing to restart NetworkManager / dnsmasq"
                    exit 52
                fi

                sleep 2

                echo "   + Trying YET AGAIN to ping kubernetes.default.svc.$CLUSTER_DNS_DOMAIN"
                /bin/ping -c 1 -W 5 -w 10 kubernetes.default.svc.$CLUSTER_DNS_DOMAIN > /var/log/kubernetes/start_k8s_master.log 2>&1
                if [[ $? != 0 ]]; then

                    let ping_cnt=ping_cnt+1

                else
                    ping_cnt=0
                fi
            else
                ping_cnt=0
            fi
        else
            ping_cnt=0
        fi

    fi

done

