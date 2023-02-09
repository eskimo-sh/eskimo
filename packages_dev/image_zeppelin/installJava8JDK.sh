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

echo "-- INSTALLING OPENJDK 8 FOR FLINK INTERPRETER  --------------------------------"

if [ -z "$ZEPPELIN_VERSION" ]; then
    echo "Need to set ZEPPELIN_VERSION environment variable before calling this script !"
    exit 1
fi

saved_dir=$(pwd)
function returned_to_saved_dir() {
     cd $saved_dir || true
}
trap returned_to_saved_dir 15
trap returned_to_saved_dir EXIT
trap returned_to_saved_dir ERR


echo " - Changing to temp directory"
mkdir -p /tmp/zeppelin_setup_java8/
cd /tmp/zeppelin_setup_java8/ || (echo "couldn't cd to /tmp/zeppelin_setup_java8/" && exit 1)


wget https://builds.openlogic.com/downloadJDK/openlogic-openjdk/${OPENLOGIC_JDK_8_VERSION}/openlogic-openjdk-${OPENLOGIC_JDK_8_VERSION}-linux-x64.tar.gz  > /tmp/zeppelin_install_log 2>&1
if [[ $? != 0 ]]; then
   echo " -> Failed to downolad openlogic-openjdk-${OPENLOGIC_JDK_8_VERSION}-linux-x64 from apache. Trying to download from niceideas.ch"
   wget --no-check-certificate  https://www.niceideas.ch/mes/openlogic-openjdk-${OPENLOGIC_JDK_8_VERSION}-linux-x64.tar.gz >> /tmp/zeppelin_install_log 2>&1
   fail_if_error $? "/tmp/zeppelin_install_log" -1
fi

echo " - Extracting openlogic-openjdk-${OPENLOGIC_JDK_8_VERSION}"
tar -xvf openlogic-openjdk-${OPENLOGIC_JDK_8_VERSION}-linux-x64.tar.gz > /tmp/zeppelin_install_log 2>&1
fail_if_error $? "/tmp/zeppelin_install_log"

echo " - Installing openlogic-openjdk-${OPENLOGIC_JDK_8_VERSION}"
mkdir -p /usr/local/lib/jvm
mv openlogic-openjdk-${OPENLOGIC_JDK_8_VERSION}-linux-x64 /usr/local/lib/jvm/openjdk-8


echo " - Cleaning up"
rm -Rf /tmp/zeppelin_setup_java8/
rm -Rf ~/.m2/



# Caution : the in container setup script must mandatorily finish with this log"
echo "$IN_CONTAINER_INSTALL_SUCESS_MESSAGE"






