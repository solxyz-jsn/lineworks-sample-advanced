package example.lineworks.json;

import lombok.Builder;
import lombok.Data;

/** リンクメッセージの送信用DTO */
@Data
@Builder
public class LwLinkRequest {
    /** コンテント */
    private LwLinkContent content;
}
