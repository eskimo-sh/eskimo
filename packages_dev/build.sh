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

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $SCRIPT_DIR/common/common.sh "$@"

cd $SCRIPT_DIR || exit 199

package=""  # Default to empty package

export DONT_OVERWRITE=0

echo_usage() {
      echo "Usage:"
      echo "    build.sh -h                    Display this help message."
      echo "    build.sh [-n] <package>        Build <package>."
      echo "        where package in [kube, <image>, all_images]"
      echo "        and <image> any docker image such as eg, kafka, kube-master, "
      echo "                                                 ntp, spark, etc."
      echo "        Options:"
      echo "          -n Don't rebuild if image is already built"
}

# Parse options to the `pip` command
while getopts ":hn" opt; do
  case ${opt} in
    h )
      echo_usage
      exit 0
      ;;
   \? )
     echo "Invalid Option: -$OPTARG" 1>&2
     exit 1
     ;;
   n )
     export DONT_OVERWRITE=1
     ;;
  esac
done
shift $((OPTIND -1))

package=$1;

check_for_internet


if [[ $package == "kube" ]] ; then

    if [[ $DONT_OVERWRITE == 0 || ! -f ../packages_distrib/eskimo_kube_"$K8S_VERSION"_1.tar.gz ]]; then
        bash -c "cd binary_k8s && bash build.sh"
    else
        echo "Kubernetes package already built"
    fi

elif [[ $package == "all_images" ]]; then

    check_for_docker

    echo "BUILDING ALL PACKAGES"
    set -e

    all_services='base-eskimo ntp zookeeper gluster kube-master kube-shell kubernetes-dashboard prom-node-exporter prometheus grafana elasticsearch logstash cerebro kibana kafka flink kafka-manager spark zeppelin'

    IFS=$' '
    for service in $all_services; do
        if [[ $DONT_OVERWRITE == 0 || $(ls -1 ../packages_distrib/ | grep docker_template_${service/ *$//}_) == "" ]]; then
            bash -c "cd image_$service && bash build.sh"
        else
            echo "image $service already built"
        fi
    done

else

    if [[ -z $1 ]]; then
        echo_usage

    else
        check_for_docker

        #echo "Building $package"
        #echo "(overwrite is disabled ? $DONT_OVERWRITE)"
        #echo "(Package is existing ?"`ls ../packages_distrib/ | grep docker_template_"$package"_`
        if [[ $DONT_OVERWRITE == 0 || $(ls ../packages_distrib/ | grep docker_template_"$package"_) == "" ]]; then
            echo "BUILDING PACKAGE $package"
            set -e
            bash -c "cd image_$package && bash build.sh"
        else
            echo "image $package already built"
        fi
    fi
fi