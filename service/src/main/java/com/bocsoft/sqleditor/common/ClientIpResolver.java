package com.bocsoft.sqleditor.common;

import com.bocsoft.sqleditor.config.SqlEditorProperties;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class ClientIpResolver {
    private static final int MAX_LENGTH = 64;
    private final List<Cidr> trusted;

    public ClientIpResolver(SqlEditorProperties properties) {
        List<String> configured = properties.getHttp().getTrustedProxyCidrs();
        this.trusted = parse(configured == null ? Collections.<String>emptyList() : configured);
    }

    public String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (trustedPeer(remote)) {
            String forwarded = firstForwarded(request.getHeader("X-Forwarded-For"));
            if (forwarded == null) forwarded = trimToNull(request.getHeader("X-Real-IP"));
            if (forwarded != null) return truncate(forwarded);
        }
        return truncate(remote);
    }

    private boolean trustedPeer(String remote) {
        if (remote == null || trusted.isEmpty()) return false;
        try {
            InetAddress address = InetAddress.getByName(remote);
            for (Cidr cidr : trusted) if (cidr.contains(address)) return true;
        } catch (UnknownHostException ignored) {
            return false;
        }
        return false;
    }

    private String firstForwarded(String header) {
        if (header == null) return null;
        for (String hop : header.split(",")) {
            String value = trimToNull(hop);
            if (value != null) return value;
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String truncate(String value) {
        if (value == null) return null;
        return value.length() <= MAX_LENGTH ? value : value.substring(0, MAX_LENGTH);
    }

    private List<Cidr> parse(List<String> values) {
        List<Cidr> out = new ArrayList<Cidr>();
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) continue;
            out.add(Cidr.parse(value));
        }
        return out;
    }

    private static final class Cidr {
        private final byte[] network;
        private final int prefix;

        private Cidr(byte[] network, int prefix) {
            this.network = network;
            this.prefix = prefix;
        }

        static Cidr parse(String value) {
            try {
                String[] pieces = value.trim().split("/", -1);
                if (pieces.length != 2 || pieces[0].contains("%")) throw new IllegalArgumentException();
                byte[] bytes = InetAddress.getByName(pieces[0]).getAddress();
                int bits = Integer.parseInt(pieces[1]);
                if (bits < 0 || bits > bytes.length * 8) throw new IllegalArgumentException();
                return new Cidr(mask(bytes, bits), bits);
            } catch (UnknownHostException | NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid CIDR");
            }
        }

        boolean contains(InetAddress address) {
            byte[] bytes = address.getAddress();
            return bytes.length == network.length && Arrays.equals(mask(bytes, prefix), network);
        }

        private static byte[] mask(byte[] value, int prefix) {
            byte[] result = Arrays.copyOf(value, value.length);
            int full = prefix / 8;
            int remainder = prefix % 8;
            if (remainder > 0 && full < result.length) {
                result[full] = (byte) (result[full] & (0xff << (8 - remainder)));
                full++;
            }
            for (int index = full; index < result.length; index++) result[index] = 0;
            return result;
        }
    }
}
