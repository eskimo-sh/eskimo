package ch.niceideas.eskimo.model.service.proxy;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ReplacementContext {

    private final String contextPath;
    private final String prefixPath;
    private final String appRootUrl;

    public String getResolved(String initial) {
        return initial
                .replace("{PREFIX_PATH}", prefixPath)
                .replace("{CONTEXT_PATH}", contextPath)
                .replace("{APP_ROOT_URL}", appRootUrl);

    }

}
