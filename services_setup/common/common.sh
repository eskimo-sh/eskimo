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

# Copy a script to the docker container
# Arguments are:
# - $1 : the script to be copied. Needs to be in the service setup directory
# - $2 : the folder to copy to under /usr/local
# - $3 : the docker container to copy to
# - $4 : the log file to log errors to
docker_cp_script() {

    if [[ $1 == "" ]]; then
        echo "expected Script filename as first argument"
        exit 1
    fi
    export SCRIPT=$1

    if [[ $2 == "" ]]; then
        echo "expected target folder as second argument"
        exit 2
    fi
    export FOLDER=$2

    if [[ $3 == "" ]]; then
        echo "expected docker container name folder as third argument"
        exit 3
    fi
    export CONTAINER=$3

    if [[ $4 == "" ]]; then
        echo "expected target folder as fourth argument"
        exit 4
    fi
    export LOGFILE=$4

    echo " - Copying $SCRIPT Script to $CONTAINER"
    docker cp $SCRIPT_DIR/$SCRIPT $CONTAINER:/usr/local/$FOLDER/$SCRIPT >> $LOGFILE 2>&1
    fail_if_error $? "$LOGFILE" -20

    docker exec --user root $CONTAINER bash -c "chmod 755 /usr/local/$FOLDER/$SCRIPT" >> $LOGFILE 2>&1
    fail_if_error $? "$LOGFILE" -21
}


# This function installs the topology related scripts
# - inContainerInjectTopology.sh
# - inContainerStartService.sh
# - ../common/settingsInjector.sh
# to the container passed as argument
# Arguments are:
# - $1 : the container to install the files to
# - $2 - the log file in which to dump command outputs
function handle_topology_settings() {

    if [[ $1 == "" ]]; then
        echo "Container needs to be passed in argument"
        exit 2
    fi
    export CONTAINER=$1

    if [[ $2 == "" ]]; then
        echo "Log file path needs to be passed in argument"
        exit 3
    fi
    export LOG_FILE=$2

    echo " - Copying Topology Injection Script"
    docker cp $SCRIPT_DIR/inContainerInjectTopology.sh $CONTAINER:/usr/local/sbin/inContainerInjectTopology.sh >> $LOG_FILE 2>&1
    fail_if_error $? $LOG_FILE -20

    docker exec --user root $CONTAINER bash -c "chmod 755 /usr/local/sbin/inContainerInjectTopology.sh" >> $LOG_FILE 2>&1
    fail_if_error $? $LOG_FILE -21

    echo " - Copying Service Start Script"
    docker cp $SCRIPT_DIR/inContainerStartService.sh $CONTAINER:/usr/local/sbin/inContainerStartService.sh >> $LOG_FILE 2>&1
    fail_if_error $? $LOG_FILE -22

    docker exec --user root $CONTAINER bash -c "chmod 755 /usr/local/sbin/inContainerStartService.sh" >> $LOG_FILE 2>&1
    fail_if_error $? $LOG_FILE -24

    echo " - Copying settingsInjector.sh Script"
    docker cp $SCRIPT_DIR/settingsInjector.sh $CONTAINER:/usr/local/sbin/settingsInjector.sh >> $LOG_FILE 2>&1
    fail_if_error $? $LOG_FILE -23

    docker exec --user root $CONTAINER bash -c "chmod 755 /usr/local/sbin/settingsInjector.sh" >> $LOG_FILE 2>&1
    fail_if_error $? $LOG_FILE -24
}

# This is used to load the topology definition file
function loadTopology() {

    if [[ ! -f /etc/eskimo_topology.sh ]]; then
        echo "  - ERROR : no topology file defined !"
        exit 2
    fi

    . /etc/eskimo_topology.sh
}

