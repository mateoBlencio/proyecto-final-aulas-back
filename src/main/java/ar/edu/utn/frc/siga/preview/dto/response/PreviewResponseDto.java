package ar.edu.utn.frc.siga.preview.dto.response;

import java.util.List;

public record PreviewResponseDto(
        String previewId,
        List<PreviewItemDto> allocations,
        List<UnresolvedAllocationDto> unresolved
) {
}
