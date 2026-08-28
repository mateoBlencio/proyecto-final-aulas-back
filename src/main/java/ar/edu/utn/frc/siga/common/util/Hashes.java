package ar.edu.utn.frc.siga.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class Hashes {

    private static final String ALGORITHM = "SHA-256";
    private static final String SEPARATOR = "|";

    private Hashes() {
    }

    public static String sha256Hex(Object... parts) {
        String joined = Stream.of(parts)
                .map(part -> part == null ? "" : part.toString())
                .collect(Collectors.joining(SEPARATOR));
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(joined.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(ALGORITHM + " no disponible", e);
        }
    }
}
