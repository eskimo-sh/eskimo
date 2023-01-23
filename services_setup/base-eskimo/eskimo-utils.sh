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
        exit 1
    fi
    export LOCK_NAME=$1

    if [[ $2 == "" ]]; then
        export LOCK_FOLDER=/var/lib/eskimo/locks/
        sudo mkdir -p $LOCK_FOLDER
    else
        export LOCK_FOLDER=$2
        if [[ ! -d $LOCK_FOLDER ]]; then
            echo "Folder $LOCK_FOLDER doesn't exist"
            exit 2
        fi
    fi

    if [[ $3 == "nonblock" ]]; then
        export NON_BLOCK="true"
    fi

    export ESKIMO_LOCK_HANDLE=$(shuf -i 600-1023 -n 1)
    export ESKIMO_LOCK_FILE="$LOCK_FOLDER/""$LOCK_NAME""_.lock"

    eval "exec $ESKIMO_LOCK_HANDLE>$ESKIMO_LOCK_FILE" || (echo "Couldn't take handle on lock file" && exit 3)

    if [[ "$NON_BLOCK" == "true" ]]; then
        flock -n $ESKIMO_LOCK_HANDLE || (echo "Couldn't flock file handle" && exit 4)
    else
        flock -w 300 $ESKIMO_LOCK_HANDLE || (echo "Couldn't flock file handle" && exit 4)
    fi

    export LAST_LOCK_HANDLE="$ESKIMO_LOCK_HANDLE:$ESKIMO_LOCK_FILE"
    echo "New Lock handle : $LAST_LOCK_HANDLE"
}

# Release the lock identified with the handle passed as argument.
# Arguments are:
# - $1 : the lock handle identifying the lock to be released
release_lock() {

    if [[ $1 == "" ]]; then
        echo "Expecting lock handle representations as 'number:lock_file_path' in argument"
        exit 1
    fi
    export ESKIMO_HANDLE_REPR=$1

    export ESKIMO_LOCK_HANDLE=$(echo "$ESKIMO_HANDLE_REPR" | sed s/'\([^:]*\):\(.*\)'/'\1'/g)
    export ESKIMO_LOCK_FILE=$(echo "$ESKIMO_HANDLE_REPR" | sed s/'\([^:]*\):\(.*\)'/'\2'/g)

    if [[ "$ESKIMO_LOCK_HANDLE" == "" || "$ESKIMO_LOCK_HANDLE" == "$ESKIMO_HANDLE_REPR" ]]; then
        echo "Failed to parse HANDLE in lock handle representations $ESKIMO_HANDLE_REPR"
        exit 2
    fi

    if [[ "$ESKIMO_LOCK_FILE" == "" || "$ESKIMO_LOCK_FILE" == "$ESKIMO_HANDLE_REPR" ]]; then
        echo "Failed to parse FILE in lock handle representations $ESKIMO_HANDLE_REPR"
        exit 3
    fi

    flock -u $ESKIMO_LOCK_HANDLE || (echo "Couldn't UN-flock file handle" && exit 4)
}

__release_global_lock() {

    if [[ "$GLOBAL_LOCK" == "" ]]; then
        echo "No defined global lock"
        exit 1
    fi

    release_lock $GLOBAL_LOCK

    unset GLOBAL_LOCK
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
        exit 1
    fi

    take_lock $1 $2 $3 || (echo "Couldn't flock file handle" && exit 4)

    export GLOBAL_LOCK=$LAST_LOCK_HANDLE

    trap __release_global_lock 15
    trap __release_global_lock EXIT
    trap __release_global_lock ERR
}


