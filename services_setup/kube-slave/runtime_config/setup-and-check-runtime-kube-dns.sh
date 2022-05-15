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

if [[ "$1" == "" ]]; then
    echo "Need an argument being either MASTER or SLAVE to drive behaviour"
    exit 1
fi

export MODE=$1

if [[ $MODE != "MASTER" && $MODE != "SLAVE" ]]; then
    echo "Need an argument being either MASTER or SLAVE to drive behaviour"
    exit 2
fi

# Sourcing kubernetes environment
. /etc/k8s/env.sh

export HOME=/root


if [[ ! -f /etc/k8s/flag-dns-setup ]]; then
    
    function delete_k8s_dns_setup_lock_file() {
         rm -Rf /etc/k8s/k8s_dns_setup_management_lock
    }
    
    # From here we will be messing with gluster and hence we need to take a lock
    counter=0
    while [[ -f /etc/k8s/k8s_dns_setup_management_lock ]] ; do
        echo "   + /etc/k8s/k8s_dns_setup_management_lock exist. waiting 2 secs ... "
        sleep 2
        let counter=counter+1
        if [[ $counter -ge 15 ]]; then
            echo " !!! Couldn't get /etc/k8s/k8s_dns_setup_management_lock in 30 seconds. crashing !"
            exit 150
        fi
    done
    
    trap delete_k8s_dns_setup_lock_file 15
    trap delete_k8s_dns_setup_lock_file EXIT
    
    touch /etc/k8s/k8s_dns_setup_management_lock
      
    echo " - Setting up dnsmasq to reach kube services"

    sudo mkdir -p /etc/dnsmasq.d
    if [[ ! -f /etc/dnsmasq.d/kube.conf ]]; then
        sudo bash -c "echo \"server=/cluster.eskimo/$CLUSTER_DNS_SVC_IP\" > /etc/dnsmasq.d/kube.conf"
        if [[ -d /etc/NetworkManager/dnsmasq.d/ ]]; then
            sudo ln -s /etc/dnsmasq.d/kube.conf /etc/NetworkManager/dnsmasq.d/kube.conf
        fi
    fi

    if [[ -f /etc/NetworkManager/NetworkManager.conf ]] ; then
    
        if [[ `grep -e "^dns=dnsmasq" /etc/NetworkManager/NetworkManager.conf` == "" ]]; then
            sudo sed -i -n '1h;1!H;${;g;s/'\
'\[main\]'\
'/'\
'\[main\]\n'\
'dns=dnsmasq\n'\
'/g;p;}' /etc/NetworkManager/NetworkManager.conf

            sudo systemctl restart NetworkManager
        fi
    fi

    if [[ -f /etc/dnsmasq.conf ]]; then

        if [[ `grep "Eksimo specifics" /etc/dnsmasq.conf` == "" ]]; then

            sudo bash -c "echo -e \"\n#Eksimo specifics\" >> /etc/dnsmasq.conf"

            if [[ `grep -e "^bind-interfaces" /etc/dnsmasq.conf` == "" ]]; then
                sudo bash -c "echo \"bind-interfaces\" >> /etc/dnsmasq.conf"
            fi

            if [[ `grep -e "^listen-address=127.0.0.1" /etc/dnsmasq.conf` == "" ]]; then
                sudo bash -c "echo \"listen-address=127.0.0.1\" >> /etc/dnsmasq.conf"
            fi

            if [[ `grep -e "^conf-dir.*/etc/dnsmasq.d.*" /etc/dnsmasq.conf` == "" ]]; then
                sudo bash -c "echo \"conf-dir=/etc/dnsmasq.d\" >> /etc/dnsmasq.conf"
            fi

            sudo systemctl restart dnsmasq
        fi
    fi

    if [[ -f /etc/sysconfig/network/config ]]; then

        if [[ `cat /etc/sysconfig/network/config | grep "NETCONFIG_DNS_FORWARDER=\"resolver\""` != "" ]]; then

            sudo bash -c 'sed -i s/"NETCONFIG_DNS_FORWARDER=\"resolver\""/"NETCONFIG_DNS_FORWARDER=\"dnsmasq\""/g /etc/sysconfig/network/config'

            sudo netconfig update -f
        fi
    fi

    touch /etc/k8s/flag-dns-setup
    rm -Rf /etc/k8s/k8s_dns_setup_management_lock
fi


if [[ -f /etc/k8s/dns-ping-cnt ]]; then
    export ping_cnt=`cat /etc/k8s/dns-ping-cnt`
