package ar.edu.utn.frc.siga.notification.internal.config;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

import java.util.Optional;
import java.util.regex.Pattern;

/** Formato de casilla que aceptan el arranque ({@link ReplyToStartupCheck}) y el envío
 *  ({@code EmailChannelSender}): una sola dirección, sin nombre, sin comas ni saltos de línea, con dominio
 *  que tenga punto. Un mismo criterio en los dos lados evita que una casilla aceptada al arrancar se descarte
 *  después en el envío. */
public final class EmailAddressFormat {

    private static final Pattern EMAIL_FORMAT = Pattern.compile("^[^\\s@,]+@[^\\s@,]+\\.[^\\s@,]+$");

    private EmailAddressFormat() {
    }

    public static boolean isValid(String address) {
        return parse(address).isPresent();
    }

    /** La dirección ya armada para Jakarta Mail, o vacío si no cumple el formato. */
    public static Optional<InternetAddress> parse(String address) {
        if (address == null || !EMAIL_FORMAT.matcher(address).matches()) {
            return Optional.empty();
        }
        try {
            InternetAddress parsed = new InternetAddress(address);
            return address.equals(parsed.getAddress()) ? Optional.of(parsed) : Optional.empty();
        } catch (AddressException e) {
            return Optional.empty();
        }
    }
}
