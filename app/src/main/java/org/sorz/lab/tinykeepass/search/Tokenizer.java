package org.sorz.lab.tinykeepass.search;

import org.sorz.lab.tinykeepass.keepass.KeePassHelper;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import de.slackspace.openkeepass.domain.Entry;

/**
 * Parse strings into tokens (a list of words).
 */
public class Tokenizer {
    final static private Set<String> IGNORE_TOKENS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "com", "net", "org"
            ))
    );

    static public Stream<String> parse(String str) {
        String[] tokens = str.split("\\b");
        return Arrays.stream(tokens)
                .filter(s -> s.matches("\\w{2,}"))
                .filter(s -> !IGNORE_TOKENS.contains(s))
                .map(String::toLowerCase);
    }

    static public Stream<String> parse(Entry entry) {
        String[] str = {
                entry.getTitle(),
                entry.getNotes(),
                entry.getUrl()
        };
        return Arrays.stream(str)
                .filter(KeePassHelper::notEmpty)
                .flatMap(Tokenizer::parse);
    }
}
