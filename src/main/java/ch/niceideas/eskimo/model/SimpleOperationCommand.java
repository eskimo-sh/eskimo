package ch.niceideas.eskimo.model;

import ch.niceideas.common.utils.Pair;
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

    @Data
    @RequiredArgsConstructor
    public static class SimpleOperationId implements OperationId {

        private final String operation;
        private final String service;
        private final String node;

        public boolean isOnNode(String node) {
            return node.equals(ServicesInstallStatusWrapper.MARATHON_NODE);
        }

        public boolean isSameNode(OperationId other) {
            return other.isOnNode(ServicesInstallStatusWrapper.MARATHON_NODE);
        }

        public String getMessage() {
            return "Executing " + operation + " on " + getService() + " on " + getNode();
        }
    }
}
