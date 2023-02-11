#!/bin/bash

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


# Take a lock with flock on the file identified as argument.
# Using this function, the lock is not released automatically and it needs to be released with 'release_lock'.
# The lock handle to pass back to 'release_lock' is made available with the ENV variable 'LAST_LOCK_HANDLE' after
# calling this function
# Arguments are:
# - $1 : the lock identifier
# - $2 : the folder where to create the lock
# - $3 : A flag which cause the lock taking to be non-blocking if and only if it is set to the value 'nonblock'
# - return : sets a global environment variable 'LAST_LOCK_HANDLE' with lock handle to be used to release lock.
take_lock() {

    if [[ $1 == "" ]]; then
        echo "Expecting Unique lock identifier as argument"
        return 1
    fi
    local LOCK_NAME=$1

    if [[ $2 == "" ]]; then
        local LOCK_FOLDER=/var/lib/eskimo/locks/
        sudo mkdir -p $LOCK_FOLDER
    else
        local LOCK_FOLDER=$2
        if [[ ! -d $LOCK_FOLDER ]]; then
            echo "Folder $LOCK_FOLDER doesn't exist"
            return 2
        fi
    fi

    if [[ $3 == "nonblock" ]]; then
        local NON_BLOCK="true"
    fi

    local ESKIMO_LOCK_HANDLE=$(shuf -i 600-1023 -n 1)
    local ESKIMO_LOCK_FILE="$LOCK_FOLDER/""$LOCK_NAME""_.lock"

    eval "exec $ESKIMO_LOCK_HANDLE>$ESKIMO_LOCK_FILE"
    local result=$?
    if [[ $result != 0 ]]; then
        echo "Couldn't take handle on lock file"
        return 3
    fi

    if [[ "$NON_BLOCK" == "true" ]]; then
        flock -n $ESKIMO_LOCK_HANDLE
        result=$?
        if [[ $result != 0 ]]; then
            echo "Couldn't flock file handle (immediate / non-block) - $1 $2 $3"
            return 4
        fi
    else
        flock -w 300 $ESKIMO_LOCK_HANDLE
        result=$?
        if [[ $result != 0 ]]; then
            echo "Couldn't flock file handle after 300 seconds waiting - $1 $2 $3"
            return 4
        fi
    fi

    export LAST_LOCK_HANDLE="$ESKIMO_LOCK_HANDLE:$ESKIMO_LOCK_FILE"
    #echo "New Lock handle : $LAST_LOCK_HANDLE"
    return 0
}

# Release the lock identified with the handle passed as argument.
# Arguments are:
# - $1 : the lock handle identifying the lock to be released
release_lock() {

    if [[ $1 == "" ]]; then
        echo "Expecting lock handle representations as 'number:lock_file_path' in argument"
        exit 1
    fi
    local ESKIMO_HANDLE_REPR=$1

    local ESKIMO_LOCK_HANDLE=$(echo "$ESKIMO_HANDLE_REPR" | sed s/'\([^:]*\):\(.*\)'/'\1'/g)
    local ESKIMO_LOCK_FILE=$(echo "$ESKIMO_HANDLE_REPR" | sed s/'\([^:]*\):\(.*\)'/'\2'/g)

    if [[ "$ESKIMO_LOCK_HANDLE" == "" || "$ESKIMO_LOCK_HANDLE" == "$ESKIMO_HANDLE_REPR" ]]; then
        echo "Failed to parse HANDLE in lock handle representations $ESKIMO_HANDLE_REPR"
        return 2
    fi

    if [[ "$ESKIMO_LOCK_FILE" == "" || "$ESKIMO_LOCK_FILE" == "$ESKIMO_HANDLE_REPR" ]]; then
        echo "Failed to parse FILE in lock handle representations $ESKIMO_HANDLE_REPR"
        return 3
    fi

    flock -u $ESKIMO_LOCK_HANDLE
    local result=$?
    if [[ $result != 0 ]]; then
        echo "Couldn't UN-flock file handle"
        return 4
    fi
}

__release_global_lock() {

    if [[ "$" == "$1" ]]; then
        echo "No passed global lock"
        return 1
    fi
    local GLOBAL_LOCK=$1

    release_lock $GLOBAL_LOCK
}