# Install container in registry and deploy it using marathon
# Arguments:
# - $1 the name of the systemd service
# - $2 the lof file to dump command outputs to
function deploy_marathon() {

    if [[ $1 == "" ]]; then
        echo "Container needs to be passed in argument"
        exit 2
    fi
    export CONTAINER=$1

    if [[ $2 == "" ]]; then
        echo "Log file path needs to be passed in argument"
        exit 3
    fi
    export LOG_FILE=$2

    echo " - Deploying $CONTAINER Service in docker registry for marathon"
    docker tag eskimo:$CONTAINER marathon.registry:5000/$CONTAINER >> $LOG_FILE 2>&1
    if [[ $? != 0 ]]; then
        echo "   + Could not re-tag marathon container image"
        exit 4
    fi

    docker push marathon.registry:5000/$CONTAINER >> $LOG_FILE 2>&1
    if [[ $? != 0 ]]; then
        echo "Image push in docker registry failed !"
        exit 5
    fi

    echo " - removing local image"
    docker image rm eskimo:$CONTAINER  >> $LOG_FILE 2>&1
    if [[ $? != 0 ]]; then
        echo "local image removal failed !"
        exit 6
    fi

    echo " - Removing any previously deployed $CONTAINER service from marathon"
    curl -XDELETE http://$MASTER_MARATHON_1:28080/v2/apps/$CONTAINER >> $LOG_FILE"_marathon_deploy" 2>&1
    if [[ $? != 0 ]]; then
        echo "   + Could not reach marathon"
        cat "$LOG_FILE"_marathon_deploy
        exit 7
    fi

    if [[ `cat "$LOG_FILE"_marathon_deploy` != "" && `grep "does not exist" "$LOG_FILE"_marathon_deploy` == "" ]]; then
        echo "   + Previous instance removed"
        if [[ -z "$NO_SLEEP" ]]; then sleep 5; fi
    fi

    echo " - Deploying $CONTAINER service in marathon"
    # 10 attempts with 5 seconds sleep each
    for i in $(seq 1 10); do
        echo "   + Attempt $i"
        curl  -X POST -H 'Content-Type: application/json' \
            -d "@$CONTAINER.marathon.json" \
            http://$MASTER_MARATHON_1:28080/v2/apps 2>&1 | tee "$LOG_FILE"_deploy >> $LOG_FILE
        if [[ $? != 0 ]]; then
            echo "   + Could not deploy $CONTAINER application in marathon"
            cat "$LOG_FILE"_deploy
            exit 8
        fi
        if [[ `grep "App is locked by one or more deployments" "$LOG_FILE"_deploy` == "" ]]; then
            break
        fi
        if [[ "$i" == "10" ]]; then
            echo "   + Failed 10 times !!"
            exit 9
        fi
        if [[ -z "$NO_SLEEP" ]]; then sleep 5; fi
    done


    #-H "Authorization: token=$(dcos config show core.dcos_acs_token)" \
}


# Install systemd service and check startup of service
# Arguments:
# - $1 the name of the systemd service
# - $2 the lof file to dump command outputs to
function install_and_check_service_file() {

    if [[ $1 == "" ]]; then
        echo "Container needs to be passed in argument"
        exit 2
    fi
    export CONTAINER=$1

    if [[ $2 == "" ]]; then
        echo "Log file path needs to be passed in argument"
        exit 3
    fi
    export LOG_FILE=$2

    if [[ -d /lib/systemd/system/ ]]; then
        export systemd_units_dir=/lib/systemd/system/
    elif [[ -d /usr/lib/systemd/system/ ]]; then
        export systemd_units_dir=/usr/lib/systemd/system/
    else
        echo "Couldn't find systemd unit files directory"
        exit 4
    fi

    echo " - Copying $CONTAINER systemd file"
    sudo cp $SCRIPT_DIR/$CONTAINER.service $systemd_units_dir
    sudo chmod 644 $systemd_units_dir/$CONTAINER.service

    echo " - Reloading systemd config"
    if [[ -z "$NO_SLEEP" ]]; then sleep 1; fi # hacky hack - I get weird and unexplainable errors here sometimes.
    sudo systemctl daemon-reload >> $LOG_FILE 2>&1
    fail_if_error $? "$LOG_FILE" -6

    echo " - Checking Systemd file"
    if [[ `sudo systemctl status $CONTAINER | grep 'could not be found'` != "" ]]; then
        echo "$CONTAINER systemd file installation failed"
        exit 5
    fi
    sudo systemctl status $CONTAINER >> $LOG_FILE 2>&1
    if [[ $? != 0 && $? != 3 ]]; then
        echo "$CONTAINER systemd file doesn't work as expected"
        exit 6
    fi

    echo " - Testing systemd startup - starting $CONTAINER"
    sudo systemctl start $CONTAINER >> $LOG_FILE 2>&1
    fail_if_error $? "$LOG_FILE" -7

    echo " - Testing systemd startup - Checking startup"
    if [[ -z "$NO_SLEEP" ]]; then sleep 12; fi
    sudo systemctl status $CONTAINER >> $LOG_FILE 2>&1
    fail_if_error $? "$LOG_FILE" -8

    echo " - Testing systemd startup - Make sure service is really running"
    if [[ `systemctl show -p SubState $CONTAINER | grep exited` != "" ]]; then
        echo "$CONTAINER service is actually not really running"
        exit 7
    fi

    #echo " - Testing systemd startup - stopping $CONTAINER"
    #sudo systemctl stop $CONTAINER >> $LOG_FILE 2>&1
    #fail_if_error $? "$LOG_FILE" -9

    echo " - Enabling $CONTAINER on startup"
    sudo systemctl enable $CONTAINER >> $LOG_FILE 2>&1
    fail_if_error $? "$LOG_FILE" -10

    #echo " - Testing systemd startup - starting $CONTAINER (again)"
    #sudo systemctl start $CONTAINER >> $LOG_FILE 2>&1
    #fail_if_error $? "$LOG_FILE" -11
}

