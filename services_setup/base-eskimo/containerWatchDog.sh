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

# This script acts as a Watch Dog for docker containers when docker containers star background processes.
# When this happens, the docker container won't die unless the main process invoked by the run command - the one that
# keeps it waiting . stops.
# As a consequence, the background process have no chance to be restarted since the container won't ire if they crash
# unless the main process dies as well.
#
# Here comes the Watch Dog, it monitors a background process carefully and kills the main container process in case
# the background process dies.
# The Watch Dog takes both process IDs in argument
# - the first parameter is the PID is the background process to be monitored
# - the second parameter is
#   + either PID if the process to be killed in case the monitored background process vanishes
#   + or a command enabling to find that PID at runtime
# - A third argument is passed to log actions in the given log file
#
# A Watch Dog command should be invoked in background as well AFTER all the container process have been launched in
# background and BEFORE waiting on the main process.
# when using Watdh Dogs, even the main container process is started in background and "waited" (wait PID) in the end
# wait PID should be the last command in the startup script.

if [[ "$1" == "" ]]; then
    echo "Expected background process PID to be monitored as first argument"
    exit 1
fi

if [[ "$2" == "" ]]; then
    echo "Expected main container process PID (the one to be killed) as second argument"
    exit 2
fi

if [[ "$3" == "" ]]; then
    echo "Expected logfile path to log actions as third argument"
    exit 3
fi


export BACKGROUND_PID=$1
export MAIN_PID=$2
export LOG_FILE=$3

case $MAIN_PID in
    ''|*[!0-9]*)
        echo "$(date +'%Y-%m-%d %H:%M:%S') - Passed MAIN_PID is a command, need to use it to find PID" >> $LOG_FILE
        sleep 10 # need to give a chance to the target process to start
        MAIN_PID="$(eval $MAIN_PID)"
        case $MAIN_PID in
            ''|*[!0-9]*)
                echo "$(date +'%Y-%m-%d %H:%M:%S') - Could not resolve MAIN_PID to a process ID ($MAIN_PID). Killing $BACKGROUND_PID and crashing..." >> $LOG_FILE
                kill -15 $BACKGROUND_PID >> $LOG_FILE 2>&1
                exit 4
            ;;
            *)
                echo "$(date +'%Y-%m-%d %H:%M:%S') - Found MAIN_PID=$MAIN_PID" >> $LOG_FILE
            ;;
        esac
    ;;
    *)
        echo "$(date +'%Y-%m-%d %H:%M:%S') - Passed MAIN_PID is a process ID, good to go..." >> $LOG_FILE
    ;;
esac

# make sure this process never exits because of an error
set +e

while true; do

    sleep 10

    if [[ $(ps -o pid= -p $BACKGROUND_PID 2>>$LOG_FILE) == "" ]]; then

        echo "$(date +'%Y-%m-%d %H:%M:%S') - Process with PID $BACKGROUND_PID could not be found Killing $MAIN_PID" \
                >> $LOG_FILE

        kill -15 $MAIN_PID >> $LOG_FILE 2>&1

        break
    fi

    if [[ $(ps -o pid= -p $MAIN_PID 2>>$LOG_FILE) == "" ]]; then

        echo "$(date +'%Y-%m-%d %H:%M:%S') - Process with PID $MAIN_PID could not be found Killing $BACKGROUND_PID" \
                >> $LOG_FILE

        kill -15 $BACKGROUND_PID >> $LOG_FILE 2>&1

        break
    fi
done