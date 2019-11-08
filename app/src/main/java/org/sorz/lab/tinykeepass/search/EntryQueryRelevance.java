package org.sorz.lab.tinykeepass.search;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.List;

import de.slackspace.openkeepass.domain.Entry;

/**
 * Given a list of keywords, return an score of relevance between
 * entry and keywords.
 */
public class EntryQueryRelevance implements Comparable<EntryQueryRelevance> {
    private static final double WEIGHT_KW_IN_TITLE = 1.0;
    private static final double WEIGHT_KW_IN_USERNAME = 0.8;
    private static final double WEIGHT_KW_IN_NOTES = 0.5;
    private static final double WEIGHT_KW_IN_URL = 0.5;
    private static final double WEIGHT_KW_IN_TAGS = 0.6;

    private Entry entry;
    private double rank;
    private int unrelatedKeywords;

    public EntryQueryRelevance(Entry entry, List<String> keywords) {
        this.entry = entry;
        double[] ranks = keywords.stream().mapToDouble(w ->
                fieldScore(entry.getTitle(), w) * WEIGHT_KW_IN_TITLE +
                        fieldScore(entry.getUsername(), w) * WEIGHT_KW_IN_USERNAME +
                        fieldScore(entry.getNotes(), w) * WEIGHT_KW_IN_NOTES +
                        fieldScore(entry.getTags(), w) * WEIGHT_KW_IN_TAGS +
                        fieldScore(entry.getUrl(), w) * WEIGHT_KW_IN_URL).toArray();
        rank = Arrays.stream(ranks).sum();
        unrelatedKeywords = (int) Arrays.stream(ranks).filter(r -> r == 0).count();
    }

    public boolean isRelated() {
        return rank > 0;
    }

    public Entry getEntry() {
        return entry;
    }

    private static double logScore(int queryLength, int totalLength) {
        return Math.log(Math.E - 1 + queryLength * 1.0 / totalLength);
    }

    private static double fieldScore(String field, String query) {
        if (field == null || !field.toLowerCase().contains(query))
            return 0;
        // TODO: give prefix matching higher score
        // assumptions: longer words has higher importance;
        // "true match" of keywords are seldom repeated on single field.
        return logScore(query.length(), field.length());
    }

    private static double fieldScore(List<String> keywords, String query) {
        if (keywords != null && keywords.stream()
                .map(String::toLowerCase)
                .anyMatch(kw -> kw.equalsIgnoreCase(query))) {
            return logScore(1, keywords.size());
        }
        return 0;
    }

    @Override
    public int compareTo(@NonNull EntryQueryRelevance entryQueryRelevance) {
        if (unrelatedKeywords != entryQueryRelevance.unrelatedKeywords)
            return Integer.compare(unrelatedKeywords, entryQueryRelevance.unrelatedKeywords);
        else
            return -Double.compare(rank, entryQueryRelevance.rank);
    }
}
