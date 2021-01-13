/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2021 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

package ch.niceideas.eskimo.model;

import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.services.ServicesDefinition;
import lombok.Data;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Data
public class Service {

    private String name;

    private final List<Dependency> dependencies = new ArrayList<>();
    private final List<String> additionalEnvironment= new ArrayList<>();
    private final List<EditableSettings> editableSettings = new ArrayList<>();

    // configuration
    private String imageName;
    private int configOrder = -1;

    private boolean unique = false;
    private boolean marathon = false;
    private boolean mandatory = false;

    private ConditionalInstallation conditional = null;

    private UIConfig uiConfig = null;

    private String statusGroup = null;
    private String statusName = null;

    private int selectionLayoutCol = -1;
    private int selectionLayoutRow = -1;

    private MemoryConsumptionSize memoryConsumptionSize;

    private final List<String> additionalmemoryServices = new ArrayList<>();

    private String logo;
    private String icon;

    private final List<Command> commands = new ArrayList<>();

    public void addCommand (Command command) {
        commands.add (command);
    }

    public Command getCommand(String commandId) {
        return commands.stream()
                .filter(command -> command.getId().equals(commandId))
                .findFirst()
                .orElse(null);
    }

    public int getMemoryConsumptionParts (ServicesDefinition servicesDefinition) {
        AtomicInteger parts = new AtomicInteger (getMemoryConsumptionSize().getNbrParts());
        getAdditionalmemoryServices().stream()
                .map(servicesDefinition::getService)
                .forEach(service -> parts.addAndGet(service.getMemoryConsumptionSize().getNbrParts()));
        return parts.get();
    }


    public boolean isNotMarathon() {
        return !marathon;
    }

    public boolean dependsOn (String service) {
        return dependencies.stream()
                .anyMatch(o -> o.getMasterService().equals(service));
    }

    public void addAdditionalEnvironment(String additionalEnvironment) {
        this.additionalEnvironment.add (additionalEnvironment);
    }

    public void addDependency(Dependency dep) {
        dependencies.add (dep);
    }

    public void addEditableSettings(EditableSettings conf) {
        editableSettings.add(conf);
    }

    public List<EditableSettings> getEditableSettings() {
        return Collections.unmodifiableList(editableSettings);
    }

    public int getRelevantDependenciesCount() {
        return (int) dependencies.stream()
                .filter(dep -> !dep.getMasterService().equals(getName()) && !dep.getMasterService().equals(NodesConfigWrapper.NODE_ID_FIELD))
                .count();
    }

    public JSONArray toDependenciesJSON() {
        return new JSONArray(getDependencies().stream()
                .map(Dependency::toJSON)
                .collect(Collectors.toList()));
    }

    public JSONObject toConfigJSON() {
        return new JSONObject(new HashMap<String, Object>() {{
            put("name", getName());
            put("unique", isUnique());
            put("marathon", isMarathon());
            put("mandatory", isMandatory());
            put("conditional", getConditional().name());
            put("configOrder", configOrder);
            put("title", (StringUtils.isNotBlank(getStatusGroup()) ? getStatusGroup() + " " : "") + getStatusName().replace("-", ""));
            put("row", getSelectionLayoutRow());
            put("col", getSelectionLayoutCol());
            put("logo", getLogo());
            put("icon", getIcon());
        }});
    }

    public JSONObject toUiStatusConfigJSON() {
        return new JSONObject(new HashMap<String, Object>() {{
            put("group", StringUtils.isNotBlank(getStatusGroup()) ? getStatusGroup() : "");
            put("name", getStatusName());
            put("commands", getCommandsJSON());
        }});
    }

    private JSONArray getCommandsJSON() {
        List<JSONObject> commandJsonList = getCommands().stream()
                .map (Command::toStatusConfigJSON)
                .collect(Collectors.toList());
        return new JSONArray(commandJsonList);
    }

    public JSONObject getEditableConfigurationsJSON() {

        JSONArray settingsArray = new JSONArray(getEditableSettings().stream()
                .map(EditableSettings::toJSON)
                .collect(Collectors.toList())
        );

        return new JSONObject(new HashMap<String, Object>() {{
            put("name", getName());
            put("settings", settingsArray);
        }});
    }

    public boolean isProxied() {
        return getUiConfig() != null && getUiConfig().getProxyTargetPort() != null;
    }

    public boolean hasDependency(Service service) {
        return this.getDependencies().stream()
                .anyMatch(dep -> dep.getMasterService().equals(service.getName()));
    }

    public Optional<Dependency> getDependency(Service service) {
        return this.getDependencies().stream()
                .filter(dep -> dep.getMasterService().equals(service.getName()))
                .findFirst();
    }

    public void addAdditionalMemory(String memAdditionalService) {
        this.additionalmemoryServices.add(memAdditionalService);
    }

    public String getServiceId(String host) {
        if (isUnique()) {
            return name;
        } else {
            return name + "/" + host.replace(".", "-");
        }
    }

    public boolean isUiService() {
        return getUiConfig() != null && StringUtils.isNotBlank(getUiConfig().getTitle());
    }

    public boolean isLink() {
        return getUiConfig() != null && StringUtils.isNotBlank(getUiConfig().getStatusPageLinkTitle());
    }

}
