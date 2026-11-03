package careerpilot.domain;

import java.util.ArrayList;
import java.util.List;

public class PreparationTask {
    private String id;
    private String description;
    private int minutes;
    private List<String> dependsOn = new ArrayList<>();

    private PreparationTask() { }

    public PreparationTask(String id, String description, int minutes, List<String> dependsOn) {
        this.id = id; this.description = description; this.minutes = minutes;
        this.dependsOn = new ArrayList<>(dependsOn == null ? List.of() : dependsOn);
    }

    public String getId() { return id; }
    public String getDescription() { return description; }
    public int getMinutes() { return minutes; }
    public List<String> getDependsOn() { return dependsOn; }
}
