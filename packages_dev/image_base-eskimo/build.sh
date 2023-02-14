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


echo "Building Base Eskimo Image"
echo "--------------------------------------------------------------------------------"

# reinitializing log
rm -f /tmp/base_image_build_log


echo " - Killing any previous containers"
if [[ $(docker ps -a -q -f name=base-eskimo_template) != "" ]]; then
    docker stop base-eskimo_template > /dev/null 2>&1
    docker container rm base-eskimo_template > /dev/null 2>&1
fi

# build
echo " - Building docker image"
docker build --iidfile id_file --tag eskimo/base-eskimo_template:latest . > /tmp/base_image_build_log 2>&1
fail_if_error $? "/tmp/base_image_build_log" -2

# create and start container
echo " - Starting setup docker container"
docker run -d --name base-eskimo_template -i -t eskimo/base-eskimo_template:latest bash > /tmp/base_image_build_log 2>&1
fail_if_error $? "/tmp/base_image_build_log" -2

echo " - (Hack) Creating missing directory /usr/share/man/man1/"
docker exec -i base-eskimo_template mkdir -p /usr/share/man/man1/ > /tmp/base_image_build_log 2>&1
fail_if_error $? "/tmp/base_image_build_log" -2

echo " - Updating the packages"
docker exec -i base-eskimo_template apt-get update > /tmp/base_image_build_log 2>&1
fail_if_error $? "/tmp/base_image_build_log" -2

echo " - Upgrading the appliance"
docker exec -i -e DEBIAN_FRONTEND=noninteractive base-eskimo_template apt-get -yq upgrade > /tmp/base_image_build_log 2>&1
fail_if_error $? "/tmp/base_image_build_log" -2

echo " - Installing required utility tools for eskimo framework"
docker exec -i base-eskimo_template apt-get install -y tar wget git unzip curl moreutils procps sudo net-tools jq iputils-ping apt-utils > /tmp/base_image_build_log 2>&1
fail_if_error $? "/tmp/base_image_build_log" -2

echo " - Removing unused packages"
docker exec -i base-eskimo_template apt-get remove -y openssh-client > /tmp/base_image_build_log 2>&1
fail_if_error $? "/tmp/base_image_build_log" -2



# connect to container
#docker exec -it base-eskimo_template bash


close_and_save_image base-eskimo_template /tmp/base_image_build_log $ESKIMO_VERSION