# Take a lock with flock on the file identified as argument.
# Using this function, the lock is released automatically upon shell exit and a call to 'release_lock' doesn't need to
# be done
# Arguments are:
# - $1 : the lock identifier
# - $2 : the folder where to create the lock
# - $3 : A flag which cause the lock taking to be non-blocking if and only if it is set to the value 'nonblock'
take_global_lock() {

    if [[ "$GLOBAL_LOCK" != "" ]]; then
        echo "Already has a global lock $GLOBAL_LOCK"
        return 1
    fi

    take_lock $1 $2 $3
    local result=$?
    if [[ $result != 0 ]]; then
        echo "Couldn't flock global file handle - $1 $2 $3"
        return 4
    fi

    export GLOBAL_LOCK=$LAST_LOCK_HANDLE

    trap "__release_global_lock $GLOBAL_LOCK" 15
    trap "__release_global_lock $GLOBAL_LOCK" EXIT
    trap "__release_global_lock $GLOBAL_LOCK" ERR
}


# Get the local cluster domain names (space separated)
get_kube_domain_names() {
    if [[ ":$PATH:" != *":/usr/local/bin:"* ]]; then
        PATH=$PATH:/usr/local/bin
    fi
    local DOMAIN_NAMES=" "

    # ensuring access to kube
    kubectl get cm coredns -n kube-system > /dev/null 2>&1
    if [[ $? != 0 ]]; then
        echo "Access to kube failed. Do you have proper kube credentials ?"
        return 1
    fi

    for i in $(kubectl get cm coredns -n kube-system -o jsonpath="{.data.Corefile}" | grep ".local "); do
        if [[ "$i" != "{" ]]; then
            DOMAIN_NAMES="$i $DOMAIN_NAMES "
        fi
    done
    echo $DOMAIN_NAMES
}

# Get the cluster defined services (space separated)
get_kube_services() {
    if [[ ":$PATH:" != *":/usr/local/bin:"* ]]; then
        PATH=$PATH:/usr/local/bin
    fi
    kubectl get services -A -o jsonpath="{range .items[*]}{@.metadata.name}{'.'}{@.metadata.namespace}{' '}" | sed s/' \. '//g
    if [[ $? != 0 ]]; then
        echo "Failed to list kube services with kubectl"
        return 1
    fi
}

__get_kube_service_IP() {

    if [[ ":$PATH:" != *":/usr/local/bin:"* ]]; then
        PATH=$PATH:/usr/local/bin
    fi

    if [[ $(echo $1 | grep '.') == "" ]]; then
        echo "Expecting service in format NAME.NAMESPACE"
        return 1
    fi

    local SERVICE=$(echo $1 | cut -d '.' -f 1)
    local NAMESPACE=$(echo $1 | cut -d '.' -f 2)

    kubectl get endpoints $SERVICE -n $NAMESPACE -o jsonpath="{range .subsets[*].addresses[*]}{@.hostname}{'/'}{@.ip}{' '}" | sed s/' \/ '//g
}

__dump_service_ip_dns() {

    if [[ $(echo $1 | grep ':') == "" ]]; then
        echo "Expecting service in format NAME:IP"
        return 1
    fi
    local FULL_SERVICE=$1

    if [[ "$2" == "etc_hosts" ]]; then
        export gks_format=$2
    else
        unset gks_format
    fi

    local D_SERVICE=$(echo $FULL_SERVICE | cut -d ':' -f 1)
    local D_NAMESPACE=$(echo $FULL_SERVICE | cut -d ':' -f 2)
    local D_ADRESS=$(echo $FULL_SERVICE | cut -d ':' -f 3)


    if [[ "$D_SERVICE" == "" ]]; then
        echo "Couldn't parse service in __dump_service_ip_dns"
        return 2
    fi

    if [[ "$D_NAMESPACE" == "" ]]; then
        echo "Couldn't parse namespace in __dump_service_ip_dns"
        return 3
    fi

    if [[ "$D_ADRESS" == "" ]]; then
        echo "Couldn't parse ADDRESS in __dump_service_ip_dns"
        return 4
    fi

    if [[ "$ESKIMO_DOMAINS" == "" ]]; then
        local ESKIMO_DOMAINS
        ESKIMO_DOMAINS=$(get_kube_domain_names)
        if [[ $? != 0 ]]; then
            echo "Fail to access kube domain"
            return 5
        fi
    fi

    for eskimo_domain in $(echo $ESKIMO_DOMAINS); do
        if [[ $(echo $eskimo_domain | grep arpa) == "" ]]; then
            if [[ "$gks_format" == "etc_hosts" ]]; then
                echo $D_ADRESS $D_SERVICE.$D_NAMESPACE.svc.$eskimo_domain
            else
                echo $D_SERVICE.$D_NAMESPACE.svc.$eskimo_domain $D_ADRESS
            fi
        fi
    done
}

