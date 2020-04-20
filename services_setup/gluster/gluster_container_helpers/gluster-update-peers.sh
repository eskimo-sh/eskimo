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

if [[ $SELF_IP_ADDRESS == $MASTER_IP_ADDRESS ]]; then

    echo " - NO NEED TO ADD ANY PEER - Master is self node - likely only one node in gluster cluster"

else

    # add other master if not done
    if [[ `gluster pool list | grep $MASTER_IP_ADDRESS` == "" ]]; then

       # 3 attempts (to address concurrency issues coming from parallel installations)
       set +e
       for i in 1 2 3 ; do
           echo " - Trying : /usr/local/sbin/gluster_call_remote.sh $SELF_IP_ADDRESS peer probe $MASTER_IP_ADDRESS"
           /usr/local/sbin/gluster_call_remote.sh $SELF_IP_ADDRESS peer probe $MASTER_IP_ADDRESS
           if [[ $? != 0 ]]; then
               sleep 2
               continue
           fi
           break
       done

       # Trying the other way around
       if [[ `gluster pool list | grep $MASTER_IP_ADDRESS` == "" ]]; then
           for i in 1 2 3 ; do
               echo " - Trying : /usr/local/sbin/gluster_call_remote.sh $MASTER_IP_ADDRESS peer probe $SELF_IP_ADDRESS"
               /usr/local/sbin/gluster_call_remote.sh $MASTER_IP_ADDRESS peer probe $SELF_IP_ADDRESS
               if [[ $? != 0 ]]; then
                   sleep 2
                   continue
               fi
               break
           done
       fi

       # checking
       sleep 1
       if [[ `gluster pool list | grep $MASTER_IP_ADDRESS` == "" ]]; then
           echo "Failed to add $SELF_IP_ADDRESS to cluster where master is $MASTER_IP_ADDRESS"
           exit -101
       fi

       # replicate blocks if required
       __replicate-master-blocks.sh $MASTER_IP_ADDRESS

       set -e
    fi

    # ensure peer is well connected
    sleep 1
    if [[ `gluster peer status | grep $MASTER_IP_ADDRESS` == "" ]]; then
       echo "Error : $MASTER_IP_ADDRESS not found in peers"
       gluster peer status
       exit -1
    fi
fi
