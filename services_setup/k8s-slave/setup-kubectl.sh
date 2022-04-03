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

if [[ ! -f /etc/k8s/env.sh ]]; then
    echo "Could not find /etc/k8s/env.sh"
    exit 1
fi

. /etc/k8s/env.sh

sudo rm -Rf /tmp/kube_base_setup
mkdir /tmp/kube_base_setup
cd /tmp/kube_base_setup

# Defining topology variables
if [[ $SELF_NODE_NUMBER == "" ]]; then
    echo " - No Self Node Number found in topology"
    exit 1
fi

if [[ $SELF_IP_ADDRESS == "" ]]; then
    echo " - No Self IP address found in topology for node $SELF_NODE_NUMBER"
    exit 2
fi



set -e

if [[ ! -d /etc/k8s/ssl/ ]]; then
    echo " - Creating folder /etc/k8s/ssl/"
    sudo mkdir -p /etc/k8s/ssl/
fi

# attempt to recreate  / remount gluster shares
sudo /bin/bash /usr/local/sbin/setupK8sGlusterShares.sh

echo " - Creating / checking eskimo kubernetes base config"


function delete_ssl_lock_file() {
     rm -Rf /etc/k8s/ssl/ssl_management_lock
}

# From here we will be messing with gluster and hence we need to take a lock
counter=0
while [[ -f /etc/k8s/ssl/ssl_management_lock ]] ; do
    echo "   + /etc/k8s/ssl/ssl_management_lock exist. waiting 2 secs ... "
    sleep 2
    let counter=counter+1
    if [[ $counter -ge 15 ]]; then
        echo " !!! Couldn't get /etc/k8s/ssl/ssl_management_lock in 30 seconds. crashing !"
        exit 150
    fi
done

touch /etc/k8s/ssl/ssl_management_lock

trap delete_ssl_lock_file 15
trap delete_ssl_lock_file EXIT


if [[ ! -f /etc/k8s/ssl/ca-config.json ]]; then
    echo "   + Create and install ca-config.json"
    cat > ca-config.json <<EOF
{
    "signing": {
        "default": {
            "expiry": "43800h"
        },
        "profiles": {
            "server": {
                "expiry": "43800h",
                "usages": [
                    "signing",
                    "key encipherment",
                    "server auth"
                ]
            },
            "client": {
                "expiry": "43800h",
                "usages": [
                    "signing",
                    "key encipherment",
                    "client auth"
                ]
            },
            "peer": {
                "expiry": "43800h",
                "usages": [
                    "signing",
                    "key encipherment",
                    "server auth",
                    "client auth"
                ]
            },
            "kubernetes": {
                "expiry": "43800h",
                "usages": [
                    "signing",
                    "key encipherment",
                    "server auth",
                    "client auth"
                ]
            }
        }
    }
}
EOF

    sudo mv ca-config.json /etc/k8s/ssl/ca-config.json
    sudo chown root /etc/k8s/ssl/ca-config.json
    sudo chmod 755 /etc/k8s/ssl/ca-config.json
fi

if [[ ! -f /etc/k8s/ssl/ca-csr.json ]]; then
    echo "   + Create and install ca-csr.json"
    cat > ca-csr.json <<EOF
{
  "CN": "eskimo",
  "hosts": [],
  "key": {
    "algo": "rsa",
    "size": 2048
  },
  "names": [
    {
      "C": "SH",
      "ST": "Eskimo",
      "L": "Eskimo",
      "O": "system:masters",
      "OU": "System"
    }
  ]
}
EOF

    sudo mv ca-csr.json /etc/k8s/ssl/ca-csr.json
    sudo chown root /etc/k8s/ssl/ca-csr.json
    sudo chmod 755 /etc/k8s/ssl/ca-csr.json
fi


# TODO re-generate cert with the following
# echo "   + generate certificate ca.pen"
# cfssl gencert -initca /etc/k8s/ssl/ca-csr.json | cfssljson -bare ca

