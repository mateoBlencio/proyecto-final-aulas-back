package ar.edu.utn.frc.siga.preview.service;

import ar.edu.utn.frc.siga.preview.dto.request.ConfirmPreviewRequestDto;
import ar.edu.utn.frc.siga.preview.dto.request.PreviewRequestDto;
import ar.edu.utn.frc.siga.preview.dto.response.ConfirmPreviewResponseDto;
import ar.edu.utn.frc.siga.preview.dto.response.PreviewResponseDto;

public interface PreviewService {

    PreviewResponseDto autoPreview(PreviewRequestDto request);

    PreviewResponseDto getPreview(String previewId);

    ConfirmPreviewResponseDto confirm(String previewId, ConfirmPreviewRequestDto request);
}
