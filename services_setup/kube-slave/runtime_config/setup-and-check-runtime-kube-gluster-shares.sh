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

# Sourcing kubernetes environment
. /etc/k8s/env.sh

. /usr/local/sbin/eskimo-utils.sh

export HOME=/root


export VOLUME=kubernetes_shared
export MOUNT_POINT=/usr/local/etc/k8s/shared
export OWNER=kubernetes


# !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
# ALL OF WHAT FOLLOWS IS COPIED FROM gluster-mount-internal.sh from the eskimo gluster package
# Don't do any modification here, make them in gluster-mount-internal.sh and then only report them here
# !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

export MOUNT_POINT_NAME=$(echo $MOUNT_POINT | tr -s '/' '-')
export MOUNT_POINT_NAME=${MOUNT_POINT_NAME#?};

set +e

# Take exclusive lock
take_global_lock volume_management_lock_$VOLUME /var/lib/gluster/ nonblock
if [[ $? != 0 ]]; then
    echo " - setup-and-check-runtime-kube-gluster-shares.sh is in execution already (from either master or slave). Skipping ..."
    exit 0
fi


# Creating the mount point if it does not exist
if [[ ! -d "$MOUNT_POINT" ]]; then
    echo " - Creating mount point $MOUNT_POINT"
    mkdir -p $MOUNT_POINT
fi


# This is really just addressing the need to unmount the mount point before anything else is to be attempted
# In case the underlying gluster transport is not connected and yet the mount point is still referenced as mounted
#echo " - Checking existing mount of $MOUNT_POINT"
rm -Rf /tmp/gluster_error_$VOLUME
ls -la $MOUNT_POINT >/dev/null 2>/tmp/gluster_error_$VOLUME
if [[ $? != 0 ]]; then
    if [[ $(grep "Transport endpoint is not connected" /tmp/gluster_error_$VOLUME) != "" \
         || $(grep "Too many levels of symbolic links" /tmp/gluster_error_$VOLUME) != "" \
         || $(grep "No such device" /tmp/gluster_error_$VOLUME) != "" ]]; then
        echo " - There is an issue with $MOUNT_POINT (Transport endpoint is not connected / too many levels of symbolic links), unmounting ..."
        /bin/umount -f $MOUNT_POINT  >> /tmp/gluster_mount_${VOLUME}_log 2>&1
        if [[ $? != 0 ]]; then
            echo "Failed to unmount $MOUNT_POINT"
            cat /tmp/gluster_mount_${VOLUME}_log
            exit 9
        fi
    fi
fi


# Now we have everything ready to actually proceed with the mount
if [[ $(grep "$MOUNT_POINT" /etc/mtab 2>/dev/null) == "" ]]; then
    echo " - Mounting $MOUNT_POINT"
    /bin/systemctl restart $MOUNT_POINT_NAME.mount > /tmp/gluster_mount_${VOLUME}_log 2>&1
    if [[ $? != 0 ]]; then
        echo "   + Failed to mount $MOUNT_POINT"
        cat /tmp/gluster_mount_${VOLUME}_log
        exit 10
    fi

    sleep 1

    if [[ $(grep "$MOUNT_POINT" /etc/mtab 2>/dev/null) == "" ]]; then
        echo "   + Unsuccessfully attempted to mount $MOUNT_POINT"
        cat /tmp/gluster_mount_${VOLUME}_log
        exit 11
    fi


    # give it a little time to actually connect the transport
    sleep 4

    if [[ $(stat -c '%U' $MOUNT_POINT) != "$OWNER" ]]; then
        echo " - Changing owner and rights of $MOUNT_POINT"
        chown -R $OWNER $MOUNT_POINT
    fi

    chmod -R 777 $MOUNT_POINT

fi
