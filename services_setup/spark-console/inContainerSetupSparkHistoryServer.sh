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

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $SCRIPT_DIR/common.sh "$@"

echo " - Creating start-spark-console-wrapper.sh wrapper (changing name to start-history-server-wrapper.sh)"
sudo cp $SCRIPT_DIR/start-spark-console-wrapper.sh /usr/local/lib/spark/sbin/start-history-server-wrapper.sh
sudo chmod 755 /usr/local/lib/spark/sbin/start-history-server-wrapper.sh

echo " - Enabling spark user to change config"
sudo chown -R spark. /usr/local/lib/spark/sbin/

echo " - creating gluster log dir (for gluster mount checker)"
sudo mkdir -p /var/log/gluster/
sudo chmod 777 /var/log/gluster/

echo " - Enabling spark user to call glusterMountCheckerPeriodic.sh"
bash -c "echo \"spark  ALL = NOPASSWD: /bin/bash /usr/local/sbin/glusterMountChecker.sh\" >> /etc/sudoers.d/spark"

echo " - Defining history server configuration properties"
sudo bash -c "echo -e \"\n#For the filesystem history provider, the directory containing application event logs to load.\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"
sudo bash -c "echo -e \"spark.history.fs.logDirectory=file:///var/lib/spark/eventlog\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"

sudo bash -c "echo -e \"\n#The period at which to check for new or updated logs in the log directory.\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"
sudo bash -c "echo -e \"spark.history.fs.update.interval=5s\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"


# Caution : the in container setup script must mandatorily finish with this log"
echo "$IN_CONTAINER_CONFIG_SUCESS_MESSAGE"