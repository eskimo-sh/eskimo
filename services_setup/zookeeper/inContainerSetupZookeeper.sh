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

# Loading topology
. /etc/eskimo_topology.sh

# Defining topology variables
if [[ $SELF_NODE_NUMBER == "" ]]; then
    echo " - No Self Node Number found in topology"
    exit 1
fi

if [[ $SELF_IP_ADDRESS == "" ]]; then
    echo " - No Self IP address found in topology for node $SELF_NODE_NUMBER"
    exit 2
fi

export MASTER_IP_ADDRESS=$MASTER_ZOOKEEPER_1
if [[ $MASTER_IP_ADDRESS == "" ]]; then
    echo " - No zookeeper master found in topology"
    exit 3
fi


SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $SCRIPT_DIR/common.sh "$@"


echo "-- SETTING UP ZOOKEEPER --------------------------------------------------------"

echo " - Removing system zookeeper auto-startup"
sudo update-rc.d zookeeper remove

echo " - Changing owner of /var/lib/zookeeper"
chown -R zookeeper. /var/lib/zookeeper


# FIXME : We shall support multiple zookeeper nodes obviously
# At the moment we support only one single node and as such, we ensure that the ZooPeeker master is the self IP
if [[ -z $TEST_MODE && $MASTER_IP_ADDRESS != $SELF_IP_ADDRESS ]]; then
    echo " !!! FIXME At the momenr we only support a single zookeeper node !!! "
    exit 4
fi

echo " - Adapting configuration in file zoo.cfg"

sudo sed -i s/"initLimit=10"/"initLimit=100"/g /etc/zookeeper/conf/zoo.cfg
sudo sed -i s/"syncLimit=5"/"syncLimit=10"/g /etc/zookeeper/conf/zoo.cfg


echo " - Adapting configuration in file /etc/zookeeper/conf/environment"

sudo sed -i s/"JAVA_OPTS=\"\""/"JAVA_OPTS=\"-Xmx512m -Xms512m\""/g /etc/zookeeper/conf/environment

echo " - Creating /usr/local folder structure to enable editable settings"
sudo ln -s /etc/zookeeper /usr/local/lib/zookeeper



echo " - Enabling user zookeeper to change config at runtime"
chown -R zookeeper. /etc/zookeeper/conf/

# Caution : the in container setup script must mandatorily finish with this log"
echo "$IN_CONTAINER_CONFIG_SUCESS_MESSAGE"