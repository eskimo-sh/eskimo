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


echo "-- SETTING UP GLUSTER FS ------------------------------------------------------------"


echo " - Removing system glusterfs-server auto-startup"
sudo update-rc.d glusterfs-server remove

echo " - Creating directories in which gluster implements volumes"
mkdir -p /var/lib/gluster/volume_bricks/


echo " - Creating gluster volume configuration file"

rm -Rf /var/lib/gluster/glusterfs.VOLUME_FILE
touch /var/lib/gluster/glusterfs.VOLUME_FILE

sudo bash -c "echo \"volume management\" >> /var/lib/gluster/glusterfs.VOLUME_FILE"
sudo bash -c "echo \"    type mgmt/glusterd\" >> /var/lib/gluster/glusterfs.VOLUME_FILE"
sudo bash -c "echo \"    option working-directory /var/lib/gluster/working_directory\" >> /var/lib/gluster/glusterfs.VOLUME_FILE"
sudo bash -c "echo \"    option nfs.disable on\" >> /var/lib/gluster/glusterfs.VOLUME_FILE"
sudo bash -c "echo \"    option transport-type socket,rdma\" >> /var/lib/gluster/glusterfs.VOLUME_FILE"
sudo bash -c "echo \"    option transport.socket.keepalive-time 20\" >> /var/lib/gluster/glusterfs.VOLUME_FILE"
sudo bash -c "echo \"    option transport.socket.keepalive-interval 2\" >> /var/lib/gluster/glusterfs.VOLUME_FILE"
sudo bash -c "echo \"    option transport.socket.keepalive-count 9\" >> /var/lib/gluster/glusterfs.VOLUME_FILE"
sudo bash -c "echo \"    option transport.socket.read-fail-log off\" >> /var/lib/gluster/glusterfs.VOLUME_FILE"
sudo bash -c "echo \"    option ping-timeout 0\" >> /var/lib/gluster/glusterfs.VOLUME_FILE"
sudo bash -c "echo \"    option event-threads 2\" >> /var/lib/gluster/glusterfs.VOLUME_FILE"
# Not working in anyway ...
#sudo bash -c "echo \"    option nfs.addr-namelookup off\" >> /var/lib/gluster/glusterfs.VOLUME_FILE"
sudo bash -c "echo \"    option transport.rdma.bind-address $SELF_IP_ADDRESS\" >> /var/lib/gluster/glusterfs.VOLUME_FILE"
sudo bash -c "echo \"    option transport.socket.bind-address $SELF_IP_ADDRESS\" >> /var/lib/gluster/glusterfs.VOLUME_FILE"
sudo bash -c "echo \"    option transport.tcp.bind-address $SELF_IP_ADDRESS\" >> /var/lib/gluster/glusterfs.VOLUME_FILE"
sudo bash -c "echo \"    option transport.address-family inet\" >> /var/lib/gluster/glusterfs.VOLUME_FILE"
sudo bash -c "echo \"#   option base-port 49152\" >> /var/lib/gluster/glusterfs.VOLUME_FILE"
sudo bash -c "echo \"end-volume\" >> /var/lib/gluster/glusterfs.VOLUME_FILE"


# Caution : the in container setup script must mandatorily finish with this log"
echo "$IN_CONTAINER_CONFIG_SUCESS_MESSAGE"
