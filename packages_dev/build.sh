#!/usr/bin/env bash

#
# This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
# well to this individual file than to the Eskimo Project as a whole.
#
# Copyright 2019 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

cd $SCRIPT_DIR

package=""  # Default to empty package

export DONT_OVERWRITE=0
export USE_VIRTUALBOX=0

echo_usage() {
      echo "Usage:"
      echo "    build.sh -h                    Display this help message."
      echo "    build.sh [-n] <package>        Build <package>."
      echo "        where package in [mesos-rhel, mesos-deb, mesos-all, <image>, all_images]"
      echo "        and <image> any docker image such as eg, kafka, mesos-master, "
      echo "                                                 ntp, spark, etc."
      echo "        Options:"
      echo "          -n Don't rebuild if image is already built"
      echo "          -b Use virtualBox instead of libvirt for building"
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
   n )
     export USE_VIRTUALBOX=1
     ;;
  esac
done
shift $((OPTIND -1))

package=$1;

check_for_internet


if [[ $package == "mesos-rhel" ]] ; then

    check_for_vagrant

    if [[ $DONT_OVERWRITE == 0 || ! -f ../packages_distrib/niceideas_mesos-redhat-$AMESOS_VERSION.tar.gz ]]; then
        bash -c "cd binary_mesos && bash build-for-redhat.sh"
    else
        echo "RedHat Mesos package already built"
    fi

elif [[ $package == "mesos-deb" ]] ; then

    check_for_vagrant

    if [[ $DONT_OVERWRITE == 0 || ! -f ../packages_distrib/niceideas_mesos-debian-$AMESOS_VERSION.tar.gz ]]; then
        bash -c "cd binary_mesos && bash build-for-debian.sh"
    else
        echo "Debian Mesos package already built"
    fi

elif [[ $package == "mesos-all" ]] ; then

    check_for_vagrant

    if [[ $DONT_OVERWRITE == 0 || ! -f ../packages_distrib/niceideas_mesos-debian-$AMESOS_VERSION.tar.gz ]]; then
        bash -c "cd binary_mesos && bash build-for-debian.sh"
    else
        echo "Debian Mesos package already built"
    fi

    if [[ $DONT_OVERWRITE == 0 || ! -f ../packages_distrib/niceideas_mesos-redhat-$AMESOS_VERSION.tar.gz ]]; then
        bash -c "cd binary_mesos && bash build-for-redhat.sh"
    else
        echo "RedHat Mesos package already built"
    fi

elif [[ $package == "all_images" ]]; then

    check_for_docker

    echo "BUILDING ALL PACKAGES"
    set -e

    if [[ $DONT_OVERWRITE == 0 || ! -f ../packages_distrib/docker_template_base_eskimo.tar.gz ]]; then
        bash -c "cd image_base_eskimo && bash build.sh"
    else
        echo "image base_eskimo already built"
    fi

    if [[ $DONT_OVERWRITE == 0 || ! -f ../packages_distrib/docker_template_ntp.tar.gz ]]; then
        bash -c "cd image_ntp && bash build.sh"
    else
        echo "image ntp already built"
    fi

    if [[ $DONT_OVERWRITE == 0 || ! -f ../packages_distrib/docker_template_zookeeper.tar.gz ]]; then
        bash -c "cd image_zookeeper && bash build.sh"
    else
        echo "image zookeeper already built"
    fi

    if [[ $DONT_OVERWRITE == 0 || ! -f ../packages_distrib/docker_template_gluster.tar.gz ]]; then
        bash -c "cd image_gluster && bash build.sh"
    else
        echo "image gluster already built"
    fi

    if [[ $DONT_OVERWRITE == 0 || ! -f ../packages_distrib/docker_template_gdash.tar.gz ]]; then
        bash -c "cd image_gdash && bash build.sh"
    else
        echo "image gdash already built"
    fi

    if [[ $DONT_OVERWRITE == 0 || ! -f ../packages_distrib/docker_template_mesos-master.tar.gz ]]; then
        bash -c "cd image_mesos-master && bash build.sh"
    else
        echo "image mesos-master already built"
    fi

    if [[ $DONT_OVERWRITE == 0 || ! -f ../packages_distrib/docker_template_prometheus.tar.gz ]]; then
        bash -c "cd image_prometheus && bash build.sh"
    else
        echo "image prometheus already built"
    fi

    if [[ $DONT_OVERWRITE == 0 || ! -f ../packages_distrib/docker_template_grafana.tar.gz ]]; then
        bash -c "cd image_grafana && bash build.sh"
    else
        echo "image grafana already built"
    fi

    if [[ $DONT_OVERWRITE == 0 || ! -f ../packages_distrib/docker_template_elasticsearch.tar.gz ]]; then
        bash -c "cd image_elasticsearch && bash build.sh"
    else
        echo "image elasticsearch already built"
    fi

    if [[ $DONT_OVERWRITE == 0 || ! -f ../packages_distrib/docker_template_logstash.tar.gz ]]; then
        bash -c "cd image_logstash && bash build.sh"
    else
        echo "image logstash already built"
    fi

    if [[ $DONT_OVERWRITE == 0 || ! -f ../packages_distrib/docker_template_cerebro.tar.gz ]]; then
        bash -c "cd image_cerebro && bash build.sh"
    else
        echo "image cerebro already built"
    fi

    if [[ $DONT_OVERWRITE == 0 || ! -f ../packages_distrib/docker_template_kibana.tar.gz ]]; then
        bash -c "cd image_kibana && bash build.sh"
    else
        echo "image kibana already built"
    fi

    if [[ $DONT_OVERWRITE == 0 || ! -f ../packages_distrib/docker_template_kafka.tar.gz ]]; then
        bash -c "cd image_kafka && bash build.sh"
    else
        echo "image kafka already built"
    fi

    if [[ $DONT_OVERWRITE == 0 || ! -f ../packages_distrib/docker_template_kafka-manager.tar.gz ]]; then
        bash -c "cd image_kafka-manager && bash build.sh"
    else
        echo "image kafka-manager already built"
    fi

    if [[ $DONT_OVERWRITE == 0 || ! -f ../packages_distrib/docker_template_spark.tar.gz ]]; then
        bash -c "cd image_spark && bash build.sh"
    else
        echo "image spark already built"
    fi

    if [[ $DONT_OVERWRITE == 0 || ! -f ../packages_distrib/docker_template_zeppelin.tar.gz ]]; then
        bash -c "cd image_zeppelin && bash build.sh"
    else
        echo "image zeppelin already built"
    fi

else

    if [[ -z $1 ]]; then
        echo_usage

    else
        check_for_docker

        if [[ $DONT_OVERWRITE == 0 || ! -f ../packages_distrib/docker_template_$package.tar.gz ]]; then
            echo "BUILDING PACKAGE $package"
            set -e
            bash -c "cd image_$package && bash build.sh"
        else
            echo "image $package already built"
        fi
    fi
fi