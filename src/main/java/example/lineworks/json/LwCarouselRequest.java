package example.lineworks.json;

import lombok.Builder;
import lombok.Data;

/** カルーセルテンプレート送信用DTO */
@Data
@Builder
public class LwCarouselRequest {
    /** コンテント */
    private LwCarouselContent content;
}
