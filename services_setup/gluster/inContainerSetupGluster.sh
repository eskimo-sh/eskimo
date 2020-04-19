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

SELF_IP_ADDRESS=$1
if [[ $SELF_IP_ADDRESS == "" ]]; then
    echo " - No self ip address passed in argument"
    exit -2
fi

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $SCRIPT_DIR/common.sh "$@"


echo "-- SETTING UP GLUSTER FS ------------------------------------------------------------"


echo " - Removing system glusterfs-server auto-startup"
sudo update-rc.d glusterfs-server remove

echo " - Creating directories in which gluster implements volumes"
mkdir -p /var/lib/gluster/volume_bricks/


echo " - Creating gluster volume configuration file"

rm -Rf /var/lib/gluster/glusterfs.VOLUME_FILE
touch /var/lib/gluster/glusterfs.VOLUME_FILE

sudo bash -c "echo \"volume management\" >> /var/lib/gluster/glusterfs.VOLUME_FILE"
sudo bash -c "echo \"    type mgmt/glusterd\" >> /var/lib/gluster/glusterfs.VOLUME_FILE"
sudo bash -c "echo \"    option working-directory /var/lib/gluster/working_directory\" >> /var/lib/gluster/glusterfs.VOLUME_FILE"
sudo bash -c "echo \"    option transport-type socket,rdma\" >> /var/lib/gluster/glusterfs.VOLUME_FILE"
sudo bash -c "echo \"    option transport.socket.keepalive-time 10\" >> /var/lib/gluster/glusterfs.VOLUME_FILE"
sudo bash -c "echo \"    option transport.socket.keepalive-interval 2\" >> /var/lib/gluster/glusterfs.VOLUME_FILE"
sudo bash -c "echo \"    option transport.socket.read-fail-log off\" >> /var/lib/gluster/glusterfs.VOLUME_FILE"
sudo bash -c "echo \"    option ping-timeout 0\" >> /var/lib/gluster/glusterfs.VOLUME_FILE"
sudo bash -c "echo \"    option event-threads 1\" >> /var/lib/gluster/glusterfs.VOLUME_FILE"
sudo bash -c "echo \"    option transport.rdma.bind-address $SELF_IP_ADDRESS\" >> /var/lib/gluster/glusterfs.VOLUME_FILE"
sudo bash -c "echo \"    option transport.socket.bind-address $SELF_IP_ADDRESS\" >> /var/lib/gluster/glusterfs.VOLUME_FILE"
sudo bash -c "echo \"    option transport.tcp.bind-address $SELF_IP_ADDRESS\" >> /var/lib/gluster/glusterfs.VOLUME_FILE"
sudo bash -c "echo \"#   option transport.address-family inet6\" >> /var/lib/gluster/glusterfs.VOLUME_FILE"
sudo bash -c "echo \"#   option base-port 49152\" >> /var/lib/gluster/glusterfs.VOLUME_FILE"
sudo bash -c "echo \"end-volume\" >> /var/lib/gluster/glusterfs.VOLUME_FILE"


echo " - Creating in container cluster incoherence detection script"

sudo cat > /usr/local/sbin/detect-gluster-inconsistency.sh <<- "EOF"
#!/usr/bin/env bash

set -e

# Inject topology
. /etc/eskimo_topology.sh

export MASTER_IP_ADDRESS=`eval echo "\$"$(echo MASTER_GLUSTER_$SELF_IP_ADDRESS | tr -d .)`
if [[ $MASTER_IP_ADDRESS == "" ]]; then
    echo " - No gluster master found in topology"
    exit -3
fi

echo " - Checking out if master is in local pool"
if [[ `gluster pool list | grep $MASTER_IP_ADDRESS` == "" ]]; then
    MASTER_IN_LOCAL=0
else
    MASTER_IN_LOCAL=1
fi

echo " - Checking if local in master pool"
remote_result=`/usr/local/sbin/gluster_call_remote.sh $MASTER_IP_ADDRESS pool list`
if [[ `echo $remote_result | grep $SELF_IP_ADDRESS` == "" ]]; then
    LOCAL_IN_MASTER=0
else
    LOCAL_IN_MASTER=1
fi

echo " - Checking consistency "
if [[ $MASTER_IN_LOCAL == 0 ]]; then
    if [[ $LOCAL_IN_MASTER == 0 ]] ; then
        echo " -> gluster cluster is consistent. Neither local nor master know each others"
    else
        echo " -> gluster cluster is inconsistent. Local doesn't know master but master knows local"
        echo " - Attempting to remove local from master pool list"
        /usr/local/sbin/gluster_call_remote.sh $MASTER_IP_ADDRESS peer detach $SELF_IP_ADDRESS
        if [[ $? != 0 ]] ; then
            echo " - FAILED to remove local from master pool list"
            exit -1
        fi
    fi
else
    if [[ $LOCAL_IN_MASTER == 0 ]] ; then
        echo " -> gluster cluster is inconsistent. Master doesn't know local but local knows master"
        echo " - Attempting to remove master from local pool list"
        gluster peer detach $MASTER_IP_ADDRESS
        if [[ $? != 0 ]] ; then
            echo " - FAILED to remove master from local pool list"
            exit -1
        fi
    else
        echo " -> gluster cluster is consistent. both local and master know each others"
    fi
fi

EOF

sudo chmod 755 /usr/local/sbin/detect-gluster-inconsistency.sh



echo " - Creating in container general configuration script"

sudo cat > /usr/local/sbin/configure-general-gluster.sh <<- "EOF"
#!/usr/bin/env bash

set -e

# Inject topology
. /etc/eskimo_topology.sh