if [[ ! -f /etc/k8s/ssl/ca.pem ]]; then
    # Generate certificates
    echo "   + Generate root certificates"
    sudo /usr/local/bin/cfssl gencert -initca /etc/k8s/ssl/ca-csr.json | cfssljson -bare ca

    echo "   + Install root certificates"
    sudo mv ca*.pem /etc/k8s/ssl/
    sudo mv ca*csr* /etc/k8s/ssl/
fi

#if [[ ! -f /etc/k8s/ssl/ca.pem ]]; then
#    echo "   + Create and install ca.pem"
#    cat > ca.pem <<EOF
#-----BEGIN CERTIFICATE-----
#MIIDpDCCAoygAwIBAgIUdLAwIsCXelv+v/mHO7DQfUByav4wDQYJKoZIhvcNAQEL
#BQAwajELMAkGA1UEBhMCU0gxDzANBgNVBAgTBkVza2ltbzEPMA0GA1UEBxMGRXNr
#aW1vMRcwFQYDVQQKEw5zeXN0ZW06bWFzdGVyczEPMA0GA1UECxMGU3lzdGVtMQ8w
#DQYDVQQDEwZlc2tpbW8wHhcNMjIwMzMxMjM1MzAwWhcNMjcwMzMwMjM1MzAwWjBq
#MQswCQYDVQQGEwJTSDEPMA0GA1UECBMGRXNraW1vMQ8wDQYDVQQHEwZFc2tpbW8x
#FzAVBgNVBAoTDnN5c3RlbTptYXN0ZXJzMQ8wDQYDVQQLEwZTeXN0ZW0xDzANBgNV
#BAMTBmVza2ltbzCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBALP3tqu6
#/OdtEWjGs1K531K0JkqN+O90mbpHZI2N5IsNqRwnJyYjqgjc+2Puc+HGScTZ840W
#9+YS9gJyj9Fe65umA00wZA4Qh1PTiZ5r3UyiOPtIhd4CrTpuzjPloJnGiu8lSlfb
#5s400PGuoNT6VIRG5aOROCa36+gJTBHsNnnJUhEfAJC54MbItxX4HAGuC9GBBQBg
#rdl/fDhzjj1ea+o0M10Hi+SriDZ5UoUm0jSYpEL9eWDbYkAV7v2sZ4CNQCGglDq3
#AYW89uYdNLi+OjhJtoHtOtAh/DMRzR+TqhQVwkA2zIrm55Eq/6f+FUy97UjrQgCl
#ksWnkYnnqm/Kqz8CAwEAAaNCMEAwDgYDVR0PAQH/BAQDAgEGMA8GA1UdEwEB/wQF
#MAMBAf8wHQYDVR0OBBYEFKiDOMiCUDSYhphSicIXs+Fcqe5lMA0GCSqGSIb3DQEB
#CwUAA4IBAQADtEWgBTGUEYWLyzWQvePrzBbVftqGQuTVF6zchShglsF9ZwTLlOhy
#Ow/qy9DaONAUWBxOZQ672T8ImDzL4L3/hiIec3Fgd0yF5eIP1+Ew59X5BaDG56Gc
#yGau2CEtgUOWN+UmRB/S2RYu653mvKcJZTfksRa2/hMwCVs3GGiQUnADEEzRs3In
#4pXOzO1J5ZJ4k/VmtG5XDF8A+p+2jTbuqVo4c/iLzPRKSkE3p0K0FBe2c6aaEE7S
#xJbI1eI3Sy7ROn2Snxk8vztQ00S3wmX56xMuma1yEtWmYIj8VL9iEeiOEGPjibrD
#tl0LFKSyZJzn/k8ZWEh+sD5sMb+i/wZc
#-----END CERTIFICATE-----
#EOF
#
#    sudo mv ca.pem /etc/k8s/ssl/ca.pem
#    sudo chown root /etc/k8s/ssl/ca.pem
#    sudo chmod 755 /etc/k8s/ssl/ca.pem
#fi
#
#if [[ ! -f /etc/k8s/ssl/ca-key.pem ]]; then
#    echo "   + Create and install ca-key.pem"
#    cat > ca-key.pem <<EOF
#-----BEGIN RSA PRIVATE KEY-----
#MIIEpQIBAAKCAQEAs/e2q7r8520RaMazUrnfUrQmSo3473SZukdkjY3kiw2pHCcn
#JiOqCNz7Y+5z4cZJxNnzjRb35hL2AnKP0V7rm6YDTTBkDhCHU9OJnmvdTKI4+0iF
#3gKtOm7OM+WgmcaK7yVKV9vmzjTQ8a6g1PpUhEblo5E4Jrfr6AlMEew2eclSER8A
#kLngxsi3FfgcAa4L0YEFAGCt2X98OHOOPV5r6jQzXQeL5KuINnlShSbSNJikQv15
#YNtiQBXu/axngI1AIaCUOrcBhbz25h00uL46OEm2ge060CH8MxHNH5OqFBXCQDbM
#iubnkSr/p/4VTL3tSOtCAKWSxaeRieeqb8qrPwIDAQABAoIBAQCST+UKLwZnnAuY
#VFr7+bNvSJkM6emlu/UzNdGxJ9fTmTKQeeBhKIOXmxkigH1j49KboNpaLX8zZNzu
#1nbIMFR6gXiTq7DcomFzLDrpOiZ7pDORT7/N4/0z0VwBY0GzY3AWxwlu7o/xu4zX
#wDJvlGlb9UvDNFAjYkn/pnda5uDPFxl+7tFZg2TM+SRLJ+pv4KT1Xa3q/zmtEKM5
#fQ8UpCXjURC9jsjJVV91+gVFfNQmDuIce86B2PcwAmN9w5s4WhPs39JK6rLLoTN5
#TavHkzzaS1ayak36Xee7+L3N0oAoe/Ss8NPu6fpvKxSXSB8QdI7uJ6zFpEdYw5Jm
#yA1A+TSJAoGBANOyOyCIOypu3yejpMqdndD9QPCQ+PR1MH0lBdMDs0ettSrWS41l
#MORDmOL0jXiw/VRl134f/Y2Mb+B5x9VABrdPkTzkJ1ohYVU6Zna4W9r7vbPxhDhp
#Dri/wPu6jnncvHZRjQ7Y0Ze/Jhg9y9B79TzCzMHpHqvrbeqlEz3AMBUTAoGBANmh
#miLE4l7C5Pc7VtxoG8uoVkMHLjzCzSg9+RgbTTdo4eUUNOczB83ORGu0R0IQFPxZ
#8khj0sekhUlAijbCyOLgI5KxKK0B1R0J+0zeVoWDW53x6E7z8zXzJcwwOro1Kru9
#DUzTE+DZmTjLm8iSjhqTL1/3T6SOiXPgLnNIX1KlAoGBAMrhjPjolotcJlF+63ej
#BpQ7ofjrdywRA59r0+EfMroHKBEdvtqLmAerIOKhLNP2C1LPJ26gigcWCEJBc9pT
#uhwEDVUl8vDV/MoJZhlxJdcAXBYP6H+Hb6xvWSvm5pSzj4p1LZKFCJk9f+zLjIEJ
#y0WoC0PrfpGU14qBGaecgHgxAoGBAMxMafeNv7uZVXvJVqcdjEpIwoG1sfAUJqdK
#VhYNOxVy/ewgZfFL9F187rn0yJf5pNmoTksqemozxXOYhKcXf6GLK5m/eSHAAKF8
#B06Hwc0gHNdOiffkBkJ0+NyPwfGfi3gl5uBhnd2MJ8wrRxioi4Hi/awm67rcm6gN
#oU1TeTiJAoGAamK7l5DHgbBchF1sXYsAy1mvmtan6ci9n/rc0Tu5OcLY/LcsFMlE
#eaJbuiNu8ZCr27ei91e7aOHLP7NrK3khj1hnlsh4OAvTeukGsFe0L5JZVI+70pwR
#vhXK+f6o4t8Ahv/MEhBhO0SrAEocKspAiPkrpZ8qyS8J8hgdHblxRrg4=
#-----END RSA PRIVATE KEY-----
#EOF
#
#    sudo mv ca-key.pem /etc/k8s/ssl/ca-key.pem
#    sudo chown root /etc/k8s/ssl/ca-key.pem
#fi
#
#if [[ ! -f /etc/k8s/ssl/ca.csr ]]; then
#    echo "   + Create and install ca.csr"
#    cat > ca.csr <<EOF
#-----BEGIN CERTIFICATE REQUEST-----
#MIICrzCCAZcCAQAwajELMAkGA1UEBhMCU0gxDzANBgNVBAgTBkVza2ltbzEPMA0G
#A1UEBxMGRXNraW1vMRcwFQYDVQQKEw5zeXN0ZW06bWFzdGVyczEPMA0GA1UECxMG
#U3lzdGVtMQ8wDQYDVQQDEwZlc2tpbW8wggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAw
#ggEKAoIBAQCz97aruvznbRFoxrNSud9StCZKjfjvdJm6R2SNjeSLDakcJycmI6oI
#3Ptj7nPhxknE2fONFvfmEvYCco/RXuubpgNNMGQOEIdT04mea91Mojj7SIXeAq06
#bs4z5aCZxorvJUpX2+bONNDxrqDU+lSERuWjkTgmt+voCUwR7DZ5yVIRHwCQueDG
#yLcV+BwBrgvRgQUAYK3Zf3w4c449XmvqNDNdB4vkq4g2eVKFJtI0mKRC/Xlg22JA
#Fe79rGeAjUAhoJQ6twGFvPbmHTS4vjo4SbaB7TrQIfwzEc0fk6oUFcJANsyK5ueR
#Kv+n/hVMve1I60IApZLFp5GJ56pvyqs/AgMBAAGgADANBgkqhkiG9w0BAQsFAAOC
#AQEAbHz56HPvhVYXIffK6cAE13n0Af26Sbdufpx6CNDnZyN9jKXhaVKQR+GVXrnI
#MPuxQ0SUjEiAFvMICLSe2zMe7NCM1XUTkOoBHDEyE6OgSKOXafZjYrrhBAIhFpig
#VriiUp+tMoYkMOBqr6QMGEcZiaEQTOr68/QxY10jfG0GNTpkac7C/0yqahXBBpt3
#KpnTF1miktmLPyBkhZmL8zzmOzLAmxd2MlE4uB+fkVLcWJc6I5rQnRBc0AIYS/YS
#X/VOMGqCvx2rf0b+BMuq0wxXWC5iuqEQE6WQXCYim5v1b2O9tNiHuK3qXX97fVe5
#sbQ2b9xJVmIpiscPEF6Dn17v1Q==
#-----END CERTIFICATE REQUEST-----
#EOF
#
#    sudo mv ca.csr /etc/k8s/ssl/ca.csr
#    sudo chown root /etc/k8s/ssl/ca.csr
#fi

