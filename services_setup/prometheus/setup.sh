#!/usr/bin/env bash

#
# This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
# well to this individual file than to the Eskimo Project as a whole.
#
# Copyright 2019 - 2021 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

# CHange current folder to script dir (important !)
cd $SCRIPT_DIR

# Loading topology
loadTopology


# reinitializing log
sudo rm -f prometheus_install_log

echo " - Building container prometheus"
build_container prometheus prometheus prometheus_install_log

echo " - Creating shared directory"
# TODO

# create and start container
echo " - Running docker container"
docker run \
        -v $PWD:/scripts \
        -v $PWD/../common:/common \
        -v /var/log/prometheus:/var/log/prometheus \
        -d --name prometheus \
        -i \
        -t eskimo:prometheus bash >> prometheus_install_log 2>&1
fail_if_error $? "prometheus_install_log" -2

# connect to container
#docker exec -it prometheus bash

echo " - Copying containerWatchDog.sh script to container"
docker_cp_script containerWatchDog.sh sbin prometheus prometheus_install_log

echo " - Configuring prometheus container"
docker exec prometheus bash /scripts/inContainerSetupPrometheus.sh | tee -a prometheus_install_log 2>&1
if [[ `tail -n 1 prometheus_install_log` != " - In container config SUCCESS" ]]; then
    echo " - In container setup script ended up in error"
    cat prometheus_install_log
    exit -100
fi

#echo " - TODO"
#docker exec -it prometheus TODO

echo " - Handling topology and setting injection"
handle_topology_settings prometheus prometheus_install_log

echo " - Committing changes to local template and exiting container prometheus"
commit_container prometheus prometheus_install_log

echo " - Installing and checking systemd service file"
install_and_check_service_file prometheus prometheus_install_log
