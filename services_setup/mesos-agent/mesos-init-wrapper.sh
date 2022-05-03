#!/bin/bash

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

set -o errexit -o nounset -o pipefail

function -h {
cat <<USAGE
 USAGE: mesos-init-wrapper (master|slave)
  Run Mesos in master or slave mode, loading environment files, setting up
  logging and loading config parameters as appropriate.
  To configure Mesos, you have many options:
 *  You can set environment variables (including the MESOS_* variables) in
    files under /etc/default/:
      /usr/local/etc/mesos/mesos-env.sh          # For both slave and master.
      /usr/local/etc/mesos/mesos-master-env.sh   # For the master only.
      /usr/local/etc/mesos/mesos-slave-env.sh    # For the slave only.
 *  To set command line options for the slave or master, you can create files
    under certain directories:
      /usr/local/etc/mesos-slave            # For the slave only.
      /usr/local/etc/mesos-master           # For the master only.
    For example, to set the port for the slave:
      echo 5050 > /etc/mesos-slave/port
    To set the switch user flag:
      touch /etc/mesos-slave/?switch_user
    To explicitly disable it:
      touch /etc/mesos-slave/?no-switch_user
    Adding attributes and resources to the slaves is slightly more granular.
    Although you can pass them all at once with files called 'attributes' and
    'resources', you can also set them by creating files under directories
    labeled 'attributes' or 'resources':
      echo north-west > /etc/mesos-slave/attributes/rack
    This is intended to allow easy addition and removal of attributes and
    resources from the slave configuration.
USAGE
}; function --help { -h ;}                 # A nice way to handle -h and --help
#export LC_ALL=en_US.UTF-8                    # A locale that works consistently

function main {
  err "Please use \`master' or \`slave'."
}

function slave {
  local etc_slave=/usr/local/etc/mesos-slave
  local args=()
  local attributes=()
  local resources=()
  # Call mesosphere-dnsconfig if present on the system to generate config files.
  #[ -x /usr/bin/mesosphere-dnsconfig ] && mesosphere-dnsconfig -write -service=mesos-slave
  set -o allexport
  [[ ! -f /usr/local/etc/mesos/mesos-env.sh ]]       || . /usr/local/etc/mesos/mesos-env.sh
  [[ ! -f /usr/local/etc/mesos/mesos-slave-env.sh ]] || . /usr/local/etc/mesos/mesos-slave-env.sh
  set +o allexport
  [[ ! ${ULIMIT:-} ]]    || ulimit $ULIMIT
  [[ ! ${MASTER:-} ]]    || args+=( --master="$MASTER" )
  [[ ! ${IP:-} ]]        || args+=( --ip="$IP" )
  [[ ! ${LOGS:-} ]]      || args+=( --log_dir="$LOGS" )
  [[ ! ${ISOLATION:-} ]] || args+=( --isolation="$ISOLATION" )
  for f in "$etc_slave"/* # attributes ip resources isolation &al.
  do
    if [[ -f $f ]]
    then
      local name="$(basename "$f")"
      if [[ $name == '?'* ]]         # Recognize flags (options without values)
      then args+=( --"${name#'?'}" )
      else args+=( --"$name"="$(cat "$f")" )
      fi
    fi
  done
  # We allow the great multitude of attributes and resources to be specified
  # in directories, where the filename is the key and the contents its value.
  for f in "$etc_slave"/attributes/*
  do [[ ! -s $f ]] || attributes+=( "$(basename "$f")":"$(cat "$f")" )
  done
  if [[ ${#attributes[@]} -gt 0 ]]
  then
    local formatted="$(printf ';%s' "${attributes[@]}")"
    args+=( --attributes="${formatted:1}" )        # NB: Leading ';' is clipped
  fi
  for f in "$etc_slave"/resources/*
  do [[ ! -s $f ]] || resources+=( "$(basename "$f")":"$(cat "$f")" )
  done
  if [[ ${#resources[@]} -gt 0 ]]
  then
    local formatted="$(printf ';%s' "${resources[@]}")"
    args+=( --resources="${formatted:1}" )         # NB: Leading ';' is clipped
  fi

  if [[ "${args[@]:-}" == *'--no-logger'* ]]
  then
    local clean_args=()
    for i in "${args[@]}"; do
      if [[ "${i}" != "--no-logger" ]]; then
        clean_args+=( "${i}" )
      fi
    done
    exec /usr/local/sbin/mesos-slave "${clean_args[@]}"
  else
    logged /usr/local/sbin/mesos-slave "${args[@]:-}"
  fi
}

function master {
  local etc_master=/usr/local/etc/mesos-master
  local args=()
  # Call mesosphere-dnsconfig if present on the system to generate config files.
  #[ -x /usr/bin/mesosphere-dnsconfig ] && mesosphere-dnsconfig -write -service=mesos-master
  set -o allexport
  [[ ! -f /usr/local/etc/mesos/mesos-env.sh ]]        || . /usr/local/etc/mesos/mesos-env.sh
  [[ ! -f /usr/local/etc/mesos/mesos-master-env.sh ]] || . /usr/local/etc/mesos/mesos-master-env.sh
  set +o allexport
  [[ ! ${ULIMIT:-} ]]  || ulimit $ULIMIT
  [[ ! ${ZK:-} ]]      || args+=( --zk="$ZK" )
  [[ ! ${IP:-} ]]      || args+=( --ip="$IP" )
  [[ ! ${PORT:-} ]]    || args+=( --port="$PORT" )
  [[ ! ${CLUSTER:-} ]] || args+=( --cluster="$CLUSTER" )
  [[ ! ${LOGS:-} ]]    || args+=( --log_dir="$LOGS" )
  for f in "$etc_master"/* # cluster log_dir port &al.
  do
    if [[ -f $f ]]
    then
      local name="$(basename "$f")"
      if [[ $name == '?'* ]]         # Recognize flags (options without values)
      then args+=( --"${name#'?'}" )
      else args+=( --"$name"="$(cat "$f")" )
      fi
    fi
  done

  if [[ "${args[@]:-}" == *'--no-logger'* ]]
  then
    local clean_args=()
    for i in "${args[@]}"; do
      if [[ "${i}" != "--no-logger" ]]; then
        clean_args+=( "${i}" )
      fi
    done
    exec /usr/local/sbin/mesos-master "${clean_args[@]}"
  else
    logged /usr/local/sbin/mesos-master "${args[@]:-}"
  fi
}

# Send all output to syslog and tag with PID and executable basename.
function logged {
  local tag="${1##*/}[$$]"
  exec 1> >(exec logger -p user.info -t "$tag")
  exec 2> >(exec logger -p user.err  -t "$tag")
  exec "$@"
}

function msg { out "$*" >&2 ;}
function err { local x=$? ; msg "$*" ; return $(( $x == 0 ? 1 : $x )) ;}
function out { printf '%s\n' "$*" ;}

if [[ ${1:-} ]] && declare -F | cut -d' ' -f3 | fgrep -qx -- "${1:-}"
then "$@"
else main "$@"
fi

