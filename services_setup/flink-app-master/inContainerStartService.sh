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

set -e

echo " - Injecting topology"
. /usr/local/sbin/inContainerInjectTopology.sh

echo " - Inject settings"
/usr/local/sbin/settingsInjector.sh flink-app-master

echo " - Basic mesos configuration for mesos Scheduler (set env vars)"
# point to your libmesos.so if you use Mesos
export MESOS_NATIVE_JAVA_LIBRARY=/usr/local/lib/mesos/lib/libmesos.so
# Set external IP for Mesos Scheduler used by Flink / Marathon / etc. to reach mesos (required for mesos callback to succeed)
export LIBPROCESS_ADVERTISE_IP=$SELF_IP_ADDRESS


echo " - Starting service"
/usr/local/lib/flink/bin/mesos-appmaster.sh


# This hack was required with FLink 1.9 when a killed task manager made is to that flink was never anymore able to
# recover some resources from mesos !
#echo " - TEMPORARY HACK : monitoring task kill problem (FLINK-14074)"
#/usr/local/lib/flink/bin/mesos-appmaster.sh &
#export APP_MASTER_PROC_ID=$!
#
#while [[ 1 ]]; do
#
#    sleep 30
#
#    # List frameworks
#    for i in `curl -XGET http://$MASTER_MESOS_MASTER_1:5050/master/frameworks 2>/dev/null | jq -r  ".frameworks | .[] | select (.name==\"Flink\") | .id"`; do
#
#        # Find active frameworks
#        ACTIVE=`curl -XGET http://$MASTER_MESOS_MASTER_1:5050/master/frameworks?framework_id=$i 2>/dev/null | jq -r " .frameworks | .[] | .active"`
#        if [[ $ACTIVE == "true" ]] ; then
#
#            echo " - Found active framework : $i"
#
#            # List framework tasks states
#            KILLED_FOUND="0"
#            for j in `curl -XGET http://$MASTER_MESOS_MASTER_1:5050/master/tasks?framework_id=$i 2>/dev/null | jq -r " .tasks | .[] |  .state"`; do
#                if [[ $j == "TASK_KILLED" ]]; then
#                    KILLED_FOUND="1"
#                fi
#            done
#
#            # If killed task is found, restart FLink app master
#            if [[ $KILLED_FOUND == "1" ]]; then
#                echo " - Killed task is found in framework $i, restarting App master"
#
#                echo " - Killing Mesos framework"
#                curl -XPOST http://$MASTER_MESOS_MASTER_1:5050/master/teardown -d "frameworkId=$i" 2>/dev/null
#
#                echo " - Flink App master"
#                kill -15 $APP_MASTER_PROC_ID
#                sleep 5
#
#                echo " - RE-Starting service"
#                /usr/local/lib/flink/bin/mesos-appmaster.sh &
#                export APP_MASTER_PROC_ID=$!
#           fi
#        fi
#    done
#done

