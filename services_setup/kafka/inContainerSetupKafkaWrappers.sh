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

echo " - Creating kafka binaries wrappres to /usr/local/bin"

for i in $(ls -1 /usr/local/lib/kafka/bin/connect*); do
    create_binary_wrapper $i "/usr/local/bin/kafka-$(basename $i)"
done
create_binary_wrapper /usr/local/lib/kafka/bin/kafka-broker-api-versions.sh /usr/local/bin/kafka-broker-api-versions.sh
create_binary_wrapper /usr/local/lib/kafka/bin/kafka-console-consumer.sh /usr/local/bin/kafka-console-consumer.sh
create_binary_wrapper /usr/local/lib/kafka/bin/kafka-console-producer.sh /usr/local/bin/kafka-console-producer.sh
create_binary_wrapper /usr/local/lib/kafka/bin/kafka-consumer-groups.sh /usr/local/bin/kafka-consumer-groups.sh
create_binary_wrapper /usr/local/lib/kafka/bin/kafka-consumer-perf-test.sh /usr/local/bin/kafka-consumer-perf-test.sh
create_binary_wrapper /usr/local/lib/kafka/bin/kafka-delegation-tokens.sh /usr/local/bin/kafka-delegation-tokens.sh
create_binary_wrapper /usr/local/lib/kafka/bin/kafka-delete-records.sh /usr/local/bin/kafka-delete-records.sh
create_binary_wrapper /usr/local/lib/kafka/bin/kafka-dump-log.sh /usr/local/bin/kafka-dump-log.sh
create_binary_wrapper /usr/local/lib/kafka/bin/kafka-log-dirs.sh /usr/local/bin/kafka-log-dirs.sh
create_binary_wrapper /usr/local/lib/kafka/bin/kafka-producer-perf-test.sh /usr/local/bin/kafka-producer-perf-test.sh
create_binary_wrapper /usr/local/lib/kafka/bin/kafka-run-class.sh /usr/local/bin/kafka-run-class.sh
create_binary_wrapper /usr/local/lib/kafka/bin/kafka-topics.sh /usr/local/bin/kafka-topics.sh
create_binary_wrapper /usr/local/lib/kafka/bin/kafka-verifiable-consumer.sh /usr/local/bin/kafka-verifiable-consumer.sh
create_binary_wrapper /usr/local/lib/kafka/bin/kafka-verifiable-producer.sh /usr/local/bin/kafka-verifiable-producer.sh
create_binary_wrapper /usr/local/lib/kafka/bin/trogdor.sh /usr/local/bin/kafka-trogdor.sh


echo " - Creating kafka system binaries wrappers to /usr/local/sbin"
create_binary_wrapper /usr/local/lib/kafka/bin/kafka-acls.sh /usr/local/sbin/kafka-acls.sh
create_binary_wrapper /usr/local/lib/kafka/bin/kafka-configs.sh /usr/local/sbin/kafka-configs.sh
create_binary_wrapper /usr/local/lib/kafka/bin/kafka-server-start.sh /usr/local/sbin/kafka-server-start.sh
create_binary_wrapper /usr/local/lib/kafka/bin/kafka-server-stop.sh /usr/local/sbin/kafka-server-stop.sh
create_binary_wrapper /usr/local/lib/kafka/bin/kafka-configs.sh /usr/local/sbin/kafka-configs.sh
create_binary_wrapper /usr/local/lib/kafka/bin/kafka-mirror-maker.sh /usr/local/sbin/kafka-mirror-maker.sh
create_binary_wrapper /usr/local/lib/kafka/bin/kafka-preferred-replica-election.sh /usr/local/sbin/kafka-preferred-replica-election.sh
create_binary_wrapper /usr/local/lib/kafka/bin/kafka-reassign-partitions.sh /usr/local/sbin/kafka-reassign-partitions.sh
create_binary_wrapper /usr/local/lib/kafka/bin/kafka-replica-verification.sh /usr/local/sbin/kafka-replica-verification.sh
create_binary_wrapper /usr/local/lib/kafka/bin/kafka-streams-application-reset.sh /usr/local/sbin/kafka-streams-application-reset.sh



# Caution : the in container setup script must mandatorily finish with this log"
echo "$IN_CONTAINER_CONFIG_SUCESS_MESSAGE"