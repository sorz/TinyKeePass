package org.sorz.lab.tinykeepass.search;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import de.slackspace.openkeepass.domain.Entry;
import kotlin.text.StringsKt;

/**
 * Parse strings into tokens (a list of words).
 */
class Tokenizer {
    final static private Set<String> IGNORE_TOKENS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "com", "net", "org"
            ))
    );

    static private String canonicalize(String token) {
        return token.toLowerCase();
    }

    static Stream<String> parse(String str) {
        String[] tokens = str.split("\\b");
        return Arrays.stream(tokens)
                .filter(s -> s.matches("\\w{2,}"))
                .filter(s -> !IGNORE_TOKENS.contains(s))
                .map(Tokenizer::canonicalize);
    }

    static Stream<String> parse(Entry entry) {
        String[] str = {
                entry.getTitle(),
                entry.getNotes(),
                entry.getUrl(),
        };
        Stream<String> tokens = Arrays.stream(str)
                .filter(t -> !StringsKt.isBlank(t))
                .flatMap(Tokenizer::parse);

        if (entry.getTags() != null) {
            tokens = Stream.concat(tokens,
                    entry.getTags().stream()
                            .distinct()
                            .map(Tokenizer::canonicalize)
            );
        }
        return tokens;
    }
}
