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

# Version of software to install
export ESKIMO_VERSION=0.5
export DEBIAN_VERSION=debian_11_bullseye

export FLINK_VERSION_FOR_ZEPPELIN=115
export FLINK_VERSION=1.15.4
export FLINK_HADOOP_VERSION=2.8.3-10.0
#export FLINK_KUBE_OPERATOR_VERSION=0.1.0
export FLINK_STATEFUN_VERSION=3.2.0
export FLINK_ML_VERSION=2.1.0
export FLINK_MINICONDA_VERSION=4.7.10
export FLINK_PYTHON_VERSION=3.7.16
export FLINK_CONNECTOR_ELASTICSEARCH_VERSION=3.0.0-1.16

export HADOOP_MAJOR_VERSION=3

export SPARK_VERSION=3.3.2
export SPARK_VERSION_MAJOR=3.3

export K8S_VERSION=1.26.3
export CFSSL_VERSION=1.6.3
# KEEPING OLD VERSION OF ETCD since later ones have the problem of initial-cluster repeating last node for all keys
# instead of taking the actual keys
# export ETCD_VERSION=3.3.27
export ETCD_VERSION=3.5.7
export CRI_DOCKER_VERSION=0.3.1
export K8S_ROUTER_VERSION=1.5.3
export K8S_CNI_PLUGINS_VERSION=1.2.0
export K8S_DASHBOARD_VERSION=2.7.0
#export GOLANG_VERSION=1.17
#export FULL_GOLANG_DEBIAN_VERSION=1.17.8-1~bpo11+1
export K8S_INFRA_IMAGE_PAUSE=3.6
export K8S_INFRA_IMAGE_COREDNS=1.10.1
export K8S_INFRA_IMAGE_STATE_METRICS=2.8.2
export K8S_DASHBOARD_METRICS_SCRAPER_VERSION=1.0.9
#export K8S_CLIENT_JAVA_VERSION=5.7.4

export DOCKER_REGISTRY_VERSION=2.7.1+ds2-7+b6_amd64
export DOCKER_REGISTRY_VERSION_SHORT=2.7.1
export REGCLIENT_VERSION=0.4.5

export ES_VERSION=8.5.3
export ES_VERSION_MAJOR_FOR_FLINK=7

export CEREBRO_VERSION=0.9.4

export KAFKA_VERSION=2.8.2
export KAFKA_MANAGER_VERSION=3.0.0.6

export SCALA_VERSION=2.12
export SCALA_VERSION_FULL=2.12.2
export SPARK_STREAMING_KAFKA_CON_VERSION=0-10
export SPARK_STREAMING_KAFKA_CLIENT_VERSION=2.0.0
export SPARK_UNUSED_VERSION=1.0.0

export EGMI_VERSION=0.3.0

export GRAFANA_VERSION=9.3.2
export PROMETHEUS_VERSION=2.41.0
export PROMETHEUS_NODE_EXPORTER_VERSION=1.5.0
export PROMETHEUS_PUSHGATEWAY_VERSION=1.5.1

# Zeppelin
export ZEPPELIN_VERSION_FULL=0.11.0-eskimo-2
export ZEPPELIN_VERSION=0.11
export ZEPPELIN_VERSION_SNAPSHOT=0.11.0-SNAPSHOT
export ZEPPELIN_IS_SNAPSHOT="false" # set to "true" to build zeppelin from zeppelin git master (which is rarely working)

export OPENLOGIC_JDK_8_VERSION="8u352-b08"


export IN_CONTAINER_INSTALL_SUCESS_MESSAGE=" - In container install SUCCESS"

# This function checks the successful execution of the install script running in a container
# by ensuring the expected message is find on the last line of the log file
# Arguments:
# - $1 the log file where to search for the expected message
check_in_container_install_success() {

    if [[ $1 == "" ]]; then
        echo "expected log file to be checked as argument"
        exit 1
    fi
    local LOG_FILE=$1

    if [[ $(tail -n 1 $LOG_FILE) != "$IN_CONTAINER_INSTALL_SUCESS_MESSAGE" ]]; then
        echo " - In container install script ended up in error"
        cat $LOG_FILE
        exit 100
    fi
}

# This functions ensures that internet is available on host machine (the one running eskimo)
# Internet is indeed required to download source packages for services.
check_for_internet() {

    if [ -x "$(command -v wget)" ]; then
        wget https://www.google.com -O /tmp/test.html >/dev/null 2>&1
        if [[ $? != 0 ]]; then
            echo "No internet connection available"
            rm -Rf /tmp/test.html
            exit 10
        fi
        rm -Rf /tmp/test.html
    else
        echo "No wget command available"
        exit 11
    fi
}

