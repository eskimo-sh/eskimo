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

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. $SCRIPT_DIR/common.sh "$@"

echo "-- INSTALLING MARATHON -----------------------------------------------------------"

if [ -z "$MARATHON_VERSION_SOURCES_GITHUB" ]; then
    echo "Need to set MARATHON_VERSION_SOURCES_GITHUB environment variable before calling this script !"
    exit 1
fi

saved_dir=$(pwd)
function returned_to_saved_dir() {
    cd $saved_dir
}
trap returned_to_saved_dir 15
trap returned_to_saved_dir EXIT

echo " - Changing to temp directory"
rm -Rf /tmp/marathon_source_build
mkdir -p /tmp/marathon_source_build
cd /tmp/marathon_source_build

echo " - Updating dependencies for libmesos "
sudo DEBIAN_FRONTEND=noninteractive apt-get -y install \
    libcurl4-nss-dev libsasl2-dev libsasl2-modules maven libapr1-dev libsvn-dev zlib1g-dev \
    > /tmp/marathon_install_log 2>&1
fail_if_error $? "/tmp/marathon_install_log" -2

echo " - Downloading marathon-$MARATHON_VERSION_SOURCES_GITHUB"
git clone https://github.com/mesosphere/marathon.git > /tmp/marathon_install_log 2>&1
fail_if_error $? "/tmp/marathon_install_log" -1

echo " - Installing SBT build system"

echo "   + Adding sbt debian sources"
echo "deb https://dl.bintray.com/sbt/debian /" | sudo tee -a /etc/apt/sources.list.d/sbt.list > /tmp/marathon_install_log 2>&1
fail_if_error $? "/tmp/marathon_install_log" -1

echo "   + Adding sbt source to keyring"
curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | sudo apt-key add > /tmp/marathon_install_log 2>&1
fail_if_error $? "/tmp/marathon_install_log" -1

echo "   + Updating apt sources"
sudo apt-get update > /tmp/marathon_install_log 2>&1
fail_if_error $? "/tmp/marathon_install_log" -1

echo "   + Installing sbt"
sudo apt-get install sbt > /tmp/marathon_install_log 2>&1
fail_if_error $? "/tmp/marathon_install_log" -1

cd marathon

echo " - Checking out branch $MARATHON_VERSION_SOURCES_GITHUB"
git checkout tags/v$MARATHON_VERSION_SOURCES_GITHUB -b marathon-v"$MARATHON_VERSION_SOURCES_GITHUB"-branch > /tmp/marathon_install_log 2>&1
fail_if_error $? "/tmp/marathon_install_log" -1

# Patch for this problem
# [error] /tmp/marathon_source_build/marathon/src/main/scala/mesosphere/marathon/core/task/update/impl/TaskStatusUpdateProcessorImpl.scala:66:42: value REASON_CONTAINER_MEMORY_REQUEST_EXCEEDED is not a member of object org.apache.mesos.Protos.TaskStatus.Reason

# in src/main/scala/mesosphere/marathon/core/task/update/impl/TaskStatusUpdateProcessorImpl.scala
# Change

echo " - Patching branch $MARATHON_VERSION_SOURCES_GITHUB (https://jira.d2iq.com/browse/MARATHON-8750)"
sed -i -n '1h;1!H;${;g;s/'\
'      case MesosProtos.TaskStatus.Reason.REASON_CONTAINER_MEMORY_REQUEST_EXCEEDED =>\n'\
'        oomKilledTasksWithinLimits.increment()'\
'/'\
''\
'/g;p;}' src/main/scala/mesosphere/marathon/core/task/update/impl/TaskStatusUpdateProcessorImpl.scala


echo " - Building branch $MARATHON_VERSION_SOURCES_GITHUB"
sbt universal:packageZipTarball  > /tmp/marathon_install_log 2>&1
fail_if_error $? "/tmp/marathon_install_log" -1

echo " - Linking result tarball"
cd /tmp/marathon_source_build/marathon/target/universal/
ln -s marathon-$MARATHON_VERSION_SOURCES_GITHUB-*.tgz marathon-$MARATHON_VERSION_SOURCES_GITHUB.tgz




# Caution : the in container setup script must mandatorily finish with this log"
echo " - In container install SUCCESS"
