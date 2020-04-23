package ch.niceideas.eskimo.model;

import java.util.Objects;

public class ProxyTunnelConfig {

    private final String serviceName;
    private final int localPort;
    private final String remoteAddress;
    private final int remotePort;

    public ProxyTunnelConfig(String serviceName, int localPort, String remoteAddress, int remotePort) {
        this.serviceName = serviceName;
        this.localPort = localPort;
        this.remoteAddress = remoteAddress;
        this.remotePort = remotePort;
    }

    public int getLocalPort() {
        return localPort;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public int getRemotePort() {
        return remotePort;
    }

    public String getServiceName() {
        return serviceName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProxyTunnelConfig)) return false;
        ProxyTunnelConfig that = (ProxyTunnelConfig) o;
        return getLocalPort() == that.getLocalPort() &&
                getRemotePort() == that.getRemotePort() &&
                Objects.equals(getServiceName(), that.getServiceName()) &&
                Objects.equals(getRemoteAddress(), that.getRemoteAddress());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getServiceName(), getLocalPort(), getRemoteAddress(), getRemotePort());
    }
}
