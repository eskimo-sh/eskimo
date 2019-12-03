package ch.niceideas.eskimo.configurations;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {

    @Value("${eskimo.externalLogoAndIconFolder}")
    private String externalLogoAndIconFolder = "";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry
                .addResourceHandler("/"+externalLogoAndIconFolder+"/**")
                .addResourceLocations("file:./"+externalLogoAndIconFolder+"/");
    }

}