if [[ ! -f /etc/k8s/ssl/$USER-csr.json ]]; then
    echo "   + Create and install {user}-csr.json"
    cat > $USER-csr.json <<EOF
{
  "CN": "$USER",
  "hosts": [],
  "key": {
    "algo": "rsa",
    "size": 2048
  },
  "names": [
    {
      "C": "SH",
      "ST": "Eskimo",
      "L": "Eskimo",
      "O": "system:masters",
      "OU": "System"
    }
  ]
}
EOF

    sudo mv $USER-csr.json /etc/k8s/ssl/$USER-csr.json
fi

if [[ ! -f /etc/k8s/ssl/$USER.pem ]]; then
    # Generate certificates
    echo "   + Generate Admin certificates"
    sudo /usr/local/bin/cfssl gencert -ca=/etc/k8s/ssl/ca.pem \
      -ca-key=/etc/k8s/ssl/ca-key.pem \
      -config=/etc/k8s/ssl/ca-config.json \
      -profile=kubernetes /etc/k8s/ssl/`echo $USER`-csr.json | cfssljson -bare `echo $USER`

    echo "   + Install Admin certificates"
    sudo mv `echo $USER`*.pem /etc/k8s/ssl/
    sudo mv `echo $USER`*csr* /etc/k8s/ssl/
