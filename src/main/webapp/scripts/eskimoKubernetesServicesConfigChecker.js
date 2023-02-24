/*
This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
well to this individual file than to the Eskimo Project as a whole.

Copyright 2019 - 2023 eskimo.sh / https://www.eskimo.sh - All rights reserved.
Author : eskimo.sh / https://www.eskimo.sh

Eskimo is available under a dual licensing model : commercial and GNU AGPL.
If you did not acquire a commercial licence for Eskimo, you can still use it and consider it free software under the
terms of the GNU Affero Public License. You can redistribute it and/or modify it under the terms of the GNU Affero
Public License  as published by the Free Software Foundation, either version 3 of the License, or (at your option)
any later version.
Compliance to each and every aspect of the GNU Affero Public License is mandatory for users who did no acquire a
commercial license.

Eskimo is distributed as a free software under GNU AGPL in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
Affero Public License for more details.

You should have received a copy of the GNU Affero Public License along with Eskimo. If not,
see <https://www.gnu.org/licenses/> or write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
Boston, MA, 02110-1301 USA.

You can be released from the requirements of the license by purchasing a commercial license. Buying such a
commercial license is mandatory as soon as :
- you develop activities involving Eskimo without disclosing the source code of your own product, software,
  platform, use cases or scripts.
- you deploy eskimo as part of a commercial product, platform or software.
For more information, please contact eskimo.sh at https://www.eskimo.sh

The above copyright notice and this licensing notice shall be included in all copies or substantial portions of the
Software.
*/


window.eskimoKubeCpuCheckRE = /^[0-9\\.]+[m]?$/;
window.eskimoKubeRamCheckRE = /^[0-9\\.]+[EPTGMk]?$/;
window.eskimoKubeReplicasCheckRE = /^[0-9\\.]+$/;

function checkKubernetesSetup (kubernetesSetupConfig, servicesDependencies, kubernetesServices, successCallback) {

    console.log (kubernetesSetupConfig);

    $.ajaxGet({
        url: "load-nodes-config",
        success: (data, status, jqXHR) => {

            try {
                doCheckKubernetesSetup (data, kubernetesSetupConfig, servicesDependencies, kubernetesServices);

                if(successCallback && successCallback instanceof Function) {
                    successCallback();
                } else {
                    eskimoMain.alert(ESKIMO_ALERT_LEVEL.ERROR, "TECHNICAL ERROR : successCallback is not a function.");
                }

            } catch (error) {
                eskimoMain.alert(ESKIMO_ALERT_LEVEL.ERROR, error);
            }

        },
        error: errorHandler
    });

    return true;
}

