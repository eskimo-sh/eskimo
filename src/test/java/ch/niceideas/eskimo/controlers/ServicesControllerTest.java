package ch.niceideas.eskimo.controlers;

import ch.niceideas.eskimo.model.ConditionalInstallation;
import ch.niceideas.eskimo.model.Service;
import ch.niceideas.eskimo.model.UIConfig;
import ch.niceideas.eskimo.services.ServicesDefinition;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static junit.framework.TestCase.assertEquals;

public class ServicesControllerTest {

    private ServicesController sc = new ServicesController();

    @Before
    public void setUp() throws Exception {
        ServicesDefinition sd = new ServicesDefinition();
        sd.afterPropertiesSet();
        sc.setServicesDefinition(sd);
    }

    @Test
    public void testListServices() {

        sc.setServicesDefinition(new ServicesDefinition() {
            @Override
            public String[] listServicesInOrder() {
                return new String[] {"A", "B", "C"};
            }
        });

        assertEquals ("{\n" +
                "  \"services\": [\n" +
                "    \"A\",\n" +
                "    \"B\",\n" +
                "    \"C\"\n" +
                "  ],\n" +
                "  \"status\": \"OK\"\n" +
                "}", sc.listServices());

        sc.setServicesDefinition(new ServicesDefinition() {
            @Override
            public String[] listServicesInOrder() {
                throw new JSONException("Test Error");
            }
        });

        assertEquals ("{\n" +
                "  \"error\": \"Test Error\",\n" +
                "  \"status\": \"KO\"\n" +
                "}", sc.listServices());
    }

    @Test
    public void testListUIServices() {

        sc.setServicesDefinition(new ServicesDefinition() {
            @Override
            public String[] listUIServices() {
                return new String[] {"A", "B", "C"};
            }
        });

        assertEquals ("{\n" +
                "  \"uiServices\": [\n" +
                "    \"A\",\n" +
                "    \"B\",\n" +
                "    \"C\"\n" +
                "  ],\n" +
                "  \"status\": \"OK\"\n" +
                "}", sc.listUIServices());

        sc.setServicesDefinition(new ServicesDefinition() {
            @Override
            public String[] listUIServices() {
                throw new JSONException("Test Error");
            }
        });

        assertEquals ("{\n" +
                "  \"error\": \"Test Error\",\n" +
                "  \"status\": \"KO\"\n" +
                "}", sc.listUIServices());
    }

    @Test
    public void testGetUIServicesConfig() {

        sc.setServicesDefinition(new ServicesDefinition() {
            @Override
            public Map<String, UIConfig> getUIServicesConfig() {
                return new HashMap<String, UIConfig>(){{
                    put ("A", new UIConfig(new Service()));
                    put ("B", new UIConfig(new Service()));
                }};
            }
        });

        assertEquals ("{\n" +
                "  \"uiServicesConfig\": {\n" +
                "    \"A\": {\n" +
                "      \"proxyContext\": \"./null/\",\n" +
                "      \"waitTime\": 0,\n" +
                "      \"urlTemplate\": \"\",\n" +
                "      \"unique\": false\n" +
                "    },\n" +
                "    \"B\": {\n" +
                "      \"proxyContext\": \"./null/\",\n" +
                "      \"waitTime\": 0,\n" +
                "      \"urlTemplate\": \"\",\n" +
                "      \"unique\": false\n" +
                "    }\n" +
                "  },\n" +
                "  \"status\": \"OK\"\n" +
                "}", sc.getUIServicesConfig());

        sc.setServicesDefinition(new ServicesDefinition() {
            @Override
            public Map<String, UIConfig> getUIServicesConfig() {
                throw new JSONException("Test Error");
            }
        });

        assertEquals ("{\n" +
                "  \"error\": \"Test Error\",\n" +
                "  \"status\": \"KO\"\n" +
                "}", sc.getUIServicesConfig());
    }

    @Test
    public void testGetUIServicesStatusConfig() {

        sc.setServicesDefinition(new ServicesDefinition() {
            @Override
            public String[] listAllServices() {
                return new String[]{"A", "B", "C"};
            }
            @Override
            public Service getService(String serviceName) {
                Service retService = new Service();
                retService.setName(serviceName);
                return retService;
            }
        });

        assertEquals ("{\n" +
                "  \"uiServicesStatusConfig\": {\n" +
                "    \"A\": {\n" +
                "      \"commands\": [],\n" +
                "      \"group\": \"\"\n" +
                "    },\n" +
                "    \"B\": {\n" +
                "      \"commands\": [],\n" +
                "      \"group\": \"\"\n" +
                "    },\n" +
                "    \"C\": {\n" +
                "      \"commands\": [],\n" +
                "      \"group\": \"\"\n" +
                "    }\n" +
                "  },\n" +
                "  \"status\": \"OK\"\n" +
                "}", sc.getUIServicesStatusConfig());
    }

