package ch.niceideas.eskimo.services;

import ch.niceideas.common.utils.FileException;
import ch.niceideas.eskimo.model.MarathonServicesConfigWrapper;
import ch.niceideas.eskimo.model.NodesConfigWrapper;
import ch.niceideas.eskimo.utils.ErrorStatusHelper;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class MarathonServicesConfigService {

    private static final Logger logger = Logger.getLogger(ServicesConfigService.class);
    public static final String SERVICES_CONFIG_JSON_FILE = "/services-config.json";

    @Autowired
    private SetupService setupService;

    @Autowired
    private ServicesDefinition servicesDefinition;

    @Autowired
    private SystemService systemService;

    @Autowired
    private ConfigurationService configurationService;

    /* For tests */
    void setServicesDefinition(ServicesDefinition servicesDefinition) {
        this.servicesDefinition = servicesDefinition;
    }
    void setConfigurationService (ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }


    public void saveAndApplyMarathonServicesConfig(String configFormAsString) throws FileException, SetupException, SystemException {

        // TODO

    }
}
