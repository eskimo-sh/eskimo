package ch.niceideas.eskimo.model;

import java.util.Objects;

public class ProxyTunnelConfig {

    private int localPort;
    private String remoteAddress;
    private int remotePort;

    public ProxyTunnelConfig() {
    }

    public ProxyTunnelConfig(int localPort, String remoteAddress, int remotePort) {
        this.localPort = localPort;
        this.remoteAddress = remoteAddress;
        this.remotePort = remotePort;
    }

    public int getLocalPort() {
        return localPort;
    }

    public void setLocalPort(int localPort) {
        this.localPort = localPort;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public void setRemoteAddress(String remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    public int getRemotePort() {
        return remotePort;
    }

    public void setRemotePort(int remotePort) {
        this.remotePort = remotePort;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProxyTunnelConfig that = (ProxyTunnelConfig) o;
        return getLocalPort() == that.getLocalPort() &&
                getRemotePort() == that.getRemotePort() &&
                Objects.equals(getRemoteAddress(), that.getRemoteAddress());
    }

    @Override
    public int hashCode() {

        return Objects.hash(getLocalPort(), getRemoteAddress(), getRemotePort());
    }


}
