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
  name: kafka
  namespace: eskimo
  labels:
    service: kafka
spec:
  clusterIP: None
  ports:
    - port: 9092
      name: serving
    - port: 9093
      name: serving-other
    - port: 9999
      name: control
  selector:
    service: kafka

---

apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: kafka
  namespace: eskimo
  labels:
    service: kafka
spec:
  serviceName: kafka
  replicas: $(get_replicas kafka)
  selector:
    matchLabels:
      service: kafka
  template:
    metadata:
      namespace: eskimo
      labels:
        service: kafka
    spec:
      # Force all replicas on different nodes
      topologySpreadConstraints:
        - maxSkew: 1
          topologyKey: kubernetes.io/hostname
          whenUnsatisfiable: DoNotSchedule
          labelSelector:
            matchLabels:
              service: kafka
      tolerations:
        # this toleration is to have the daemonset runnable on master nodes
        # remove it if your masters can't run pods
        - key: node-role.kubernetes.io/master
          operator: Exists
          effect: NoSchedule
      containers:
        - name: kafka
          image: kubernetes.registry:5000/kafka:$(get_last_tag kafka)
          resources:
            requests:
              cpu: "$ESKIMO_KUBE_REQUEST_KAFKA_CPU"
              memory: "$ESKIMO_KUBE_REQUEST_KAFKA_RAM"
          ports:
            - containerPort: 9200
              name: http
            - containerPort: 9300
              name: tcp
          command: ["/usr/local/sbin/inContainerStartService.sh"]
          volumeMounts:
            - name: var-log-kafka
              mountPath: /var/log/kafka
            - name: var-run-kafka
              mountPath: /var/run/kafka
            - name: etc-eskimo-topology
              mountPath: /etc/eskimo_topology.sh
            - name: etc-eskimo-services-settings
              mountPath: /etc/eskimo_services-settings.json
          env:
            - name: ESKIMO_POD_NAME
              valueFrom:
                fieldRef:
                  fieldPath: metadata.name
          securityContext:
            privileged: true
            allowPrivilegeEscalation: true
            readOnlyRootFilesystem: false
            runAsUser: 3303
            runAsGroup: 3303
      terminationGracePeriodSeconds: 60
      volumes:
        - name: var-log-kafka
          hostPath:
            path: /var/log/kafka
            type: Directory
        - name: var-run-kafka
          hostPath:
            path: /var/run/kafka
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