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

import ch.niceideas.common.utils.FileException;
import ch.niceideas.eskimo.model.MarathonServicesConfigWrapper;
import ch.niceideas.eskimo.services.MarathonServicesConfigService;
import ch.niceideas.eskimo.services.SetupException;
import ch.niceideas.eskimo.services.SystemException;
import ch.niceideas.eskimo.utils.ErrorStatusHelper;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;


@Controller
public class MarathonServicesConfigController {

    private static final Logger logger = Logger.getLogger(MarathonServicesConfigController.class);

    @Autowired
    private MarathonServicesConfigService marathonServicesConfigService;

    /* For tests */
    void setMarathonServicesConfigService(MarathonServicesConfigService marathonServicesConfigService) {
        this.marathonServicesConfigService = marathonServicesConfigService;
    }

    @GetMapping("/load-marathon-services-config")
    @ResponseBody
    public String loadMarathonServicesConfig() {
        try {

            MarathonServicesConfigWrapper wrapper = marathonServicesConfigService.loadMarathonServicesConfig();

            wrapper.setValueForPath("status", "OK");

            return wrapper.getFormattedValue();
        } catch (JSONException | FileException | SetupException e) {
            logger.error(e, e);
            return ErrorStatusHelper.createErrorStatus(e.getMessage());
        }
    }

    @PostMapping("/apply-marathon-services-config")
    @Transactional(isolation= Isolation.REPEATABLE_READ)
    @ResponseBody
    public String saveAndApplyMarathonServicesConfig(@RequestBody String configFormAsString) {

        logger.info("Got config : " + configFormAsString);

        try {
            marathonServicesConfigService.saveAndApplyMarathonServicesConfig(configFormAsString);

            return new JSONObject(new HashMap<String, Object>() {{
                put("status", "OK");
            }}).toString(2);

        } catch (JSONException | FileException | SetupException | SystemException e) {
            logger.error(e, e);
            return ErrorStatusHelper.createErrorStatus (e);
        }
    }

}
