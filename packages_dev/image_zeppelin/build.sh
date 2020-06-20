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

echo "Building Zeppelin Image"
echo "--------------------------------------------------------------------------------"

# reinitializing log
rm -f /tmp/zeppelin_build_log

echo " - Checking if spark eskimo image is available"
if [[ `docker images -q eskimo:spark_template 2>/dev/null` == "" ]]; then
    echo " - Trying to loads spark image"
    for i in `ls -rt ../../packages_distrib/docker_template_spark*.tar.gz | tail -1`; do
        echo "   + loading image $i"
        gunzip -c $i | docker load >> /tmp/zeppelin_build_log 2>&1
        if [[ $? != 0 ]]; then
            echo "Could not load base image eskimo:spark_template"
            cat /tmp/zeppelin_build_log
            exit -1
        fi
    done
fi

echo " - Building image zeppelin"
build_image zeppelin_template /tmp/zeppelin_build_log

echo " - Installing a few utilities"
docker exec -i zeppelin_template apt-get install -y zip netcat sshpass >> /tmp/zeppelin_build_log 2>&1
fail_if_error $? "/tmp/zeppelin_build_log" -11

echo " - Installing python"
docker exec -i zeppelin_template apt-get -y install  python-dev python-six python-virtualenv python-pip cython >> /tmp/zeppelin_build_log 2>&1
fail_if_error $? "/tmp/zeppelin_build_log" -5

echo " - Installing python packages for datascience"
docker exec -i zeppelin_template pip install pandas scikit-learn matplotlib nltk plotly filelock py4j >> /tmp/zeppelin_build_log 2>&1
fail_if_error $? "/tmp/zeppelin_build_log" -12

echo " - Installing GlusterFS client"
docker exec -i zeppelin_template apt-get -y install glusterfs-client >> /tmp/zeppelin_build_log 2>&1
fail_if_error $? "/tmp/zeppelin_build_log" -10


echo " - Installing flink"
cp installFlink.sh __installFlinkEff.sh
docker exec -i zeppelin_template bash /scripts/__installFlinkEff.sh | tee -a /tmp/zeppelin_build_log 2>&1
if [[ `tail -n 1 /tmp/zeppelin_build_log | grep " - In container install SUCCESS"` == "" ]]; then
    echo " - In container install script ended up in error"
    cat /tmp/zeppelin_build_log
    exit 102
fi
rm -f __installFlinkEff.sh

echo " - Installing zeppelin"
if [[ $ZEPPELIN_IS_SNAPSHOT == "true" ]]; then
    docker exec -i zeppelin_template bash /scripts/installZeppelinFromSources.sh $USER $UID | tee -a /tmp/zeppelin_build_log 2>&1
else
    docker exec -i zeppelin_template bash /scripts/installZeppelin.sh | tee -a /tmp/zeppelin_build_log 2>&1
fi
if [[ `tail -n 1 /tmp/zeppelin_build_log | grep " - In container install SUCCESS"` == "" ]]; then
    echo " - In container install script ended up in error"
    cat /tmp/zeppelin_build_log
    exit 103
fi

#echo " - TODO"
#docker exec -i zeppelin TODO

#docker exec -it zeppelin bash

echo " - Closing and saving image zeppelin"
close_and_save_image zeppelin_template /tmp/zeppelin_build_log $ZEPPELIN_VERSION
