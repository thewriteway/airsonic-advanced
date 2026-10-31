package org.airsonic.player.repository;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Matches a {@code genre} attribute against a single genre, honouring the genre separators setting the same
 * way {@link org.airsonic.player.domain.Genres} splits tags while scanning: with separators {@code ";"}, the
 * genre {@code "House"} matches {@code "House"}, {@code "Dance;House"} and {@code "Dance; House; Edm"}.
 */
public class GenreSpecifications {

    private static final char ESCAPE = '\\';

    private GenreSpecifications() {
    }

    public static Predicate matches(CriteriaBuilder cb, Expression<String> attribute, String genre, String separators) {
        Set<String> patterns = new LinkedHashSet<>();
        String g = escape(genre);
        for (char c : Objects.toString(separators, "").toCharArray()) {
            String s = escape(String.valueOf(c));
            // split parts are trimmed, so allow a space on either side of the genre
            for (String part : new String[] { g, " " + g, g + " ", " " + g + " " }) {
                patterns.add(part.stripLeading() + s + "%"); // first
                patterns.add("%" + s + part + s + "%");      // middle
                patterns.add("%" + s + part.stripTrailing()); // last
            }
        }
        Predicate[] predicates = new Predicate[patterns.size() + 1];
        predicates[0] = cb.equal(attribute, genre);
        int i = 1;
        for (String pattern : patterns) {
            predicates[i++] = cb.like(attribute, pattern, ESCAPE);
        }
        return cb.or(predicates);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
