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

TEMP_FILE=$(mktemp)

cat > $TEMP_FILE <<EOF
kind: Service
apiVersion: v1
metadata:
  labels:
    k8s-app: spark-console
  name: spark-console
  namespace: eskimo
spec:
  ports:
    - port: 31810
      targetPort: 18080
  selector:
    k8s-app: spark-console

---

kind: Service
apiVersion: v1
metadata:
  labels:
    k8s-app: spark-console
  name: spark-console-node
  namespace: eskimo
spec:
  type: NodePort
  ports:
    - port: 31811
      targetPort: 18080
      nodePort: 31811
  selector:
    k8s-app: spark-console
    
---

kind: Deployment
apiVersion: apps/v1
metadata:
  labels:
    k8s-app: spark-console
  name: spark-console
  namespace: eskimo
spec:
  replicas: 1
  revisionHistoryLimit: 10
  selector:
    matchLabels:
      k8s-app: spark-console
  template:
    metadata:
      namespace: eskimo
      labels:
        k8s-app: spark-console
    spec:
      #securityContext:
      #  seccompProfile:
      #    type: RuntimeDefault
      enableServiceLinks: false
      containers:
        - name: spark-console
          image: kubernetes.registry:5000/spark-console:$(get_last_tag spark-console)
          imagePullPolicy: IfNotPresent
          ports:
            - containerPort: 18080
              protocol: TCP
          command: ["/usr/local/sbin/inContainerStartService.sh"]
          volumeMounts:
            - name: var-log-spark
              mountPath: /var/log/spark
            - name: etc-eskimo-topology
              mountPath: /etc/eskimo_topology.sh
            - name: etc-eskimo-services-settings
              mountPath: /etc/eskimo_services-settings.json
          livenessProbe:
            httpGet:
              scheme: HTTP
              path: /
              port: 18080
            initialDelaySeconds: 60
            timeoutSeconds: 30
          securityContext:
            privileged: true
            allowPrivilegeEscalation: true
            readOnlyRootFilesystem: false
            runAsUser: 3302
            runAsGroup: 3302
          resources:
            requests:
              cpu: "$ESKIMO_KUBE_REQUEST_SPARK_CONSOLE_CPU"
              memory: "$ESKIMO_KUBE_REQUEST_SPARK_CONSOLE_RAM"
      volumes:
        - name: var-log-spark
          hostPath:
            path: /var/log/spark
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
      nodeSelector:
        "kubernetes.io/os": linux
      # Comment the following tolerations if app must not be deployed on master
      tolerations:
        - key: node-role.kubernetes.io/master
          effect: NoSchedule
EOF
cat $TEMP_FILE
rm -Rf $TEMP_FILE