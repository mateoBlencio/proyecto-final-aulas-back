package ar.edu.utn.frc.siga.dashboard.model;

public record CategoryCounts(long overlap, long overcrowded, long underused, long adequate) {

    public static final CategoryCounts ZERO = new CategoryCounts(0, 0, 0, 0);

    public long total() {
        return overlap + overcrowded + underused + adequate;
    }

    public CategoryCounts plus(CategoryCounts other) {
        return new CategoryCounts(overlap + other.overlap, overcrowded + other.overcrowded,
                underused + other.underused, adequate + other.adequate);
    }

    public CategoryCounts with(AllocationCategory category) {
        return plus(switch (category) {
            case OVERLAP -> new CategoryCounts(1, 0, 0, 0);
            case OVERCROWDED -> new CategoryCounts(0, 1, 0, 0);
            case UNDERUSED -> new CategoryCounts(0, 0, 1, 0);
            case ADEQUATE -> new CategoryCounts(0, 0, 0, 1);
        });
    }
}
