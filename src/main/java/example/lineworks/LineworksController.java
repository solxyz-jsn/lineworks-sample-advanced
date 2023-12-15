package example.lineworks;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import example.lineworks.json.LwTextContent;
import example.lineworks.json.LwTextRequest;
import example.lineworks.json.LwAccessTokenIssueResponse;
import example.lineworks.json.LwButtonActions;
import example.lineworks.json.LwButtonTemplateContent;
import example.lineworks.json.LwButtonTemplateRequest;
import example.lineworks.json.LwCarouselColumns;
import example.lineworks.json.LwCarouselContent;
import example.lineworks.json.LwCarouselRequest;
import example.lineworks.json.LwLinkContent;
import example.lineworks.json.LwLinkRequest;
import example.lineworks.json.LwRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.tomcat.util.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

@RestController
@PropertySource("classpath:application.properties")
public class LineworksController {

  // application.properties の設定値を基に変数の値を定義
  /** LINE WORKS クライアントID */
  @Value("${lineworks.clientId}")
  private String clientId;

  /** LINE WORKS クライアントシークレット */
  @Value("${lineworks.clientSecret}")
  private String clientSecret;

  /** LINE WORKS サービスアカウント */
  @Value("${lineworks.serviceAccount}")
  private String serviceAccount;

  /** LINE WORKS 秘密鍵 */
  @Value("${lineworks.privateKey}")
  private String privateKey;

  /** LINE WORKS上で用いるbotId */
  @Value("${lineworks.botId}")
  private String botId;

  /** メッセージ改ざん検知で用いるbotシークレット */
  @Value("${lineworks.botSecret}")
  private String botSecret;

  /**
   * LINE WORKSのBotからのCallbackをPOSTリクエストとして受信して、Botへメッセージを送信する
   * 
   * @param lineworksRequest リクエストボディ
   * @param request          リクエスト情報が格納されているオブジェクト
   */
  @PostMapping("/callback")
  public void processLineWorksCallback(@RequestBody LwRequest lineworksRequest, HttpServletRequest request) {

    // 送信メッセージの署名検証
    boolean isValid;
    try {
      isValid = isValidRequest(lineworksRequest, request);
      if (!isValid) {
        System.out.println("コールバックの署名検証に失敗しました。");
        return;
      }
    } catch (Exception e) {
      System.out.println("コールバックの署名検証中に例外が発生しました。");
      e.printStackTrace();
      return;
    }

    // POSTリクエストから必要となる情報を取得
    String lineworksUserId = lineworksRequest.getSource().getUserId(); // メッセージを送信したユーザーのID。返信先に使用
    String postback = lineworksRequest.getContent().getPostback(); // 送信されたポストバック内容。条件分岐に使用
    String contentText = lineworksRequest.getContent().getText(); // 送信されたメッセージ内容

    // アクセストークン取得メソッド呼出（アクセストークンの有効期限：24時間）
    String accessToken = getAccessToken();

    // 受け取ったコールバックの中身に、ポストバックの値が存在するかを確認
    if (postback == null) {
      // ポストバックの値がない場合：ボタンテンプレートを送信する

      // ボタンテンプレートのボタン部分を作成
      LwButtonActions[] lwButtonActions = {
          LwButtonActions.builder().type("message").label("ボタンA").postback("buttonA").build(),
          LwButtonActions.builder().type("message").label("ボタンB").postback("buttonB").build(),
          LwButtonActions.builder().type("message").label("ボタンC").postback("buttonC").build()
      };

      // ボタンテンプレート作成
      LwButtonTemplateRequest lwButtonTemplateMessage = LwButtonTemplateRequest.builder().content(
          LwButtonTemplateContent.builder().type("button_template").contentText("テキストメッセージが送信されました。ボタンを選択してください")
              .actions(
                  lwButtonActions)
              .build())
          .build();

      // LINE WORKSへメッセージ送信メソッド呼出
      sendMessage(lwButtonTemplateMessage, lineworksUserId, accessToken);
      return;

    } else {
      // ポストバックの値がある場合：switch文で、さらに具体的なポストバックの値を確認する
      switch (postback) {
        case "buttonA":
          // ボタンA押下時：テキストメッセージ作成
          LwTextRequest lwTextMessage = LwTextRequest.builder()
              .content(
                  LwTextContent.builder().type("text").text(contentText).build())
              .build();

          // LINE WORKSへメッセージ送信メソッド呼出
          sendMessage(lwTextMessage, lineworksUserId, accessToken);
          return;

        case "buttonB":
          // ボタンB押下時：リンクメッセージ作成
          LwLinkRequest lwLinkMessage = LwLinkRequest.builder().content(
              LwLinkContent.builder().type("link").contentText("ボタンBが押下されました").linkText("リンクメッセージの詳細")
                  .link("https://developers.worksmobile.com/jp/docs/bot-send-link").build())
              .build();

          // LINE WORKSへメッセージ送信メソッド呼出
          sendMessage(lwLinkMessage, lineworksUserId, accessToken);
          return;

        case "buttonC":
          // ボタンC押下時：カルーセルテンプレート作成

          // カルーセルテンプレートの1つ目のカラムに表示するボタンD部分作成
          LwButtonActions[] lwButtonActions_D = {
              LwButtonActions.builder().type("message").label("ボタンD").text("ボタンD").postback("buttonD").build()
          };
          // カルーセルテンプレートの2つ目のカラムに表示するボタンE部分作成
          LwButtonActions[] lwButtonActions_E = {
              LwButtonActions.builder().type("message").label("ボタンE").text("ボタンE").postback("buttonE").build()
          };
          // カラム部分作成
          LwCarouselColumns[] lwCarouselColumns = {
              LwCarouselColumns.builder().title("ボタンCが押下されました").text("ボタンDを押してください").actions(lwButtonActions_D).build(),
              LwCarouselColumns.builder().title("ボタンCが押下されました").text("ボタンEを押してください").actions(lwButtonActions_E).build()
          };

          // カルーセルテンプレート作成
          LwCarouselRequest lwCarouselMessage = LwCarouselRequest.builder()
              .content(
                  LwCarouselContent.builder().type("carousel").columns(lwCarouselColumns).build())
              .build();

          // LINE WORKSへメッセージ送信メソッド呼出
          sendMessage(lwCarouselMessage, lineworksUserId, accessToken);
          return;

        default:
          // ボタンA, B, C以外のポストバックが送信された場合の処理（ボタンテンプレート作成）

          // ボタンテンプレートのボタン部分を作成
          LwButtonActions[] lwButtonActions = {
              LwButtonActions.builder().type("message").label("ボタンA").postback("buttonA").build(),
              LwButtonActions.builder().type("message").label("ボタンB").postback("buttonB").build(),
              LwButtonActions.builder().type("message").label("ボタンC").postback("buttonC").build()
          };

          // ボタンテンプレート作成
          LwButtonTemplateRequest lwButtonTemplateMessage = LwButtonTemplateRequest.builder().content(
              LwButtonTemplateContent.builder().type("button_template").contentText("ボタンを選択してください").actions(
                  lwButtonActions).build())
              .build();

          // LINE WORKSへメッセージ送信メソッド呼出
          sendMessage(lwButtonTemplateMessage, lineworksUserId, accessToken);
          return;
      }
    }
  }

