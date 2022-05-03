#!/usr/bin/env bash

#
# This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
# well to this individual file than to the Eskimo Project as a whole.
#
# Copyright 2019 - 2022 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $SCRIPT_DIR/common.sh "$@"


echo "-- INSTALLING PROMETHEUS Mesos Exporter ---------------------------------------"

if [ -z "$PROMETHEUS_VERSION" ]; then
    echo "Need to set PROMETHEUS_VERSION environment variable before calling this script !"
    exit 1
fi

if [ -z "$PROMETHEUS_MESOS_EXPORTER_VERSION" ]; then
    echo "Need to set PROMETHEUS_MESOS_EXPORTER_VERSION environment variable before calling this script !"
    exit 1
fi

saved_dir=`pwd`
function returned_to_saved_dir() {
     cd $saved_dir
}
trap returned_to_saved_dir 15
trap returned_to_saved_dir EXIT

echo " - Creating distribution directory"
sudo mkdir -p /usr/local/lib/prometheus-$PROMETHEUS_VERSION.linux-amd64/exporters/mesos_exporter-$PROMETHEUS_MESOS_EXPORTER_VERSION
sudo chown -R root.staff /usr/local/lib/prometheus-$PROMETHEUS_VERSION.linux-amd64/exporters/mesos_exporter-$PROMETHEUS_MESOS_EXPORTER_VERSION
cd /usr/local/lib/prometheus-$PROMETHEUS_VERSION.linux-amd64/exporters/mesos_exporter-$PROMETHEUS_MESOS_EXPORTER_VERSION


echo " - Downloading mesos_exporter-$PROMETHEUS_MESOS_EXPORTER_VERSION"
export GOPATH=`pwd`
go get github.com/mesos/mesos_exporter  > /tmp/prometheus_install_log 2>&1
if [[ $? != 0 ]]; then
    echo " -> Failed to get mesos_exporter-$PROMETHEUS_MESOS_EXPORTER_VERSION from github."
    cat /tmp/prometheus_install_log
    exit -1
fi

echo " - symlinking mesos_exporter to /usr/local/lib/prometheus/exporters/mesos_exporter"
sudo ln -s /usr/local/lib/prometheus-$PROMETHEUS_VERSION.linux-amd64/exporters/mesos_exporter-$PROMETHEUS_MESOS_EXPORTER_VERSION \
        /usr/local/lib/prometheus/exporters/mesos_exporter


echo " - Registering test cleaning traps"
export ES_PROC_ID=-1
export MESOS_EXPORTER_PROC_ID=-1
function check_stop_mesos_exporter(){
    if [[ $ES_PROC_ID != -1 ]]; then
        echo " - Stopping ES !!"
        kill -15 $ES_PROC_ID
    fi
    if [[ $MESOS_EXPORTER_PROC_ID != -1 ]]; then
        echo " - Stopping Prometheus Mesos Exporter !!"
        kill -15 $MESOS_EXPORTER_PROC_ID
    fi
}
trap check_stop_mesos_exporter 15
trap check_stop_mesos_exporter EXIT


echo " - Starting Prometheus Mesos Exporter"
# (localhost is dummy, not expected to work but doesn't crash the node exporter)
/usr/local/lib/prometheus/exporters/mesos_exporter/bin/mesos_exporter -master localhost:5050 -logLevel debug > /tmp/prometheus_run_log 2>&1 &
export MESOS_EXPORTER_PROC_ID=$!

echo " - Checking Mesos Exporter startup"
sleep 5
if [[ `ps -e | grep $MESOS_EXPORTER_PROC_ID` == "" ]]; then
    echo " !! Failed to start Mesos Exporter !!"
    cat /tmp/prometheus_run_log
    exit -8
fi

echo " - Stopping Mesos Exporter"
kill -15 $MESOS_EXPORTER_PROC_ID
export MESOS_EXPORTER_PROC_ID=-1


# Caution : the in container setup script must mandatorily finish with this log"
echo " - In container install SUCCESS"