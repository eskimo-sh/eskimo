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

if [[ -f /usr/local/sbin/eskimo-utils.sh ]]; then
    . /usr/local/sbin/eskimo-utils.sh
fi

export IN_CONTAINER_CONFIG_SUCESS_MESSAGE=" - In container config SUCCESS"

# Copy a script to the docker container
# Arguments are:
# - $1 : the script to be copied. Needs to be in the service setup directory
# - $2 : the folder to copy to under /usr/local
# - $3 : the docker container to copy to
# - $4 : the log file to log errors to
docker_cp_script() {

    if [[ $1 == "" ]]; then
        echo "expected Script filename as first argument"
        exit 91
    fi
    export SCRIPT=$1

    if [[ $2 == "" ]]; then
        echo "expected target folder as second argument"
        exit 92
    fi
    export FOLDER=$2

    if [[ $3 == "" ]]; then
        echo "expected docker container name folder as third argument"
        exit 93
    fi
    export CONTAINER=$3

    if [[ $4 == "" ]]; then
        echo "expected target folder as fourth argument"
        exit 94
    fi
    export LOGFILE=$4

    echo " - Copying $SCRIPT to $CONTAINER"
    if [[ -f $SCRIPT ]]; then
        FULL_LOCAL_PATH=$SCRIPT
        SCRIPT=$(basename $SCRIPT)
    else
        FULL_LOCAL_PATH=$SCRIPT_DIR/$SCRIPT
        if [[ -f $FULL_LOCAL_PATH ]]; then
            echo "Script $FULL_LOCAL_PATH can't be found"
            exit 99
        fi
    fi

    docker cp $FULL_LOCAL_PATH $CONTAINER:/usr/local/$FOLDER/$SCRIPT >> $LOGFILE 2>&1
    fail_if_error $? "$LOGFILE" 95

    docker exec --user root $CONTAINER bash -c "chmod 755 /usr/local/$FOLDER/$SCRIPT" >> $LOGFILE 2>&1
    fail_if_error $? "$LOGFILE" 96
}

# This function installs the common eskimo infrastructure scripts to the target container
# - containerWatchDog.sh
# - glusterMountChecker.sh
# - glusterMountCheckerPeriodic.sh
# - inContainerMountGluster.sh
# - settingsInjector.sh
# to the container passed as argument
# Arguments are:
# - $1 : the container to install the files to
# - $2 - the log file in which to dump command outputs
function handle_eskimo_base_infrastructure() {

    if [[ $1 == "" ]]; then
        echo "Container needs to be passed in argument"
        exit 81
    fi
    export CONTAINER=$1

    if [[ $2 == "" ]]; then
        echo "Log file path needs to be passed in argument"
        exit 82
    fi
    export LOG_FILE=$2

    docker_cp_script /usr/local/sbin/settingsInjector.sh sbin $CONTAINER $LOG_FILE

    docker_cp_script /usr/local/sbin/gluster-mount.sh sbin $CONTAINER $LOG_FILE

    docker_cp_script /usr/local/sbin/glusterMountChecker.sh sbin $CONTAINER $LOG_FILE

    docker_cp_script /usr/local/sbin/glusterMountCheckerPeriodic.sh sbin $CONTAINER $LOG_FILE

    docker_cp_script /usr/local/sbin/inContainerMountGluster.sh sbin $CONTAINER $LOG_FILE

    docker_cp_script /usr/local/sbin/containerWatchDog.sh sbin $CONTAINER $LOG_FILE

    docker_cp_script /usr/local/bin/kube_do bin $CONTAINER $LOG_FILE

    docker_cp_script /usr/local/sbin/import-hosts.sh sbin $CONTAINER $LOG_FILE
}

