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

package ch.niceideas.eskimo.services;

import ch.niceideas.common.utils.*;
import ch.niceideas.eskimo.model.*;
import ch.niceideas.eskimo.proxy.ProxyManagerService;
import ch.niceideas.eskimo.utils.ErrorStatusHelper;
import ch.niceideas.eskimo.utils.SystemStatusParser;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class ServicesConfigService {

    private static final Logger logger = Logger.getLogger(ServicesConfigService.class);

    @Autowired
    private ServicesDefinition servicesDefinition;

    @Autowired
    private SetupService setupService;

    private ReentrantLock servicesConfigFileLock = new ReentrantLock();


    public void saveServicesConfig(ServicesConfigWrapper status) throws FileException, JSONException, SetupException {
        servicesConfigFileLock.lock();
        try {
            String configStoragePath = setupService.getConfigStoragePath();
            FileUtils.writeFile(new File(configStoragePath + "/services-config.json"), status.getFormattedValue());
        } finally {
            servicesConfigFileLock.unlock();
        }
    }

    public ServicesConfigWrapper loadServicesConfig() throws JSONException, FileException, SetupException {
        servicesConfigFileLock.lock();
        try {
            String configStoragePath = setupService.getConfigStoragePath();
            File statusFile = new File(configStoragePath + "/services-config.json");
            if (!statusFile.exists()) {
                return ServicesConfigWrapper.initEmpty(servicesDefinition);
            }

            return new ServicesConfigWrapper(statusFile);
        } finally {
            servicesConfigFileLock.unlock();
        }
    }
}
