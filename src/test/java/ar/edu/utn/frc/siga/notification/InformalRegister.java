package ar.edu.utn.frc.siga.notification;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Detector de registro informal para los textos de los mails: tuteo, voseo y "mail" como palabra suelta.
 *  Con UNICODE_CHARACTER_CLASS, \b reconoce letras acentuadas ("respondé"), y "estudiante" o "tuteo" no
 *  coinciden con "te" ni con "tu". */
public final class InformalRegister {

    private static final Pattern INFORMAL = Pattern.compile(
            "\\b(tu|tus|te|ti|vos|contigo|pediste|solicitaste|respondé|respondés|respondas|contanos|confirmá|confirmes"
                    + "|necesitás|estás|podés|tenés|querés|volvés|consultá|mail|mails|email)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern STYLE = Pattern.compile("(?is)<style.*?</style>");
    private static final Pattern ALT = Pattern.compile("(?i)\\balt=\"([^\"]*)\"");
    private static final Pattern TAG = Pattern.compile("<[^>]+>");

    private InformalRegister() {
    }

    /** La primera palabra informal del texto, si hay alguna. */
    public static Optional<String> find(String text) {
        Matcher matcher = INFORMAL.matcher(text);
        return matcher.find() ? Optional.of(matcher.group()) : Optional.empty();
    }

    /** Lo que ve quien lee el mail: el texto sin etiquetas ni CSS, más los alt de las imágenes. */
    public static String visibleText(String html) {
        String withoutStyle = STYLE.matcher(html).replaceAll(" ");
        StringBuilder alts = new StringBuilder();
        Matcher alt = ALT.matcher(withoutStyle);
        while (alt.find()) {
            alts.append(' ').append(alt.group(1));
        }
        return TAG.matcher(withoutStyle).replaceAll(" ") + alts;
    }
}