function doCheckKubernetesSetup (nodesConfig, kubernetesSetupConfig, servicesDependencies, kubernetesServices) {
    if (!nodesConfig.clear) {

        // data is nodesConfig

        // collecting encountered servics
        let encounteredService = [];

        // check service dependencies
        for (let key in kubernetesSetupConfig) {
            let re = /([a-zA-Z\-]+)_install/;

            let matcher = key.match(re);

            if (matcher != null) {

                let serviceName = matcher[1];
                encounteredService.push (serviceName);

                let serviceDeps = servicesDependencies[serviceName];

                for (let i = 0; i < serviceDeps.length; i++) {
                    let dependency = serviceDeps[i];

                    // All the following are unsupported for kubernetes service
                    if (dependency.mes == "SAME_NODE"
                        || dependency.mes == "SAME_NODE_OR_RANDOM"
                        || dependency.mes == "RANDOM_NODE_AFTER"
                        || dependency.mes == "RANDOM_NODE_AFTER_OR_SAME") {

                        throw "Inconsistency found : Service " + serviceName + " is a kubernetes service and defines a dependency "
                        + dependency.mes + " on " + dependency.masterService + " which is disallowed";
                    }

                    // I want the dependency somewhere
                    else if (dependency.mandatory) {

                        // if dependency is a kubernetes service
                        if (kubernetesServices[dependency.masterService]) {
                            if (dependency.numberOfMasters > 1) {
                                throw "Inconsistency found : Service " + serviceName + " is a kubernetes service and defines a dependency with master count "
                                + dependency.numberOfMasters + " on " + dependency.masterService + " which is disallowed for kubernetes dependencies";
                            }

                            // make sure dependency is installed or going to be
                            if (!kubernetesSetupConfig[dependency.masterService + "_install"]
                                || kubernetesSetupConfig[dependency.masterService + "_install"] != "on") {
                                throw "Inconsistency found : Service " + serviceName + " expects a installaton of  " + dependency.masterService +
                                ". But it's not going to be installed";
                            }
                        }
                        // otherwise if dependency is a node service
                        else {
                            // ensure count of dependencies are available
                            enforceMandatoryDependency(dependency, nodesConfig, null, serviceName);
                        }
                    }
                }
            }
        }

        for (let key in kubernetesSetupConfig) {
            let re = /([a-zA-Z\-]+)_install/;

            let matcher = key.match(re);

            if (matcher != null) {

                let serviceName = matcher[1];

                if (!kubernetesSetupConfig[serviceName + "_cpu"] || kubernetesSetupConfig[serviceName + "_cpu"] === "") {
                    throw "Inconsistency found : Kubernetes Service " + serviceName + " is enabled but misses CPU request setting";
                }

                if (!kubernetesSetupConfig[serviceName + "_ram"] || kubernetesSetupConfig[serviceName + "_ram"] === "") {
                    throw "Inconsistency found : Kubernetes Service " + serviceName + " is enabled but misses RAM request setting";
                }
            }
        }

        // Now checking CPU definition
        for (let key in kubernetesSetupConfig) {
            let re = /([a-zA-Z\-]+)_cpu/;

            let matcher = key.match(re);
            if (matcher != null) {
                let serviceName = matcher[1];

                if (encounteredService.length <= 0 || !encounteredService.includes(serviceName)) {
                    throw "Inconsistency found : Found a CPU definition for " + key +
                            ". But corresponding service installation is not enabled";
                }

                let cpuDef = kubernetesSetupConfig [serviceName + "_cpu"];
                if (cpuDef.match(eskimoKubeCpuCheckRE) == null) {
                    throw "CPU definition for " + key + " doesn't match expected REGEX - " + eskimoKubeCpuCheckRE;
                }
            }
        }

        // Now checking RAM definition
        for (let key in kubernetesSetupConfig) {
            let re = /([a-zA-Z\-]+)_ram/;

            let matcher = key.match(re);
            if (matcher != null) {
                let serviceName = matcher[1];

                if (encounteredService.length <= 0 || !encounteredService.includes(serviceName)) {
                    throw "Inconsistency found : Found a RAM definition for " + key +
                    ". But corresponding service installation is not enabled";
                }

                let ramDef = kubernetesSetupConfig [serviceName + "_ram"];
                if (ramDef.match(eskimoKubeRamCheckRE) == null) {
                    throw "RAM definition for " + key + " doesn't match expected REGEX - " + eskimoKubeRamCheckRE;
                }
            }
        }

        // Now check deployment_strategy and replicas
        for (let service in kubernetesServices) {
            if (!kubernetesServices[service].unique && !kubernetesServices[service].registryOnly) {

                let serviceInstalled = kubernetesSetupConfig [service + "_install"];
                if (serviceInstalled && serviceInstalled === "on") {

                    // either wide cluster selection is selected, or I want eplicas configured (in the proper format)
                    let deplStratDef = kubernetesSetupConfig [service + "_deployment_strategy"];

                    let replicasDef = kubernetesSetupConfig [service + "_replicas"];

                    if (deplStratDef && deplStratDef === "on") {

                        // in this case I want no replica !
                        if (replicasDef && replicasDef !== "") {
                            throw "Service " + service + " defines a deployment strategy as 'cluster wide' and yet it has replicas configured";
                        }

                    } else {

                        // in this case I want replica and in the proper format
                        if (!replicasDef || replicasDef === "") {
                            throw "Service " + service + " defines a deployment strategy as 'custom' and yet it is missing replicas configuration";

                        } else {
                            if (replicasDef.match(eskimoKubeReplicasCheckRE) == null) {
                                throw "Replica definition for " + service + " doesn't match expected REGEX - " + eskimoKubeReplicasCheckRE;
                            }
                        }
                    }
                }
            }
        }

    } else if (nodesConfig.clear === "missing") {
        throw "No Nodes Configuration is available";

    } else if (nodesConfig.clear === "setup") {
        throw "Setup is not completed";
    }
}