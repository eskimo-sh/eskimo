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

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $SCRIPT_DIR/common.sh "$@"

echo "-- INSTALLING LOGSTASH ---------------------------------------------------------"

if [ -z "$ES_VERSION" ]; then
    echo "Need to set ES_VERSION environment variable before calling this script !"
    exit 1
fi

saved_dir=$(pwd)
function returned_to_saved_dir() {
     cd $saved_dir || return
}
trap returned_to_saved_dir 15
trap returned_to_saved_dir EXIT
trap returned_to_saved_dir ERR

echo " - Changing to temp directory"
rm -Rf /tmp/ls_setup
mkdir -p /tmp/ls_setup
cd /tmp/ls_setup || (echo "Couldn't change to /tmp/ls_setup" && exit 200)

echo " - Downloading logstash-$ES_VERSION"
wget https://artifacts.elastic.co/downloads/logstash/logstash-$ES_VERSION-linux-x86_64.tar.gz > /tmp/ls_install_log 2>&1
if [[ $? != 0 ]]; then
    echo " -> Failed to downolad logstash-$ES_VERSION from https://artifacts.elastic.co/downloads/. Trying to download from niceideas.ch"
    wget https://niceideas.ch/mes/logstash-$ES_VERSION-linux-x86_64.tar.gz >> /tmp/ls_install_log 2>&1
    fail_if_error $? "/tmp/ls_install_log" -1
fi

echo " - Extracting logstash-$ES_VERSION"
tar -xvf logstash-$ES_VERSION-linux-x86_64.tar.gz > /tmp/ls_install_log 2>&1
fail_if_error $? "/tmp/ls_install_log" -2

echo " - Installing LogStash"
sudo chown root.staff -R logstash-$ES_VERSION
sudo mv logstash-$ES_VERSION /usr/local/lib

sudo mkdir -p /usr/local/lib/logstash-$ES_VERSION/data
sudo chmod -R 777 /usr/local/lib/logstash-$ES_VERSION/data
sudo mkdir -p /usr/local/lib/logstash-$ES_VERSION/logs
sudo chmod -R 777 /usr/local/lib/logstash-$ES_VERSION/logs
sudo chmod -R 755 /usr/local/lib/logstash-$ES_VERSION/config/

echo " - symlinking /usr/local/lib/logstash/ to /usr/local/lib/logstash-$ES_VERSION/"
ln -s /usr/local/lib/logstash-$ES_VERSION /usr/local/lib/logstash

echo " - Using logstash bundled JDK as OS JDK"
mkdir -p /usr/lib/jvm/
sudo chmod -R 755 /usr/local/lib/logstash/jdk/
ln -s  /usr/local/lib/logstash/jdk /usr/lib/jvm/java-1.11

echo "export PATH=/usr/lib/jvm/java-1.11/bin:$PATH" >> /etc/profile
echo "export PATH=/usr/lib/jvm/java-1.11/bin:$PATH" >> /etc/bash.bashrc

echo " - Testing logstash startup"
echo "test" | /usr/local/lib/logstash/bin/logstash -e 'input { stdin { } } output { stdout {} }'  > /tmp/logstash_run_log 2>&1 &
if [[ $? != 0 ]]; then
    echo "Failed to run logstash"
    cat /tmp/logstash_run_log
fi

echo " - Cleaning build directory"
sudo rm -Rf /tmp/ls_setup
returned_to_saved_dir


# Caution : the in container setup script must mandatorily finish with this log"
echo "$IN_CONTAINER_INSTALL_SUCESS_MESSAGE"