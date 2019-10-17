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
. $SCRIPT_DIR/common.sh "$@"

# Change current folder to script dir (important !)
cd $SCRIPT_DIR

# Loading topology
loadTopology


# reinitializing log
sudo rm -f /tmp/ntp_install_log

echo " - Building container ntp"
build_container ntp ntp /tmp/ntp_install_log

echo " - Creating shared lib"
sudo mkdir -p /var/lib/ntp/
sudo chmod 755 /var/lib/ntp/
sudo mkdir -p /var/run/ntp/
sudo chmod 755 /var/run/ntp/
sudo mkdir -p /var/log/ntp/
sudo chmod 755 /var/log/ntp/

# create and start container
echo " - Running docker container"
docker run \
        -v $PWD:/scripts \
        -v $PWD/../common:/common \
        -v /var/log/ntp:/var/log/ntp \
        -v /var/run/ntp:/var/run/ntp \
        -v /var/lib/ntp:/var/lib/ntp \
        --cap-add SYS_RESOURCE\
        --cap-add SYS_TIME\
        -d --name ntp \
        -i \
        -t eskimo:ntp bash >> /tmp/ntp_install_log 2>&1
fail_if_error $? "/tmp/ntp_install_log" -2

# connect to container
#docker exec -it ntp bash

echo " - Configuring ntp container"
docker exec ntp bash /scripts/inContainerSetupNtp.sh | tee -a /tmp/ntp_install_log 2>&1
if [[ `tail -n 1 /tmp/ntp_install_log` != " - In container config SUCCESS" ]]; then
    echo " - In container setup script ended up in error"
    cat /tmp/ntp_install_log
    exit -100
fi

#echo " - TODO"
#docker exec -it ntp TODO

echo " - Handling topology and setting injection"
handle_topology_settings ntp /tmp/ntp_install_log

echo " - Committing changes to local template and exiting container ntp"
commit_container ntp /tmp/ntp_install_log

echo " - Installing and checking systemd service file"
install_and_check_service_file ntp /tmp/ntp_install_log
