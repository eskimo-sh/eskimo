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
        exit -2
    fi

    if [[ $2 == "" ]]; then
        echo "Log file path needs to be passed in argument"
        exit -3
    fi

    echo " - Copying Topology Injection Script"
    docker cp $SCRIPT_DIR/inContainerInjectTopology.sh $1:/usr/local/sbin/inContainerInjectTopology.sh >> $2 2>&1
    fail_if_error $? $2 -20

    docker exec --user root $1 bash -c "chmod 755 /usr/local/sbin/inContainerInjectTopology.sh" >> $2 2>&1
    fail_if_error $? $2 -21

    echo " - Copying Service Start Script"
    docker cp $SCRIPT_DIR/inContainerStartService.sh $1:/usr/local/sbin/inContainerStartService.sh >> $2 2>&1
    fail_if_error $? $2 -22

    docker exec --user root $1 bash -c "chmod 755 /usr/local/sbin/inContainerStartService.sh" >> $2 2>&1
    fail_if_error $? $2 -24

    echo " - Copying settingsInjector.sh Script"
    docker cp $SCRIPT_DIR/settingsInjector.sh $1:/usr/local/sbin/settingsInjector.sh >> $2 2>&1
    fail_if_error $? $2 -23

    docker exec --user root $1 bash -c "chmod 755 /usr/local/sbin/settingsInjector.sh" >> $2 2>&1
    fail_if_error $? $2 -24
}

# This is used to load the topology definition file
function loadTopology() {

    if [[ ! -f /etc/eskimo_topology.sh ]]; then
        echo "  - ERROR : no topology file defined !"
        exit -123
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
        exit -2
    fi

    if [[ $2 == "" ]]; then
        echo "Log file path needs to be passed in argument"
        exit -3
    fi

    echo " - Deploying $1 Service in docker registry for marathon"
    docker tag eskimo:$1 marathon.registry:5000/$1 >> $2 2>&1
    if [[ $? != 0 ]]; then
        echo "   + Could not re-tag marathon container image"
        exit -30
    fi

    docker push marathon.registry:5000/$1 >> $2 2>&1
    if [[ $? != 0 ]]; then
        echo "Image push in docker registry failed !"
        exit -22
    fi

    echo " - removing local image"
    docker image rm eskimo:$1  >> $2 2>&1
    if [[ $? != 0 ]]; then
        echo "local image removal failed !"
        exit -32
    fi


    echo " - Removing any previously deployed $1 service from marathon"
    curl -XDELETE http://$MASTER_MARATHON_1:28080/v2/apps/$1 >> $2_marathon_deploy 2>&1
    if [[ $? != 0 ]]; then
        echo "   + Could not reach marathon"
        exit -23
    fi
    cat $2_marathon_deploy
    if [[ `cat $2_marathon_deploy` != "" && `grep "does not exist" $2_marathon_deploy` == "" ]]; then
        echo "   + Previous instance removed"
        sleep 5
    fi


    echo " - Deploying $1 service in marathon"
    # 10 attempts with 5 seconds sleep each
    for i in $(seq 1 10); do
        echo "   + Attempt $i"
        curl  -X POST -H 'Content-Type: application/json' \
            -d "@$1.marathon.json" \
            http://$MASTER_MARATHON_1:28080/v2/apps 2>&1 | tee "$2"_deploy >> $2
        if [[ $? != 0 ]]; then
            echo "   + Could not deploy $1 application in marathon"
            cat $2
            exit -24
        fi
        if [[ `grep "App is locked by one or more deployments" "$2"_deploy` == "" ]]; then
            break
        fi
        if [[ "$i" == "10" ]]; then
            echo "   + Failed 10 times !!"
            exit -25
        fi
        sleep 5
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
        exit -2
    fi

    if [[ $2 == "" ]]; then
        echo "Log file path needs to be passed in argument"
        exit -3
    fi

    if [[ -d /lib/systemd/system/ ]]; then
        export systemd_units_dir=/lib/systemd/system/
    elif [[ -d /usr/lib/systemd/system/ ]]; then
        export systemd_units_dir=/usr/lib/systemd/system/
    else
        echo "Couldn't find systemd unit files directory"
        exit -10
    fi

    echo " - Copying $1 systemd file"
    sudo cp $SCRIPT_DIR/$1.service $systemd_units_dir
    sudo chmod 644 $systemd_units_dir/$1.service

    echo " - Reloading systemd config"
    sleep 1 # hacky hack - I get weird and unexplainable errors here sometimes.
    sudo systemctl daemon-reload >> $2 2>&1
    fail_if_error $? "$2" -6

    echo " - Checking Systemd file"
    if [[ `sudo systemctl status $1 | grep 'could not be found'` != "" ]]; then
        echo "$1 systemd file installation failed"
        exit -12
    fi
    sudo systemctl status $1 >> $2 2>&1
    if [[ $? != 0 && $? != 3 ]]; then
        echo "$1 systemd file doesn't work as expected"
        exit -12
    fi

    echo " - Testing systemd startup - starting $1"
    sudo systemctl start $1 >> $2 2>&1
    fail_if_error $? "$2" -7

    echo " - Testing systemd startup - Checking startup"
    sleep 12
    sudo systemctl status $1 >> $2 2>&1
    fail_if_error $? "$2" -8

    echo " - Testing systemd startup - Make sure service is really running"
    if [[ `systemctl show -p SubState $1 | grep exited` != "" ]]; then
        echo "$1 service is actually not really running"
        exit -13
    fi

    #echo " - Testing systemd startup - stopping $1"
    #sudo systemctl stop $1 >> $2 2>&1
    #fail_if_error $? "$2" -9

    echo " - Enabling $1 on startup"
    sudo systemctl enable $1 >> $2 2>&1
    fail_if_error $? "$2" -10

    #echo " - Testing systemd startup - starting $1 (again)"
    #sudo systemctl start $1 >> $2 2>&1
    #fail_if_error $? "$2" -11
}

