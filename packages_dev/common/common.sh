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

# Version of software to install
export ESKIMO_VERSION=0.4
export DEBIAN_VERSION=debian_10_buster

export FLINK_VERSION=1.12.2
export FLINK_HADOOP_VERSION=2.8.3-10.0

export HADOOP_MAJOR_VERSION=2.8

export SPARK_VERSION=2.4.7
export SPARK_VERSION_MAJOR=2.4

# CAUTION : This version number needs to be aligned with the expected mesos version defined in
# /services_setup/base-eskimo/install-mesos.sh
export AMESOS_VERSION=1.11.0

# fom binaries
export MARATHON_VERSION_SHORT=1.10.17
export MARATHON_VERSION=1.10.17-c427ce965
# from sources
export MARATHON_VERSION_SOURCES_GITHUB=1.10.17

export DOCKER_REGISTRY_VERSION=2.7.1+ds2-7+b2_amd64

export ES_VERSION=7.10.2
export ES_VERSION_MAJOR=7

export CEREBRO_VERSION=0.9.3

export KAFKA_VERSION=2.4.1
export KAFKA_MANAGER_VERSION=3.0.0.5

export SCALA_VERSION=2.11
export SPARK_STREAMING_KAFKA_CON_VERSION=0-10
export SPARK_STREAMING_KAFKA_CLIENT_VERSION=2.0.0
export SPARK_UNUSED_VERSION=1.0.0

export EGMI_VERSION=0.1-SNAPSHOT

export GRAFANA_VERSION=7.3.7
export PROMETHEUS_VERSION=2.24.0
export PROMETHEUS_NODE_EXPORTER_VERSION=1.0.1
export PROMETHEUS_PUSHGATEWAY_VERSION=1.3.1
export PROMETHEUS_MESOS_EXPORTER_VERSION=1.1.2

# Zeppelin
export ZEPPELIN_VERSION_FULL=0.9.0
export ZEPPELIN_VERSION=0.9
export ZEPPELIN_VERSION_SNAPSHOT=0.9.1-SNAPSHOT
export ZEPPELIN_IS_SNAPSHOT="false" # set to "true" to build zeppelin from zeppelin git master (which is rarely working)


