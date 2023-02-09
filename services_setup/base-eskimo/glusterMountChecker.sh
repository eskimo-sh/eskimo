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

# This script checks the gluster mount defined in /etc/fstab and attenpts to fix those that report issues

# Not using eskimo-utils - locking features since this can run in container as well
export VOLUME_MANAGEMENT_LOCK_FILE=/var/lib/gluster/volume_management_lock_check.lock

function delete_gluster_check_lock_file() {
     rm -Rf $VOLUME_MANAGEMENT_LOCK_FILE
}

# From here we will be messing with gluster and hence we need to take a lock
if [[ -f $VOLUME_MANAGEMENT_LOCK_FILE ]] ; then
    echo "$(date +'%Y-%m-%d %H:%M:%S') - glusterMountChecker.sh is in execution already. Skipping ..."
    exit 0
fi

trap delete_gluster_check_lock_file 15
trap delete_gluster_check_lock_file EXIT
trap delete_gluster_check_lock_file ERR

mkdir -p /var/lib/gluster/
touch $VOLUME_MANAGEMENT_LOCK_FILE


# when running on host, checking that the gluster container is actually running before doing anything
if command -v docker &> /dev/null ; then

    echo "$(date +'%Y-%m-%d %H:%M:%S') - Checking whether gluster container is running" \
        >> /var/log/gluster/gluster-mount-checker.log

    if [[ $(docker ps --filter "name=gluster" | grep -v CREATED) == "" ]]; then
        echo "$(date +'%Y-%m-%d %H:%M:%S') - gluster container is NOT running. Skipping ..."  \
            >> /var/log/gluster/gluster-mount-checker.log
        exit 0
    fi
fi


for MOUNT_POINT in $(cat /etc/fstab | grep glusterfs | cut -d ' ' -f 2); do

    VOLUME=$(cat /etc/fstab | grep glusterfs | grep $MOUNT_POINT | cut -d ' ' -f 1 | cut -d '/' -f 2)
        
    echo "$(date +'%Y-%m-%d %H:%M:%S') - Handling $VOLUME"  \
        >> /var/log/gluster/gluster-mount-checker.log


    rm -Rf /tmp/gluster_mount_checker_error

    # check if working only if it is supposed to be mounted
    if [[ $(grep $MOUNT_POINT /etc/mtab | grep glusterfs) != "" ]]; then

        # give it a try
        ls -la $MOUNT_POINT >/dev/null 2>/tmp/gluster_mount_checker_error

        # unmount if it's not working
        if [[ $? != 0 ]]; then

            if [[ $(grep "Transport endpoint is not connected" /tmp/gluster_mount_checker_error) != "" \
                 || $(grep "Too many levels of symbolic links" /tmp/gluster_mount_checker_error) != "" \
                 || $(grep "No such device" /tmp/gluster_mount_checker_error) != "" ]]; then

                echo "$(date +'%Y-%m-%d %H:%M:%S') - There is an issue with $MOUNT_POINT. Unmounting" \
                    >> /var/log/gluster/gluster-mount-checker.log

                # 4 attempts
                for i in 1 2 3 4; do

                    echo "$(date +'%Y-%m-%d %H:%M:%S')   + Attempt $i" >> /var/log/gluster/gluster-mount-checker.log
                    /bin/umount $MOUNT_POINT  >> /var/log/gluster/gluster-mount-checker.log 2>&1

                    if [[ $? != 0 ]]; then

                        echo "$(date +'%Y-%m-%d %H:%M:%S')   + Unmount FAILED \!" >> /var/log/gluster/gluster-mount-checker.log

                    else

                        # give a little time
                        sleep 2

                        break
                    fi

                    # give a little time
                    sleep 2
                done
            fi
        fi
    fi

    # try to mount / remount
    if [[ $(grep $MOUNT_POINT /etc/mtab | grep glusterfs) == "" ]]; then

        echo "$(date +'%Y-%m-%d %H:%M:%S') - $MOUNT_POINT is not mounted, remounting" \
            >> /var/log/gluster/gluster-mount-checker.log

         # 4 attempts
        for i in 1 2 3 4; do

            echo "$(date +'%Y-%m-%d %H:%M:%S')   + Attempt $i" >> /var/log/gluster/gluster-mount-checker.log
            /bin/mount $MOUNT_POINT  >> /var/log/gluster/gluster-mount-checker.log 2>&1

            if [[ $? != 0 ]]; then
                echo "$(date +'%Y-%m-%d %H:%M:%S')   + Re-mount FAILED \!" >> /var/log/gluster/gluster-mount-checker.log

            else
                break;
            fi

            # give a little time
            sleep 2
        done

    fi

done

delete_gluster_check_lock_file