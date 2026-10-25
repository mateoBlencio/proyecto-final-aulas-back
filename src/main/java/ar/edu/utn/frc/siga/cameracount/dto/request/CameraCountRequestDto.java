package ar.edu.utn.frc.siga.cameracount.dto.request;

import ar.edu.utn.frc.siga.cameracount.client.RtspUrls;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CameraCountRequestDto(
        @NotBlank
        @Size(max = 2048)
        @Pattern(regexp = "(?i)rtsps?://.+", message = "debe ser una URL rtsp:// o rtsps://")
        String rtspUrl,
        @Min(1) @Max(10) Integer samples
) {

    @Override
    public String toString() {
        return "CameraCountRequestDto[rtspUrl=" + RtspUrls.mask(rtspUrl) + ", samples=" + samples + "]";
    }
}
