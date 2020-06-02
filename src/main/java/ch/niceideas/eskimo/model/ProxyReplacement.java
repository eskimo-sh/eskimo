package ch.niceideas.eskimo.model;

import ch.niceideas.common.utils.StringUtils;

public class ProxyReplacement {

    public enum ProxyReplacementType {
        PLAIN
    }

    private ProxyReplacementType type;
    private String source;
    private String target;
    private String urlPattern;

    public ProxyReplacementType getType() {
        return type;
    }

    public void setType(ProxyReplacementType type) {
        this.type = type;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getUrlPattern() {
        return urlPattern;
    }

    public void setUrlPattern(String urlPattern) {
        this.urlPattern = urlPattern;
    }

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
        String effective = initial.replace("{PREFIX_PATH}", prefixPath);
        effective = effective.replace("{CONTEXT_PATH}", contextPath);
        return effective;
    }


}
