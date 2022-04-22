/*
This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
well to this individual file than to the Eskimo Project as a whole.

Copyright 2019 - 2021 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

if (typeof eskimo === "undefined" || eskimo == null) {
    window.eskimo = {}
}
/* @Deprecated To Be renamed */
eskimo.KubernetesServicesSelection = function() {

    // will be injected eventually from constructorObject
    this.eskimoKubernetesServicesConfig = null;

    const that = this;

    // Initialize HTML Div from Template
    this.initialize = function() {
        $("#kubernetes-services-selection-modal-wrapper").load("html/eskimoKubernetesServicesSelection.html", function (responseTxt, statusTxt, jqXHR) {

            if (statusTxt == "success") {

                $("#kubernetes-services-select-header-cancel").click(cancelKubernetesServicesSelection);

                $("#select-all-kubernetes-services-button").click(kubernetesServicesSelectionSelectAll);

                $("#kubernetes-services-select-button-cancel").click(cancelKubernetesServicesSelection);

                $("#kubernetes-services-select-button-validate").click(validateKubernetesServicesSelection);

            } else if (statusTxt == "error") {
                alert("Error: " + jqXHR.status + " " + jqXHR.statusText);
            }
        });
    };

    function showKubernetesServiceSelection() {
        $('#kubernetes-services-selection-modal').modal("show");
    }
    this.showKubernetesServiceSelection = showKubernetesServiceSelection;

    function kubernetesServicesSelectionSelectAll() {

        let allSelected = true;

        let kubernetesServices = that.eskimoKubernetesServicesConfig.getKubernetesServices();

        // are they all selected already
        for (let i = 0; i < kubernetesServices.length; i++) {
            if (!$('#' + kubernetesServices[i] + "_reinstall").get(0).checked) {
                allSelected = false;
            }
        }

        // select all boxes
        for (let i = 0; i < kubernetesServices.length; i++) {
            $('#' + kubernetesServices[i] + "_reinstall").get(0).checked = !allSelected;
        }
    }
    this.kubernetesServicesSelectionSelectAll = kubernetesServicesSelectionSelectAll;

    function cancelKubernetesServicesSelection() {
        $('#kubernetes-services-selection-modal').modal("hide");
    }
    this.cancelKubernetesServicesSelection = cancelKubernetesServicesSelection;

    function validateKubernetesServicesSelection() {

        $('#kubernetes-services-selection-modal').modal("hide");

        let reinstallConfig = $("form#kubernetes-servicesreinstall").serializeObject();

        that.eskimoKubernetesServicesConfig.proceedWithReinstall (reinstallConfig);
    }
    this.validateKubernetesServicesSelection = validateKubernetesServicesSelection;

};