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

# Set some key environment variables
echo " - Setting key environment variables"
export FLINK_HOME=/usr/local/lib/flink/
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64/

# Load eskimo topolgy
if [[ -f /usr/local/sbin/inContainerInjectTopology.sh ]]; then
    # Injecting topoloy
    . /usr/local/sbin/inContainerInjectTopology.sh
fi

echo " - Making sure log dir exists"
mkdir -p /var/log/flink/log

echo " - Inject settings (flink-runtime)"
/usr/local/sbin/settingsInjector.sh flink-runtime

echo " - Start glusterMountCheckerPeriodic.sh script"
/bin/bash /usr/local/sbin/glusterMountCheckerPeriodic.sh &
export GLUSTER_MOUNT_CHECKER_PID=$!

echo " - Changing current directory to /home/flink"
cd /home/flink

echo " - Making sure script calls flink executables and not wrappers !"
export PATH=/usr/local/lib/flink/bin/:$PATH

# Call flink provided entrypoint
echo " - Executing : /usr/local/lib/flink/kubernetes/docker-entrypoint.sh" "$@"
last_arg="${@: -1}"
bash /usr/local/lib/flink/kubernetes/docker-entrypoint.sh "$@" | tee "/var/log/flink/flink-log-$(echo $last_arg | cut -d ' ' -f 1).log" &
export FLINK_PROCESS=$!


echo " - Launching Watch Dog on glusterMountCheckerPeriodic remote server"
/usr/local/sbin/containerWatchDog.sh $GLUSTER_MOUNT_CHECKER_PID $FLINK_PROCESS /var/log/flink/gluster-mount-checker-periodic-watchdog-$FLINK_PROCESS.log &

echo " - Now waiting on main process to exit"
wait $FLINK_PROCESS
