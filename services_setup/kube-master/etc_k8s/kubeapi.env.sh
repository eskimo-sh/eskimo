#!/bin/bash

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


if [[ ! -f /etc/k8s/env.sh ]]; then
    echo "Could not find /etc/k8s/env.sh"
    exit 1
fi

. /etc/k8s/env.sh



###
# kubernetes system config
#
# The following values are used to configure the kube-apiserver
###

# The address on the local server to listen to.
# ESKIMO_KUBE_API_INSECURE_ADDRESS="--insecure-bind-address=127.0.0.1"
# NOT SUPPORTED IN MY VERSION

# The IP address on which to advertise the apiserver to members of the cluster. This address must be reachable
# by the rest of the cluster. If blank, the --bind-address will be used.
# If --bind-address is unspecified, the host's default interface will be used.
export ESKIMO_KUBE_API_ADVERTISE_ADDRESS="$SELF_IP_ADDRESS"
echo "   + Using ESKIMO_KUBE_API_ADVERTISE_ADDRESS=$ESKIMO_KUBE_API_ADVERTISE_ADDRESS"

# The IP address on which to listen for the --secure-port port. The associated interface(s) must be reachable
# by the rest of the cluster, and by CLI/web clients.
# If blank, all interfaces will be used (0.0.0.0 for all IPv4 interfaces and :: for all IPv6 interfaces).
export ESKIMO_KUBE_API_BIND_ADDRESS="0.0.0.0"
echo "   + Using ESKIMO_KUBE_API_BIND_ADDRESS=$ESKIMO_KUBE_API_BIND_ADDRESS"

# The certificates used by API server
export ESKIMO_KUBE_TLS_CERT_FILE=/etc/k8s/shared/ssl/kubernetes.pem
echo "   + Using ESKIMO_KUBE_TLS_CERT_FILE=$ESKIMO_KUBE_TLS_CERT_FILE"

export ESKIMO_KUBE_TLS_PRIVATE_KEY=/etc/k8s/shared/ssl/kubernetes-key.pem
echo "   + Using ESKIMO_KUBE_TLS_PRIVATE_KEY=$ESKIMO_KUBE_TLS_PRIVATE_KEY"

export ESKIMO_KUBE_CLIENT_CA_FILE=/etc/k8s/shared/ssl/ca.pem
echo "   + Using ESKIMO_KUBE_CLIENT_CA_FILE=$ESKIMO_KUBE_CLIENT_CA_FILE"

# The Kubelet authorization configs
export ESKIMO_KUBE_AUTHORIZATION_MODE="Node,RBAC"
echo "   + Using ESKIMO_KUBE_AUTHORIZATION_MODE=$ESKIMO_KUBE_AUTHORIZATION_MODE"

#export ESKIMO_KUBE_RUNTIME_CONFIG="rbac.authorization.k8s.io/v1alpha1" # not working
#export ESKIMO_KUBE_RUNTIME_CONFIG="api/all=true,rbac.authorization.k8s.io/v1alpha1" # not working
#export ESKIMO_KUBE_RUNTIME_CONFIG="v1=true,extensions/v1beta1=true,batch/v1=true,rbac.authorization.k8s.io/v1alpha1=true" # not working
export ESKIMO_KUBE_RUNTIME_CONFIG="extensions/v1beta1=true,batch/v1=true,rbac.authorization.k8s.io/v1alpha1=true"
echo "   + Using ESKIMO_KUBE_RUNTIME_CONFIG=$ESKIMO_KUBE_RUNTIME_CONFIG"

export ESKIMO_KUBE_TOKEN_AUTH_FILE="/etc/k8s/token.csv"
echo "   + Using ESKIMO_KUBE_TOKEN_AUTH_FILE=$ESKIMO_KUBE_TOKEN_AUTH_FILE"

export ESKIMO_KUBE_ETCD_SERVER="http://$SELF_IP_ADDRESS:$EKIMO_ETCD_PORT"
echo "   + Using ESKIMO_KUBE_ETCD_SERVER=$ESKIMO_KUBE_ETCD_SERVER"

# The etcd SSL certificates
# TODO not using etcs ssl for now
#export ESKIMO_KUBE_ETCD_CAFILE=/etc/k8s/shared/ssl/ca.pem
#echo "   + Using ESKIMO_KUBE_ETCD_CAFILE=$ESKIMO_KUBE_ETCD_CAFILE"
#
#export ESKIMO_KUBE_ETCD_CERTFILE=/etc/k8s/shared/ssl/kubernetes.pem
#echo "   + Using ESKIMO_KUBE_ETCD_CERTFILE=$ESKIMO_KUBE_ETCD_CERTFILE"
#
#export ESKIMO_KUBE_ETCD_KEYFILE=/etc/k8s/shared/ssl/kubernetes-key.pem
#echo "   + Using ESKIMO_KUBE_ETCD_KEYFILE=$ESKIMO_KUBE_ETCD_KEYFILE"

