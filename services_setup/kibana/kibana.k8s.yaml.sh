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
    k8s-app: kibana
  name: kibana
  namespace: eskimo
spec:
  ports:
    - port: 31561
      targetPort: 5601
  selector:
    k8s-app: kibana

---

kind: Service
apiVersion: v1
metadata:
  labels:
    k8s-app: eskimo
  name: kibana-node
  namespace: eskimo
spec:
  type: NodePort
  ports:
    - port: 31562
      targetPort: 5601
      nodePort: 31562
  selector:
    k8s-app: kibana

---

kind: Deployment
apiVersion: apps/v1
metadata:
  labels:
    k8s-app: kibana
  name: kibana
  namespace: eskimo
spec:
  replicas: 1
  revisionHistoryLimit: 10
  selector:
    matchLabels:
      k8s-app: kibana
  template:
    metadata:
      namespace: eskimo
      labels:
        k8s-app: kibana
    spec:
      #securityContext:
      #  seccompProfile:
      #    type: RuntimeDefault
      enableServiceLinks: false
      containers:
        - name: kibana
          image: kubernetes.registry:5000/kibana:$(get_last_tag kibana)
          imagePullPolicy: IfNotPresent
          ports:
            - containerPort: 5601
              protocol: TCP
          command: ["/usr/local/sbin/inContainerStartService.sh"]
          volumeMounts:
            - name: var-log-elasticsearch
              mountPath: /var/log/elasticsearch
            - name: var-run-elasticsearch
              mountPath: /var/run/elasticsearch
            - name: etc-eskimo-topology
              mountPath: /etc/eskimo_topology.sh
            - name: etc-eskimo-services-settings
              mountPath: /etc/eskimo_services-settings.json
          livenessProbe:
            tcpSocket:
              port: 5601
            initialDelaySeconds: 180
            timeoutSeconds: 45
          securityContext:
            allowPrivilegeEscalation: false
            readOnlyRootFilesystem: false
            runAsUser: 3301
            runAsGroup: 3301
          resources:
            requests:
              cpu: "$ESKIMO_KUBE_REQUEST_KIBANA_CPU"
              memory: "$ESKIMO_KUBE_REQUEST_KIBANA_RAM"
      volumes:
        - name: var-log-elasticsearch
          hostPath:
            path: /var/log/elasticsearch
            type: Directory
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
      nodeSelector:
        "kubernetes.io/os": linux
      # Comment the following tolerations if app must not be deployed on master
      tolerations:
        - key: node-role.kubernetes.io/master
          effect: NoSchedule
EOF
cat $TEMP_FILE
rm -Rf $TEMP_FILE