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


echo "-- INSTALLING DOCKER REGISTRY ------------------------------------------------------"


echo " - Changing to temp directory"
cd /tmp || (echo "Couldn't change to /tmp" && exit 200)

echo " - Downloading docker-registry_$DOCKER_REGISTRY_VERSION.deb "
wget http://ftp.debian.org/debian/pool/main/d/docker-registry/docker-registry_$DOCKER_REGISTRY_VERSION.deb > /tmp/docker_registry_install_log 2>&1
if [[ $? != 0 ]]; then
    echo " -> Failed to downolad docker-registry_$DOCKER_REGISTRY_VERSION.deb from debian. Trying to download from niceideas.ch"
    wget https://niceideas.ch/mes/docker-registry_$DOCKER_REGISTRY_VERSION.deb  >> /tmp/docker_registry_install_log 2>&1
    fail_if_error $? "/tmp/docker_registry_install_log" -1
fi

echo " - Installing docker registry"
dpkg -i docker-registry_$DOCKER_REGISTRY_VERSION.deb > /tmp/docker_registry_install_log 2>&1
fail_if_error $? "/tmp/docker_registry_install_log" -2


echo " - Creating configuration file"
mkdir -p /etc/docker/registry > /tmp/docker_registry_install_log 2>&1
fail_if_error $? "/tmp/docker_registry_install_log" -3

ln -s /etc/docker/registry /etc/docker_registry > /tmp/docker_registry_install_log 2>&1
fail_if_error $? "/tmp/docker_registry_install_log" -4


cat > /etc/docker_registry/config.yml <<- "EOF"
version: 0.1
log:
  fields:
    service: registry
storage:
  cache:
    blobdescriptor: inmemory
  filesystem:
    rootdirectory: /var/lib/docker_registry
  delete:
    enabled: true
http:
  addr: :5000
  headers:
    X-Content-Type-Options: [nosniff]
health:
  storagedriver:
    enabled: true
    interval: 10s
    threshold: 3
EOF


echo " - Downloading regclient regctl-$REGCLIENT_VERSION "
wget https://github.com/regclient/regclient/releases/download/v$REGCLIENT_VERSION/regctl-linux-amd64 > /tmp/docker_registry_install_log 2>&1
if [[ $? != 0 ]]; then
    echo " -> Failed to downolad regclient regctl-$REGCLIENT_VERSION from github. Trying to download from niceideas.ch"
    wget https://niceideas.ch/mes/regctl-linux-amd64-v$REGCLIENT_VERSION  >> /tmp/docker_registry_install_log 2>&1
    fail_if_error $? "/tmp/docker_registry_install_log" -1
fi

set +e
mv regctl-linux-amd64-v$REGCLIENT_VERSION regctl-linux-amd64 2>/dev/null
set -e

echo " - Installing regclient regctl"
set -e
mv regctl-linux-amd64 /usr/local/bin/
ln -s /usr/local/bin/regctl-linux-amd64 /usr/local/bin/regctl
chmod 755 /usr/local/bin/regctl-linux-amd64
set +e


echo " - Checking startup"
# check startup

echo "   + starting docker registry"
docker-registry serve /etc/docker_registry/config.yml > /dev/null 2>&1 &

sleep 5

echo "   + checking startup"
if [[ $(pgrep docker-registry) == "" ]]; then
    echo "Checking startup failed !!"
    exit 6
fi

echo "   + stopping docker registry"
killall docker-registry > /dev/null


# Caution : the in container setup script must mandatorily finish with this log"
echo "$IN_CONTAINER_INSTALL_SUCESS_MESSAGE"



