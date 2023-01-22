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

. /etc/eskimo_topology.sh

if [[ $SELF_IP_ADDRESS == "" ]]; then
    echo " - Didn't find Self IP Address in eskimo_topology.sh"
    exit 1
fi

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $SCRIPT_DIR/common.sh "$@"


echo "-- SETTING UP EGMI ------------------------------------------------------------"

mkdir -p /var/lib/egmi
mkdir -p /var/log/gluster/egmi

echo " - Simlinking EGMI logs to /var/log/"
sudo rm -Rf /usr/local/lib/egmi/logs
sudo ln -s /var/log/gluster/egmi /usr/local/lib/egmi/logs

echo " - Simlinking EGMI config to /usr/local/etc/egmi"
sudo ln -s /usr/local/lib/egmi/conf /usr/local/etc/egmi

echo " - Tweaking egmi.properties"
sed -i s/"zookeeper.myId=localhost"/"zookeeper.myId=$SELF_IP_ADDRESS"/g /usr/local/lib/egmi/conf/egmi.properties
#sed -i s/"system.statusUpdatePeriodSeconds=30"/"system.statusUpdatePeriodSeconds=60"/g /usr/local/lib/egmi/conf/egmi.properties

echo " - HACK - creating and cron'ing a script that checks for deadlocks and force restarts container "

echo "   + Creating script __periodic-check-deadlocks-hack.sh"
cat > /tmp/__periodic-check-deadlocks-hack.sh <<- "EOF"
#!/bin/bash

echo `/bin/date +"%Y-%m-%d %H:%M:%S"`" - Checking for stacking of 'gluster volume status'" | /usr/bin/tee -a /var/log/gluster/__periodic-check-deadlocks-hack.log
if [[ `/bin/ps -efl  | /bin/grep "gluster volume status all detail" | /bin/grep -v grep | /usr/bin/wc -l` -ge 3 ]]; then

    echo `/bin/date +"%Y-%m-%d %H:%M:%S"`"    + Stacking detected !!!! Killing container" | /usr/bin/tee -a /var/log/gluster/__periodic-check-deadlocks-hack.log
    /bin/kill -15 `cat /run/glusterd.pid`
fi
EOF

sudo mv /tmp/__periodic-check-deadlocks-hack.sh /usr/local/sbin/__periodic-check-deadlocks-hack.sh
sudo chmod 755 /usr/local/sbin/__periodic-check-deadlocks-hack.sh

echo "   + Scheduling periodic execution of __periodic-check-deadlocks-hack.sh using crontab"
echo "* * * * * /bin/bash /usr/local/sbin/__periodic-check-deadlocks-hack.sh" > /tmp/crontab
sudo crontab -u root /tmp/crontab

echo " - Enabling and starting cron"
sudo update-rc.d cron enable
sudo bash -c "/etc/init.d/cron start >> /tmp/egmi-setup-log"



# Caution : the in container setup script must mandatorily finish with this log"
echo "$IN_CONTAINER_CONFIG_SUCESS_MESSAGE"
