package ch.niceideas.eskimo.model.service.proxy;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UrlRewriting {

    private String startUrl;
    private String replacement;

    public boolean matches(String theUrl, ReplacementContext context) {
        String effStartUrl = context.getResolved(startUrl);
        return theUrl.startsWith(effStartUrl);
    }

    public String rewrite(String theUrl, ReplacementContext context) {
        String effStartUrl = context.getResolved(startUrl);
        String effReplacement = context.getResolved(replacement);
        return effReplacement + theUrl.substring(effStartUrl.length());
    }
}
