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

echo "Building Spark Image"
echo "--------------------------------------------------------------------------------"

# reinitializing log
rm -f /tmp/spark_build_log

echo " - Building image spark"
build_image spark_template /tmp/spark_build_log

echo " - Installing OpenJDK 11"
docker exec -i spark_template apt-get install -y openjdk-11-jdk-headless > /tmp/spark_build_log 2>&1
fail_if_error $? "/tmp/spark_build_log" -3

echo " - Installing scala"
docker exec -i spark_template bash -c ". /common/common.sh && install_scala"  > /tmp/spark_build_log 2>&1
fail_if_error $? "/tmp/spark_build_log" -4

echo " - Installing python"
docker exec -i spark_template apt-get -y install  python3-dev python3-six python3-virtualenv python3-pip > /tmp/spark_build_log 2>&1
fail_if_error $? "/tmp/spark_build_log" -5

echo " - Installing python elasticsearch and kafka clients and other utilities"
docker exec -i spark_template pip3 install elasticsearch kafka-python > /tmp/spark_build_log 2>&1
fail_if_error $? "/tmp/spark_build_log" -6

echo " - Installing other python packages"
docker exec -i spark_template pip3 install requests filelock > /tmp/spark_build_log 2>&1
fail_if_error $? "/tmp/spark_build_log" -11

echo " - Switching python default version to 3.x"
docker exec -i spark_template update-alternatives --force --install /usr/bin/python python /usr/bin/python3.9 2 > /tmp/flink_build_log 2>&1
fail_if_error $? "/tmp/flink_build_log" -5

echo " - Installing GlusterFS client"
docker exec -i spark_template apt-get -y install  glusterfs-client > /tmp/spark_build_log 2>&1
fail_if_error $? "/tmp/spark_build_log" -10

echo " - Installing other dependencies"
docker exec -i spark_template apt-get -y install tini > /tmp/spark_build_log 2>&1
fail_if_error $? "/tmp/spark_build_log" -10


echo " - Installing spark"
docker exec -i spark_template bash /scripts/installSpark.sh | tee /tmp/spark_build_log 2>&1
check_in_container_install_success /tmp/spark_build_log

echo " - Installing ESHadoop connector"
docker exec -i spark_template bash /scripts/installESHadoop.sh | tee  /tmp/spark_build_log 2>&1
check_in_container_install_success /tmp/spark_build_log

echo " - Installing Spark-Kafka connector"
docker exec -i spark_template bash /scripts/installSparkKafkaConnector.sh  | tee /tmp/spark_build_log 2>&1
check_in_container_install_success /tmp/spark_build_log


#echo " - TODO"
#docker exec -it spark_template bash


echo " - Cleaning up image"
docker exec -i spark_template apt-get remove -y git gcc adwaita-icon-theme >> /tmp/spark_build_log 2>&1
docker exec -i spark_template apt-get -y auto-remove >> /tmp/spark_build_log 2>&1

echo " - Closing and saving image spark"
close_and_save_image spark_template /tmp/spark_build_log $SPARK_VERSION