  // *********************************************************
  // 以下、processLineWorksCallback内で呼び出されるメソッド
  // *********************************************************

  /**
   * LINE WORKS API呼び出しに必要なアクセストークンを取得する。
   * アクセストークンの有効期限：24時間
   * 
   * @see https://developers.worksmobile.com/jp/docs/auth-jwt
   * @return アクセストークン
   */
  public String getAccessToken() {
    // JWT取得のための秘密鍵生成
    RSAPrivateKey rsaPrivateKey = null;
    try {
      // 指定アルゴリズム（RSA）の非公開鍵を変換するKeyFactoryオブジェクトを取得
      final KeyFactory keyFactory = KeyFactory.getInstance("RSA");
      final PKCS8EncodedKeySpec privateSpec = new PKCS8EncodedKeySpec(
          java.util.Base64.getDecoder().decode(privateKey));
      // 非公開鍵オブジェクトを生成
      rsaPrivateKey = (RSAPrivateKey) keyFactory.generatePrivate(privateSpec);
    } catch (InvalidKeySpecException e) {
      System.out.println("非公開鍵オブジェクト生成に失敗しました");
      e.printStackTrace();
    } catch (Exception e) {
      System.out.println("予期せぬ例外が発生しました");
      e.printStackTrace();
    }

    // JWT生成時のヘッダー作成
    final Map<String, Object> headerClaims = new HashMap<>();
    // JWT生成時のペイロード（データ本体）作成
    final Map<String, Object> payloadClaims = new HashMap<>() {
      {
        put("iss", clientId);
        put("sub", serviceAccount);
        put("iat", LocalDateTime.now().atZone(ZoneOffset.UTC).toEpochSecond());
        put("exp", LocalDateTime.now().atZone(ZoneOffset.UTC).plusHours(1).toEpochSecond());
      }
    };

    // JWT生成のための署名に用いるアルゴルズム定義
    final Algorithm algorithm = Algorithm.RSA256(null, rsaPrivateKey);
    String jwtToken = null;
    try {
      // JWT生成
      jwtToken = JWT.create().withHeader(headerClaims).withPayload(payloadClaims).sign(algorithm);
    } catch (JWTCreationException e) {
      System.out.println("JWT生成に失敗しました");
      e.printStackTrace();
    } catch (Exception e) {
      System.out.println("予期せぬ例外が発生しました");
      e.printStackTrace();
    }

    // アクセストークン発行API実行のボディ部作成
    final MultiValueMap<String, String> accessTokenRequestMap = new LinkedMultiValueMap<>();
    accessTokenRequestMap.add("assertion", jwtToken);
    accessTokenRequestMap.add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
    accessTokenRequestMap.add("client_id", clientId);
    accessTokenRequestMap.add("client_secret", clientSecret);
    accessTokenRequestMap.add("scope", "bot");

    // アクセストークン発行API実行のヘッダ部作成
    final HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    // エンティティ作成
    String apiTokenUrl = "https://auth.worksmobile.com/oauth2/v2.0/token";
    RequestEntity<MultiValueMap<String, String>> entity = RequestEntity.post(URI.create(apiTokenUrl))
        .headers(httpHeaders)
        .body(accessTokenRequestMap);

    // アクセストークン取得API実行
    ResponseEntity<LwAccessTokenIssueResponse> lwAccessTokenIssueResponse = new ResponseEntity<LwAccessTokenIssueResponse>(
        null, httpHeaders, 0);
    try {
      RestTemplate restTemplate = new RestTemplate();
      lwAccessTokenIssueResponse = restTemplate.exchange(entity, LwAccessTokenIssueResponse.class);
    } catch (HttpClientErrorException | HttpServerErrorException e) {
      System.out.println("HTTPリクエスト送信時にエラーが発生しました");
      e.printStackTrace();
    } catch (Exception e) {
      System.out.println("予期せぬ例外が発生しました");
      e.printStackTrace();
    }

    // レスポンスからアクセストークン取得
    final String accessToken = Objects.requireNonNull(lwAccessTokenIssueResponse.getBody()).getAccessToken();
    return accessToken;
  }

