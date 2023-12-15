package example.lineworks.json;

import lombok.Builder;
import lombok.Data;

/** カルーセルテンプレートのコンテント用DTO */
@Data
@Builder
public class LwCarouselContent {
    /** タイプ */
    private String type;
    /** カルーセルのオブジェクトリスト */
    private LwCarouselColumns[] columns;

}
