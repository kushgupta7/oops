package com.bits.itsm.intelligence;

import java.util.Collection;

/**
 * Abstraction over "how alike are two incident texts" (0 = unrelated, 1 = identical). Intelligent features
 * depend on this interface, not on a concrete algorithm, so a real ML/embedding model can replace the
 * rule-based ones later without touching callers (Dependency Inversion).
 */
public interface SimilarityModel {
    double similarity(String a, String b);

    /** Optional training step for corpus-based models such as TF-IDF. Default: nothing to learn. */
    default SimilarityModel fit(Collection<String> corpus) { return this; }

    /** Rule-based Jaccard similarity on normalised, synonym-merged tokens (used by duplicate detection). */
    static SimilarityModel jaccard() { return TextSimilarity::similarity; }
}
