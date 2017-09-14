package org.sorz.lab.tinykeepass.autofill.search;

import org.sorz.lab.tinykeepass.keepass.KeePassHelper;

import java.util.Arrays;
import java.util.stream.Stream;

import de.slackspace.openkeepass.domain.Entry;

/**
 * Parse strings into tokens (a list of words).
 */
public class Tokenizer {
    static public Stream<String> parse(String str) {
        String[] tokens = str.split("\\b");
        return Arrays.stream(tokens).filter(s -> s.matches("\\w{2,}"));
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
