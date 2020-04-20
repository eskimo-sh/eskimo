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


# Remove all bricks I believe the shabow has and then force remove the shadow from my peers
SHADOW_IP_ADDRESS=$1
if [[ $SHADOW_IP_ADDRESS == "" ]]; then
   echo "Expecting Gluster Shadow (vanished !) IP address as first argument"
   exit -1
fi

echo " - Forcing removal of $SHADOW_IP_ADDRESS from local peer list"

echo "    + Listing local volumes"
volumes=`gluster volume list 2>/tmp/volume_list_log`
if [[ $? != 0 ]]; then
    echo "Failed to list local volumes"
    echo $volumes
    cat /tmp/volume_list_log
    exit -2
fi

echo "    + Removing all bricks from $SHADOW_IP_ADDRESS"
for vol in $volumes; do

    echo "    + Listing volume bricks for $vol"
    brick=`gluster volume info $vol  | grep Brick | grep $SHADOW_IP_ADDRESS | cut -d ' ' -f 2  2>/tmp/bricks_log`
    if [[ $? != 0 ]]; then
        echo "Failed to get volume info for $vol"
        echo $brick
        cat /tmp/bricks_log
        exit -3
    fi

    echo "   + Removing brick $brick"
    share_name=`echo $brick | rev | cut -d'/' -f 1 | rev`
    echo "y" | gluster volume remove-brick $share_name replica 1 $brick force >/tmp/remove_brick 2>&1
    if [[ $? != 0 ]]; then
        echo "Failed to remove brick $brick"
        cat /tmp/remove_brick
        exit -4
    fi

done

echo "    + Detaching peer $SHADOW_IP_ADDRESS"
gluster peer detach $SHADOW_IP_ADDRESS >/tmp/peer_detach 2>&1
if [[ $? != 0 ]]; then
    echo "Failed to detach peer $SHADOW_IP_ADDRESS"
    cat /tmp/peer_detach
    exit -5
fi


