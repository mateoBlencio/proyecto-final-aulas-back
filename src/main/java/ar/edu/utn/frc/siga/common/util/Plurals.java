package ar.edu.utn.frc.siga.common.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Spanish count + noun formatting for user-facing texts. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Plurals {

    /** E.g. {@code count(1, "alta", "altas")} gives "1 alta"; {@code count(0, ...)} gives "0 altas". */
    public static String count(long n, String singular, String plural) {
        return n + " " + (n == 1 ? singular : plural);
    }
}
