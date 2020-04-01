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

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $SCRIPT_DIR/common.sh "$@"


echo "-- SETTING UP LOGSTASH -----------------------------------------------------------"

echo " - Getting elasticsearch user_id"
set +e
elasticsearch_user_id=`id -u elasticsearch 2> /tmp/cerebro_install_log`
set -e


echo " - creating logstash wrapper in /usr/local/bin"
create_binary_wrapper /usr/local/lib/logstash/bin/logstash /usr/local/bin/logstash

echo " - Simlinking logstash config directory to /usr/local/etc"
sudo ln -s /usr/local/lib/logstash/config /usr/local/etc/logstash

echo " - Simlinking logstash log directory to /var/log/logstash/"
sudo rm -Rf /usr/local/lib/logstash/logs
sudo ln -s /var/log/elasticsearch/logstash /usr/local/lib/logstash/logs

echo " - Simlinking logstash working directory to /var/lib/elasticsearch/logstash/"
sudo rm -Rf /usr/local/lib/logstash/data/
sudo ln -s /var/lib/elasticsearch/logstash /usr/local/lib/logstash/data


# Caution : the in container setup script must mandatorily finish with this log"
echo " - In container config SUCCESS"