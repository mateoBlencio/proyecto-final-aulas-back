package ar.edu.utn.frc.auditfixtures;

import jakarta.persistence.Entity;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Table;

/**
 * Annotated classes for {@code SigaRevisionListenerSummaryTest}. They live outside {@code ar.edu.utn.frc.siga}
 * on purpose: the Spring Boot entity scan covers that package, and these {@code @Entity} classes have no
 * {@code @Id}, so the integration contexts would fail to start.
 */
public final class SummaryEntityFixtures {

    private SummaryEntityFixtures() {
    }

    @MappedSuperclass
    public static class BaseNotEntity {
    }

    @Entity
    @Table(name = "unit_root")
    public static class Root extends BaseNotEntity {
    }

    @Entity
    @Table(name = "unit_child")
    public static class JoinedChild extends Root {
    }

    @Entity
    public static class JoinedGrandChild extends JoinedChild {
    }

    @Entity
    public static class RootWithoutTable {
    }

    @Entity
    @Table(name = "unit_child_with_table")
    public static class ChildOfRootWithoutTable extends RootWithoutTable {
    }

    @Entity
    @Table(schema = "public")
    public static class RootWithBlankTableName {
    }
}
