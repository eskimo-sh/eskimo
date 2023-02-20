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

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $SCRIPT_DIR/common.sh "$@"


check_for_internet

check_for_docker


mkdir -p /tmp/package-k8s
saved_dir=$(pwd)
cleanup() {
    #rm -f Vagrantfile
    #rm -f ssh_key
    #rm -f ssh_key.pub
    cd $saved_dir || return
}
trap cleanup 15
trap cleanup EXIT
trap cleanup ERR

err_report() {
    echo "Error on line $1"
    tail -n 2000 /tmp/package-k8s-log
}

trap 'err_report $LINENO' ERR

set -e

cp common.sh /tmp/package-k8s/common.sh
chmod 755 /tmp/package-k8s/common.sh

cp packageK8s.sh /tmp/package-k8s/packageK8s.sh
chmod 755 /tmp/package-k8s/packageK8s.sh

rm -f /tmp/package-k8s-log

cp Dockerfile.debian /tmp/package-k8s/Dockerfile

cd /tmp/package-k8s

set +e

export NO_BASE_IMAGE=true
build_image package_k8s /tmp/package-k8s-log

echo " - Updating the appliance"
docker exec -i package_k8s bash -c "DEBIAN_FRONTEND=noninteractive apt-get -yq update" > /tmp/package-k8s-log 2>&1
fail_if_error $? "package-k8s-log" -3

echo " - Upgrading the appliance"
docker exec -i package_k8s bash -c "DEBIAN_FRONTEND=noninteractive apt-get -yq upgrade" > /tmp/package-k8s-log 2>&1
fail_if_error $? "package-k8s-log" -4

echo " - Installing a few utility tools"
docker exec -i package_k8s bash -c "DEBIAN_FRONTEND=noninteractive apt-get install -y tar wget git unzip curl moreutils sudo procps" > /tmp/package-k8s-log 2>&1
fail_if_error $? "package-k8s-log" -6

echo " - Downloading required infrastructure containers for Kubernetes"

echo "   + k8s.gcr.io/pause:$K8S_INFRA_IMAGE_PAUSE"
docker pull k8s.gcr.io/pause:$K8S_INFRA_IMAGE_PAUSE > /tmp/package-k8s-log 2>&1
fail_if_error $? "package-k8s-log" -10

docker save k8s.gcr.io/pause:$K8S_INFRA_IMAGE_PAUSE | gzip > /tmp/k8s.gcr.io_pause:$K8S_INFRA_IMAGE_PAUSE.tar.gz
if [[ $? != 0 ]]; then
    echo "failed to save image !"
    exit 21
fi

echo "   + coredns/coredns:$K8S_INFRA_IMAGE_COREDNS"
docker pull coredns/coredns:$K8S_INFRA_IMAGE_COREDNS > /tmp/package-k8s-log 2>&1
fail_if_error $? "package-k8s-log" -22

docker save coredns/coredns:$K8S_INFRA_IMAGE_COREDNS | gzip > /tmp/coredns_coredns:$K8S_INFRA_IMAGE_COREDNS.tar.gz
if [[ $? != 0 ]]; then
    echo "failed to save image !"
    exit 23
fi

echo "   + kube-state-metrics/kube-state-metrics:v$K8S_INFRA_IMAGE_STATE_METRICS"
docker pull registry.k8s.io/kube-state-metrics/kube-state-metrics:v$K8S_INFRA_IMAGE_STATE_METRICS > /tmp/package-k8s-log 2>&1
fail_if_error $? "package-k8s-log" -22

docker save registry.k8s.io/kube-state-metrics/kube-state-metrics:v$K8S_INFRA_IMAGE_STATE_METRICS | gzip > /tmp/kube-state-metrics_kube-state-metrics:v$K8S_INFRA_IMAGE_STATE_METRICS.tar.gz
if [[ $? != 0 ]]; then
    echo "failed to save image !"
    exit 23
fi



echo " - Package Kubernetes"
docker exec -i package_k8s bash -c "/scripts/packageK8s.sh"
if [[ $? != 0 ]]; then
    echo "failed !"
    exit 11
fi


echo " - Copying K8s archive to shared folder"
#docker exec -it package_k8s bash
docker exec -i package_k8s bash -c "mv /usr/local/lib/k8s-$K8S_VERSION.tar.gz /scripts/" > /tmp/package-k8s-log 2>&1
fail_if_error $? "package-k8s-log" -12

set -e

if [[ -f "$SCRIPT_DIR/../../packages_distrib/eskimo_kube-$K8S_VERSION.tar.gz" ]]; then
    echo " - renaming previous kube-$K8S_VERSION.tar.gz"
    mv $SCRIPT_DIR/../../packages_distrib/eskimo_kube-$K8S_VERSION.tar.gz $SCRIPT_DIR/../../packages_distrib/eskimo_kube-$K8S_VERSION.tar.gz.old
fi

echo " - Copying K8s archive to distribution folder"
mv k8s-$K8S_VERSION.tar.gz $SCRIPT_DIR/../../packages_distrib/eskimo_kube_"$K8S_VERSION"_1.tar.gz

echo " - Destroying Docker container"
#vagrant destroy --force > /tmp/package-k8s-log 2>&1

docker stop package_k8s

docker container rm package_k8s

set +e
