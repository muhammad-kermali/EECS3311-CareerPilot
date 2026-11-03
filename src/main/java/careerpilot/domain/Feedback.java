package careerpilot.domain;

import java.util.ArrayList;
import java.util.List;

/** Coaching feedback on one answer. It is advice, not a grade from an employer. */
public class Feedback {
    private String summary;
    private List<String> strengths = new ArrayList<>();
    private List<String> improvements = new ArrayList<>();
    private int rating;

    private Feedback() { }

    public Feedback(String summary, List<String> strengths, List<String> improvements, int rating) {
        this.summary = summary; this.strengths = new ArrayList<>(strengths);
        this.improvements = new ArrayList<>(improvements); this.rating = rating;
    }

    public String getSummary() { return summary; }
    public List<String> getStrengths() { return strengths; }
    public List<String> getImprovements() { return improvements; }
    public int getRating() { return rating; }
}
