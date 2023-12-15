package example.lineworks.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/** LINE WORKSのBotからのCallbackを受信する - 送信者情報格納DTO */
@Data
public class LwRequestSource {
  /** 送信元アカウントID */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String userId;
  /** ドメインID */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Integer domainId;
  /** チャンネルID */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Integer channelId;
}
