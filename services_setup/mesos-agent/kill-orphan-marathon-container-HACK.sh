#!/bin/bash

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


# 1. Search for "Running docker stop on container" in mesos-slave log
# e.g. Running docker stop on container b6c5020b-4694-4b93-bcbb-6825f6160bd1


if [[ ! -f /var/log/mesos/mesos-slave.INFO ]]; then
    echo `date +"%Y-%m-%d %H:%M:%S"`" - /var/log/mesos/mesos-slave.INFO not found. skipping." >> /var/log/mesos/mesos-marathon-container-killer-hack.log
    exit 0
fi

# 2. Searching for all candidate containers, those that the mesos-agent wants to kill
echo `date +"%Y-%m-%d %H:%M:%S"`" - Searching for candidates (mesos attempting to stop containers)" \
            >> /var/log/mesos/mesos-marathon-container-killer-hack.log

year=`date +%Y`
candidate_containers=`tail -n 10000 /var/log/mesos/mesos-slave.INFO | \
        grep "Running docker stop on container" | \
        sed -E 's/[I,W]([0-9]{2})([0-9]{2}) ([0-9\:\.]+).*container (.*)/2020-\1-\2 \3\/\4/'`
# this parses lines such as
#    I0421 14:43:54.288918   491 docker.cpp:2363] Running docker stop on container 6f63a3cd-9705-47f3-bf42-5d71fd3a8634
# to
#    0421 14:43:54.288918-6f63a3cd-9705-47f3-bf42-5d71fd3a8634

IFS=$'\n'
for candidate in $candidate_containers;  do

    candidate_date_raw=`echo $candidate | cut -d '/' -f 1`
    candidate_container_id=`echo $candidate | cut -d '/' -f 2`

    #echo $date_raw   # --> 2020-04-21 14:43:54.288918
    #echo $candidate_container_id   # --> 6f63a3cd-9705-47f3-bf42-5d71fd3a8634

    candidate_date_s=`date -d $candidate_date_raw +%s`
    #echo $candidate_date_s   # --> 1587480234

    echo `date +"%Y-%m-%d %H:%M:%S"`" - Found candidate for verification: date=$candidate_date_raw - date_in_seconds=$candidate_date_s - mesos_id=$candidate_container_id" \
            >> /var/log/mesos/mesos-marathon-container-killer-hack.log

    # 3. Now Search for corresponding container execution
    docker_container_id=`/usr/bin/docker ps -q -f name=mesos-$candidate_container_id 2>> /var/log/mesos/mesos-marathon-container-killer-hack.log`
    if [[ $? != 0 ]]; then
        echo `date +"%Y-%m-%d %H:%M:%S"`"   + Failed to search for corresponding docker container!" >> /var/log/mesos/mesos-marathon-container-killer-hack.log
        continue
    fi

    # If a corresponding container is found
    if [[ $docker_container_id != "" ]]; then

        echo `date +"%Y-%m-%d %H:%M:%S"`"   + Found matching docker container: docker_id=$docker_container_id" \
                >> /var/log/mesos/mesos-marathon-container-killer-hack.log

        # now checking the container date
        started_date_raw=`/usr/bin/docker inspect $docker_container_id | grep "StartedAt" | sed -E 's/.*": "(.*)",/\1/'`
        started_date_s=`date -d "$started_date_raw" +%s`
        echo `date +"%Y-%m-%d %H:%M:%S"`"   + docker container dates : started_date=$started_date_raw - started_date_in_secs=$started_date_s" \
                >> /var/log/mesos/mesos-marathon-container-killer-hack.log

        # taking 5 seconds reserve to avoid killing a container that has actually just been started
        let date_reserve_s=$started_date_s+5
        echo `date +"%Y-%m-%d %H:%M:%S"`"   + docker container date reserve : $date_reserve_s" \

        if [[ $date_reserve_s < $candidate_date_s ]]; then

            echo `date +"%Y-%m-%d %H:%M:%S"`"   + container_date_reserve=$date_reserve_s is before candidate_date=$candidate_date_s, killing container $docker_container_id" \
                    >> /var/log/mesos/mesos-marathon-container-killer-hack.log

            /usr/bin/docker stop $docker_container_id 2>> /var/log/mesos/mesos-marathon-container-killer-hack.log
            if [[ $? != 0 ]]; then
                echo `date +"%Y-%m-%d %H:%M:%S"`"   + Failed to stop container !" >> /var/log/mesos/mesos-marathon-container-killer-hack.log
                continue
            fi

            /usr/bin/docker rm $docker_container_id 2>> /var/log/mesos/mesos-marathon-container-killer-hack.log
            if [[ $? != 0 ]]; then
                echo `date +"%Y-%m-%d %H:%M:%S"`"   + Failed to remove container !" >> /var/log/mesos/mesos-marathon-container-killer-hack.log
                continue
            fi

        fi

    fi

done

# 2. Searching for all candidate containers, those that the mesos-agent wants to kill
echo `date +"%Y-%m-%d %H:%M:%S"`" - Finished cycle." \
            >> /var/log/mesos/mesos-marathon-container-killer-hack.log
