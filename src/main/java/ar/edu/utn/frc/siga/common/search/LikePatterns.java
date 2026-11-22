package ar.edu.utn.frc.siga.common.search;

public final class LikePatterns {

    private LikePatterns() {}

    public static String escape(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public static String containing(String text) {
        return "%" + escape(text) + "%";
    }
}
