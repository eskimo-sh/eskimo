#!/usr/bin/env bash

#
# This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
# well to this individual file than to the Eskimo Project as a whole.
#
# Copyright 2019 - 2022 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

# Checking arguments
if [[ $1 == "" ]]; then
   echo "expected gluster volume name as first argument"
   exit 1
fi
export VOLUME=$1

if [[ $2 == "" ]]; then
   echo "expected mount point as second argument"
   exit 2
fi
export MOUNT_POINT=$2
export MOUNT_POINT_NAME=`echo $MOUNT_POINT | tr -s '/' '-'`
export MOUNT_POINT_NAME=${MOUNT_POINT_NAME#?};

if [[ $3 == "" ]]; then
   echo "expected mount point owner user as third argument"
   exit 3
fi
export OWNER=$3

if [[ $4 == "" ]]; then
   echo "expected mount point owner user ID as fourth argument"
   exit 4
fi
export OWNER_ID=$4

echo "-> gluster_mount_internal.sh"
echo " - Proceeding with gluster mount with :"
echo "   + volume           : $VOLUME"
echo "   + mount point      : $MOUNT_POINT"
echo "   + mount point name : $MOUNT_POINT_NAME"
echo "   + owner            : $OWNER"
echo "   + owner UID        : $OWNER_ID"

# Loading topology
if [[ ! -f /etc/eskimo_topology.sh ]]; then
    echo "  - ERROR : no topology file defined !"
    exit 5
fi

rm -Rf /tmp/gluster_mount_$1_log

. /etc/eskimo_topology.sh

# Defining topology variables
if [[ $SELF_NODE_NUMBER == "" ]]; then
    echo " - No Self Node Number found in topology"
    exit 6
fi

if [[ $SELF_IP_ADDRESS == "" ]]; then
    echo " - No Self IP address found in topology for node $SELF_NODE_NUMBER"
    exit 7
fi

set +e

function delete_gluster_lock_file() {
     rm -Rf /var/lib/gluster/volume_management_lock_$VOLUME
}

# From here we will be messing with gluster and hence we need to take a lock
if [[ -f /var/lib/gluster/volume_management_lock_$VOLUME ]] ; then
    echo " - gluster-mount.sh is in execution on $VOLUME already. Stopping"
    exit 0
fi

trap delete_gluster_lock_file 15
trap delete_gluster_lock_file EXIT

touch /var/lib/gluster/volume_management_lock_$VOLUME

# Creating the mount point if it does not exist
if [[ ! -d "$MOUNT_POINT" ]]; then
    echo " - Creating mount point $MOUNT_POINT"
    mkdir -p $MOUNT_POINT
fi


# This is really just addressing the need to unmount the mount point before anything else is to be attempted
# In case the underlying gluster transport is not connected and yet the mount point is still referenced as mounted
echo " - Checking existing mount of $MOUNT_POINT"
rm -Rf /tmp/gluster_error_$1
ls -la $MOUNT_POINT >/dev/null 2>/tmp/gluster_error_$1
if [[ $? != 0 ]]; then
    if [[ `grep "Transport endpoint is not connected" /tmp/gluster_error_$1` != "" \
         || `grep "Too many levels of symbolic links" /tmp/gluster_error_$1` != "" \
         || `grep "No such device" /tmp/gluster_error_$1` != "" ]]; then
        echo " - There is an issue with $MOUNT_POINT (Transport endpoint is not connected / too many levels of symbolic links), unmounting ..."
        /bin/umount -f $MOUNT_POINT  >> /tmp/gluster_mount_$1_log 2>&1
        if [[ $? != 0 ]]; then
            echo "Failed to unmount $MOUNT_POINT"
            cat /tmp/gluster_mount_$1_log
            exit 9
        fi
    fi
fi

# The below is to define the systemd unit and the fastab entry to proceed with automatic mount of the gluster
# share in the future
if [[ `grep $MOUNT_POINT /etc/fstab` == "" ]]; then

    echo " - Enabling gluster share $MOUNT_POINT"
    # XXX I change noauto to auto following issues after recover from suspend
    bash -c "echo \"$SELF_IP_ADDRESS:/$VOLUME $MOUNT_POINT glusterfs auto,rw,_netdev,x-systemd.automount,x-systemd.requires=gluster.service,x-systemd.after=gluster.service 0 0\" >> /etc/fstab"

    sleep 1

    echo " - reloading systemd daemon"
    /bin/systemctl daemon-reload

    sleep 2
fi


# Now we have everything ready to actually proceed with the mount
if [[ `grep "$MOUNT_POINT" /etc/mtab 2>/dev/null` == "" ]]; then
    echo " - Mounting $MOUNT_POINT"
    /bin/systemctl restart $MOUNT_POINT_NAME.mount > /tmp/gluster_mount_$1_log 2>&1
    if [[ $? != 0 ]]; then
        echo "   + Failed to mount $MOUNT_POINT"
        cat /tmp/gluster_mount_$1_log
        exit 10
    fi

    sleep 1

    if [[ `grep "$MOUNT_POINT" /etc/mtab 2>/dev/null` == "" ]]; then
        echo "   + Unsuccessfully attempted to mount $MOUNT_POINT"
        cat /tmp/gluster_mount_$1_log
        exit 11
    fi
fi


# give it a little time to actually connect the transport
sleep 4

if [[ `stat -c '%U' $MOUNT_POINT` != "$OWNER" ]]; then
    echo " - Changing owner and rights of $MOUNT_POINT"
    chown -R $OWNER $MOUNT_POINT
fi

chmod -R 777 $MOUNT_POINT

delete_gluster_lock_file