package ch.niceideas.eskimo.model.service.proxy;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ReplacementContext {

    private final String contextPath;
    private final String prefixPath;
    private final String fullServerRoot;
    private final String fullServerRootNoContext;
    private final String appRoot;
    private final String appRootNoContext;

    public String getResolved(String initial) {
        return initial
                .replace("{PREFIX_PATH}", prefixPath)
                .replace("{CONTEXT_PATH}", contextPath)
                .replace("{FULL_SERVER_ROOT}", fullServerRoot)
                .replace("{FULL_SERVER_ROOT_NO_CONTEXT}", fullServerRootNoContext)
                .replace("{APP_ROOT}", appRoot)
                .replace("{APP_ROOT_NO_CONTEXT}", appRootNoContext);

    }

}
