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

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.common.utils.*;
import ch.niceideas.eskimo.model.*;
import ch.niceideas.eskimo.proxy.ProxyManagerService;
import ch.niceideas.eskimo.utils.ErrorStatusHelper;
import ch.niceideas.eskimo.utils.SystemStatusParser;
import com.trilead.ssh2.Connection;
import com.trilead.ssh2.SCPClient;
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
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.text.SimpleDateFormat;
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
public class StatusService {

    private static final Logger logger = Logger.getLogger(StatusService.class);

    @Autowired
    private SetupService setupService;

    @Value("${status.monitoringDashboardID}")
    private String monitoringDashboardId = null;

    @Value("${status.monitoringDashboardRefreshPeriod}")
    private String monitoringDashboardRefreshPeriod = "30s";

    @Value("${build.version}")
    private String buildVersion = "DEV-SNAPSHOT";

    @Value("${build.timestamp}")
    private String buildTimestamp = "LATEST DEV";

    private ThreadLocal<SimpleDateFormat> localDateFormatter = new ThreadLocal<>();

    public JSONObject getStatus() {

        // 0. Build returned status
        JsonWrapper systemStatus = JsonWrapper.empty();

        systemStatus.setValueForPath("monitoringDashboardId", monitoringDashboardId);

        systemStatus.setValueForPath("monitoringDashboardRefreshPeriod", monitoringDashboardRefreshPeriod);

        systemStatus.setValueForPath("buildVersion", buildVersion);

        systemStatus.setValueForPath("buildTimestamp", buildTimestamp);

        try {
            JsonWrapper systemConfig = new JsonWrapper(setupService.loadSetupConfig());
            systemStatus.setValueForPath("sshUsername", systemConfig.getValueForPath("ssh_username"));
        } catch (FileException e) {

            logger.error (e, e);
            systemStatus.setValueForPath("sshUsername", "(ERROR)");
        } catch (SetupException e) {

            logger.warn (e.getMessage());
            logger.debug (e, e);
            systemStatus.setValueForPath("sshUsername", "(Setup incomplete)");
        }

        // Get JVM's thread system bean
        RuntimeMXBean bean = ManagementFactory.getRuntimeMXBean();
        long startTime = bean.getStartTime();
        Date startDate = new Date(startTime);

        SimpleDateFormat df = localDateFormatter.get();
        if (df == null) {
            df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            localDateFormatter.set(df);
        }

        systemStatus.setValueForPath("startTimestamp", df.format(startDate));


        return systemStatus.getJSONObject();
    }
}
