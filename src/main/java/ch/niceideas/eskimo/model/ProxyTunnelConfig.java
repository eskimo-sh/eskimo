package ch.niceideas.eskimo.model;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class ProxyTunnelConfig {

    private final String serviceName;
    private final int localPort;
    private final String remoteAddress;
    private final int remotePort;
}
