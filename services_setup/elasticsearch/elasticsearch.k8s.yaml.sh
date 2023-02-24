#!/bin/bash
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

. /usr/local/sbin/eskimo-utils.sh

# Loading topology
loadTopology

TEMP_FILE=$(mktemp)

cat > $TEMP_FILE <<EOF
apiVersion: v1
kind: Service
metadata:
  name: elasticsearch
  namespace: eskimo
  labels:
    service: elasticsearch
spec:
  clusterIP: None
  ports:
    - port: 9200
      name: serving
    - port: 9300
      name: node-to-node
  selector:
    service: elasticsearch

---

apiVersion: v1
kind: PersistentVolume
metadata:
  name: es-var-lib-volume
  namespace: eskimo
  labels:
    type: local
spec:
  storageClassName: es-local-storage
  volumeMode: Filesystem
  capacity:
    storage: 20Gi
  accessModes:
    - ReadWriteOnce
  local:
    path: "/var/lib/elasticsearch"
  nodeAffinity: # dummy node affinity since one is required.
    required:
      nodeSelectorTerms:
        - matchExpressions:
            - key: kubernetes.io/os
              operator: In
              values:
                - linux

---

apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: es-var-lib-claim
  namespace: eskimo
spec:
  storageClassName: es-local-storage
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 5Gi

---

apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: elasticsearch
  namespace: eskimo
  labels:
    service: elasticsearch
spec:
  serviceName: elasticsearch
  replicas: $(get_replicas elasticsearch)
  selector:
    matchLabels:
      service: elasticsearch
  template:
    metadata:
      labels:
        service: elasticsearch
    spec:
      # Force all replicas on different nodes
      topologySpreadConstraints:
        - maxSkew: 1
          topologyKey: kubernetes.io/hostname
          whenUnsatisfiable: DoNotSchedule
          labelSelector:
            matchLabels:
              service: elasticsearch
      tolerations:
        # this toleration is to have the daemonset runnable on master nodes
        # remove it if your masters can't run pods
        - key: node-role.kubernetes.io/master
          operator: Exists
          effect: NoSchedule
      containers:
        - name: elasticsearch
          image: kubernetes.registry:5000/elasticsearch:$(get_last_tag elasticsearch)
          resources:
            requests:
              cpu: "$ESKIMO_KUBE_REQUEST_ELASTICSEARCH_CPU"
              memory: "$ESKIMO_KUBE_REQUEST_ELASTICSEARCH_RAM"
          ports:
            - containerPort: 9200
              name: http
            - containerPort: 9300
              name: tcp
          command: ["/usr/local/sbin/inContainerStartService.sh"]
          volumeMounts:
            - name: var-log-elasticsearch
              mountPath: /var/log/elasticsearch
            - name: var-lib-elasticsearch
              mountPath: /var/lib/elasticsearch
            - name: var-run-elasticsearch
              mountPath: /var/run/elasticsearch
            - name: etc-eskimo-topology
              mountPath: /etc/eskimo_topology.sh
            - name: etc-eskimo-services-settings
              mountPath: /etc/eskimo_services-settings.json
          env:
            - name: ESKIMO_NODE_NAME
              valueFrom:
                fieldRef:
                  fieldPath: metadata.name
          securityContext:
            allowPrivilegeEscalation: true
            readOnlyRootFilesystem: false
            runAsUser: 3301
            runAsGroup: 3301
      terminationGracePeriodSeconds: 60
      volumes:
        - name: var-log-elasticsearch
          hostPath:
            path: /var/log/elasticsearch
            type: Directory
        - name: var-lib-elasticsearch
          persistentVolumeClaim:
            claimName: es-var-lib-claim
        - name: var-run-elasticsearch
          hostPath:
            path: /var/run/elasticsearch
            type: Directory
        - name: etc-eskimo-topology
          hostPath:
            path: /etc/eskimo_topology.sh
            type: File
        - name: etc-eskimo-services-settings
          hostPath:
            path: /etc/eskimo_services-settings.json
            type: File
      #hostNetwork: true
EOF
cat $TEMP_FILE
rm -Rf $TEMP_FILE