  /**
   * **************************************************************
   * LINE WORKS APIを呼びだして、Botへメッセージ送信を行う。
   * **************************************************************
   *
   * @see https://developers.worksmobile.com/jp/docs/bot-user-message-send
   * @param lineworksRequst Botへ送信するメッセージを含むオブジェクト
   * @param lineworksUserId LINE WORKSユーザーID
   * @param accessToken     LINE WORKS API呼び出しに必要なアクセストークン
   */
  public void sendMessage(Object lineworksRequst, String lineworksUserId, String accessToken) {
    // メッセージ送信URLのパスパラメータを置換
    String lineworksSendMessageUrl = "https://www.worksapis.com/v1.0/bots/{botId}/users/{userId}/messages";
    lineworksSendMessageUrl = lineworksSendMessageUrl.replace("{botId}", botId);
    lineworksSendMessageUrl = lineworksSendMessageUrl.replace("{userId}", lineworksUserId);

    // ヘッダー作成
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(accessToken);

    ObjectMapper mapper = new ObjectMapper();
    try {
      // オブジェクトをJSON文字列へシリアライズ
      String lineworksRequestStr = mapper.writeValueAsString(lineworksRequst);

      // LINE WORKSへリクエスト送信
      RequestEntity<String> requestEntity = RequestEntity.post(new URI(lineworksSendMessageUrl)).headers(headers)
          .body(lineworksRequestStr);
      RestTemplate restTemplate = new RestTemplate();
      restTemplate.exchange(requestEntity, String.class);
    } catch (JsonProcessingException e) {
      System.out.println("JSON文字列への変換に失敗しました");
      e.printStackTrace();
    } catch (HttpClientErrorException | HttpServerErrorException e) {
      System.out.println("HTTPリクエスト送信時にエラーが発生しました");
      e.printStackTrace();
    } catch (Exception e) {
      System.out.println("予期せぬ例外が発生しました");
      e.printStackTrace();
    }

    return;
  }

  /**
   * メッセージの改ざん有無を確認。X-WORKS-Signature のヘッダー値と比較し、同一であればメッセージは改ざんされていないと判断
   *
   * @param lwRequest
   * @param request
   * @return true:改ざんされていない false:改ざんされている
   * @throws Exception
   */
  private boolean isValidRequest(LwRequest lwRequest, HttpServletRequest request)
      throws JsonProcessingException,
      NoSuchAlgorithmException,
      InvalidKeyException,
      UnsupportedEncodingException {
    // DTOをString型のJSONに変換するライブラリ
    ObjectMapper mapper = new ObjectMapper();

    // javax.crypto.Macでメッセージ認証コード（MAC）作成
    Mac mac = Mac.getInstance("HmacSHA256");
    SecretKeySpec key = new SecretKeySpec(botSecret.getBytes(), "HmacSHA256");
    mac.init(key);
    // メッセージをバイト配列に変換
    String httpRequestBody = mapper.writeValueAsString(lwRequest);
    byte[] source = httpRequestBody.getBytes("UTF-8");
    // HMAC-SHA256の計算結果をBase64でエンコードして出力
    String signature = Base64.encodeBase64String(mac.doFinal(source));

    // signatureの比較で改ざん確認
    boolean isValid = signature.equals(request.getHeader("X-WORKS-Signature"));
    return isValid;
  }
}