# This function installs the topology related scripts
# - inContainerInjectTopology.sh
# - inContainerStartService.sh
# to the container passed as argument
# Arguments are:
# - $1 : the container to install the files to
# - $2 - the log file in which to dump command outputs
function handle_topology_infrastructure() {

    if [[ $1 == "" ]]; then
        echo "Container needs to be passed in argument"
        exit 81
    fi
    export CONTAINER=$1

    if [[ $2 == "" ]]; then
        echo "Log file path needs to be passed in argument"
        exit 82
    fi
    export LOG_FILE=$2

    if [[ -f $SCRIPT_DIR/inContainerInjectTopology.sh ]]; then
        echo " - Copying Topology Injection Script"
        docker cp $SCRIPT_DIR/inContainerInjectTopology.sh $CONTAINER:/usr/local/sbin/inContainerInjectTopology.sh >> $LOG_FILE 2>&1
        fail_if_error $? $LOG_FILE 83

        docker exec --user root $CONTAINER bash -c "chmod 755 /usr/local/sbin/inContainerInjectTopology.sh" >> $LOG_FILE 2>&1
        fail_if_error $? $LOG_FILE 84
    fi

    echo " - Copying Service Start Script"
    docker cp $SCRIPT_DIR/inContainerStartService.sh $CONTAINER:/usr/local/sbin/inContainerStartService.sh >> $LOG_FILE 2>&1
    fail_if_error $? $LOG_FILE 85

    docker exec --user root $CONTAINER bash -c "chmod 755 /usr/local/sbin/inContainerStartService.sh" >> $LOG_FILE 2>&1
    fail_if_error $? $LOG_FILE 86
}

# This is used to load the topology definition file
function loadTopology() {

    if [[ ! -f /etc/eskimo_topology.sh ]]; then
        echo "  - ERROR : no topology file defined !"
        exit 2
    fi

    . /etc/eskimo_topology.sh
}