# Get a list of all DNS entries required to reach kubernetes services
get_kube_services_IPs() {

    if [[ "$1" == "etc_hosts" ]]; then
        local gks_format=$1
    else
        unset gks_format
    fi

    ESKIMO_DOMAINS=$(get_kube_domain_names)
    if [[ $? != 0 ]]; then
        echo "Fail to get kube domains list"
        return 5
    fi
    # Need to export that one to make it available to further calls
    export ESKIMO_DOMAINS

    local KUBE_SERVICES
    KUBE_SERVICES=$(get_kube_services)
    if [[ $? != 0 ]]; then
        echo "Failed to list kube services with kubectl"
        return 1
    fi
    for service in $KUBE_SERVICES; do
        if [[ ${service/ *$//} != "" ]]; then

            local SERVICE=$(echo $service | cut -d '.' -f 1)
            local NAMESPACE=$(echo $service | cut -d '.' -f 2)

            type=single
            for endpoint in $(__get_kube_service_IP $service); do

                HOST=$(echo $endpoint | cut -d '/' -f 1)
                IP=$(echo $endpoint | cut -d '/' -f 2)

                if [[ ${HOST/ *$//} == "" ]]; then
                    __dump_service_ip_dns $SERVICE:$NAMESPACE:$IP $gks_format
                else
                    type=many
                    __dump_service_ip_dns $HOST.$SERVICE:$NAMESPACE:$IP $gks_format
                fi
            done

            if [[ "$type" == "many" ]]; then
                __dump_service_ip_dns $SERVICE:$NAMESPACE:$IP $gks_format
            fi
        fi
    done
}

# Use ^get_kube_services_IP`to create a list of DNS entries for kube services in a temporary flat file
# and echo file path to console
create_kube_services_hosts_file() {
    add_hosts_file=/tmp/$(uuidgen -r)_hosts
    get_kube_services_IPs etc_hosts >> $add_hosts_file
    if [[ $? != 0 ]]; then
        echo "Failed to create additional host file"
        return 1
    fi
    echo $add_hosts_file
}

parse_cli_docker_volume_mounts() {

    if [[ "$1" == "" ]]; then
        echo "Expected coma separated searched args as first argument"
        return 1
    fi
    local SEARCHES=$1
    shift

    if [[ "$1" == "" || ! ("$1" == "single" || "$1" == "multiple") ]]; then
        echo "Expected MODE in [single, multiple] as argument"
        return 1
    fi
    local MODE=$1
    shift

    if [[ "$MODE" == "single" ]]; then
        if [[ "$1" == "" ]]; then
            echo "Expected (list of) argument(s) to process as further parameters"
            return 2
        fi
    else
        if [[ "$1" == "" || ${#1} != 1 ]]; then
            echo "Expected separator as first argument"
            return 3
        fi

        local SEPARATOR=$1
        shift

        if [[ "$1" == "" ]]; then
            echo "Expected (list of) argument(s) to process as further parameters"
            return 2
        fi
    fi

    if [[ "$DOCKER_VOLUMES_ARGS" == "" ]]; then
        export DOCKER_VOLUMES_ARGS=""
    fi

    local DIR
    local PROCESS_NEXT="0"
    for argument in "$@"; do
        if [[ $PROCESS_NEXT == "1" ]]; then

            if [[ "$MODE" == "single" ]]; then

                if [[ -d $argument ]]; then
                    DIR=$argument
                else
                    DIR=$(dirname $argument)
                fi

                # only if dir exists in local
                if [[ -d $DIR || ! -z $TEST_MODE ]]; then
                    if [[ $(echo $DOCKER_VOLUMES_ARGS | grep "$DIR:$DIR:slave") == "" ]]; then
                        export DOCKER_VOLUMES_ARGS=" -v $DIR:$DIR:slave $DOCKER_VOLUMES_ARGS"
                    fi
                fi
            else

                # --files is a comma-separated list of files
                IFS="$SEPARATOR" read -ra files <<< $argument
                for i in "${files[@]}"; do
                    if [[ -d $i ]]; then
                        DIR=$i
                    else
                        DIR=$(dirname $i)
                    fi
                    if [[ -d $DIR || ! -z $TEST_MODE ]]; then
                        if [[ $(echo $DOCKER_VOLUMES_ARGS | grep "$DIR:$DIR:slave") == "" ]]; then
                            export DOCKER_VOLUMES_ARGS=" -v $DIR:$DIR:slave $DOCKER_VOLUMES_ARGS"
                        fi
                    fi
                done

            fi
        fi

        PROCESS_NEXT="0"
        for search in $(echo "$SEARCHES" | tr "," " "); do
            if [[ $argument == "$search" ]]; then
                PROCESS_NEXT="1"
                break
            fi
        done
    done

}