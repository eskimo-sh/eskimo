/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 eskimo.sh / https://www.eskimo.sh - All rights reserved.
 * Author : eskimo.sh / https://www.eskimo.sh
 *
 * Eskimo is available under a dual licensing model : commercial and GNU AGPL.
 * If you did not acquire a commercial licence for Eskimo, you can still use it and consider it free software under the
 * terms of the GNU Affero Public License. You can redistribute it and/or modify it under the terms of the GNU Affero
 * Public License  as published by the Free Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 * Compliance to each and every aspect of the GNU Affero Public License is mandatory for users who did no acquire a
 * commercial license.
 *
 * Eskimo is distributed as a free software under GNU AGPL in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero Public License for more details.
 *
 * You should have received a copy of the GNU Affero Public License along with Eskimo. If not,
 * see <https://www.gnu.org/licenses/> or write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA, 02110-1301 USA.
 *
 * You can be released from the requirements of the license by purchasing a commercial license. Buying such a
 * commercial license is mandatory as soon as :
 * - you develop activities involving Eskimo without disclosing the source code of your own product, software,
 *   platform, use cases or scripts.
 * - you deploy eskimo as part of a commercial product, platform or software.
 * For more information, please contact eskimo.sh at https://www.eskimo.sh
 *
 * The above copyright notice and this licensing notice shall be included in all copies or substantial portions of the
 * Software.
 */

package ch.niceideas.eskimo.controlers;

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.common.utils.FileException;
import ch.niceideas.eskimo.services.SetupException;
import ch.niceideas.eskimo.services.SetupService;
import ch.niceideas.eskimo.services.SystemService;
import ch.niceideas.eskimo.utils.ErrorStatusHelper;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;


@Controller
public class SetupConfigController {

    private static final Logger logger = Logger.getLogger(SetupConfigController.class);

    @Autowired
    private SetupService setupService;

    @Autowired
    private SystemService systemService;

    @GetMapping("/load-setup")
    @ResponseBody
    public String loadSetupConfig() {

        try {
            String config = setupService.loadSetupConfig();

            JsonWrapper configWrapper = new JsonWrapper(config);

            try {
                setupService.ensureSetupCompleted();
            } catch (SetupException e) {
                logger.debug (e, e);
                try {

                    configWrapper.setValueForPath("clear", "setup");
                    configWrapper.setValueForPath("message", e.getMessage());
                } catch (JSONException e1) {
                    logger.error (e1, e1);
                    throw new RuntimeException(e1);
                }
            }

            if (systemService.isProcessingPending()) {
                configWrapper.setValueForPath("processingPending", "true");
            }

            return configWrapper.getFormattedValue();

        } catch (FileException | JSONException e) {
            logger.error(e, e);
            return ErrorStatusHelper.createErrorStatus(e);

        } catch (SetupException e) {
            // this is OK. means application is not initialized
            logger.debug(e, e);
            return ErrorStatusHelper.createClearStatus("missing", systemService.isProcessingPending());
        }
    }


    @PostMapping("/apply-setup")
    @Transactional(isolation= Isolation.REPEATABLE_READ)
    @ResponseBody
    public String saveAndApplySetup(@RequestBody String configAsString) {

        logger.info("Got config : " + configAsString);

        try {
            return setupService.saveAndApplySetup(configAsString);

        } catch (SetupException e) {
            logger.error(e, e);
            return ErrorStatusHelper.createErrorStatus (e);
        }
    }

}
