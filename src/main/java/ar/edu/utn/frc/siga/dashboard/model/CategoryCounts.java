package ar.edu.utn.frc.siga.dashboard.model;

/** Counts per category; a multi-room group contributes a fraction (1/roomCount) per allocation. */
public record CategoryCounts(double overlap, double overcrowded, double underused, double adequate) {

    public static final CategoryCounts ZERO = new CategoryCounts(0, 0, 0, 0);

    public double total() {
        return overlap + overcrowded + underused + adequate;
    }

    public CategoryCounts plus(CategoryCounts other) {
        return new CategoryCounts(overlap + other.overlap, overcrowded + other.overcrowded,
                underused + other.underused, adequate + other.adequate);
    }

    public CategoryCounts with(AllocationCategory category, double weight) {
        return plus(switch (category) {
            case OVERLAP -> new CategoryCounts(weight, 0, 0, 0);
            case OVERCROWDED -> new CategoryCounts(0, weight, 0, 0);
            case UNDERUSED -> new CategoryCounts(0, 0, weight, 0);
            case ADEQUATE -> new CategoryCounts(0, 0, 0, weight);
        });
    }
}
