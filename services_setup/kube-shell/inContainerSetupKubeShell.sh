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

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $SCRIPT_DIR/common.sh "$@"


ESKIMO_USER=$1
if [[ $ESKIMO_USER == "" ]]; then
    echo " - Didn't get Eskimo User as argument"
    exit 1
fi

ESKIMO_USER_ID=$2
if [[ $ESKIMO_USER_ID == "" ]]; then
    echo " - Didn't get Eskimo User ID as argument"
    exit 1
fi


echo " - Creating eskimo user (if not exist) in container"
set +e
eskimo_user_id=$(id -u $ESKIMO_USER 2> es_common_install_log)
set -e
echo " - Found user with ID $eskimo_user_id"
if [[ $eskimo_user_id == "" ]]; then
    echo " - Creating eskimo user in container with id $ESKIMO_USER_ID"
    useradd -u $ESKIMO_USER_ID $ESKIMO_USER
elif [[ "$eskimo_user_id" != "$ESKIMO_USER_ID" ]]; then
    echo "Docker Eskimo USER ID is $eskimo_user_id while requested USER ID is $ESKIMO_USER_ID"
    exit 2
fi


echo " - Enabling Eskimo user run kube_do and sudo bash"
sudo bash -c "echo \"$ESKIMO_USER  ALL = NOPASSWD:SETENV: /bin/bash /usr/local/sbin/import-hosts.sh\" >> /etc/sudoers.d/$ESKIMO_USER"
sudo bash -c "echo \"$ESKIMO_USER  ALL = NOPASSWD:SETENV: /bin/bash\" >> /etc/sudoers.d/$ESKIMO_USER"
sudo bash -c "echo \"$ESKIMO_USER  ALL = NOPASSWD:SETENV: /bin/su\" >> /etc/sudoers.d/$ESKIMO_USER"

# Caution : the in container setup script must mandatorily finish with this log"
echo "$IN_CONTAINER_CONFIG_SUCESS_MESSAGE"
