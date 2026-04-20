package ru.syncroom.games.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Ответ после загрузки PNG для шага Gartic Phone")
public class GarticDrawingUploadResponse {
    @Schema(description = "Идентификатор файла на сервере; передаётся в SUBMIT_DRAWING как drawingAssetId")
    private String drawingAssetId;
    @Schema(description = "Относительный URL для скачивания PNG (GET с тем же Bearer)")
    private String imageUrl;
}
