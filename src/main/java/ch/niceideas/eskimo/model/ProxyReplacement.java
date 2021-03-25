package ch.niceideas.eskimo.model;

import ch.niceideas.common.utils.StringUtils;
import lombok.Data;

@Data
public class ProxyReplacement {

    public enum ProxyReplacementType {
        PLAIN
    }

    private ProxyReplacementType type;
    private String source;
    private String target;
    private String urlPattern;

    public String performReplacement(String input, String contextPath, String prefixPath, String requestURI) {

        if (type.equals(ProxyReplacementType.PLAIN)) {

            if (StringUtils.isBlank(urlPattern) || requestURI.contains(urlPattern)) {

                String effSource = getResolved(source, contextPath, prefixPath);
                String effTarget = getResolved(target, contextPath, prefixPath);

                return input.replace(effSource, effTarget);
            }

        } else {
            throw new UnsupportedOperationException("Not Implemented yet. Support of " + type);
        }

        return input;
    }

    String getResolved(String initial, String contextPath, String prefixPath) {
        return initial
                .replace("{PREFIX_PATH}", prefixPath)
                .replace("{CONTEXT_PATH}", contextPath);
    }


}
