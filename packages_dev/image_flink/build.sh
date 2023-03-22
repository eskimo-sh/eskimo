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


echo "Building Flink Image"
echo "--------------------------------------------------------------------------------"

# reinitializing log
rm -f /tmp/flink_build_log

echo " - Building image flink"
build_image flink_template /tmp/flink_build_log

echo " - Installing OpenJDK 11"
docker exec -i flink_template apt-get install -y openjdk-11-jdk-headless > /tmp/flink_build_log 2>&1
fail_if_error $? "/tmp/flink_build_log" -3

echo " - Installing scala"
docker exec -i flink_template bash -c ". /common/common.sh && install_scala" > /tmp/flink_build_log 2>&1
fail_if_error $? "/tmp/flink_build_log" -4

#echo " - Installing python"
#docker exec -i flink_template apt-get -y install  python3-dev python3-six python3-virtualenv python3-pip zip > /tmp/flink_build_log 2>&1
#fail_if_error $? "/tmp/flink_build_log" -5

echo " - Installing python 3.7 build dependencies"
docker exec -i flink_template apt-get -y install zip libreadline-dev libncursesw5-dev libssl-dev libsqlite3-dev tk-dev libgdbm-dev libc6-dev libbz2-dev libffi-dev zlib1g-dev libjpeg-dev > /tmp/flink_build_log 2>&1
fail_if_error $? "/tmp/flink_build_log" -5

echo " - Installing python 3.7"
docker exec -i flink_template bash /scripts/installPython37.sh | tee -a /tmp/flink_build_log 2>&1
check_in_container_install_success /tmp/flink_build_log

echo " - Installing GlusterFS client"
docker exec -i flink_template apt-get -y install  glusterfs-client > /tmp/flink_build_log 2>&1
fail_if_error $? "/tmp/flink_build_log" -10

#echo " - Installing python elasticsearch and kafka clients and other utilities"
#docker exec -i flink pip install elasticsearch kafka-python > /tmp/flink_build_log 2>&1
#fail_if_error $? "/tmp/flink_build_log" -6

echo " - Switching python default version to 3.7"
docker exec -i flink_template update-alternatives --force --install /usr/bin/python python /usr/bin/python3.9 1 > /tmp/flink_build_log 2>&1
fail_if_error $? "/tmp/flink_build_log" -5
docker exec -i flink_template update-alternatives --force --install /usr/bin/python python /usr/bin/python3.7 2 > /tmp/flink_build_log 2>&1
fail_if_error $? "/tmp/flink_build_log" -5
docker exec -i flink_template mv /usr/bin/python3.9 /usr/bin/python3.9-DISABLED > /tmp/flink_build_log 2>&1
fail_if_error $? "/tmp/flink_build_log" -5

echo " - Installing other python packages"
docker exec -i flink_template pip3 install requests filelock > /tmp/flink_build_log 2>&1
fail_if_error $? "/tmp/flink_build_log" -6

echo " - Installing flink"
docker exec -i flink_template bash /scripts/installFlink.sh | tee -a /tmp/flink_build_log 2>&1
check_in_container_install_success /tmp/flink_build_log

echo " - Installing pyflink venv archive"
docker exec -i flink_template bash /scripts/installPyFlinkVenv.sh | tee -a /tmp/flink_build_log 2>&1
check_in_container_install_success /tmp/flink_build_log


echo " - Removing useless stuff"
docker exec -i flink_template bash /scripts/removeUselessStuff.sh | tee /tmp/flink_build_log 2>&1
check_in_container_install_success /tmp/flink_build_log

#echo " - TODO"
#docker exec -it flink_template bash


echo " - Cleaning up image"
docker exec -i flink_template apt-get remove -y git gcc adwaita-icon-theme > /tmp/flink_build_log 2>&1
docker exec -i flink_template apt-get -y auto-remove > /tmp/flink_build_log 2>&1

echo " - Closing and saving image flink"
close_and_save_image flink_template /tmp/flink_build_log $FLINK_VERSION
