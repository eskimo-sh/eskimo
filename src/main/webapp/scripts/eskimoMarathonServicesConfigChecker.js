/*
This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
well to this individual file than to the Eskimo Project as a whole.

Copyright 2019 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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


function checkMarathonSetup (marathonSetupConfig, serviceDependencies, successCallback) {

    $.ajax({
        type: "GET",
        dataType: "json",
        url: "load-nodes-config",
        success: function (data, status, jqXHR) {

            try {

                if (!data.clear) {

                    // data is nodesConfig

                    // check service dependencies
                    for (var key in marathonSetupConfig) {
                        var re = /([a-zA-Z\-]+)_install/;

                        var matcher = key.match(re);

                        var serviceName = matcher[1];

                        var serviceDeps = servicesDependencies[property.serviceName];

                        for (var i = 0; i < serviceDeps.length; i++) {
                            var dependency = serviceDeps[i];

                            // I want the dependency on same node
                            if (dependency.mes == "SAME_NODE") {

                                throw "Inconsistency found : Service " + serviceName + " is a marathon service and defines a dependency SAME_NODE which is disallowed"
                            }

                            // I want the dependency somewhere
                            else if (dependency.mandatory) {

                                // ensure count of dependencies are available
                                enforceMandatoryDependency(dependency, data, null, property);
                            }
                        }
                    }

                    if(successCallback && successCallback != null && successCallback instanceof Function) {
                        successCallback();
                    } else {
                        throw "TECHNICAL ERROR : successCallback is not a function.";
                    }

                } else if (data.clear == "missing") {
                    throw "No Nodes Configuration is available";

                } else if (data.clear == "setup") {
                    throw "Setup is not completed";
                }

            } catch (error) {
                alert(error);
            }

        },
        error: errorHandler
    });

    alert (" TODO check setup")

    return true;
}