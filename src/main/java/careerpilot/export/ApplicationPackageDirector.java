package careerpilot.export;

/** Director: knows the order of building steps, not the output format. */
public class ApplicationPackageDirector {
    public ApplicationPackage construct(ApplicationPackageBuilder builder, ResolvedSelection selection) {
        builder.reset(selection.job());
        if (selection.resume() != null) builder.addResume(selection.resume());
        if (selection.letter() != null) builder.addLetter(selection.letter());
        if (selection.plan() != null) builder.addPlan(selection.plan());
        return builder.build();
    }
}
