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

if [[ $1 == "" ]]; then
    echo "Expecting Eskimo service / container  name as argument"
    exit 1
fi

if [[ $1 == "-h" || $1 == "--help" ]]; then
    echo "eskimo-edit-image [-h] SERVICE [SCRIPT]"
    echo "  where SERVICE is the eskimo service / container name whose image is to be edited"
    echo "        SCRIPT is an optional script path to perfom customization"
    exit 0
fi
export CONTAINER=$1

if [[ $2 != "" ]]; then
    export SCRIPT=$2
    if [[ ! -f $SCRIPT ]]; then
        echo "Script $SCRIPT is not found"
        exit 2
    fi
fi

export IS_KUBERNETES=1
if [[ -d "/var/lib/kubernetes/docker_registry/docker/registry/v2/repositories/$CONTAINER/" ]]; then
    export IS_KUBERNETES=0 # true
fi

if [[ $IS_KUBERNETES == 0 ]]; then # true
    export IMAGE=kubernetes.registry:5000/$CONTAINER
else
    export IMAGE=eskimo/$CONTAINER
fi

echo " - Image to be edited is $IMAGE"


if [[ ":$PATH:" != *":/usr/local/sbin/:"* ]]; then
    PATH=$PATH:/usr/local/sbin/
fi
. eskimo-utils.sh


echo " - Parsing arguments"
# extract path arguments and create volume mount command part
export DOCKER_VOLUMES_ARGS=""

# manipulating file path
if [[ $SCRIPT != "" ]]; then

    # last parameter is file to execute
    export SCRIPT_DIR=$(dirname $SCRIPT)

    export FILENAME_ARG=$(realpath $SCRIPT)
    export LAST_ARG_DIR=$(realpath $SCRIPT_DIR)
    #set -- "${@:1:$(($#-1))}"

    if [[ "$SCRIPT_DIR" != "" ]]; then
        if [[ $(echo $DOCKER_VOLUMES_ARGS | grep "$SCRIPT_DIR:$SCRIPT_DIR:slave") == "" ]]; then
            export DOCKER_VOLUMES_ARGS=" -v $SCRIPT_DIR:$SCRIPT_DIR:slave $DOCKER_VOLUMES_ARGS"
        fi
    fi
else
    export FILENAME_ARG=""
fi

echo " - Creating Kubernetes service DNS entries"
KUBE_SERVICES_HOSTS_FILE=$(create_kube_services_hosts_file)
if [[ $? != 0 ]]; then
    echo "Failed to list kube services and create host file"
    exit 1
fi
if [[ ! -f $KUBE_SERVICES_HOSTS_FILE ]]; then
    echo "Failed to create 'Kube services host file' with create_kube_services_hosts_file"
    exit 2
fi

export TEMP_CONTAINER_NAME=`uuidgen`
echo " - Temp container name is $TEMP_CONTAINER_NAME"
export LOG_FILE=/tmp/$TEMP_CONTAINER_NAME.log


echo " - Finding new tag for container image"
LAST_TAG=$(get_last_tag $CONTAINER)
NEW_TAG=$(($LAST_TAG+1))

echo " - Launching Container ..."
docker run \
        -it \
        --rm \
        --network host \
        --user $USER \
        --privileged \
        $DOCKER_VOLUMES_ARGS \
        --mount type=bind,source=/etc/eskimo_topology.sh,target=/etc/eskimo_topology.sh \
        --mount type=bind,source=/etc/eskimo_services-settings.json,target=/etc/eskimo_services-settings.json \
        --mount type=bind,source=$KUBE_SERVICES_HOSTS_FILE,target=$KUBE_SERVICES_HOSTS_FILE \
        -v /etc/k8s:/etc/k8s:ro \
        -e NODE_NAME=$HOSTNAME \
        -e ADDITONAL_HOSTS_FILE=$KUBE_SERVICES_HOSTS_FILE \
        --name $TEMP_CONTAINER_NAME \
        -d \
        "$IMAGE:$LAST_TAG" \
            /bin/bash >> $LOG_FILE 2>&1
if [[ $? != 0 ]]; then
    echo "Failed to un new container for image $IMAGE:$LAST_TAG"
    exit 11
fi


# Finding out if we have y TTY here
tty -s && USE_TTY="-t"

if [[ $FILENAME_ARG != "" ]]; then
    echo " - Running script $FILENAME_ARG in container"
else
    echo " - Invoking shell in container. You can now type commands!"  
fi  
docker exec \
        -i $USE_TTY \
        $TEMP_CONTAINER_NAME \
            /bin/bash $FILENAME_ARG
export RESULT=$?

if [[ $RESULT == 0 ]]; then

    echo " - Customization command exited successfully, will now save change as new tag in $IMAGE:$NEW_TAG"

    echo " - Committing the changes to the container"
    docker commit $TEMP_CONTAINER_NAME $IMAGE:$NEW_TAG  >> $LOG_FILE 2>&1
    if [[ $? != 0 ]]; then
        echo "Committing container $TEMP_CONTAINER_NAME to $IMAGE:$NEW_TAG failed"
        cat $LOG_FILE
        exit 71
    fi

    # Stop setup container and and delete it
    echo " - Stopping container"
    docker stop $TEMP_CONTAINER_NAME  >> $LOG_FILE 2>&1
    if [[ $? != 0 ]]; then
        echo "Stopping container $TEMP_CONTAINER_NAME failed"
        cat $LOG_FILE
        exit 72
    fi

    docker container rm $TEMP_CONTAINER_NAME >> $LOG_FILE 2>&1
#    if [[ $? != 0 ]]; then
#        echo "Removing container $TEMP_CONTAINER_NAME failed"
#        cat $LOG_FILE
#        exit 73
#    fi

    if [[ $IS_KUBERNETES == 0 ]]; then # true

        echo " - Pushing image in docker registry"
        docker push $IMAGE:$NEW_TAG >> $LOG_FILE 2>&1
        if [[ $? != 0 ]]; then
            echo "Image push in docker registry failed !"
            cat $LOG_FILE
            exit 5
        fi
    fi

    echo " - Deleting previous tag"
    delete_tag $CONTAINER $LAST_TAG $LOG_FILE

else
    echo " !! Command exited in error, not committing any change"
    exit 6
fi


rm -Rf $KUBE_SERVICES_HOSTS_FILE