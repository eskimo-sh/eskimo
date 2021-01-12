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

# This script checks the gluster mount defined in /etc/fstab and attenpts to fix those that report issues

for SHARE in `cat /etc/fstab  | grep glusterfs | cut -d ' ' -f 2`; do

    rm -Rf /tmp/gluster_mount_checker_error

    ls -la $SHARE >/dev/null 2>/tmp/gluster_mount_checker_error
    if [[ $? != 0 ]]; then

        if [[ `grep "Transport endpoint is not connected" /tmp/gluster_mount_checker_error` != "" \
             || `grep "Too many levels of symbolic links" /tmp/gluster_mount_checker_error` != "" \
             || `grep "No such device" /tmp/gluster_mount_checker_error` != "" ]]; then

            echo `date +"%Y-%m-%d %H:%M:%S"`" - There is an issue with $SHARE. Unmounting" \
                >> /var/log/gluster/gluster-mount-checker.log

            # 3 attempts
            for i in 1 2 3; do

                echo `date +"%Y-%m-%d %H:%M:%S"`"   + Attempt $i" >> /var/log/gluster/gluster-mount-checker.log
                /bin/umount $SHARE  >> /var/log/gluster/gluster-mount-checker.log 2>&1

                if [[ $? != 0 ]]; then

                    echo `date +"%Y-%m-%d %H:%M:%S"`"   + Unmount FAILED \!" >> /var/log/gluster/gluster-mount-checker.log

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

    if [[ `grep $SHARE /etc/mtab | grep glusterfs` == "" ]]; then

        echo `date +"%Y-%m-%d %H:%M:%S"`" - $SHARE is not mounted, remounting" \
            >> /var/log/gluster/gluster-mount-checker.log

         # 3 attempts
        for i in 1 2 3; do

            echo `date +"%Y-%m-%d %H:%M:%S"`"   + Attempt $i" >> /var/log/gluster/gluster-mount-checker.log
            /bin/mount $SHARE  >> /var/log/gluster/gluster-mount-checker.log 2>&1

            if [[ $? != 0 ]]; then
                echo `date +"%Y-%m-%d %H:%M:%S"`"   + Re-mount FAILED \!" >> /var/log/gluster/gluster-mount-checker.log

            else
                break;
            fi

            # give a little time
            sleep 2
        done

    fi

done

