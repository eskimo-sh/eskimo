package ch.niceideas.eskimo.model.service.proxy;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ReplacementContext {

    private final String contextPath;
    private final String prefixPath;
    private final String fullServerRoot;
    private final String appRoot;

    public String getResolved(String initial) {
        return initial
                .replace("{PREFIX_PATH}", prefixPath)
                .replace("{CONTEXT_PATH}", contextPath)
                .replace("{FULL_SERVER_ROOT}", fullServerRoot)
                .replace("{APP_ROOT}", appRoot);

    }

}
