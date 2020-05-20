package ch.niceideas.eskimo.model;

import ch.niceideas.eskimo.services.SSHCommandException;
import ch.niceideas.eskimo.services.SSHCommandService;
import org.json.JSONObject;

import java.util.HashMap;

public class Command {

    private String id = null;
    private String name = null;
    private String commandCall = null;
    private String icon = null;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCommandCall() {
        return commandCall;
    }

    public void setCommandCall(String commandCall) {
        this.commandCall = commandCall;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public JSONObject toStatusConfigJSON () {
        return new JSONObject(new HashMap<String, Object>() {{
            put("id", id);
            put("name", name);
            put("icon", icon);
        }});
    }

    public String call(String ipAddress, SSHCommandService sshCommandService) throws SSHCommandException {
        return sshCommandService.runSSHCommand(ipAddress, commandCall);
    }
}
