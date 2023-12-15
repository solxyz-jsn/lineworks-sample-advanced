package example.lineworks.json;

import lombok.Builder;
import lombok.Data;

/** ボタンテンプレート送信用DTO */
@Data
@Builder
public class LwButtonTemplateRequest {
    /** コンテント */
    private LwButtonTemplateContent content;
}
