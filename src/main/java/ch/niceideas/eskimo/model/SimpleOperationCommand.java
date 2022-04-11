package ch.niceideas.eskimo.model;

import ch.niceideas.common.utils.Pair;
import ch.niceideas.common.utils.StringUtils;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@RequiredArgsConstructor
public class SimpleOperationCommand implements JSONOpCommand {

    private final String operation, service, node;

    @Override
    public JSONObject toJSON() {
        return new JSONObject(new HashMap<String, Object>() {{
            put("operation", operation);
            put("service", service);
            put("node", node);
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
        return member.replace("(", "").replace(")", "").replace("/", "").replace(" ", "-").replace(".", "-");
    }

    @Data
    @RequiredArgsConstructor
    public static class SimpleOperationId implements OperationId {

        private final String operation;
        private final String service;
        private final String node;

        public boolean isOnNode(String node) {
            return node.equals(this.node);
        }

        public boolean isSameNode(OperationId other) {
            return other.isOnNode(this.node);
        }

        public String getMessage() {
            return "Executing " + operation + " on " + getService() + " on " + getNode();
        }

        @Override
        public String toString() {
            return standardizeOperationMember(operation)
                    + "_"
                    + standardizeOperationMember (service)
                    + "_"
                    + standardizeOperationMember (node);
        }
    }
}