function deploy_image_in_registry() {

    if [[ $1 == "" ]]; then
        echo "Image archive full path needs to be passed in argument"
        exit 81
    fi
    export IMAGE_FULL_PATH=$1

    if [[ ! -f $IMAGE_FULL_PATH ]]; then
        echo "File not found $IMAGE_FULL_PATH"
        exit 3
    fi
    
    if [[ $2 == "" ]]; then
        echo "Image name needs to be passed in argument"
        exit 82
    fi
    export IMAGE_NAME=$2

    IMAGE_FILE=$(basename $IMAGE_FULL_PATH)
    IMAGE_TAR=${IMAGE_FILE//\.gz/}
    IMAGE_VERSION=$(echo $IMAGE_FILE | cut -d ':' -f 2 | sed 's/\.tar\.gz//')

    if [[ $(echo "$IMAGE_FULL_PATH" | sed -n -e 's/^\(\/\).*/\1/p') != "" ]]; then
        echo "   + Copying $IMAGE_FULL_PATH to $PWD"
        /bin/cp -f $IMAGE_FULL_PATH .
    fi

    echo "   + Deleting previous docker image for $IMAGE_NAME:$IMAGE_VERSION if exist"
    if [[ $(docker images -q $IMAGE_NAME:$IMAGE_VERSION 2>/dev/null) != "" ]]; then
        docker image rm $IMAGE_NAME:$IMAGE_VERSION >> /tmp/kube_services_setup_log 2>&1
        fail_if_error $? "/tmp/kube_services_setup_log" 5
    fi

    echo "   + Deleting previous docker image for $IMAGE_NAME:$IMAGE_VERSION IN REPOSITORY if exist"
    if [[ $(docker images -q kubernetes.registry:5000/$IMAGE_NAME:$IMAGE_VERSION 2>/dev/null) != "" ]]; then
        docker image rm kubernetes.registry:5000/$IMAGE_NAME:$IMAGE_VERSION >> /tmp/kube_services_setup_log 2>&1
        fail_if_error $? "/tmp/kube_services_setup_log" 5
    fi

    echo "   + Importing latest docker image for $IMAGE_NAME:$IMAGE_VERSION"
    gunzip -f $IMAGE_FILE > /tmp/kube_services_setup_log 2>&1
    fail_if_error $? "/tmp/kube_services_setup_log" 5

    echo "     - Docker loading archive"
    docker load -i ./$IMAGE_TAR >> /tmp/kube_services_setup_log 2>&1
    if [[ $? != 0 ]]; then
        # dunno why but docker load is randomly failing from times to times
        echo "   + Second attempt"
        docker load -i ./$IMAGE_TAR >> /tmp/kube_services_setup_log 2>&1
        fail_if_error $? "/tmp/kube_services_setup_log" 6
    fi

    echo "   + Tagging $IMAGE_NAME in docker registry for kubernetes AS LATEST"
    docker tag $IMAGE_NAME:$IMAGE_VERSION kubernetes.registry:5000/$IMAGE_NAME:latest>> /tmp/kube_services_setup_log 2>&1
    if [[ $? != 0 ]]; then
        # try a second time
        sleep 4
        echo "   + second attempt"
        docker tag $IMAGE_NAME:$IMAGE_VERSION kubernetes.registry:5000/$IMAGE_NAME:$IMAGE_VERSION >> /tmp/kube_services_setup_log 2>&1
        fail_if_error $? "/tmp/kube_services_setup_log" 7
    fi

    echo "   + Deploying $IMAGE_NAME in docker registry for kubernetes AS LATEST"
    docker push kubernetes.registry:5000/$IMAGE_NAME:latest >> /tmp/kube_services_setup_log 2>&1
    if [[ $? != 0 ]]; then
        # try a second time
        sleep 4
        echo "   + second attempt"
        docker push kubernetes.registry:5000/$IMAGE_NAME:$IMAGE_VERSION >> /tmp/kube_services_setup_log 2>&1
        fail_if_error $? "/tmp/kube_services_setup_log" 8
    fi

    echo "   + removing local image"
    docker image rm $IMAGE_NAME:$IMAGE_VERSION   >> /tmp/kube_services_setup_log 2>&1
    fail_if_error $? "/tmp/kube_services_setup_log" 9
}


# Install container in registry
# Arguments:
# - $1 the name of the container image and service
# - $2 the lof file to dump command outputs to
# - $3 the tag to use
# - $4 a set of runtime flags
function deploy_registry() {

    if [[ $1 == "" ]]; then
        echo "Container needs to be passed in argument"
        exit 2
    fi
    export CONTAINER=$1

    if [[ $2 == "" ]]; then
        echo "New tag needs to be passed in argument"
        exit 4
    fi
    export NEW_TAG=$2

    if [[ $3 == "" ]]; then
        echo "Log file path needs to be passed in argument"
        exit 3
    fi
    export LOG_FILE=$3

    export FLAGS=""
    if [[ $4 != "" ]]; then
        export FLAGS="$4"
    fi

    if [[ $(echo $FLAGS | grep "NO_CONTAINER") == "" ]]; then

        echo " - Deploying $CONTAINER Service in docker registry for kubernetes"
        docker tag eskimo/$CONTAINER:$NEW_TAG kubernetes.registry:5000/$CONTAINER:$NEW_TAG >> $LOG_FILE 2>&1
        if [[ $? != 0 ]]; then
            echo "   + Could not re-tag kubernetes container image for $CONTAINER"
            cat $LOG_FILE
            exit 4
        fi
        sleep 2

        docker push kubernetes.registry:5000/$CONTAINER:$NEW_TAG >> $LOG_FILE 2>&1
        if [[ $? != 0 ]]; then
            echo "Image push in docker registry failed !"
            cat $LOG_FILE
            exit 5
        fi
        sleep 2

        echo " - removing local image 'eskimo/$CONTAINER:$NEW_TAG'"
        docker image rm eskimo/$CONTAINER:$NEW_TAG  >> $LOG_FILE 2>&1
        if [[ $? != 0 ]]; then
            echo "local image removal failed !"
            cat $LOG_FILE
            exit 6
        fi
    fi
}


# Install container in registry and deploy it using kubernetes
# Arguments:
# - $1 the name of the container image and service
# - $2 the lof file to dump command outputs to
# - $3 the tag to use
# - $4 a set of runtime flags
function deploy_kubernetes_only() {

    if [[ $1 == "" ]]; then
        echo "Container needs to be passed in argument"
        exit 2
    fi
    export CONTAINER=$1

    if [[ $2 == "" ]]; then
        echo "New tag needs to be passed in argument"
        exit 4
    fi
    export NEW_TAG=$2

    if [[ $3 == "" ]]; then
        echo "Log file path needs to be passed in argument"
        exit 3
    fi
    export LOG_FILE=$3

    export FLAGS=""
    if [[ $4 != "" ]]; then
        export FLAGS="$4"
    fi

    if [[ ! -f $CONTAINER.k8s.yaml.sh ]]; then
        echo "Kubernetes deployment file $CONTAINER.k8s.yaml.sh not found"
        exit 16
    fi

    export PATH=$PATH:/usr/local/bin

    if [[ -z "$TEST_MODE" ]]; then
        echo " - Saving Kube deployment file $CONTAINER.k8s.yml in /var/lib/eskimo/kube-services"
        mkdir -p /var/lib/eskimo/kube-services/
        /bin/cp -f $CONTAINER.k8s.yaml.sh /var/lib/eskimo/kube-services/  >> $LOG_FILE 2>&1
        fail_if_error $? "$LOG_FILE" 25
    fi

    echo " - Removing any previously deployed $CONTAINER service from kubernetes"
    # shellcheck disable=SC1090
    . $CONTAINER.k8s.yaml.sh | kubectl delete -f -  >> "${LOG_FILE}_kubernetes_deploy" 2>&1
    if [[ $? != 0 ]]; then
        if [[ $(cat "${LOG_FILE}_kubernetes_deploy") != "" && $(grep "not found" "${LOG_FILE}_kubernetes_deploy") != "" ]]; then
            echo "   + Some elements were not found. Moving on ..."
        else
            echo "   + Could not delete deployment from kubernetes"
            cat "${LOG_FILE}_kubernetes_deploy"
            exit 7
        fi
    fi

    if [[ $(grep "deleted" "${LOG_FILE}_kubernetes_deploy") != "" ]]; then
        echo "   + Previous instance removed"
        if [[ -z "$NO_SLEEP" ]]; then sleep 5; fi
    fi

    echo " - Deploying $CONTAINER service in kubernetes"
    /bin/bash -c ". $CONTAINER.k8s.yaml.sh | kubectl apply -f -" >> "${LOG_FILE}_kubernetes_deploy" 2>&1
    if [[ $? != 0 ]]; then
        echo "   + Could not deploy $CONTAINER application in kubernetes"
        cat "${LOG_FILE}_kubernetes_deploy"
        exit 8
    fi

}


# Install container in registry and deploy it in kubernetes
# Arguments:
# - $1 the name of the container image and service
# - $2 the lof file to dump command outputs to
# - $3 the tag to use
# - $4 a set of runtime flags
function deploy_kubernetes() {

    if [[ $1 == "" ]]; then
        echo "Container needs to be passed in argument"
        exit 2
    fi
    export CONTAINER=$1

    if [[ $2 == "" ]]; then
        echo "New tag needs to be passed in argument"
        exit 4
    fi
    export NEW_TAG=$2

    if [[ $3 == "" ]]; then
        echo "Log file path needs to be passed in argument"
        exit 3
    fi
    export LOG_FILE=$3

    export FLAGS=""
    if [[ $4 != "" ]]; then
        export FLAGS="$4"
    fi

    deploy_registry "$@"

    deploy_kubernetes_only "$@"
}


# Install systemd service and check startup of service
# Arguments:
# - $1 the name of the systemd service
# - $2 the lof file to dump command outputs to
function install_and_check_service_file() {

    if [[ $1 == "" ]]; then
        echo "Container needs to be passed in argument"
        exit 22
    fi
    export CONTAINER=$1

    if [[ $2 == "" ]]; then
        echo "Log file path needs to be passed in argument"
        exit 23
    fi
    export LOG_FILE=$2

    if [[ $(echo $3 | grep "SKIP_COPY") == "" ]]; then
        if [[ -d /lib/systemd/system/ ]]; then
            export systemd_units_dir=/lib/systemd/system/
        elif [[ -d /usr/lib/systemd/system/ ]]; then
            export systemd_units_dir=/usr/lib/systemd/system/
        else
            echo "Couldn't find systemd unit files directory"
            exit 24
        fi

        echo " - Copying $CONTAINER systemd file"
        sudo cp $SCRIPT_DIR/$CONTAINER.service $systemd_units_dir >> $LOG_FILE 2>&1
        fail_if_error $? "$LOG_FILE" 25

        sudo chmod 644 $systemd_units_dir/$CONTAINER.service
    fi

    echo " - Reloading systemd config"
    if [[ -z "$NO_SLEEP" ]]; then sleep 1; fi # hacky hack - I get weird and unexplainable errors here sometimes.
    sudo systemctl daemon-reload >> $LOG_FILE 2>&1
    fail_if_error $? "$LOG_FILE" 25

    if [[ -z "$NO_SLEEP" ]]; then sleep 2; fi # hacky hack - I get weird and unexplainable errors here sometimes.

    echo " - Checking Systemd file"
    if [[ $(sudo systemctl status $CONTAINER | grep 'could not be found') != "" ]]; then
        echo "$CONTAINER systemd file installation failed"
        exit 26
    fi
    sudo systemctl status $CONTAINER >> $LOG_FILE 2>&1
    if [[ $? != 0 && $? != 3 ]]; then
        echo "$CONTAINER systemd file doesn't work as expected"
        exit 27
    fi

    if [[ $(echo $3 | grep "RESTART") == "" ]]; then
        echo " - Testing systemd startup - starting $CONTAINER"
        sudo systemctl start $CONTAINER >> $LOG_FILE 2>&1
        fail_if_error $? "$LOG_FILE" 28
    else
        echo " - Testing systemd startup - Stopping $CONTAINER before restart"
        sudo systemctl stop $CONTAINER >> $LOG_FILE 2>&1
        fail_if_error $? "$LOG_FILE" 28

        if [[ -z "$NO_SLEEP" ]]; then sleep 2; fi # hacky hack - I get weird and unexplainable errors here sometimes.

        echo " - Testing systemd startup - starting $CONTAINER"
        sudo systemctl start $CONTAINER >> $LOG_FILE 2>&1
        fail_if_error $? "$LOG_FILE" 28
    fi

    echo " - Testing systemd startup - Checking $CONTAINER startup"
    if [[ -z "$NO_SLEEP" ]]; then sleep 12; fi
    sudo systemctl status $CONTAINER >> $LOG_FILE 2>&1
    fail_if_error $? "$LOG_FILE" 29

    if [[ $3 != "NO_CHECK" ]]; then
        echo " - Testing systemd startup - Make sure service is really running"
        if [[ $(systemctl show -p SubState $CONTAINER | grep exited) != "" ]]; then
            echo "$CONTAINER service is actually not really running"
            exit 30
        fi
    fi

    #echo " - Testing systemd startup - stopping $CONTAINER"
    #sudo systemctl stop $CONTAINER >> $LOG_FILE 2>&1
    #fail_if_error $? "$LOG_FILE" -9

    echo " - Enabling $CONTAINER on startup"
    sudo systemctl enable $CONTAINER >> $LOG_FILE 2>&1
    fail_if_error $? "$LOG_FILE" 31

    #echo " - Testing systemd startup - starting $CONTAINER (again)"
    #sudo systemctl start $CONTAINER >> $LOG_FILE 2>&1
    #fail_if_error $? "$LOG_FILE" -11
}


# Commit a container from its docker image and start it
# Arguments:
# - $1 the name of the container
# - $2 the name of the related docker image
# - $3 the log file to dump command output to
# - return new tag to use as exported variable CONTAINER_NEW_TAG
function build_container() {

    if [[ $1 == "" ]]; then
        echo "Container needs to be passed in argument"
        exit 2
    fi
    export CONTAINER=$1

    if [[ $2 == "" ]]; then
        echo "Image (template) needs to be passed in argument"
        exit 3
    fi
    export IMAGE=$2

    if [[ $3 == "" ]]; then
        echo "Log file path needs to be passed in argument"
        exit 4
    fi
    export LOG_FILE=$3

    echo " - Deleting previous docker template for $IMAGE if exist"
    if [[ $(docker images -q "eskimo/${IMAGE}_template:latest" 2>/dev/null) != "" ]]; then
        docker image rm "eskimo/${IMAGE}_template:latest" >> $LOG_FILE 2>&1
        fail_if_error $? "$LOG_FILE" 5
    fi

    echo " - Importing latest docker template for $IMAGE"

    if [[ ! -f ${SCRIPT_DIR}/docker_template_$IMAGE.tar ]]; then
        echo "   + Decompressing archive"
        gunzip ${SCRIPT_DIR}/docker_template_$IMAGE.tar.gz > $LOG_FILE 2>&1
        fail_if_error $? "$LOG_FILE" 16
    fi

    echo "   + Docker loading archive"
    docker load -i ${SCRIPT_DIR}/docker_template_$IMAGE.tar >> $LOG_FILE 2>&1
    if [[ $? != 0 ]]; then
        # dunno why but docker load is randomly failing from times to times
        echo "   + Second attempt"
        docker load -i ${SCRIPT_DIR}/docker_template_$IMAGE.tar >> $LOG_FILE 2>&1
        fail_if_error $? "$LOG_FILE" 6
    fi

    echo " - Killing any previous containers $CONTAINER"
    sudo systemctl stop $CONTAINER > /dev/null 2>&1
    if [[ $(docker ps -a -q -f name=$CONTAINER) != "" ]]; then

        echo "   + Forcing stop of previous containers"
        docker stop $CONTAINER > /dev/null 2>&1
        docker container rm $CONTAINER > /dev/null 2>&1
    fi

    echo " - Finding new tag for container image $CONTAINER"
    local LAST_TAG=$(get_last_tag $CONTAINER)
    local NEW_TAG=$(($LAST_TAG+1))

    if [[ $NEW_TAG != 1 ]]; then
        delete_tag $CONTAINER $LAST_TAG $LOG_FILE
    fi

    sleep 2

    # build
    echo " - Building docker container with tag eskimo/$CONTAINER:$NEW_TAG"
    docker build --iidfile id_file --tag eskimo/$CONTAINER:$NEW_TAG . >> $LOG_FILE 2>&1
    fail_if_error $? "$LOG_FILE" 7

    export CONTAINER_NEW_TAG=$NEW_TAG
}

# This is used to create a command wrapper around a binary command in order to
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
    TARGET=$1
    WRAPPER=$2

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

# This function checks the successful execution of the setup script running in a container
# by ensuring the expected message is find on the last line of the log file
# Arguments:
# - $1 the log file where to search for the expected message
check_in_container_config_success() {

    if [[ $1 == "" ]]; then
        echo "expected log file to be checked as argument"
        exit 1
    fi
    export LOG_FILE=$1

    if [[ $(tail -n 1 $LOG_FILE) != "$IN_CONTAINER_CONFIG_SUCESS_MESSAGE" ]]; then
        echo " - In container setup script ended up in error"
        cat $LOG_FILE
        exit 100
    fi
}


function preinstall_unmount_gluster_share () {
    if [[ $(grep $1 /etc/mtab) != "" ]]; then
        echo " - preinstallation : need to unmount gluster share $1 before proceeding with installation"
        i=0
        while [[ $(grep $1 /etc/mtab) != "" ]]; do
            sudo umount $1
            if [[ -z "$NO_SLEEP" ]]; then sleep 1; fi
            i=$((i+1))
            if [[ $1 == 5 ]]; then
                echo " - Failed to unmount $1 after 5 attempts"
                exit 1
            fi
        done
    fi
}
