package ch.niceideas.eskimo.services;

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.eskimo.model.MarathonServicesConfigWrapper;
import ch.niceideas.eskimo.model.NodesConfigWrapper;
import ch.niceideas.eskimo.model.ServicesConfigWrapper;
import ch.niceideas.eskimo.model.ServicesInstallStatusWrapper;
import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static junit.framework.TestCase.*;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class ApplicationStatusServiceTest {

    private static final Logger logger = Logger.getLogger(ApplicationStatusServiceTest.class);


    private ApplicationStatusService applicationStatusService = null;

    @Before
    public void setUp() throws Exception {
       // TODO
    }

    @Test
    public void testDummy() throws Exception {
        fail ("To Be Implemented");
    }

}
