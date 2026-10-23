package ar.edu.utn.frc.siga.common.search;

import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Free-text search over Criteria queries. Each word of the text is a token and every token must match (AND
 * between tokens, OR between fields). The match of a token is a relevance rank computed by the SQL function
 * {@code siga_busqueda_rango}: 0 no match, 1 typo, 2 substring, 3 word prefix, 4 whole word. A row scores the
 * sum of the best rank of each token and, with a search text and no explicit sort, lists by that score.
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

    public static Expression<Integer> rank(CriteriaBuilder cb, Expression<String> text, String token) {
        return cb.function("siga_busqueda_rango", Integer.class, text, cb.literal(token));
    }

    @SafeVarargs
    public static Expression<Integer> greatest(CriteriaBuilder cb, Expression<Integer>... ranks) {
        return cb.function("greatest", Integer.class, (Expression<?>[]) ranks);
    }

    /** Best rank of {@code rankOf} over the rows of {@code subquery}; 0 when it has none. */
    public static Expression<Integer> bestOf(CriteriaBuilder cb, Subquery<Integer> subquery,
            Expression<Integer> rankOf) {
        return cb.coalesce(subquery.select(cb.max(rankOf)), cb.literal(0));
    }

    public static Subquery<Integer> subquery(AbstractQuery<?> parent) {
        return parent.subquery(Integer.class);
    }

    /** One rank expression per token of {@code q}; empty when {@code q} has no tokens. */
    public static List<Expression<Integer>> ranks(String q, Function<String, Expression<Integer>> perToken) {
        return tokens(q).stream().map(perToken).toList();
    }

    /** Every token matched somewhere; {@code null} when there are no tokens. */
    public static Predicate matchesAll(CriteriaBuilder cb, List<Expression<Integer>> ranks) {
        if (ranks.isEmpty()) {
            return null;
        }
        return cb.and(ranks.stream().map(rank -> cb.greaterThan(rank, 0)).toArray(Predicate[]::new));
    }

    /**
     * Sorts the page query by relevance (then by {@code tieBreak}) unless the caller sorted it. Spring Data
     * replaces this order when the pageable carries a sort, and the count query has no order.
     */
    public static void orderByRelevance(CriteriaQuery<?> query, CriteriaBuilder cb,
            List<Expression<Integer>> ranks, Expression<?>... tieBreak) {
        if (ranks.isEmpty() || Long.class.equals(query.getResultType()) || !query.getOrderList().isEmpty()) {
            return;
        }
        Expression<Integer> score = ranks.get(0);
        for (int i = 1; i < ranks.size(); i++) {
            score = cb.sum(score, ranks.get(i));
        }
        List<Order> orders = new ArrayList<>();
        orders.add(cb.desc(score));
        for (Expression<?> expression : tieBreak) {
            orders.add(cb.asc(expression));
        }
        query.orderBy(orders);
    }

    /** With a search text and the default sort, drops the sort so the relevance order applies. */
    public static Pageable relevanceUnlessSorted(Pageable pageable, String q, Sort defaultSort) {
        if (tokens(q).isEmpty() || !pageable.getSort().equals(defaultSort)) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    }
}
