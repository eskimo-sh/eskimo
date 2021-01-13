package ch.niceideas.eskimo.model;

import ch.niceideas.eskimo.services.SSHCommandException;
import ch.niceideas.eskimo.services.SSHCommandService;
import lombok.Data;
import org.json.JSONObject;

import java.util.HashMap;

@Data
public class Command {

    private String id = null;
    private String name = null;
    private String commandCall = null;
    private String icon = null;

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
