package ar.edu.utn.frc.siga.common.search;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Free-text search over Criteria queries. Each word of the text must match some field (AND between tokens, OR
 * between fields); a token matches ignoring case and accents, as a substring or with up to 2 typos
 * (see the {@code siga_busqueda_coincide} SQL function).
 */
public final class FuzzySearch {

    private static final int MAX_TOKENS = 8;
    private static final int MAX_TOKEN_LENGTH = 50;

    private FuzzySearch() {}

    public static List<String> tokens(String q) {
        List<String> tokens = new ArrayList<>();
        if (q == null) {
            return tokens;
        }
        for (String token : q.split("[^\\p{L}\\p{N}]+")) {
            if (!token.isEmpty() && tokens.size() < MAX_TOKENS) {
                tokens.add(token.length() > MAX_TOKEN_LENGTH ? token.substring(0, MAX_TOKEN_LENGTH) : token);
            }
        }
        return tokens;
    }

    /** Single text where every non-null part is joined by a space; matching it is an OR across the parts. */
    public static Expression<String> text(CriteriaBuilder cb, Expression<?>... parts) {
        List<Expression<?>> args = new ArrayList<>();
        args.add(cb.literal(" "));
        args.addAll(List.of(parts));
        return cb.function("concat_ws", String.class, args.toArray(new Expression<?>[0]));
    }

    public static Predicate matches(CriteriaBuilder cb, Expression<String> text, String token) {
        return cb.isTrue(cb.function("siga_busqueda_coincide", Boolean.class, text, cb.literal(token)));
    }

    /** AND over the tokens of {@code q} of {@code perToken}; {@code null} when {@code q} has no tokens. */
    public static Predicate allTokens(CriteriaBuilder cb, String q, Function<String, Predicate> perToken) {
        List<String> tokens = tokens(q);
        if (tokens.isEmpty()) {
            return null;
        }
        return cb.and(tokens.stream().map(perToken).toArray(Predicate[]::new));
    }
}
