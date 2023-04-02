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


echo "-- SETTING UP KIBANA -----------------------------------------------------------"

echo " - Simlinking Kibana config to /usr/local/etc/kibana"
sudo ln -s /usr/local/lib/kibana/config /usr/local/etc/kibana

echo " - Simlinking Kibana binaries and scripts to /usr/local/sbin"
sudo ln -s /usr/local/lib/kibana/bin/kibana /usr/local/sbin/kibana

echo " - Adapting kibana config"
sudo sed -i s/"#server.host: \"localhost\""/"server.host: \"0.0.0.0\""/g /usr/local/etc/kibana/kibana.yml
sudo sed -i s/"#server.name: \"your-hostname\""/"server.name: \"Eskimo\""/g /usr/local/etc/kibana/kibana.yml

sudo sed -i s/"#pid.file: \/var\/run\/kibana.pid"/"pid.file: \/var\/run\/elasticsearch\/kibana\/kibana.pid"/g /usr/local/etc/kibana//kibana.yml




# It runs behind a proxy in eskimo
sudo sed -i s/"#server.basePath: \"\""/"server.basePath: \"\/kibana\""/g /usr/local/etc/kibana/kibana.yml

#sudo sed -i s/"#server.rewriteBasePath: false"/"server.rewriteBasePath: true"/g /usr/local/etc/kibana/kibana.yml




echo " - enabling user elasticsearch to change config"
chown -R elasticsearch. /usr/local/lib/kibana/config

echo " - Copying sample objects"
sudo mkdir /usr/local/lib/kibana/samples/
sudo cp -Rf /eskimo/samples/* /usr/local/lib/kibana/samples/
sudo chown -R elasticsearch. /usr/local/lib/kibana/samples/


# Caution : the in container setup script must mandatorily finish with this log"
echo "$IN_CONTAINER_CONFIG_SUCESS_MESSAGE"


