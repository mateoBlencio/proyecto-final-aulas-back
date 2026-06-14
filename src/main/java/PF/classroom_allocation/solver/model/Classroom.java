package PF.classroom_allocation.solver.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Classroom {

    @EqualsAndHashCode.Include
    String id;
    String name;
    float surfaceM2;

    public int capacity() {
        return (int) surfaceM2;
    }

    public int overcrowding(int enrolled) {
        return Math.max(0, enrolled - capacity());
    }

    public int undercrowding(int enrolled) {
        return Math.max(0, capacity() - enrolled);
    }
}
