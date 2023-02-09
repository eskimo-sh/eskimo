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


echo "Building kubernetes-dashboard Archive"
echo "--------------------------------------------------------------------------------"

# reinitializing log
rm -f /tmp/kubernetes-dashboard_build_log

mkdir -p /tmp/package-kubernetes-dashboard
saved_dir=$(pwd)
cleanup() {
    #rm -f Vagrantfile
    #rm -f ssh_key
    #rm -f ssh_key.pub
    cd $saved_dir
}
trap cleanup 15
trap cleanup EXIT
trap cleanup ERR


cd /tmp/package-kubernetes-dashboard || (echo "Couldn't cd to /tmp/package-kubernetes-dashboard" && exit 1)



echo " - Downloading required infrastructure containers for Kubernetes"

echo "   + kubernetesui/dashboard:v$K8S_DASHBOARD_VERSION"
docker pull kubernetesui/dashboard:v$K8S_DASHBOARD_VERSION > /tmp/package-kubernetes-dashboard-log 2>&1
fail_if_error $? "package-kubernetes-dashboard-log" -10

docker save kubernetesui/dashboard:v$K8S_DASHBOARD_VERSION | gzip > ./kubernetesui_dashboard:v$K8S_DASHBOARD_VERSION.tar.gz
if [[ $? != 0 ]]; then
    echo "failed to save image !"
    exit 21
fi

echo "   + kubernetesui/metrics-scraper:v$K8S_DASHBOARD_METRICS_SCRAPER_VERSION"
docker pull kubernetesui/metrics-scraper:v$K8S_DASHBOARD_METRICS_SCRAPER_VERSION > /tmp/package-kubernetes-dashboard-log 2>&1
fail_if_error $? "package-kubernetes-dashboard-log" -22

docker save kubernetesui/metrics-scraper:v$K8S_DASHBOARD_METRICS_SCRAPER_VERSION | gzip > ./kubernetesui_metrics-scraper:v$K8S_DASHBOARD_METRICS_SCRAPER_VERSION.tar.gz
if [[ $? != 0 ]]; then
    echo "failed to save image !"
    exit 23
fi

# save Archive
echo " - Saving archive kubernetes-dashboard"
tar cvfz $SCRIPT_DIR/../../packages_distrib/tmp_image_kubernetes-dashboard_TEMP.tar.gz * > /tmp/package-kubernetes-dashboard-log 2>&1
fail_if_error $? "package-kubernetes-dashboard-log" -22

echo " - versioning archive"
for i in $(seq 1 100); do
    if [[ ! -f "../../packages_distrib/docker_template_kubernetes-dashboard_""$K8S_DASHBOARD_VERSION""_$i.tar.gz" ]]; then
        mv $SCRIPT_DIR/../../packages_distrib/tmp_image_kubernetes-dashboard_TEMP.tar.gz \
           $SCRIPT_DIR/../../packages_distrib/docker_template_kubernetes-dashboard_"$K8S_DASHBOARD_VERSION"_$i.tar.gz
        break;
    fi
done

echo " - cleanup"
rm -Rf /tmp/package-kubernetes-dashboard