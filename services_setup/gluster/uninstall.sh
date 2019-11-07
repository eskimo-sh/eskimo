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

echoerr() { echo "$@" 1>&2; }

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Change current folder to script dir (important !)
cd $SCRIPT_DIR

# Loading topology
. /etc/eskimo_topology.sh

# Defining topology variables
if [[ $SELF_NODE_NUMBER == "" ]]; then
    echo " - No Self Node Number found in topology"
    exit -1
fi

if [[ $SELF_IP_ADDRESS == "" ]]; then
    echo " - No Self IP address found in topology for node $SELF_NODE_NUMBER"
    exit -2
fi

export MASTER_IP_ADDRESS=`eval echo "\$"$(echo MASTER_GLUSTER_$SELF_IP_ADDRESS | tr -d .)`
if [[ $MASTER_IP_ADDRESS != "" ]]; then

    if [[ `docker ps -a -f name=gluster | grep Up` != "" ]]; then

        echo " - Checking if local node needs to be removed from gluster cluster"
        poolListResult=`docker exec -i gluster bash -c "/usr/local/sbin/gluster_call_remote.sh $MASTER_IP_ADDRESS pool list"`

        if [[ `echo $poolListResult | grep $SELF_IP_ADDRESS` != "" ]]; then

            echo " - Removing bricks from node"
            for i in `sudo /usr/local/sbin/gluster volume info | grep Brick | grep / | cut -d ' ' -f 2 | grep $SELF_IP_ADDRESS`; do
                echo "   + removing brick $i"

                # FIXME I should add a replica on another node
                # Then compute nzmber ore replica and remove 1 from it to use in the command below

                # get filename
                share_name=$(basename `echo "192.168.10.13:/var/lib/gluster/volume_bricks/spark_data" | cut -d ':' -f 2`)

                echo "y" | sudo /usr/local/sbin/gluster volume remove-brick $share_name replica 1 $i force
                if [[ $? != 0 ]]; then
                    echo " -> Failed to remove brick $i!"
                    exit -3
                fi
            done


            echo " - Removing local gluster node from master pool list"
            docker exec -i gluster bash -c "/usr/local/sbin/gluster_call_remote.sh $MASTER_IP_ADDRESS peer detach $SELF_IP_ADDRESS"
            if [[ $? != 0 ]]; then
                echo " -> Failed !"
                exit -3
            fi
        fi

     else
        echo " - WARNING: gluster container is down. Not proceeding with peer manipulations"

     fi
fi

echo " - Removing gluster wrappers"
sudo rm -Rf /usr/local/sbin/*gluster*


