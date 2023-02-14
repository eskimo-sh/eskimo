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

echoerr() { echo "$@" 1>&2; }

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $SCRIPT_DIR/common.sh "$@"

# CHange current folder to script dir (important !)
cd $SCRIPT_DIR || exit 199

# Loading topology
loadTopology


# reinitializing log
sudo rm -f kibana_install_log

echo " - Building container kibana"
build_container kibana kibana kibana_install_log
#save tag
CONTAINER_TAG=$CONTAINER_NEW_TAG

echo " - Configuring host elasticsearch config part"
. ./setupESCommon.sh
if [[ $? != 0 ]]; then
    echo "Common configuration part failed !"
    exit 20
fi


# create and start container
echo " - Running docker container"
docker run \
        -v $PWD:/scripts \
        -v $PWD/../common:/common \
        -v /var/log/elasticsearch:/var/log/elasticsearch \
        -v /var/run/elasticsearch:/var/run/elasticsearch \
        -v $SCRIPT_DIR/samples:/eskimo/samples \
        -d --name kibana \
        -i \
        -t eskimo/kibana:$CONTAINER_TAG bash >> kibana_install_log 2>&1
fail_if_error $? "kibana_install_log" -2

# connect to container
#docker exec -it kibana bash

echo " - Configuring kibana container (common part)"
docker exec kibana bash /scripts/inContainerSetupESCommon.sh $elasticsearch_user_id | tee -a kibana_install_log 2>&1
check_in_container_config_success kibana_install_log

echo " - Configuring kibana container"
docker exec kibana bash /scripts/inContainerSetupKibana.sh | tee -a kibana_install_log 2>&1
check_in_container_config_success kibana_install_log

echo " - Handling Eskimo Base Infrastructure"
handle_eskimo_base_infrastructure kibana kibana_install_log

echo " - Handling topology infrastructure"
handle_topology_infrastructure kibana kibana_install_log

echo " - Committing changes to local template and exiting container kibana"
commit_container kibana $CONTAINER_TAG kibana_install_log

echo " - Starting Kubernetes deployment"
deploy_kubernetes kibana $CONTAINER_TAG kibana_install_log
