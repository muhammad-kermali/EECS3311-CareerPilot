package careerpilot.export;

import careerpilot.domain.Entity;
import careerpilot.domain.JobPosting;
import java.util.LinkedHashMap;
import java.util.Map;

/** Shared state for the concrete builders: collected files and source revisions. */
abstract class AbstractPackageBuilder implements ApplicationPackageBuilder {
    protected Map<String, String> files = new LinkedHashMap<>();
    protected Map<String, Integer> sources = new LinkedHashMap<>();
    protected JobPosting job;

    @Override
    public void reset(JobPosting job) {
        this.files = new LinkedHashMap<>();
        this.sources = new LinkedHashMap<>();
        this.job = job;
        if (job != null) track(job);
    }

    protected void track(Entity e) { sources.put(e.sourceKey(), e.getRevision()); }

    protected String manifestBody() {
        StringBuilder sb = new StringBuilder();
        sb.append("CareerPilot application package\n");
        if (job != null) sb.append("Job: ").append(job.label()).append('\n');
        sb.append("Files:\n");
        for (String f : files.keySet()) sb.append("  ").append(f).append('\n');
        sb.append("Source records (revision):\n");
        sources.forEach((k, v) -> sb.append("  ").append(k).append(" (rev ").append(v).append(")\n"));
        sb.append("Nothing in this package was submitted to any employer.\n");
        return sb.toString();
    }

    @Override
    public ApplicationPackage build() {
        if (files.isEmpty()) throw new IllegalStateException("No documents were added to the package");
        files.put("MANIFEST.txt", manifestBody());
        return new ApplicationPackage(files, sources);
    }
}
