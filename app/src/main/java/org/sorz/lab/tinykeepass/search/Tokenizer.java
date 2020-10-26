package org.sorz.lab.tinykeepass.search;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import com.kunzisoft.keepass.database.element.Entry;
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

        return Arrays.stream(str)
                .filter(t -> !StringsKt.isBlank(t))
                .flatMap(Tokenizer::parse);
    }
}