export MASTER_IP_ADDRESS=`eval echo "\$"$(echo MASTER_GLUSTER_$SELF_IP_ADDRESS | tr -d .)`
if [[ $MASTER_IP_ADDRESS == "" ]]; then
    echo " - No gluster master found in topology"
    exit -3
fi

if [[ $SELF_IP_ADDRESS == $MASTER_IP_ADDRESS ]]; then

    echo " - NO NEED TO ADD ANY PEER - Master is self node - likely only one node in gluster cluster"

else

    # add other master if not done
    if [[ `gluster pool list | grep $MASTER_IP_ADDRESS` == "" ]]; then

       # 3 attempts (to address concurrency issues coming from parallel installations)
       set +e
       for i in 1 2 3 ; do
           echo " - Trying : /usr/local/sbin/gluster_call_remote.sh $SELF_IP_ADDRESS peer probe $MASTER_IP_ADDRESS"
           /usr/local/sbin/gluster_call_remote.sh $SELF_IP_ADDRESS peer probe $MASTER_IP_ADDRESS
           if [[ $? != 0 ]]; then
               sleep 2
               continue
           fi
           break
       done

       # Trying the other way around
       if [[ `gluster pool list | grep $MASTER_IP_ADDRESS` == "" ]]; then
           for i in 1 2 3 ; do
               echo " - Trying : /usr/local/sbin/gluster_call_remote.sh $MASTER_IP_ADDRESS peer probe $SELF_IP_ADDRESS"
               /usr/local/sbin/gluster_call_remote.sh $MASTER_IP_ADDRESS peer probe $SELF_IP_ADDRESS
               if [[ $? != 0 ]]; then
                   sleep 2
                   continue
               fi
               break
           done
       fi

       # checking
       sleep 1
       if [[ `gluster pool list | grep $MASTER_IP_ADDRESS` == "" ]]; then
           echo "Failed to add $SELF_IP_ADDRESS to cluster where master is $MASTER_IP_ADDRESS"
           exit -101
       fi

       set -e
    fi

    # ensure peer is well connected
    sleep 1
    if [[ `gluster peer status | grep $MASTER_IP_ADDRESS` == "" ]]; then
       echo "Error : $MASTER_IP_ADDRESS not found in peers"
       gluster peer status
       exit -1
    fi
fi

EOF

sudo chmod 755 /usr/local/sbin/configure-general-gluster.sh


echo " - Creating in container mount script"

sudo cat > /usr/local/sbin/gluster-prepare-mount.sh <<- "EOF"
#!/usr/bin/env bash

set -e

VOL_NAME=$1
if [[ $VOL_NAME == "" ]]; then
   echo "Expecting volume name as first argument"
   exit -1
fi

UIDTOSET=$2

# Inject topology
. /etc/eskimo_topology.sh

export MASTER_IP_ADDRESS=`eval echo "\$"$(echo MASTER_GLUSTER_$SELF_IP_ADDRESS | tr -d .)`
if [[ $MASTER_IP_ADDRESS == "" ]]; then
    echo " - No gluster master found in topology"
    exit -3
fi

mkdir -p /var/lib/gluster/volume_bricks/

# define share with replicas 2 unless single node on cluster
if [[ $MASTER_IP_ADDRESS == $SELF_IP_ADDRESS ]]; then
    export NBR_REPLICAS="1"
else
    export NBR_REPLICAS="2"
fi

set +e

# 3 attempts (to address concurrency issues coming from parallel installations)
for i in 1 2 3 ; do
    if [[ `gluster volume list | grep $VOL_NAME` == "" ]]; then

        rm -Rf /var/lib/gluster/volume_bricks/$VOL_NAME

        if [[ "$NBR_REPLICAS" == "1" ]]; then
            gluster volume create $VOL_NAME transport tcp \
                    $MASTER_IP_ADDRESS:/var/lib/gluster/volume_bricks/$VOL_NAME
            if [[ $? != 0 ]]; then
                sleep 2
                continue
            fi
        else
            echo " - Creating single replica since likely single node in cluster"
            gluster volume create $VOL_NAME replica $NBR_REPLICAS transport tcp \
                    $SELF_IP_ADDRESS:/var/lib/gluster/volume_bricks/$VOL_NAME \
                    $MASTER_IP_ADDRESS:/var/lib/gluster/volume_bricks/$VOL_NAME
            if [[ $? != 0 ]]; then
                sleep 2
                continue
            fi
        fi

        if [[ $UIDTOSET != "" ]]; then
           gluster volume set $VOL_NAME storage.owner-uid $UIDTOSET
        fi

        break
    fi
done

# Make sure it has been created
if [[ `gluster volume list | grep $VOL_NAME` == "" ]]; then
    echo "Failed to create volume $VOL_NAME"
    exit -10
fi


# Start volume if not started

# 3 attempts (to address concurrency issues coming from parallel installations)
for i in 1 2 3 ; do
    if [[ `gluster volume info $VOL_NAME | grep "Status: Started"` == "" ]]; then
       gluster volume start $VOL_NAME
       if [[ $? != 0 ]]; then
           sleep 2
           continue
       fi
       break
    fi
done

if [[ `gluster volume info $VOL_NAME | grep "Status: Started"` == "" ]]; then
    echo "Failed to create start $VOL_NAME"
    exit -11
fi



EOF


sudo chmod 755 /usr/local/sbin/gluster-prepare-mount.sh


echo " - Enabling execution of gluster remote server scripts"
sudo chmod 755 /usr/local/sbin/gluster_remote.sh



# Caution : the in container setup script must mandatorily finish with this log"
echo " - In container config SUCCESS"
