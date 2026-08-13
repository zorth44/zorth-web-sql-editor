package com.bocsoft.sqleditor.datasource.connection;

import java.net.InetAddress;
import java.util.Collections;
import java.util.List;

public final class ResolvedTarget {
    private final List<InetAddress> addresses;
    ResolvedTarget(List<InetAddress> addresses) { this.addresses=Collections.unmodifiableList(addresses); }
    public List<InetAddress> getAddresses() { return addresses; }
}
