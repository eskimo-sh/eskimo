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


function delete_gluster_management_lock_file() {
    echo " - releasing gluster_management_lock"
    rm -Rf /var/lib/gluster/gluster_management_lock
}

set -e

# Inject topology
. /etc/eskimo_topology.sh

export MASTER_IP_ADDRESS=`eval echo "\$"$(echo MASTER_GLUSTER_$SELF_IP_ADDRESS | tr -d .)`
if [[ $MASTER_IP_ADDRESS == "" ]]; then
    echo " - No gluster master found in topology"
    exit -3
fi

export PATH=/usr/local/sbin/:$PATH

echo "-> gluster-address-peer-inconsistency.sh"
echo " - Checking gluster connection between $SELF_IP_ADDRESS and $MASTER_IP_ADDRESS"

# typical single node cluster or back to single node cluster
if [[ "$MASTER_IP_ADDRESS" == "$SELF_IP_ADDRESS" ]]; then

    echo "Specific situation: master is self node. Need to ensure no other peer remain in the pool"
    gluster_peers=`gluster pool list  | sed -E 's/[a-zA-Z0-9\-]+[ \t]+([0-9\.]+|localhost|marathon\.registry)[^.]+/\1/;t;d'`

    for peer in $gluster_peers; do
        if [[ $peer != "localhost" ]]; then
            echo "Deleting peer $peer from pool list"
            __force-remove-peer.sh $peer
        fi
    done

# multiple nodes in cluster, fixing inconsistencies between local and master
else

    echo " - Attempting to take gluster_management_lock"

    # From here we will be messing with gluster and hence we need to take a lock
    export wait_counter=0
    while [[ -f /var/lib/gluster/gluster_management_lock ]] ; do
        echo " - gluster management is in execution already. Sleeping 2 secs"
        sleep 2
        let wait_counter=$wait_counter+1
        if [[ $wait_counter -gt 30 ]]; then
            echo " - Attempted during 60 seconds to get gluster_management_lock unsuccessfully. Stopping here"
            exit -31
        fi
    done

    trap delete_gluster_management_lock_file 15
    trap delete_gluster_management_lock_file EXIT

    touch /var/lib/gluster/gluster_management_lock

    echo " - Checking if master is in local pool"
    # XXX Hack for gluster knowing it's IP address by IP sometimes and by 'marathon.registry' some other times
    additional_search=$MASTER_IP_ADDRESS
    if [[ $MASTER_IP_ADDRESS == $MASTER_MARATHON_1 ]]; then
        additional_search=marathon.registry
    fi
    set +e
    rm /tmp/local_gluster_check
    for i in 1 2 3 ; do
        echo "Attempt $i" >> /tmp/local_gluster_check
        export localPeerList=`gluster pool list 2>>/tmp/local_gluster_check`
        if [[ $? == 0 ]]; then
            break
        else
            if [[ $i == 3 ]] ; then
                echo "Calling 'gluster pool list' ended up in error 3 times !"
                cat /tmp/local_gluster_check
                echo "Cannot proceed any further with consistency checking ... SKIPPING"
                exit 10
             else
                sleep 1
             fi
        fi
    done
    if [[ `echo $localPeerList | grep $MASTER_IP_ADDRESS` == "" && `echo $localPeerList | grep $additional_search` == "" ]]; then
        MASTER_IN_LOCAL=0
    else
        MASTER_IN_LOCAL=1
    fi

    echo " - Checking if local in master pool"
    remote_result=`gluster_call_remote.sh $MASTER_IP_ADDRESS pool list`
    if [[ $? != 0 ]]; then
        echo "Calling remote gluster on $MASTER_IP_ADDRESS failed !"
        echo "Cannot proceed any further with consistency checking ... SKIPPING"
        exit 0
    fi

    # XXX Hack for gluster knowing it's IP address by IP sometimes and by 'marathon.registry' some other times
    additional_search=$SELF_IP_ADDRESS
    if [[ $SELF_IP_ADDRESS == $MASTER_MARATHON_1 ]]; then
        additional_search=marathon.registry
    fi
    if [[ `echo $remote_result | grep $SELF_IP_ADDRESS` == "" && `echo $remote_result | grep $additional_search` == "" ]]; then
        LOCAL_IN_MASTER=0
    else
        LOCAL_IN_MASTER=1
    fi

    echo " - Checking consistency "
    if [[ $MASTER_IN_LOCAL == 0 ]]; then
        if [[ $LOCAL_IN_MASTER == 0 ]] ; then
            echo " -> gluster cluster is consistent. Neither local nor master know each others"
        else
            # CAUTION : THIS LOG STATEMENT NEEDS TO STAY EXACTLY AS IS. IT IS DETECTED FOR FURTHER PROCESSING
            echo " -> gluster cluster is inconsistent. Local doesn't know master but master knows local"

            echo " - Attempting to remove local from master pool list"
            gluster_call_remote.sh $MASTER_IP_ADDRESS force-remove-peer now $SELF_IP_ADDRESS
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
            __force-remove-peer.sh $MASTER_IP_ADDRESS
            if [[ $? != 0 ]] ; then
                echo " - FAILED to remove master from local pool list"
                exit -1
            fi
        else
            echo " -> gluster cluster is consistent. both local and master know each others"
        fi
    fi
fi