# This function ensures that docker is available on host machine (the one running eskimo)
# Docker is required to build package images that will be installed on cluster nodes
check_for_docker() {
    if [ -x "$(command -v docker)" ]; then
        echo "Found docker : $(docker -v)"
    else
        echo "Docker is not available on system"
        exit 1
    fi
}

function install_scala() {

    cd /tmp

    wget https://downloads.lightbend.com/scala/$SCALA_VERSION_FULL/scala-$SCALA_VERSION_FULL.deb
    if [[ $? != 0 ]]; then
        echo "Failed to download scala"
        exit 10
    fi

    dpkg -i scala-$SCALA_VERSION_FULL.deb
    if [[ $? != 0 ]]; then
        echo "Failed to install scala"
        exit 10
    fi
}

# This function is used after proper building of a service image to close the image and save it
# Arguments are:
# - $1 the image name
# - $2 the log file to report problems to
function close_and_save_image() {

    if [[ $1 == "" ]]; then
        echo "Image needs to be passed in argument"
        exit 2
    fi
    local IMAGE=$1

      if [[ $2 == "" ]]; then
        echo "Log file needs to be passed in argument"
        exit 3
    fi
    local LOG_FILE=$2

    if [[ $3 == "" ]]; then
        echo "Software version needs to be passed in argument"
        exit 4
    fi
    local VERSION=$3

    echo " - Running apt-autoremove"
    docker exec -i $IMAGE apt -y autoremove > $LOG_FILE 2>&1
    fail_if_error $? $LOG_FILE -2

    echo " - Cleaning apt cache"
    docker exec -i $IMAGE apt-get clean -q > $LOG_FILE 2>&1
    fail_if_error $? $LOG_FILE -2

    echo " - Cleanup doc and man pages"
    docker exec -i $IMAGE rm -Rf /usr/share/doc/ /usr/share/man /usr/share/doc-base/ /usr/share/info/ > $LOG_FILE 2>&1
    fail_if_error $? $LOG_FILE -2

    echo " - Recreating required folders"
    docker exec -i $IMAGE mkdir -p /usr/share/man/man1/ > $LOG_FILE 2>&1
    fail_if_error $? $LOG_FILE -2

    # Exit the container and commit the changes
    # Now that we've modified the container we have to commit the changes. First exit the container with the command exit.
    # To commit the changes and create a new image based on said changes, issue the command:
    echo " - Comitting changes on container $IMAGE"
    docker commit $IMAGE eskimo/"$IMAGE":latest > $LOG_FILE 2>&1
    fail_if_error $? $LOG_FILE -3

    # Stop container and delete image
    echo " - Stopping container $IMAGE"
    docker stop $IMAGE > $LOG_FILE 2>&1
    fail_if_error $? $LOG_FILE -4

    echo " - removing container $IMAGE"
    docker container rm $IMAGE > $LOG_FILE 2>&1
    fail_if_error $? $LOG_FILE -5

    # save base image
    echo " - Saving image ""$IMAGE"
    if [[ -z $TEST_MODE ]]; then set -e; fi
    docker save eskimo/"$IMAGE":latest | gzip >  ../../packages_distrib/tmp_image_"$IMAGE"_TEMP.tar.gz
    set +e

    echo " - versioning image"
    for i in $(seq 1 100); do
        if [[ ! -f "../../packages_distrib/docker_template_$(echo $IMAGE | cut -d '_' -f 1)_${VERSION}_${i}.tar.gz" ]]; then
            mv ../../packages_distrib/tmp_image_${IMAGE}_TEMP.tar.gz ../../packages_distrib/docker_template_"$(echo $IMAGE | cut -d '_' -f 1)"_${VERSION}_$i.tar.gz
            break;
        fi
    done

    #docker image rm `cat id_file`
    echo " - removing image "$IMAGE
    docker image rm eskimo/$IMAGE:latest > $LOG_FILE 2>&1
    fail_if_error $? $LOG_FILE -6

}