else
    export ping_cnt=0
fi


# Do it on master and on slave where master is not running only
if [[ $MODE == "MASTER" || ( $MODE == "SLAVE" && "$MASTER_KUBE_MASTER_1" != "$SELF_IP_ADDRESS" ) ]]; then

    # ensure DNS is still working alright
    #echo "   + Trying to ping kubernetes.default.svc.$CLUSTER_DNS_DOMAIN" # this is filling up logs
    /bin/ping -c 1 -W 5 -w 10 kubernetes.default.svc.$CLUSTER_DNS_DOMAIN > /var/log/kubernetes/start_k8s_master.log 2>&1
    if [[ $? != 0 ]]; then

        sleep 5

        echo "   + Previous ping failed. Trying AGAIN to ping kubernetes.default.svc.$CLUSTER_DNS_DOMAIN"
        /bin/ping -c 1 -W 5 -w 10 kubernetes.default.svc.$CLUSTER_DNS_DOMAIN > /var/log/kubernetes/start_k8s_master.log 2>&1
        if [[ $? != 0 ]]; then

            echo "   + Failed to ping kubernetes.default.svc.$CLUSTER_DNS_DOMAIN. Checking pod status"

            coredns_status=`kubectl get pod -n kube-system -o custom-columns=NAME:metadata.name,STATUS:status.phase | grep coredns | sed 's/  */ /g' | cut -d ' ' -f 2`
            if [[ "$coredns_status" != "" && "$coredns_status" != "Running" ]]; then
                echo "! Coredns pod status is $coredns_status. Not attempting any resolution."
                echo "0" > /etc/k8s/dns-ping-cnt
                exit
            fi

            echo "   + Trying to restart Network Manager"

            if [[ -d /lib/systemd/system/ ]]; then
                export systemd_units_dir=/lib/systemd/system/
            elif [[ -d /usr/lib/systemd/system/ ]]; then
                export systemd_units_dir=/usr/lib/systemd/system/
            else
                echo "Couldn't find systemd unit files directory"
                echo "0" > /etc/k8s/dns-ping-cnt
                exit 51
            fi

            if [[ -f $systemd_units_dir/NetworkManager.service ]]; then
                /bin/systemctl restart NetworkManager
            else
                /bin/systemctl restart dnsmasq
            fi
            if [[ $? != 0 ]]; then
                echo "Failing to restart NetworkManager / dnsmasq"
                echo "0" > /etc/k8s/dns-ping-cnt
                exit 52
            fi

            sleep 2

            echo "   + Trying YET AGAIN to ping kubernetes.default.svc.$CLUSTER_DNS_DOMAIN"
            /bin/ping -c 1 -W 5 -w 10 kubernetes.default.svc.$CLUSTER_DNS_DOMAIN > /var/log/kubernetes/start_k8s_master.log 2>&1
            if [[ $? != 0 ]]; then

                if [[ $MODE == "MASTER" ]]; then

                    echo "   + Failed to ping kubernetes.default.svc.$CLUSTER_DNS_DOMAIN. Trying to restart coredns"

                    echo "     - Deleting coredns deployment"
                    /bin/bash --login -c '/usr/local/bin/kubectl \
                         delete -f /var/lib/kubernetes/core-dns.yaml \
                        --kubeconfig=/root/.kube/config \
                        > /var/log/kubernetes/start_k8s_master.log 2>&1'
                    if [[ $? != 0 ]]; then
                        echo "       + Failed to undeploy coredns"
                        echo "0" > /etc/k8s/dns-ping-cnt
                        exit 51
                    fi

                    echo "     - redeploy coredns deployment"
                    /bin/bash --login -c '/usr/local/bin/kubectl \
                         apply -f /var/lib/kubernetes/core-dns.yaml \
                        --kubeconfig=/root/.kube/config \
                        > /var/log/kubernetes/start_k8s_master.log 2>&1'
                    if [[ $? != 0 ]]; then
                        echo "       + Failed to re-deploy coredns"
                        echo "0" > /etc/k8s/dns-ping-cnt
                        exit 52
                    fi

                fi

                let ping_cnt=ping_cnt+1

                echo "     - checking redeploy coredns looping"
                if [[ $ping_cnt -gt 5 ]]; then
                    echo "       + Redeployed coredns 5 times in a row. Crashing !"
                    echo "0" > /etc/k8s/dns-ping-cnt
                    exit 53
                fi
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

echo $ping_cnt > /etc/k8s/dns-ping-cnt