# This functions ensures that internet is available on host machine (the one running eskimo)
# Internet is indeed required to download source packages for services.
check_for_internet() {

    if [ -x "$(command -v wget)" ]; then
        wget https://www.google.com -O /tmp/test.html >/dev/null 2>&1
        if [[ $? != 0 ]]; then
            echo "No internet connection available"
            exit 10
        fi
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

# This function is used after proper building of a service image to close the image and save it
# Arguments are:
# - $1 the image name
# - $2 the log file to report problems to
function close_and_save_image() {

    if [[ $1 == "" ]]; then
        echo "Image needs to be passed in argument"
        exit 2
    fi
    IMAGE=$1

	if [[ $2 == "" ]]; then
        echo "Log file needs to be passed in argument"
        exit 3
    fi
    LOG_FILE=$2

    if [[ $3 == "" ]]; then
        echo "Software version needs to be passed in argument"
        exit 4
    fi
    VERSION=$3
	
    echo " - Cleaning apt cache"
    docker exec -i $IMAGE apt-get clean -q > $LOG_FILE 2>&1
    fail_if_error $? $LOG_FILE -2

    # Exit the container and commit the changes
    # Now that we've modified the container we have to commit the changes. First exit the container with the command exit.
    # To commit the changes and create a new image based on said changes, issue the command:
    echo " - Comitting changes on container $IMAGE"
    docker commit $IMAGE eskimo:"$IMAGE" > $LOG_FILE 2>&1
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
    docker save eskimo:"$IMAGE" | gzip >  ../../packages_distrib/tmp_image_"$IMAGE"_TEMP.tar.gz
    set +e

    echo " - versioning image"
    for i in `seq 1 100`; do
        if [[ ! -f "../../packages_distrib/docker_template_"`echo $IMAGE | cut -d '_' -f 1`"_""$VERSION""_$i.tar.gz" ]]; then
            mv ../../packages_distrib/tmp_image_"$IMAGE"_TEMP.tar.gz ../../packages_distrib/docker_template_`echo $IMAGE | cut -d '_' -f 1`_"$VERSION"_$i.tar.gz
            break;
        fi
    done

    #docker image rm `cat id_file`
    echo " - removing image "$IMAGE
    docker image rm eskimo:$IMAGE > $LOG_FILE 2>&1
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
    IMAGE=$1
	
	if [[ $2 == "" ]]; then
        echo "Log file needs to be passed in argument"
        exit 3
    fi
    LOG_FILE=$2

    if [[ -z "$NO_BASE_IMAGE" ]]; then
        echo " - Checking if base eskimo image is available"
        if [[ `docker images -q eskimo:base-eskimo_template 2>/dev/null` == "" ]]; then
            echo " - Trying to load base eskimo image"
            for i in `ls -rt ../../packages_distrib/docker_template_base-eskimo*.tar.gz | tail -1`; do
                echo "   + loading image $i"
                gunzip -c $i | docker load > $LOG_FILE 2>&1
                fail_if_error $? $LOG_FILE -10
            done
        fi
    fi

    echo " - Deleting any previous containers"
    if [[ `docker ps -a -q -f name=$IMAGE` != "" ]]; then
        docker stop $IMAGE > /dev/null 2>&1
        docker container rm $IMAGE > /dev/null 2>&1
    fi

    # build
    echo " - building docker image $IMAGE"
    docker build --iidfile id_file --tag eskimo:"$IMAGE" .  > $LOG_FILE 2>&1
    fail_if_error $? $LOG_FILE -11

    export TMP_FOLDER=/tmp
    if [[ ! -z "$BUILD_TEMP_FOLDER" ]]; then
        export TMP_FOLDER=$BUILD_TEMP_FOLDER

        echo " - making sure I can write in $BUILD_TEMP_FOLDER"
        touch $BUILD_TEMP_FOLDER/test > $LOG_FILE 2>&1
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
            -t eskimo:"$IMAGE" bash  > $LOG_FILE 2>&1
    fail_if_error $? $LOG_FILE -12

    echo " - Ensuring image was well started"
    if [[ -z $TEST_MODE ]]; then
        for i in `seq 1 5`; do
            sleep 1
            if [[ `docker ps -q -f name=$IMAGE` != "" ]]; then
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
        exit -2
    fi
    TARGET=$1
    WRAPPER=$2

    touch $WRAPPER
    chmod 777 $WRAPPER
    echo -e '#!/bin/bash' > $WRAPPER
    echo -e "" >> $WRAPPER
    echo -e "if [[ -f /usr/local/sbin/inContainerInjectTopology.sh ]]; then" >> $WRAPPER
    echo -e "    # Injecting topoloy" >> $WRAPPER
    echo -e "    . /usr/local/sbin/inContainerInjectTopology.sh" >> $WRAPPER
    echo -e "fi" >> $WRAPPER
    echo -e "" >> $WRAPPER
    echo -e "__tmp_saved_dir=`pwd`" >> $WRAPPER
    echo -e "function __tmp_returned_to_saved_dir() {" >> $WRAPPER
    echo -e '     cd $__tmp_saved_dir' >> $WRAPPER
    echo -e "}" >> $WRAPPER
    echo -e "trap __tmp_returned_to_saved_dir 15" >> $WRAPPER
    echo -e "trap __tmp_returned_to_saved_dir EXIT" >> $WRAPPER
    echo -e "" >> $WRAPPER
    echo -e "$TARGET \"\$@\"" >> $WRAPPER
    echo -e "" >> $WRAPPER
    echo -e "__tmp_returned_to_saved_dir" >> $WRAPPER
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
    export IP_ADDRESS="`cat /etc/network/interfaces | grep address | cut -d ' ' -f 8`"
}

