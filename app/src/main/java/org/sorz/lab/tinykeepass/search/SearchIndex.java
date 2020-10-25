package org.sorz.lab.tinykeepass.search;

import org.sorz.lab.tinykeepass.keepass.KeePassHelperKt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.kunzisoft.keepass.database.element.Entry;
import com.kunzisoft.keepass.database.element.Database;
import com.kunzisoft.keepass.database.element.node.NodeId;

/**
 * Build search index from KeePass file and perform search on it.
 * Searching is based on cosine measure.
 */
public class SearchIndex {
    final private Map<String, List<EntryFreq>> tokenIndex;
    final private AtomicInteger totalEntry;
    private long totalToken;

    /**
     * Build a index that contains all entries (expect recycle bin) of
     * KeePass file.
     * @param keePass to be included in the index.
     */
    public SearchIndex(Database keePass) {
        tokenIndex = new HashMap<>();
        totalEntry = new AtomicInteger();
        KeePassHelperKt.getAllEntriesNotInRecycleBinStream(keePass)
                .parallel()
                .forEach(this::addEntry);
    }

    /**
     * Search on the index with given query with cosine measure.
     * @param query to search.
     * @return matched entry UUIDs, in descending order of similarity.
     */
    public Stream<UUID> search(String query) {
        // calculate query vector `queryTokenWeight` $w_{q,t} = \ln(1+N/f_t)$
        Map<String, Double> queryTokenWeight = new HashMap<>();
        Tokenizer.parse(query)
                .filter(tokenIndex::containsKey)
                .collect(Collectors.groupingBy(a -> a, Collectors.counting()))
                .forEach((token, count) ->
                        queryTokenWeight.put(token,
                                Math.log(1 + totalToken / numberOfEntryHasToken(token)))
                );
        // entryAccum<$d$, $A_d$>, $A_d = \sqrt{\sum_t{w_{d,t}\times w_{q,t}}}$
        Map<UUID, Double> entryAccum = new HashMap<>();
        // entryWeight<$d$, $W_d$>, $W_d = \sqrt{\sum_t{w^2_{d,t}}}$
        Map<UUID, Double> entryWeight = new HashMap<>();
        queryTokenWeight.forEach((token, queryWeight) ->
            tokenIndex.get(token).forEach(entryFreq -> {
                double accum = entryAccum.getOrDefault(entryFreq.entry, 0d);
                double weight = 1 + Math.log(entryFreq.frequency);
                accum += queryWeight * weight;
                entryAccum.put(entryFreq.entry, accum);
                entryWeight.put(entryFreq.entry,
                        entryWeight.getOrDefault(entryFreq.entry, 0d)
                                + weight * weight);
            })
        );
        Map<UUID, Double> entryScore = new HashMap<>(entryAccum.size());
        entryAccum.forEach((entry, accum) -> {
            double score = accum / Math.sqrt(accum);
            entryScore.put(entry, score);
        });
        return entryScore.entrySet().stream()
                .sorted(Comparator.comparingDouble(e -> -e.getValue()))
                .map(Map.Entry::getKey);
    }

    private void addEntry(Entry entry) {
        totalEntry.incrementAndGet();
        Tokenizer.parse(entry)
                .collect(Collectors.groupingBy(a -> a, Collectors.counting()))
                .forEach((token, count) -> addToken(token, entry, count.intValue()));
    }

    private synchronized void addToken(String token, Entry entry, int frequency) {
        List<EntryFreq> entryFreqList;
        if (tokenIndex.containsKey(token)) {
            entryFreqList = tokenIndex.get(token);
        } else {
            entryFreqList = new ArrayList<>();
            tokenIndex.put(token, entryFreqList);
        }
        entryFreqList.add(new EntryFreq(entry, frequency));
        totalToken ++;
    }

    private int numberOfEntryHasToken(String token) {
        return tokenIndex.containsKey(token)
                ? tokenIndex.get(token).size()
                : 0;
    }

    static private class EntryFreq {
        UUID entry;
        int frequency;

        EntryFreq(Entry entry, int frequency) {
            this.entry = entry.getNodeId().getId();
            this.frequency = frequency;
        }
    }
}
