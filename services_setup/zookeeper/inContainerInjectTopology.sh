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

set -e

echo " - Loading Topology"
. /etc/eskimo_topology.sh

export MASTER_IP_ADDRESS=$MASTER_ZOOKEEPER_1
if [[ $MASTER_IP_ADDRESS == "" ]]; then
    echo " - No zookeeper master found in topology"
    exit 2
fi

export ZOOKEEPER_ID=`eval echo "\$""$(echo NODE_NBR_ZOOKEEPER_$SELF_IP_ADDRESS | tr -d '.')"`
if [[ $ZOOKEEPER_ID == "" ]]; then
    echo " - No Zookeeper Node Number (ID) found in topology for ip address $SELF_IP_ADDRESS"
    exit 3
fi

echo " - Generating myid"
bash -c "echo $ZOOKEEPER_ID > /etc/zookeeper/conf/myid"

echo " - Adapting configuration in file zoo.cfg"

# clusterizing zookeeper
sed -i s/"#server.1=zookeeper1:2888:3888"/"server.$ZOOKEEPER_ID=$MASTER_IP_ADDRESS:2888:3888"/g /etc/zookeeper/conf/zoo.cfg

#sed -i s/"#server.2=zookeeper2:2888:3888"/"#server.2=192.168.10.11:2888:3888"/g /etc/zookeeper/conf/zoo.cfg
#sed -i s/"#server.3=zookeeper3:2888:3888"/"#server.3=192.168.10.12:2888:3888"/g /etc/zookeeper/conf/zoo.cfg

# EDIT : DOn't need this
# echo "clientPortAddress=$SELF_IP_ADDRESS" >> /etc/zookeeper/conf/zoo.cfg



