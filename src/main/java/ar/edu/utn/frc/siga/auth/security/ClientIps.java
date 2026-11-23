package ar.edu.utn.frc.siga.auth.security;

import java.net.InetAddress;
import java.net.UnknownHostException;

final class ClientIps {

    private static final int IPV6_BYTES = 16;
    private static final int IPV6_PREFIX_BYTES = 8;

    private ClientIps() {
    }

    static String normalize(String ip) {
        if (ip == null || !ip.contains(":")) {
            return ip;
        }
        try {
            byte[] bytes = InetAddress.getByName(ip).getAddress();
            if (bytes.length != IPV6_BYTES) {
                return ip;
            }
            StringBuilder prefix = new StringBuilder("v6:");
            for (int i = 0; i < IPV6_PREFIX_BYTES; i++) {
                prefix.append(String.format("%02x", bytes[i]));
            }
            return prefix.append("/64").toString();
        } catch (UnknownHostException e) {
            return ip;
        }
    }
}