fi

if [[ ! -f /etc/k8s/ssl/kubernetes-csr.json ]]; then
    echo "   + Create and install kubernetes-csr.json"
    cat > kubernetes-csr.json <<EOF
{
  "CN": "kubernetes",
  "hosts": [
    "127.0.0.1",
    "${SELF_IP_ADDRESS}",
    "${MASTER_URL}",
    "${CLUSTER_KUBERNETES_SVC_IP}",
    "kubernetes",
    "kubernetes.default",
    "kubernetes.default.svc",
    "kubernetes.default.svc.cluster",
    "kubernetes.default.svc.cluster.local",
    "eskimo",
    "eskimo.default",
    "eskimo.default.svc",
    "eskimo.default.svc.cluster",
    "eskimo.default.svc.cluster.local",
    "eskimo.eskimo",
    "eskimo.eskimo.svc",
    "eskimo.eskimo.svc.cluster",
    "eskimo.eskimo.svc.cluster.local"
  ],
  "key": {
    "algo": "rsa",
    "size": 2048
  },
   "names": [
      {
        "C": "SH",
        "ST": "Eskimo",
        "L": "Eskimo",
        "O": "system:masters",
        "OU": "System"
      }
    ]
}
EOF

    sudo mv kubernetes-csr.json /etc/k8s/ssl/kubernetes-csr.json
    sudo chown kubernetes /etc/k8s/ssl/kubernetes-csr.json
    sudo chmod 755 /etc/k8s/ssl/kubernetes-csr.json
