/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2022 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

import ch.niceideas.eskimo.model.MessageLogger;
import ch.niceideas.eskimo.model.OperationId;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
@Profile("!test-operation")
public class SystemOperationServiceImpl implements SystemOperationService {

    private static final Logger logger = Logger.getLogger(SystemOperationServiceImpl.class);

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private OperationsMonitoringService operationsMonitoringService;

    @Override
    public void applySystemOperation(OperationId operationId, SystemOperation operation, SystemService.StatusUpdater statusUpdater)
            throws SystemException  {

        if (!operationsMonitoringService.isInterrupted()) {

            String message = operationId.getMessage();

            try {
                operationsMonitoringService.startOperation(operationId);
                notificationService.addDoing(message);
                logOperationMessage(operationId, message);

                operation.call(new MessageLogger () {

                    @Override
                    public void addInfo(String message) {
                        operationsMonitoringService.addInfo(operationId, message);
                    }

                    @Override
                    public void addInfo(String[] messages) {
                        operationsMonitoringService.addInfo(operationId, messages);
                    }
                });

                operationsMonitoringService.addInfo(operationId, "--> Done : "
                            + message
                            + "\n-------------------------------------------------------------------------------\n"
                            + "--> Completed Successfuly.");

                notificationService.addInfo(message + " succeeded");

                if (statusUpdater != null) {
                    configurationService.updateAndSaveServicesInstallationStatus(statusUpdater);
                }

            } catch (Exception e) {
                logger.debug(e, e);
                logger.warn ("Exception will be thrown as SystemException - " + e.getClass() + ":" + e.getMessage());

                operationsMonitoringService.addInfo(operationId, "--> Done : "
                        + message
                        + "\n-------------------------------------------------------------------------------\n"
                        + "--> Completed in error : "
                        + e.getMessage());

                notificationService.addError(message + " failed !");
                operationsMonitoringService.endOperationError(operationId);
                throw new SystemException(e);

            } finally {
                operationsMonitoringService.endOperation(operationId);
            }
        }
    }

    private void logOperationMessage(OperationId operationId, String operation) {
        operationsMonitoringService.addInfo(operationId, new String[]{
                "\n" + operation
        });
    }
}
