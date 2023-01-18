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

export MASTER_IP_ADDRESS=$MASTER_NTP_1
if [[ $MASTER_IP_ADDRESS == "" ]]; then
    echo " - No NTP master found in topology"
    exit 1
fi

if [[ $SELF_IP_ADDRESS == "" ]]; then
    echo " - No Self IP address found in topology for node $SELF_NODE_NUMBER"
    exit 2
fi

export IS_MASTER="0"
if [[ $SELF_IP_ADDRESS == $MASTER_IP_ADDRESS ]]; then
    export IS_MASTER="1"
fi


if [[ $IS_MASTER == "1" ]]; then

    echo " - Configuring master NTP server"
    # hetzner ntp servers
    sudo sed -i s/"pool 0.debian.pool.ntp.org iburst"/"pool ntp1.hetzner.de iburst"/g /etc/ntp.conf
    sudo sed -i s/"pool 1.debian.pool.ntp.org iburst"/"pool ntp2.hetzner.com iburst"/g /etc/ntp.conf
    sudo sed -i s/"pool 2.debian.pool.ntp.org iburst"/"pool ntp3.hetzner.net iburst"/g /etc/ntp.conf
    sudo sed -i s/"pool 3.debian.pool.ntp.org iburst"/""/g /etc/ntp.conf

    sudo bash -c "echo -e \"\n\n#disable maximum offset of 1000 seconds\" >> /etc/ntp.conf"
    sudo bash -c "echo \"tinker panic 0\" >> /etc/ntp.conf"

else

    echo " - Configuring slave NTP server"
    # hetzner ntp servers
    sudo sed -i s/"pool 0.debian.pool.ntp.org iburst"/"#pool ntp1.hetzner.de iburst"/g /etc/ntp.conf
    sudo sed -i s/"pool 1.debian.pool.ntp.org iburst"/"#pool ntp2.hetzner.com iburst"/g /etc/ntp.conf
    sudo sed -i s/"pool 2.debian.pool.ntp.org iburst"/"#pool ntp3.hetzner.net iburst"/g /etc/ntp.conf
    sudo sed -i s/"pool 3.debian.pool.ntp.org iburst"/"\nserver $MASTER_IP_ADDRESS burst iburst"/g /etc/ntp.conf

    sudo sed -i s/"restrict -4 default kod notrap nomodify nopeer noquery limited"/"#restrict -4 default kod notrap nomodify nopeer noquery limited"/g /etc/ntp.conf
    sudo sed -i s/"restrict -6 default kod notrap nomodify nopeer noquery limited"/"#restrict -6 default kod notrap nomodify nopeer noquery limited"/g /etc/ntp.conf

    sudo bash -c "echo -e \"\n\n#disable maximum offset of 1000 seconds\" >> /etc/ntp.conf"
    sudo bash -c "echo \"tinker panic 0\" >> /etc/ntp.conf"

    sudo bash -c "echo -e \"\n\n#enabling mes_master to set time\" >> /etc/ntp.conf"
    sudo bash -c "echo \"restrict $MASTER_IP_ADDRESS mask 255.255.255.255 notrap \" >> /etc/ntp.conf"

    echo " - Scheduling periodical ntpdate force update"
    echo "* * * * * /usr/sbin/ntpdate -u $MASTER_IP_ADDRESS >> /var/log/ntp/ntpdate.log 2>&1" > /tmp/ntpdate_crontab
    crontab /tmp/ntpdate_crontab

fi



echo " - Adapting Configuration file"