# Address range to use for services
export ESKIMO_KUBE_SERVICE_ADDRESSES="$SERVICE_CIDR"
echo "   + Using ESKIMO_KUBE_SERVICE_ADDRESSES=$ESKIMO_KUBE_SERVICE_ADDRESSES"

export ESKIMO_KUBE_SERVICE_NODE_PORT_RANGE="30000-32766"
echo "   + Using ESKIMO_KUBE_SERVICE_NODE_PORT_RANGE=$ESKIMO_KUBE_SERVICE_NODE_PORT_RANGE"

# The default admission control policies
export ESKIMO_KUBE_ADMISSION_CONTROL="NamespaceExists,NamespaceLifecycle,LimitRanger,ServiceAccount,ResourceQuota,DefaultStorageClass,MutatingAdmissionWebhook"
echo "   + Using ESKIMO_KUBE_ADMISSION_CONTROL=$ESKIMO_KUBE_ADMISSION_CONTROL"

# ServiceAccount configs
export ESKIMO_KUBE_SERVICE_ACCOUNT_KEYFILE="/etc/k8s/shared/ssl/kubernetes.pem"
echo "   + Using ESKIMO_KUBE_SERVICE_ACCOUNT_KEYFILE=$ESKIMO_KUBE_SERVICE_ACCOUNT_KEYFILE"

export ESKIMO_KUBE_SERVICE_ACCOUNT_SIGNING_KEYFILE="/etc/k8s/shared/ssl/kubernetes-key.pem"
echo "   + Using ESKIMO_KUBE_SERVICE_ACCOUNT_SIGNING_KEYFILE=$ESKIMO_KUBE_SERVICE_ACCOUNT_SIGNING_KEYFILE"

export ESKIMO_KUBE_SERVUCE_ACCOUNT_ISSUER=api
echo "   + Using ESKIMO_KUBE_SERVUCE_ACCOUNT_ISSUER=$ESKIMO_KUBE_SERVUCE_ACCOUNT_ISSUER"

# The API Server audit log configs
export ESKIMO_KUBE_AUDIT_LOG_MAXAGE=30
echo "   + Using ESKIMO_KUBE_AUDIT_LOG_MAXAGE=$ESKIMO_KUBE_AUDIT_LOG_MAXAGE"

export ESKIMO_KUBE_AUDIT_LOG_MAXBACKUP=3
echo "   + Using ESKIMO_KUBE_AUDIT_LOG_MAXBACKUP=$ESKIMO_KUBE_AUDIT_LOG_MAXBACKUP"

export ESKIMO_KUBE_AUDIT_LOG_MAXSIZE=100
echo "   + Using ESKIMO_KUBE_AUDIT_LOG_MAXSIZE=$ESKIMO_KUBE_AUDIT_LOG_MAXSIZE"

export ESKIMO_KUBE_AUDIT_LOG_PATH=/var/log/kubernetes/audit.log
echo "   + Using ESKIMO_KUBE_AUDIT_LOG_PATH=$ESKIMO_KUBE_AUDIT_LOG_PATH"

export ESKIMO_KUBE_AUDIT_POLICY_FILE=/etc/k8s/audit-policy.yaml
echo "   + Using ESKIMO_KUBE_AUDIT_POLICY_FILE=$ESKIMO_KUBE_AUDIT_POLICY_FILE"

export ESKIMO_KUBE_EVENT_TTL=1h
echo "   + Using ESKIMO_KUBE_EVENT_TTL=$ESKIMO_KUBE_EVENT_TTL"

# TODO
#--apiserver-count=3

# from master env.sh
echo "   + Using ESKIMO_KUBE_LOG_LEVEL=$ESKIMO_KUBE_LOG_LEVEL"
echo "   + Using ESKIMO_ALLOW_PRIVILEGED=$ESKIMO_ALLOW_PRIVILEGED"

echo "   + Using ESKIMO_KUBE_TLS_CERT_FILE=$ESKIMO_KUBE_TLS_CERT_FILE"
echo "   + Using ESKIMO_KUBE_TLS_PRIVATE_KEY=$ESKIMO_KUBE_TLS_PRIVATE_KEY"
echo "   + Using ESKIMO_KUBE_CLIENT_CA_FILE=$ESKIMO_KUBE_CLIENT_CA_FILE"