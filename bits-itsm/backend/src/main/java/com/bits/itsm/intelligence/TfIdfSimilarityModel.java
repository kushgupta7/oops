package com.bits.itsm.intelligence;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TF-IDF + cosine similarity. Words that appear in many incidents ("not", "working") carry little weight;
 * rare, specific words ("projector", "VPN") carry more. Immutable: {@link #fit} returns a new trained model.
 * <pre>
 *   tf(t,d)  = count of t in d / |d|
 *   idf(t)   = ln((1 + N) / (1 + df(t))) + 1          (smoothed, never zero)
 *   sim(a,b) = (v_a · v_b) / (|v_a| |v_b|)
 * </pre>
 */
public final class TfIdfSimilarityModel implements SimilarityModel {
    private final Map<String, Double> idf;
    private final double unseenIdf;

    public TfIdfSimilarityModel() { this(Map.of(), 1.0); }

    private TfIdfSimilarityModel(Map<String, Double> idf, double unseenIdf) {
        this.idf = Map.copyOf(idf);
        this.unseenIdf = unseenIdf;
    }

    @Override
    public TfIdfSimilarityModel fit(Collection<String> corpus) {
        Map<String, Integer> df = new HashMap<>();
        for (String doc : corpus) for (String t : new HashSet<>(TextSimilarity.tokenList(doc))) df.merge(t, 1, Integer::sum);
        int n = corpus.size();
        Map<String, Double> weights = new HashMap<>();
        df.forEach((t, count) -> weights.put(t, Math.log((1.0 + n) / (1.0 + count)) + 1.0));
        return new TfIdfSimilarityModel(weights, Math.log(1.0 + n) + 1.0);
    }

    Map<String, Double> vector(String text) {
        List<String> tokens = TextSimilarity.tokenList(text);
        Map<String, Double> v = new HashMap<>();
        if (tokens.isEmpty()) return v;
        for (String t : tokens) v.merge(t, 1.0, Double::sum);
        v.replaceAll((t, count) -> count / tokens.size() * idf.getOrDefault(t, unseenIdf));
        return v;
    }

    @Override
    public double similarity(String a, String b) {
        Map<String, Double> va = vector(a), vb = vector(b);
        if (va.isEmpty() || vb.isEmpty()) return 0.0;
        double dot = 0;
        Set<String> shared = new HashSet<>(va.keySet());
        shared.retainAll(vb.keySet());
        for (String t : shared) dot += va.get(t) * vb.get(t);
        return dot == 0 ? 0.0 : Math.min(1.0, dot / (norm(va) * norm(vb)));
    }

    private static double norm(Map<String, Double> v) {
        double sum = 0;
        for (double x : v.values()) sum += x * x;
        return Math.sqrt(sum);
    }
}