    @Test
    public void testGetServicesDependencies() {

        sc.setServicesDefinition(new ServicesDefinition() {
            @Override
            public String[] listAllServices() {
                return new String[]{"A", "B", "C"};
            }
            @Override
            public Service getService(String serviceName) {
                Service retService = new Service();
                retService.setName(serviceName);
                return retService;
            }
        });

        assertEquals ("{\n" +
                "  \"servicesDependencies\": {\n" +
                "    \"A\": [],\n" +
                "    \"B\": [],\n" +
                "    \"C\": []\n" +
                "  },\n" +
                "  \"status\": \"OK\"\n" +
                "}", sc.getServicesDependencies());
    }

    @Test
    public void testGetServicesConfigurations() {

        sc.setServicesDefinition(new ServicesDefinition() {
            @Override
            public String[] listAllServices() {
                return new String[]{"A", "B", "C"};
            }
            @Override
            public Service getService(String serviceName) {
                Service retService = new Service();
                retService.setName(serviceName);
                retService.setConditional(ConditionalInstallation.NONE);
                retService.setStatusGroup("testGroup");
                retService.setStatusName("testName");
                return retService;
            }
        });

        assertEquals ("{\n" +
                "  \"servicesConfigurations\": {\n" +
                "    \"A\": {\n" +
                "      \"col\": -1,\n" +
                "      \"marathon\": false,\n" +
                "      \"conditional\": \"NONE\",\n" +
                "      \"configOrder\": -1,\n" +
                "      \"unique\": false,\n" +
                "      \"name\": \"A\",\n" +
                "      \"row\": -1,\n" +
                "      \"title\": \"testGroup testName\",\n" +
                "      \"mandatory\": false\n" +
                "    },\n" +
                "    \"B\": {\n" +
                "      \"col\": -1,\n" +
                "      \"marathon\": false,\n" +
                "      \"conditional\": \"NONE\",\n" +
                "      \"configOrder\": -1,\n" +
                "      \"unique\": false,\n" +
                "      \"name\": \"B\",\n" +
                "      \"row\": -1,\n" +
                "      \"title\": \"testGroup testName\",\n" +
                "      \"mandatory\": false\n" +
                "    },\n" +
                "    \"C\": {\n" +
                "      \"col\": -1,\n" +
                "      \"marathon\": false,\n" +
                "      \"conditional\": \"NONE\",\n" +
                "      \"configOrder\": -1,\n" +
                "      \"unique\": false,\n" +
                "      \"name\": \"C\",\n" +
                "      \"row\": -1,\n" +
                "      \"title\": \"testGroup testName\",\n" +
                "      \"mandatory\": false\n" +
                "    }\n" +
                "  },\n" +
                "  \"status\": \"OK\"\n" +
                "}", sc.getServicesConfigurations());
    }

    @Test
    public void testListConfigServices() {

        sc.setServicesDefinition(new ServicesDefinition() {
            @Override
            public String[] listAllServices() {
                return new String[]{"A", "B", "C"};
            }
            @Override
            public Service getService(String serviceName) {
                Service retService = new Service();
                retService.setName(serviceName);
                retService.setConditional(ConditionalInstallation.NONE);
                retService.setStatusGroup("testGroup");
                retService.setStatusName("testName");
                return retService;
            }
            @Override
            public String[] listMultipleServices() {
                return new String[]{"A"};
            }
            @Override
            public String[] listMandatoryServices() {
                return new String[]{"A", "B",};
            }
            @Override
            public String[] listUniqueServices() {
                return new String[]{"C",};
            }
        });

        assertEquals ("{\n" +
                "  \"mandatoryServices\": [\n" +
                "    \"A\",\n" +
                "    \"B\"\n" +
                "  ],\n" +
                "  \"uniqueServices\": [\"C\"],\n" +
                "  \"multipleServices\": [\"A\"],\n" +
                "  \"servicesConfigurations\": {\n" +
                "    \"A\": {\n" +
                "      \"col\": -1,\n" +
                "      \"marathon\": false,\n" +
                "      \"conditional\": \"NONE\",\n" +
                "      \"configOrder\": -1,\n" +
                "      \"unique\": false,\n" +
                "      \"name\": \"A\",\n" +
                "      \"row\": -1,\n" +
                "      \"title\": \"testGroup testName\",\n" +
                "      \"mandatory\": false\n" +
                "    },\n" +
                "    \"B\": {\n" +
                "      \"col\": -1,\n" +
                "      \"marathon\": false,\n" +
                "      \"conditional\": \"NONE\",\n" +
                "      \"configOrder\": -1,\n" +
                "      \"unique\": false,\n" +
                "      \"name\": \"B\",\n" +
                "      \"row\": -1,\n" +
                "      \"title\": \"testGroup testName\",\n" +
                "      \"mandatory\": false\n" +
                "    },\n" +
                "    \"C\": {\n" +
                "      \"col\": -1,\n" +
                "      \"marathon\": false,\n" +
                "      \"conditional\": \"NONE\",\n" +
                "      \"configOrder\": -1,\n" +
                "      \"unique\": false,\n" +
                "      \"name\": \"C\",\n" +
                "      \"row\": -1,\n" +
                "      \"title\": \"testGroup testName\",\n" +
                "      \"mandatory\": false\n" +
                "    }\n" +
                "  },\n" +
                "  \"status\": \"OK\"\n" +
                "}", sc.listConfigServices());
    }

}