# Commit the container in the docker image and remove container
# Arguments:
# - $1 the name of the container
# - $2 the log file to dump command output to
function commit_container() {

    if [[ $1 == "" ]]; then
        echo "Container needs to be passed in argument"
        exit 2
    fi
    export CONTAINER=$1

    if [[ $2 == "" ]]; then
        echo "Log file path needs to be passed in argument"
        exit 3
    fi
    export LOG_FILE=$2

    # Exit the container and commit the changes
    # Now that we've modified the container we have to commit the changes.
    echo " - Commiting the changes to the local template"
    docker commit $CONTAINER eskimo:$CONTAINER  >> $LOG_FILE 2>&1
    fail_if_error $? "$LOG_FILE" -3

    # Stop setup container and and delete it
    docker stop $CONTAINER  >> $LOG_FILE 2>&1
    fail_if_error $? "$LOG_FILE" -4

    docker container rm $CONTAINER >> $LOG_FILE 2>&1
    fail_if_error $? "$LOG_FILE" -5

}

# Commit a container from its docker image and start it
# Arguments:
# - $1 the name of the container
# - $2 the log file to dump command output to
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
    if [[ `docker images -q eskimo:$IMAGE"_template" 2>/dev/null` != "" ]]; then
        docker image rm eskimo:$IMAGE"_template" >> $LOG_FILE 2>&1
        fail_if_error $? "$LOG_FILE" 5
    fi

    echo " - Importing latest docker template for $IMAGE"
    gunzip -c ${SCRIPT_DIR}/docker_template_$IMAGE.tar.gz | docker load >> $LOG_FILE 2>&1
    if [[ $? != 0 ]]; then
        # dunno why but docker load is randomly failing from times to times
        echo "   + Second attempt"
        gunzip -c ${SCRIPT_DIR}/docker_template_$IMAGE.tar.gz | docker load >> $LOG_FILE 2>&1
        fail_if_error $? "$LOG_FILE" 6
    fi

    echo " - Killing any previous containers"
    sudo systemctl stop $CONTAINER > /dev/null 2>&1
    if [[ `docker ps -a -q -f name=$CONTAINER` != "" ]]; then
        docker stop $CONTAINER > /dev/null 2>&1
        docker container rm $CONTAINER > /dev/null 2>&1
    fi

    # build
    echo " - Building docker container"
    docker build --iidfile id_file --tag eskimo:$CONTAINER . >> $LOG_FILE 2>&1
    fail_if_error $? "$LOG_FILE" 7
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

# self explained
function fail_if_error(){
    if [[ $1 != 0 ]]; then
        echo " -> failed \!\!"
        cat $2
        exit $3
    fi
}

# extract IP address
function get_ip_address(){
    export IP_ADDRESS="`/sbin/ifconfig  | grep inet | grep broadcast | cut -d ' ' -f 10`"
}

function preinstall_unmount_gluster_share () {
    if [[ `grep $1 /etc/mtab` != "" ]]; then
        echo " - preinstallation : need to unmount gluster share $1 before proceeding with installation"
        i=0
        while [[ `grep $1 /etc/mtab` != "" ]]; do
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