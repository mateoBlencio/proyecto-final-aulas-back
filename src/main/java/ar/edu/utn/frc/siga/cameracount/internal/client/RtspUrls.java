package ar.edu.utn.frc.siga.cameracount.internal.client;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

public final class RtspUrls {

    private static final String MASKED = "***";

    private RtspUrls() {}

    /** Devuelve {@code scheme://***@host[:puerto]/...} sin usuario, clave ni ruta. */
    public static String mask(String url) {
        if (url == null) {
            return MASKED;
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            return MASKED;
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null || host.isEmpty()) {
            return MASKED;
        }
        String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
        return scheme.toLowerCase(Locale.ROOT) + "://***@" + host.toLowerCase(Locale.ROOT) + port + "/...";
    }
}
