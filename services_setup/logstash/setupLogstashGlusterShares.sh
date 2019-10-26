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



# Loading topology
if [[ ! -f /etc/eskimo_topology.sh ]]; then
    echo "  - ERROR : no topology file defined !"
    exit -123
fi

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


# find out if gluster is available
if [[ `cat /etc/eskimo_topology.sh  | grep MASTER_GLUSTER` != "" ]]; then
    export GLUSTER_AVAILABLE=1
else
    export GLUSTER_AVAILABLE=0
fi

# Only if gluster is enabled
if [[ $GLUSTER_AVAILABLE == 1 ]]; then

    echo " - Proceeding with gluster mount /var/lib/logstash/data"
    /usr/local/sbin/gluster_mount.sh logstash_data /var/lib/logstash/data elasticsearch `/usr/bin/id -u elasticsearch`

else

    echo " - Not mounting gluster shares since not working in cluster mode"

    if [[ ! -d /var/lib/logstash/data ]]; then
        echo " - Creating /var/lib/logstash/data"
        mkdir -p /var/lib/logstash/data
    fi

    if [[ `stat -c '%U' /var/lib/logstash/data` != "logstash" ]]; then
        echo " - Changing owner if /var/lib/logstash/data"
        chown -R elasticsearch /var/lib/logstash/data
    fi

    if [[ `ls -la /var/lib/logstash/ | grep data | grep drwxrwxrw` == "" ]]; then
        echo " - Changing rights of /var/lib/logstash/data"
        chmod -R 777 /var/lib/logstash/data
    fi

fi

