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

# This script is intended to be used from within a docker container when a gluster volume has to be mounted


echo " - inContainerMountGluster.sh - will now mount $MOUNT_POINT"

# checking arguments
if [[ $1 == "" ]]; then
    echo "Expecting gluster volume as first argument"
    exit 1
fi
export VOLUME=$1

if [[ $2 == "" ]]; then
    echo "Expecting mount point as second argument"
    exit 2
fi
export MOUNT_POINT=$2

if [[ $2 == "" ]]; then
    echo "Expecting owner as third argument"
    exit 3
fi
export OWNER=$3

echo "   + Loading topology"
if [[ ! -f /etc/eskimo_topology.sh ]]; then
    echo "Cannot find eskimo topology file"
    exit 4
fi

. /etc/eskimo_topology.sh


if [[ ! -d $MOUNT_POINT ]]; then
    echo "   + Creating mount point: $MOUNT_POINT"
    mkdir -p $MOUNT_POINT
fi

ls /dev/fuse > /dev/null 2>&1
if [[ $? != 0 ]]; then
    echo "   + Creating fuse device"
    mknod /dev/fuse c 10 229
    if [[ $? != 0 ]]; then
        echo "FAILED to to create /dev/fuse node file"
        exit 6
    fi
fi

echo "   + Registering gluster filesystem $VOLUME on $MOUNT_POINT"
echo "$SELF_IP_ADDRESS:/$VOLUME $MOUNT_POINT glusterfs auto,rw,_netdev 0 0" >> /etc/fstab

echo "   + Mounting $MOUNT_POINT"
mount $MOUNT_POINT >> /tmp/mount_$VOLUME 2>&1
if [[ $? != 0 ]]; then
    echo "FAILED to mount gluster filesystem. Perhaps the container is not running as privileged ?"
    cat /tmp/mount_$VOLUME
    exit 7
fi

# give it a little time to actually connect the transport (Hacky hack)
sleep 4

if [[ `stat -c '%U' $MOUNT_POINT` != "$OWNER" ]]; then
    echo "   + Changing owner of $MOUNT_POINT"
    # not doing it recursively
    chown $OWNER $MOUNT_POINT
fi

echo "   + Changing rights of $MOUNT_POINT"
# not doing it recursively
chmod 777 $MOUNT_POINT

echo "   + SUCCESSFULLY mounted $MOUNT_POINT"
