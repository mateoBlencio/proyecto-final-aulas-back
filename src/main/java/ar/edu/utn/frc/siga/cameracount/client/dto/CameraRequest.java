package ar.edu.utn.frc.siga.cameracount.client.dto;

import ar.edu.utn.frc.siga.cameracount.client.RtspUrls;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CameraRequest(
        @JsonProperty("rtsp_url") String rtspUrl,
        @JsonProperty("muestras") Integer samples
) {

    @Override
    public String toString() {
        return "CameraRequest[rtspUrl=" + RtspUrls.mask(rtspUrl) + ", samples=" + samples + "]";
    }
}
