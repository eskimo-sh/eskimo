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

# Checking arguments
if [[ $1 == "" ]]; then
   echo "expected gluster volume name as first argument"
   exit -1
fi
export VOLUME=$1

if [[ $2 == "" ]]; then
   echo "expected mount point as second argument"
   exit -2
fi
export MOUNT_POINT=$2
export MOUNT_POINT_NAME=`echo $MOUNT_POINT | tr -s '/' '-'`
export MOUNT_POINT_NAME=${MOUNT_POINT_NAME#?};

if [[ $3 == "" ]]; then
   echo "expected mount point owner user as third argument"
   exit -2
fi
export OWNER=$3

if [[ $4 == "" ]]; then
   echo "expected mount point owner user ID as fourth argument"
   exit -2
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
    exit -123
fi

rm -Rf /tmp/gluster_mount_$1_log

. /etc/eskimo_topology.sh

# Defining topology variables
if [[ $SELF_NODE_NUMBER == "" ]]; then
    echo " - No Self Node Number found in topology"
    exit -4
fi

if [[ $SELF_IP_ADDRESS == "" ]]; then
    echo " - No Self IP address found in topology for node $SELF_NODE_NUMBER"
    exit -5
fi


# find out if gluster is available
if [[ `cat /etc/eskimo_topology.sh  | grep MASTER_GLUSTER` == "" ]]; then
    echo "ERROR: No gluster master defined"
    exit -20
fi

set +e


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
            exit -3
        fi
    fi
fi

# Creating the mount point if it does not exist
if [[ ! -d "$MOUNT_POINT" ]]; then
    echo " - Creating mount point $MOUNT_POINT"
    mkdir -p $MOUNT_POINT
fi

# From here we will be messing with gluster and hence we need to take a lock
if [[ -f /var/lib/gluster/volume_management_lock_$VOLUME ]] ; then
    echo " - gluster-mount.sh is in execution on $VOLUME already. Stopping"
    exit 0
fi

function delete_gluster_lock_file() {
     rm -Rf /var/lib/gluster/volume_management_lock_$VOLUME
}

trap delete_gluster_lock_file 15
trap delete_gluster_lock_file EXIT

touch /var/lib/gluster/volume_management_lock_$VOLUME

# We first attempt mounting on two sides using __gluster-prepare-mount.sh
# - __gluster-prepare-mount-sh calls
#  + in container address-gluster-inconsistency.sh which does
#    - ensures that the master is not in the pool of the local node and the local node not in the pool of the master
#    - or that both are in each others pool
#    - fix the situation if it is incoherent
#  + in container configure-general-gluster.sh which does
#    - first try /usr/local/sbin/gluster_call_remote.sh $SELF_IP_ADDRESS peer probe $MASTER_IP_ADDRESS
#    - then try  /usr/local/sbin/gluster_call_remote.sh $MASTER_IP_ADDRESS peer probe $
#    - then checks if peer adding was successfull
#  + in container gluster-prepare-mount.sh which does
#    - gluster volume create $VOL_NAME ...
#    - gluster volume start $VOL_NAME ...
#    - then check volume was properly created
if [[ `grep "$MOUNT_POINT" /etc/mtab 2>/dev/null` == "" ]]; then

    echo " - Just trying to remount $MOUNT_POINT first"
    /bin/systemctl restart $MOUNT_POINT_NAME.mount > /tmp/gluster_mount_$1_log 2>&1

    sleep 1
    if [[ `grep "$MOUNT_POINT" /etc/mtab 2>/dev/null` == "" ]]; then
        failedSys=1
    fi

    ls -la $MOUNT_POINT >/dev/null 2>/tmp/error
    if [[ $? != 0 && $failedSys != 0 ]]; then
        if [[ `grep "Transport endpoint is not connected" /tmp/error` != "" ]]; then
            failedTrans=1
        fi
    fi

    if [[ $failedSys != 0 && $failedTrans != 0 ]]; then
        echo " - Mount failed. Need to Prepare gluster mount $MOUNT_POINT (no further logs if success)"
        /usr/local/sbin/__gluster-prepare-mount.sh $VOLUME $OWNER_ID >> /tmp/gluster_mount_$1_log 2>&1
        if [[ $? != 0 ]]; then
            echo "Failed to prepare gluster mount $MOUNT_POINT with __gluster-prepare-mount.sh"
            echo "Reporting here output of __gluster-prepare-mount.sh:"
            cat /tmp/gluster_mount_$1_log
            exit -5
        else
            echo "  + Prepare mount success."
        fi
    fi
fi

# The below is to define the systemd unit and the fastab entry to proceed with automatic mount of the gluster
# share in the future
if [[ `grep $MOUNT_POINT /etc/fstab` == "" ]]; then

    echo " - Enabling gluster share $MOUNT_POINT"
    # XXX I change noauto to auto following issues after recover from suspend
    bash -c "echo \"$SELF_IP_ADDRESS:/$VOLUME $MOUNT_POINT glusterfs auto,rw,_netdev,x-systemd.automount,x-systemd.after=gluster.service 0 0\" >> /etc/fstab"

    echo " - reloading systemd daemon"
    /bin/systemctl daemon-reload
fi


# Now we have everything ready to actually proceed with the mount
if [[ `grep "$MOUNT_POINT" /etc/mtab 2>/dev/null` == "" ]]; then
    echo " - Mounting $MOUNT_POINT"
    /bin/systemctl restart $MOUNT_POINT_NAME.mount > /tmp/gluster_mount_$1_log 2>&1
    if [[ $? != 0 ]]; then
        echo "Failed to mount $MOUNT_POINT"
        cat /tmp/gluster_mount_$1_log
        exit -7
    fi

    if [[ `grep "$MOUNT_POINT" /etc/mtab 2>/dev/null` == "" ]]; then
        echo "Unsuccessfully attempted to mount $MOUNT_POINT"
        cat /tmp/gluster_mount_$1_log
        exit -71
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