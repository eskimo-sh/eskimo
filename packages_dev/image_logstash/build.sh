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


echo "Building Logstash Image"
echo "--------------------------------------------------------------------------------"

# reinitializing log
rm -f /tmp/logstash_build_log

echo " - Building image logstash"
build_image logstash_template /tmp/logstash_build_log

#echo " - Installing OpenJDK 11"
#docker exec -i logstash_template apt-get install -y openjdk-11-jdk > /tmp/logstash_build_log 2>&1
#fail_if_error $? "/tmp/logstash_build_log" -2

echo " - Installing ZIP"
docker exec -i logstash_template apt-get install -y zip > /tmp/logstash_build_log 2>&1
fail_if_error $? "/tmp/logstash_build_log" -11

echo " - Installing python"
docker exec -i logstash_template apt-get -y install  python-dev python-six python-virtualenv python-pip cython > /tmp/logstash_build_log 2>&1
fail_if_error $? "/tmp/logstash_build_log" -5

echo " - Installing required python packages"
docker exec -i logstash_template pip install filelock furl > /tmp/logstash_build_log 2>&1
fail_if_error $? "/tmp/logstash_build_log" -12

echo " - Installing logstash"
docker exec -i logstash_template bash /scripts/installLogstash.sh | tee /tmp/logstash_build_log 2>&1
if [[ `tail -n 1 /tmp/logstash_build_log | grep " - In container install SUCCESS"` == "" ]]; then
    echo " - In container install script ended up in error"
    cat /tmp/logstash_build_log
    exit 102
fi

#echo " - TODO"
#docker exec -it logstash_template bash

echo " - Cleaning up image"
docker exec -i logstash_template apt-get remove -y git gcc adwaita-icon-theme >> /tmp/logstash_build_log 2>&1
docker exec -i logstash_template apt-get -y auto-remove >> /tmp/logstash_build_log 2>&1

echo " - Closing and saving image logstash"
close_and_save_image logstash_template /tmp/logstash_build_log $ES_VERSION