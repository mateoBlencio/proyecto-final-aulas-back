package ar.edu.utn.frc.siga.preview.service;

import java.util.List;

import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.preview.dto.request.ConfirmPreviewRequestDto;
import ar.edu.utn.frc.siga.preview.dto.request.PreviewRequestDto;
import ar.edu.utn.frc.siga.preview.dto.request.ReallocationSuggestionRequestDto;
import ar.edu.utn.frc.siga.preview.dto.response.ConfirmPreviewResponseDto;
import ar.edu.utn.frc.siga.preview.dto.response.PreviewResponseDto;
import ar.edu.utn.frc.siga.preview.dto.response.ReallocationSuggestionResponseDto;

public interface PreviewService {

    PreviewResponseDto autoPreview(PreviewRequestDto request);

    PreviewResponseDto getPreview(String previewId);

    ConfirmPreviewResponseDto confirm(String previewId, ConfirmPreviewRequestDto request);

    ReallocationSuggestionResponseDto suggestReallocation(ReallocationSuggestionRequestDto request);

    List<AllocationResponseDto> confirmReallocationSuggestion(String suggestionId);
}
