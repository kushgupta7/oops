package com.bits.itsm.intelligence;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Intelligent feature G, Suggested Resolution: shows a technician how the most similar past incidents
 * were fixed. Uses TF-IDF trained on the resolved corpus by default; any {@link SimilarityModel} can be injected.
 */
public class ResolutionSuggester {
    /** A past incident with a recorded resolution. */
    public record PastResolution(Long incidentId, String title, String description, String summary, String steps) { }

    public record Suggestion(Long incidentId, String title, double similarity, String summary, String steps) { }

    public static final double MIN_SIMILARITY = 0.10;
    private final SimilarityModel model;

    public ResolutionSuggester() { this(new TfIdfSimilarityModel()); }

    public ResolutionSuggester(SimilarityModel model) { this.model = java.util.Objects.requireNonNull(model); }

    public List<Suggestion> suggest(String title, String description, Collection<PastResolution> history, int limit) {
        if (history.isEmpty()) return List.of();
        SimilarityModel trained = model.fit(history.stream().map(h -> h.title() + " " + h.description()).toList());
        String query = (title == null ? "" : title) + " " + (description == null ? "" : description);
        return history.stream()
                .map(h -> new Suggestion(h.incidentId(), h.title(), round(trained.similarity(query, h.title() + " " + h.description())), h.summary(), h.steps()))
                .filter(s -> s.similarity() >= MIN_SIMILARITY)
                .sorted(Comparator.comparingDouble(Suggestion::similarity).reversed().thenComparing(Suggestion::incidentId))
                .limit(limit).toList();
    }

    private static double round(double v) { return Math.round(v * 1000) / 1000.0; }
}
