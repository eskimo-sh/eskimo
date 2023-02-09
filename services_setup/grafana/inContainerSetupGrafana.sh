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


GRAFANA_USER_ID=$1
if [[ $GRAFANA_USER_ID == "" ]]; then
    echo " - Didn't get Grafana User ID as argument"
    exit 2
fi

ESKIMO_CONTEXT_PATH=$2


echo "-- SETTING UP GRAFANA -----------------------------------------------------------"

echo " - Creating grafana user (if not exist) in container"
set +e
grafana_user_id=$(id -u grafana 2>> /tmp/grafana_install_log)
set -e
echo " - Found user with ID $grafana_user_id"
if [[ $grafana_user_id == "" ]]; then
    useradd -u $GRAFANA_USER_ID grafana
elif [[ $grafana_user_id != $GRAFANA_USER_ID ]]; then
    echo "Docker grafana USER ID is $grafana_user_id while requested USER ID is $GRAFANA_USER_ID"
    exit 3
fi


echo " - Symlinking grafana directories to system directories"
sudo mkdir -p /var/lib/grafana
sudo chown -R grafana. /var/lib/grafana
sudo rm -Rf /var/lib/grafana/*

sudo rm -Rf /usr/local/lib/grafana/data
sudo ln -s /var/lib/grafana /usr/local/lib/grafana/data

sudo rm -Rf /usr/local/lib/grafana/data/log
sudo ln -s /var/log/grafana /usr/local/lib/grafana/data/log


echo " - Adapting grafana configuration file defaults.ini"
sudo sed -i s/"allow_embedding = false"/"allow_embedding = true"/g /usr/local/lib/grafana/conf/defaults.ini
sudo sed -i s/"admin_user = admin"/"admin_user = eskimo"/g /usr/local/lib/grafana/conf/defaults.ini
sudo sed -i s/"admin_password = admin"/"admin_password = eskimo"/g /usr/local/lib/grafana/conf/defaults.ini
sudo sed -i s/"reporting_enabled = true"/"reporting_enabled = false"/g /usr/local/lib/grafana/conf/defaults.ini
sudo sed -i s/"default_theme = dark"/"default_theme = light"/g /usr/local/lib/grafana/conf/defaults.ini
sudo sed -i s/"plugins = data\/plugins"/"plugins = plugins"/g /usr/local/lib/grafana/conf/defaults.ini

echo " - Enabling anonymous access"
sudo sed -i -n '1h;1!H;${;g;s/'\
'# enable anonymous access\nenabled = false'\
'/'\
'# enable anonymous access\nenabled = true'\
'/g;p;}' /usr/local/lib/grafana/conf/defaults.ini

# #################################### Server ##############################
# [server]
# # Protocol (http, https, socket)
# protocol = http
#
# # The ip address to bind to, empty will bind to all interfaces
# http_addr =
#
# # The http port to use
# http_port = 3000
#
# # The public facing domain name used to access grafana from a browser
# domain = localhost
#
# # Redirect to correct domain if host header does not match domain
# # Prevents DNS rebinding attacks
# enforce_domain = false
#
# # The full public facing url
# root_url = %(protocol)s://%(domain)s:%(http_port)s/
#
# # Serve Grafana from subpath specified in `root_url` setting. By default it is set to `false` for compatibility reasons.
# serve_from_sub_path = false



#if [[ $ESKIMO_CONTEXT_PATH != "" ]]; then
#    sed -i s/"root_url = %(protocol)s:\/\/%(domain)s:%(http_port)s\/"/"root_url = %(protocol)s:\/\/%(domain)s:%(http_port)s\/$ESKIMO_CONTEXT_PATH\/grafana\/api\/v1\/namespaces\/default\/services\/grafana:31300\/proxy\/"/g \
#        /usr/local/lib/grafana/conf/defaults.ini
#else
#    sed -i s/"root_url = %(protocol)s:\/\/%(domain)s:%(http_port)s\/"/"root_url = %(protocol)s:\/\/%(domain)s:%(http_port)s\/grafana\/api\/v1\/namespaces\/default\/services\/grafana:31300\/proxy\/"/g \
#        /usr/local/lib/grafana/conf/defaults.ini
#fi

if [[ $ESKIMO_CONTEXT_PATH != "" ]]; then
    sed -i s/"root_url = %(protocol)s:\/\/%(domain)s:%(http_port)s\/"/"root_url = \/$ESKIMO_CONTEXT_PATH\/grafana\/"/g \
        /usr/local/lib/grafana/conf/defaults.ini
else
    sed -i s/"root_url = %(protocol)s:\/\/%(domain)s:%(http_port)s\/"/"root_url = \/grafana\/"/g \
        /usr/local/lib/grafana/conf/defaults.ini
fi

sed -i s/"serve_from_sub_path = false"/"serve_from_sub_path = true"/g /usr/local/lib/grafana/conf/defaults.ini



echo " - Copying provisioning configuration"
sudo cp -Rf /eskimo/provisioning/* /usr/local/lib/grafana/conf/provisioning/

echo " - Enabling user grafana to change configuration files"
sudo chown -R grafana. /usr/local/lib/grafana/conf/


# Caution : the in container setup script must mandatorily finish with this log"
echo "$IN_CONTAINER_CONFIG_SUCESS_MESSAGE"


