package example.lineworks.json;

import lombok.Builder;
import lombok.Data;

/** テキストメッセージ送信用DTO */
@Data
@Builder
public class LwTextRequest {
  /** コンテント */
  private LwTextContent content;
}