# Commit the container in the docker image and remove container
# Arguments:
# - $1 the name of the container
# - $2 the log file to dump command output to
function commit_container() {

    if [[ $1 == "" ]]; then
        echo "Container needs to be passed in argument"
        exit -2
    fi

    if [[ $2 == "" ]]; then
        echo "Log file path needs to be passed in argument"
        exit -3
    fi

    # Exit the container and commit the changes
    # Now that we've modified the container we have to commit the changes.
    echo " - Commiting the changes to the local template"
    docker commit $1 eskimo:$1  >> $2 2>&1
    fail_if_error $? "$2" -3

    # Stop setup container and and delete it
    docker stop $1  >> $2 2>&1
    fail_if_error $? "$2" -4

    docker container rm $1 >> $2 2>&1
    fail_if_error $? "$2" -5

}

# Commit a container from its docker image and start it
# Arguments:
# - $1 the name of the container
# - $2 the log file to dump command output to
function build_container() {

    if [[ $1 == "" ]]; then
        echo "Container needs to be passed in argument"
        exit -2
    fi

    if [[ $2 == "" ]]; then
        echo "Image (template) needs to be passed in argument"
        exit -1
    fi

    if [[ $3 == "" ]]; then
        echo "Log file path needs to be passed in argument"
        exit -3
    fi

    echo " - Deleting previous docker template for $2 if exist"
    if [[ `docker images -q eskimo:$2_template 2>/dev/null` != "" ]]; then
        docker image rm eskimo:$2_template >> $3 2>&1
        fail_if_error $? "$3" -1
    fi

    echo " - Importing latest docker template for $2"
    gunzip -c ${SCRIPT_DIR}/docker_template_$2.tar.gz | docker load >> $3 2>&1
    fail_if_error $? "$3" -1

    echo " - Killing any previous containers"
    sudo systemctl stop $1 > /dev/null 2>&1
    if [[ `docker ps -a -q -f name=$1` != "" ]]; then
        docker stop $1 > /dev/null 2>&1
        docker container rm $1 > /dev/null 2>&1
    fi

    # build
    echo " - Building docker container"
    docker build --iidfile id_file --tag eskimo:$1 . >> $3 2>&1
    fail_if_error $? "$3" -1
}

# This is used to create a command wrapper around a binary command in order to
# - change the current directory to the command software installation location
# - inject topology
# Arguments:
# - $1 the source binary command to be wrapped
# - $2 the wrapper location
function create_binary_wrapper(){
    if [[ $1 == "" || $2 == "" ]]; then
        echo "source and target have to be passed as argument of the create_kafka_wrapper function"
    else
        touch $2
        chmod 777 $2
        echo -e '#!/bin/bash' > $2
        echo -e "" >> $2
        echo -e "if [[ -f /usr/local/sbin/inContainerInjectTopology.sh ]]; then" >> $2
        echo -e "    # Injecting topoloy" >> $2
        echo -e "    . /usr/local/sbin/inContainerInjectTopology.sh" >> $2
        echo -e "fi" >> $2
        echo -e "" >> $2
        echo -e "__tmp_saved_dir=`pwd`" >> $2
        echo -e "function __tmp_returned_to_saved_dir() {" >> $2
        echo -e '     cd $__tmp_saved_dir' >> $2
        echo -e "}" >> $2
        echo -e "trap __tmp_returned_to_saved_dir 15" >> $2
        echo -e "trap __tmp_returned_to_saved_dir EXIT" >> $2
        echo -e "" >> $2
        echo -e "$1 \"\$@\"" >> $2
        echo -e "" >> $2
        echo -e "__tmp_returned_to_saved_dir" >> $2
        chmod 755 $2
    fi
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
            sleep 1
            i=$((i+1))
            if [[ $1 == 5 ]]; then
                echo " - Failed to unmount $1 after 5 attempts"
                exit -125
            fi
        done
    fi
}