package ar.edu.utn.frc.siga.allocation.service.command;

import ar.edu.utn.frc.siga.allocation.model.AllocationSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AllocationCommand")
class AllocationCommandTest {

    private static final List<AllocationItem> ITEMS =
            List.of(new AllocationItem(new AllocationTarget.Occurrences(List.of(10L)), 5L));

    @Test
    @DisplayName("el constructor de 3 argumentos deja currentScopeAuthorized en false")
    void threeArgConstructorDefaultsToFalse() {
        AllocationCommand command = new AllocationCommand(ITEMS, "obs", AllocationSource.MANUAL);

        assertThat(command.currentScopeAuthorized()).isFalse();
    }

    @Test
    @DisplayName("las fábricas manual, imported y automatic dejan currentScopeAuthorized en false")
    void factoriesDefaultToFalse() {
        assertThat(AllocationCommand.manual(ITEMS, "obs").currentScopeAuthorized()).isFalse();
        assertThat(AllocationCommand.imported(ITEMS, "obs").currentScopeAuthorized()).isFalse();
        assertThat(AllocationCommand.automatic(ITEMS).currentScopeAuthorized()).isFalse();
        assertThat(AllocationCommand.automatic(ITEMS, "obs").currentScopeAuthorized()).isFalse();
    }

    @Test
    @DisplayName("withCurrentScopeAuthorized pone el flag en true y conserva items, observación y origen")
    void withCurrentScopeAuthorizedSetsFlagAndKeepsFields() {
        AllocationCommand original = AllocationCommand.automatic(ITEMS, "obs");

        AllocationCommand authorized = original.withCurrentScopeAuthorized();

        assertThat(authorized.currentScopeAuthorized()).isTrue();
        assertThat(authorized.items()).isEqualTo(ITEMS);
        assertThat(authorized.observation()).isEqualTo("obs");
        assertThat(authorized.source()).isEqualTo(AllocationSource.AUTOMATIC);
    }

    @Test
    @DisplayName("withCurrentScopeAuthorized no muta el comando original")
    void withCurrentScopeAuthorizedDoesNotMutateTheOriginal() {
        AllocationCommand original = AllocationCommand.manual(ITEMS, "obs");

        original.withCurrentScopeAuthorized();

        assertThat(original.currentScopeAuthorized()).isFalse();
    }
}
