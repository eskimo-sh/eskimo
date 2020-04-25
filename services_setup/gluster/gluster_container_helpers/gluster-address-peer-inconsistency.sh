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

# Inject topology
. /etc/eskimo_topology.sh

export MASTER_IP_ADDRESS=`eval echo "\$"$(echo MASTER_GLUSTER_$SELF_IP_ADDRESS | tr -d .)`
if [[ $MASTER_IP_ADDRESS == "" ]]; then
    echo " - No gluster master found in topology"
    exit -3
fi

# typical single node cluster or back to single node cluster
if [[ "$MASTER_IP_ADDRESS" == "$SELF_IP_ADDRESS" ]]; then

    echo "Specific situation: master is self node. Need to ensure no other peer remain in the pool"
    gluster_peers=`gluster pool list  | sed -E 's/[a-zA-Z0-9\-]+[ \t]+([0-9\.]+|localhost)[^.]+/\1/;t;d'`

    for peer in $gluster_peers; do
        if [[ $peer != "localhost" ]]; then
            echo "Deleting peer $peer from pool list"
            /usr/local/sbin/__force-remove-peer.sh $peer
        fi
    done

# multiple nodes in cluster, fixing inconsistencies between local and master
else

    echo " - Checking out if master is in local pool"
    if [[ `gluster pool list | grep $MASTER_IP_ADDRESS` == "" ]]; then
        MASTER_IN_LOCAL=0
    else
        MASTER_IN_LOCAL=1
    fi

    echo " - Checking if local in master pool"
    remote_result=`/usr/local/sbin/gluster_call_remote.sh $MASTER_IP_ADDRESS pool list`
    if [[ `echo $remote_result | grep $SELF_IP_ADDRESS` == "" ]]; then
        LOCAL_IN_MASTER=0
    else
        LOCAL_IN_MASTER=1
    fi

    set +e

    echo " - Checking consistency "
    if [[ $MASTER_IN_LOCAL == 0 ]]; then
        if [[ $LOCAL_IN_MASTER == 0 ]] ; then
            echo " -> gluster cluster is consistent. Neither local nor master know each others"
        else
            # CAUTION : THIS LOG STATEMENT NEEDS TO STAY EXACTLY AS IS. IT IS DETECTED FOR FURTHER PROCESSING
            echo " -> gluster cluster is inconsistent. Local doesn't know master but master knows local"

            echo " - Attempting to remove local from master pool list"
            /usr/local/sbin/gluster_call_remote.sh $MASTER_IP_ADDRESS force-remove-peer now $SELF_IP_ADDRESS
            if [[ $? != 0 ]] ; then
                echo " - FAILED to remove local from master pool list"
                exit -1
            fi

            echo " - Deleting corresponding local blocks"
            __delete-local-blocks.sh $MASTER_IP_ADDRESS
             if [[ $? != 0 ]] ; then
                echo " - FAILED to delete corresponding local blocks"
                exit -1
            fi

        fi
    else
        if [[ $LOCAL_IN_MASTER == 0 ]] ; then
            echo " -> gluster cluster is inconsistent. Master doesn't know local but local knows master"
            echo " - Attempting to remove master from local pool list"
            /usr/local/sbin/__force-remove-peer.sh $MASTER_IP_ADDRESS
            if [[ $? != 0 ]] ; then
                echo " - FAILED to remove master from local pool list"
                exit -1
            fi
        else
            echo " -> gluster cluster is consistent. both local and master know each others"
        fi
    fi
fi