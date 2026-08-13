package com.bocsoft.sqleditor.datasource.connection;

import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.config.SqlEditorProperties;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class NetworkPolicy {
    interface HostResolver { InetAddress[] resolve(String host) throws UnknownHostException; }
    private final List<CidrBlock> allowed;
    private final List<CidrBlock> denied;
    private final HostResolver resolver;

    @Autowired
    public NetworkPolicy(SqlEditorProperties properties) {
        this(properties.getNetwork().getAllowedCidrs(), properties.getNetwork().getDeniedCidrs(),
            host -> InetAddress.getAllByName(host));
    }

    NetworkPolicy(List<String> allowed, List<String> denied, HostResolver resolver) {
        if (allowed == null || allowed.isEmpty()) throw new IllegalStateException("Allowed CIDR list cannot be empty");
        this.allowed = parse(allowed);
        this.denied = parse(denied == null ? java.util.Collections.<String>emptyList() : denied);
        this.resolver = resolver;
    }

    public ResolvedTarget resolve(String host) {
        validateHost(host);
        InetAddress[] results;
        try { results = resolver.resolve(host); }
        catch (UnknownHostException exception) { throw ApiException.validation("host", "UNRESOLVABLE", "Host 无法解析"); }
        if (results == null || results.length == 0) throw ApiException.validation("host", "UNRESOLVABLE", "Host 无法解析");
        List<InetAddress> addresses = new ArrayList<InetAddress>(Arrays.asList(results));
        for (InetAddress address : addresses) {
            if (matches(denied, address) || !matches(allowed, address)) {
                throw ApiException.validation("host", "NETWORK_NOT_ALLOWED", "Host 不在允许的网络范围内");
            }
        }
        addresses.sort(Comparator.comparing(InetAddress::getHostAddress));
        return new ResolvedTarget(addresses);
    }

    public static void validateHost(String host) {
        if (host == null) throw ApiException.validation("host", "REQUIRED", "请输入 Host");
        String value = host.trim();
        if (value.isEmpty() || value.length() > 255 || value.contains("://") || value.contains("@")
            || value.contains("/") || value.contains("?") || value.contains("#") || value.contains("%")
            || value.indexOf(' ') >= 0 || value.indexOf('\t') >= 0) {
            throw ApiException.validation("host", "INVALID", "Host 格式不合法");
        }
        if (value.startsWith("[") || value.endsWith("]")) {
            throw ApiException.validation("host", "INVALID", "IPv6 Host 请勿包含方括号");
        }
    }

    private boolean matches(List<CidrBlock> blocks, InetAddress address) {
        for (CidrBlock block : blocks) if (block.contains(address)) return true;
        return false;
    }

    private List<CidrBlock> parse(List<String> values) {
        List<CidrBlock> blocks = new ArrayList<CidrBlock>();
        try {
            for (String value : values) blocks.add(CidrBlock.parse(value));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid target-network CIDR configuration");
        }
        return blocks;
    }
}
