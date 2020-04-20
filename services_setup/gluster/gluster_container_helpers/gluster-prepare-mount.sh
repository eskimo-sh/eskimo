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

VOL_NAME=$1
if [[ $VOL_NAME == "" ]]; then
   echo "Expecting volume name as first argument"
   exit -1
fi

UIDTOSET=$2

# Inject topology
. /etc/eskimo_topology.sh

export MASTER_IP_ADDRESS=`eval echo "\$"$(echo MASTER_GLUSTER_$SELF_IP_ADDRESS | tr -d .)`
if [[ $MASTER_IP_ADDRESS == "" ]]; then
    echo " - No gluster master found in topology"
    exit -3
fi

mkdir -p /var/lib/gluster/volume_bricks/

# define share with replicas 2 unless single node on cluster
if [[ $MASTER_IP_ADDRESS == $SELF_IP_ADDRESS ]]; then
    export NBR_REPLICAS="1"
else
    export NBR_REPLICAS="2"
fi

set +e

# 3 attempts (to address concurrency issues coming from parallel installations)
for i in 1 2 3 ; do
    if [[ `gluster volume list | grep $VOL_NAME` == "" ]]; then

        rm -Rf /var/lib/gluster/volume_bricks/$VOL_NAME

        if [[ "$NBR_REPLICAS" == "1" ]]; then
            echo " - Creating single replica since likely single node in cluster"
            gluster volume create $VOL_NAME transport tcp \
                    $MASTER_IP_ADDRESS:/var/lib/gluster/volume_bricks/$VOL_NAME
            if [[ $? != 0 ]]; then
                sleep 2
                continue
            fi
        else
            gluster volume create $VOL_NAME replica $NBR_REPLICAS transport tcp \
                    $SELF_IP_ADDRESS:/var/lib/gluster/volume_bricks/$VOL_NAME \
                    $MASTER_IP_ADDRESS:/var/lib/gluster/volume_bricks/$VOL_NAME
            if [[ $? != 0 ]]; then
                sleep 2
                continue
            fi
        fi

        if [[ $UIDTOSET != "" ]]; then
           gluster volume set $VOL_NAME storage.owner-uid $UIDTOSET
        fi

        break
    fi
done

# Make sure it has been created
if [[ `gluster volume list | grep $VOL_NAME` == "" ]]; then
    echo "Failed to create volume $VOL_NAME"
    exit -10
fi


# Start volume if not started

# 3 attempts (to address concurrency issues coming from parallel installations)
for i in 1 2 3 ; do
    if [[ `gluster volume info $VOL_NAME | grep "Status: Started"` == "" ]]; then
       gluster volume start $VOL_NAME
       if [[ $? != 0 ]]; then
           sleep 2
           continue
       fi
       break
    fi
done

if [[ `gluster volume info $VOL_NAME | grep "Status: Started"` == "" ]]; then
    echo "Failed to create start $VOL_NAME"
    exit -11
fi