# This function is to build the empty version of the docker container that will be used to build the image
# Arguments are:
# - $1 the container name
# - $2 the log file to report problems to
function build_image() {

    if [[ $1 == "" ]]; then
        echo "Image needs to be passed in argument"
        exit 2
    fi
    local IMAGE=$1
    
    if [[ $2 == "" ]]; then
        echo "Log file needs to be passed in argument"
        exit 3
    fi
    local LOG_FILE=$2

    if [[ -z "$NO_BASE_IMAGE" ]]; then
        echo " - Checking if base eskimo image is available"
        if [[ $(docker images -q eskimo/base-eskimo_template 2>/dev/null) == "" ]]; then
            echo " - Trying to load base eskimo image"
            for i in $(ls -rt ../../packages_distrib/docker_template_base-eskimo*.tar.gz | tail -1); do
                echo "   + loading image $i"
                gunzip -c $i | docker load > $LOG_FILE 2>&1
                fail_if_error $? $LOG_FILE -10
            done
        fi
    fi

    echo " - Deleting any previous containers"
    if [[ $(docker ps -a -q -f name=$IMAGE) != "" ]]; then
        docker stop $IMAGE > /dev/null 2>&1
        docker container rm $IMAGE > /dev/null 2>&1
    fi

    # build
    echo " - building docker image $IMAGE"
    docker build --iidfile id_file --tag eskimo/"$IMAGE":latest .  > $LOG_FILE 2>&1
    fail_if_error $? $LOG_FILE -11

    local TMP_FOLDER=/tmp
    if [[ ! -z "$BUILD_TEMP_FOLDER" ]]; then
        export TMP_FOLDER=$BUILD_TEMP_FOLDER

        echo " - making sure I can write in $BUILD_TEMP_FOLDER"
        temp_file_created=$(mktemp)
        rm -Rf $temp_file_created
        temp_file_name=$(basename $temp_file_created)

        touch $BUILD_TEMP_FOLDER/$temp_file_name > $LOG_FILE 2>&1
        fail_if_error $? $LOG_FILE -11
    fi

    echo " - Starting container ""$IMAGE"
    # create and start container
    docker run \
            --privileged \
            -v $PWD:/scripts \
            -v $PWD/../common:/common  \
            -v $TMP_FOLDER:/tmp \
            -d --name $IMAGE \
            -i \
            -t eskimo/"$IMAGE":latest bash  > $LOG_FILE 2>&1
    fail_if_error $? $LOG_FILE -12

    echo " - Ensuring image was well started"
    if [[ -z $TEST_MODE ]]; then
        for i in $(seq 1 5); do
            sleep 1
            if [[ $(docker ps -q -f name=$IMAGE) != "" ]]; then
                break
            fi
            if [[ $i == 5 ]]; then
                echo "- Failed to start docker container $IMAGE in 4 seconds"
                exit 4
            fi
        done
    fi

}

## This is used to create a command wrapper around a binary command in order to
# - change the current directory to the command software installation location
# - inject topology
# Arguments:
# - $1 the source binary command to be wrapped
# - $2 the wrapper location
function create_binary_wrapper(){

    if [[ $1 == "" || $2 == "" ]]; then
        echo "target and wrapper have to be passed as argument of the create_binary_wrapper function"
        exit 2
    fi
    local TARGET=$1
    local WRAPPER=$2

    cat > $WRAPPER <<EOF
#!/bin/bash

if [[ -f /usr/local/sbin/inContainerInjectTopology.sh ]]; then
    # Injecting topoloy
    . /usr/local/sbin/inContainerInjectTopology.sh
fi

__tmp_saved_dir=\$(pwd)
function __tmp_returned_to_saved_dir() {
     cd \$__tmp_saved_dir
}
trap __tmp_returned_to_saved_dir 15
trap __tmp_returned_to_saved_dir EXIT

$TARGET "\$@"

__tmp_returned_to_saved_dir
EOF

    chmod 755 $WRAPPER
}


function fail_if_error(){
    if [[ $1 != 0 ]]; then
        echo " -> failed !!"
        cat $2
        exit $3
    fi
}

function get_ip_address(){
    export IP_ADDRESS="$(grep -F address /etc/network/interfaces | cut -d ' ' -f 8)"
}


function create_binary_wrapper(){

    if [[ $1 == "" || $2 == "" ]]; then
        echo "target and wrapper have to be passed as argument of the create_binary_wrapper function"
        exit 2
    fi
    local TARGET=$1
    local WRAPPER=$2

    cat > $WRAPPER <<EOF
#!/bin/bash

if [[ -f /usr/local/sbin/inContainerInjectTopology.sh ]]; then
    # Injecting topoloy
    . /usr/local/sbin/inContainerInjectTopology.sh
fi

__tmp_saved_dir=$(pwd)
function __tmp_returned_to_saved_dir() {
     cd \$__tmp_saved_dir
}
trap __tmp_returned_to_saved_dir 15
trap __tmp_returned_to_saved_dir EXIT

$TARGET "\$@"

__tmp_returned_to_saved_dir
EOF

    chmod 755 $WRAPPER
}