fi


if [[ ! -f /etc/k8s/ssl/kubernetes.pem ]]; then

    # Generate certificates
    echo "   + Generate kubernetes certificates"

    sudo /usr/local/bin/cfssl gencert -ca=/etc/k8s/ssl/ca.pem \
      -ca-key=/etc/k8s/ssl/ca-key.pem \
      -config=/etc/k8s/ssl/ca-config.json \
      -profile=kubernetes /etc/k8s/ssl/kubernetes-csr.json | cfssljson -bare kubernetes

    echo "   + Install kubernetes certificates"
    sudo mv kubernetes*.pem /etc/k8s/ssl/
    sudo chown kubernetes /etc/k8s/ssl/kubernetes*.pem
    sudo mv kubernetes*csr* /etc/k8s/ssl/
    sudo chown kubernetes /etc/k8s/ssl/kubernetes*csr*
fi


delete_ssl_lock_file


echo "   + removing previous configuration"
rm -f ~/.kube/config

echo "   + Configure the cluster and the certificates"
kubectl config set-cluster eskimo \
  --certificate-authority=/etc/k8s/ssl/ca.pem \
  --embed-certs=true \
  --server=$ESKIMO_KUBE_APISERVER

echo "   + Configure client side user and certificates"
kubectl config set-credentials $USER \
  --client-certificate=/etc/k8s/ssl/$USER.pem \
  --embed-certs=true \
  --client-key=/etc/k8s/ssl/$USER-key.pem \
  --token=$BOOTSTRAP_TOKEN

echo "   + Create context"
kubectl config set-context eskimo \
  --cluster=eskimo \
  --user=$USER

echo " - Set the context eskimo as default context"
kubectl config use-context eskimo

echo "   + Checking kube config file"
if [[ ! -f ~/.kube/config ]]; then
    echo "Couldn't find file ~/.kube/config"
    exit 101
fi

if [[ `cat ~/.kube/config | grep "name: eskimo" | wc -l` -lt 2 ]]; then
    echo "Missing config in file ~/.kube/config"
    exit 101
fi

set +e

rm -Rf /tmp/kube_base_setup