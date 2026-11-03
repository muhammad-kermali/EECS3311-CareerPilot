package careerpilot.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import careerpilot.domain.Constraints;
import careerpilot.domain.PreparationPlan;
import careerpilot.domain.PreparationTask;
import careerpilot.domain.ValidationResult;
import careerpilot.testsupport.Fixtures;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlanValidatorTest {
    private final PlanValidator validator = new PlanValidator(Fixtures.clock());
    private final Constraints twoHours = new Constraints(120, LocalDate.of(2026, 11, 30), null);

    private static PreparationPlan plan(Constraints c, PreparationTask... tasks) { return new PreparationPlan("P1", "J1", c, "", List.of(tasks)); }

    @Test
    void validPlanWithinTimeIsFeasible() {
        ValidationResult r = validator.validate(plan(twoHours, new PreparationTask("T1", "Tailor resume", 30, List.of()),
                new PreparationTask("T2", "Submit", 15, List.of("T1"))), twoHours);
        assertTrue(r.valid());
        assertTrue(r.feasible());
    }

    @Test
    void cycleIsDetected() {
        ValidationResult r = validator.validate(plan(twoHours, new PreparationTask("T1", "A", 10, List.of("T2")),
                new PreparationTask("T2", "B", 10, List.of("T1"))), twoHours);
        assertFalse(r.valid());
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("circular")));
    }

    @Test
    void unknownDependencyAndDuplicateIdsAreErrors() {
        ValidationResult r = validator.validate(plan(twoHours, new PreparationTask("T1", "A", 10, List.of("T9")),
                new PreparationTask("T1", "B", 10, List.of())), twoHours);
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("unknown task T9")));
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("duplicate task ID T1")));
    }

    @Test
    void wellFormedButTooLongIsValidButNotFeasible() {
        ValidationResult r = validator.validate(plan(twoHours, new PreparationTask("T1", "Big task", 200, List.of())), twoHours);
        assertTrue(r.valid());
        assertFalse(r.feasible());
    }

    @Test
    void pastDeadlineIsNotFeasible() {
        Constraints past = new Constraints(120, LocalDate.of(2026, 10, 1), null);
        assertFalse(validator.validate(plan(past, new PreparationTask("T1", "A", 10, List.of())), past).feasible());
    }
}
