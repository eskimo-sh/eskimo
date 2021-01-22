package ch.niceideas.eskimo.model;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class ProxyTunnelConfig {

    private final String serviceName;
    private final int localPort;
    private final String node;
    private final int remotePort;
}
