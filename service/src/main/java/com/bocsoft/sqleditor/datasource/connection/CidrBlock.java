package com.bocsoft.sqleditor.datasource.connection;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

final class CidrBlock {
    private final byte[] network;
    private final int prefix;

    private CidrBlock(byte[] network, int prefix) {
        this.network = network;
        this.prefix = prefix;
    }

    static CidrBlock parse(String value) {
        try {
            String[] pieces = value.trim().split("/", -1);
            if (pieces.length != 2 || pieces[0].contains("%")) throw new IllegalArgumentException();
            InetAddress address = InetAddress.getByName(pieces[0]);
            byte[] bytes = address.getAddress();
            int bits = Integer.parseInt(pieces[1]);
            if (bits < 0 || bits > bytes.length * 8) throw new IllegalArgumentException();
            return new CidrBlock(mask(bytes, bits), bits);
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
