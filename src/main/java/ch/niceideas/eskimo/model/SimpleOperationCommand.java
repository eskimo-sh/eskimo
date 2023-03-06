/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2023 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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
import ch.niceideas.eskimo.types.LabelledOperation;
import ch.niceideas.eskimo.types.Node;
import ch.niceideas.eskimo.types.Service;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@RequiredArgsConstructor
public class SimpleOperationCommand implements JSONOpCommand {

    private final SimpleOperation operation;
    private final Service service;
    private final Node node;

    @Override
    public JSONObject toJSON() {
        return new JSONObject(new HashMap<String, Object>() {{
            put("operation", operation.getType());
            put("service", service.getName());
            put("node", node.getAddress());
        }});
    }

    @Override
    public boolean hasChanges() {
        return true;
    }

    @Override
    public List<SimpleOperationId> getAllOperationsInOrder(OperationsContext context) {
        return new ArrayList<>() {{
            add(new SimpleOperationId(operation, service, node));
        }};
    }

    public static String standardizeOperationMember (String member) {
        if (StringUtils.isBlank(member)) {
            return "";
        }
        return member.replace("(", "")
                .replace(")", "")
                .replace("/", "")
                .replace(" ", "-")
                .replace(".", "-");
    }


    @EqualsAndHashCode(callSuper = true)
    public static class SimpleOperationId extends AbstractStandardOperationId<SimpleOperation> implements OperationId<SimpleOperation> {

        public SimpleOperationId (SimpleOperation operation, Service service, Node node) {
            super (operation, service, node);
        }
    }

    @RequiredArgsConstructor
    public enum SimpleOperation implements LabelledOperation {
        SHOW_JOURNAL("show_journal", "Showing journal", 34),
        START("start", "Starting", 31),
        STOP ("stop", "Stopping", 32),
        RESTART ("restart", "Restarting", 33),
        COMMAND ("command", "Calling custom command", 35);

        @Getter
        private final String type;

        @Getter
        private final String label;

        @Getter
        private final int ordinal;

        @Override
        public String toString () {
            return type;
        }
    }
}
