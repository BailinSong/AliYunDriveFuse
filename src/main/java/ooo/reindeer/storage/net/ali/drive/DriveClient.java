package ooo.reindeer.storage.net.ali.drive;

import com.aliyun.pds.client.Client;
import com.aliyun.pds.client.models.*;
import com.aliyun.tea.*;
import com.aliyun.teautil.Common;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

/**
 * @ClassName DriveClient
 * @Author songbailin
 * @Date 2021/6/28 10:35
 * @Version 1.0
 * @Description TODO
 */
public class DriveClient extends Client {


    String driveId;

    public String getDriveId() {
        return driveId;
    }

    public void setDriveId(String driveId) {
        this.driveId = driveId;
    }

    public DriveClient(Config config) throws Exception {
        super(config);
        if (!Common.empty(config.accessToken) || !Common.empty(config.refreshToken)) {
            com.aliyun.pdscredentials.models.Config accessConfig = com.aliyun.pdscredentials.models.Config.build(TeaConverter.buildMap(new TeaPair[]{new TeaPair("accessToken", config.accessToken), new TeaPair("endpoint", config.endpoint), new TeaPair("domainId", config.domainId), new TeaPair("clientId", config.clientId), new TeaPair("refreshToken", config.refreshToken), new TeaPair("clientSecret", config.clientSecret), new TeaPair("expireTime", config.expireTime)}));
            this._accessTokenCredential = new PDSClient(accessConfig);
        }
    }


    @Override
    public AccountTokenModel accountTokenEx(AccountTokenRequest request, RuntimeOptions runtime) throws Exception {
//        TeaModel.validateParams(request, "request");
//        TeaModel.validateParams(runtime, "runtime");
        Map<String, Object> runtime_ = TeaConverter.buildMap(new TeaPair[]{new TeaPair("timeouted", "retry"), new TeaPair("readTimeout", runtime.readTimeout), new TeaPair("connectTimeout", runtime.connectTimeout), new TeaPair("localAddr", runtime.localAddr), new TeaPair("httpProxy", runtime.httpProxy), new TeaPair("httpsProxy", runtime.httpsProxy), new TeaPair("noProxy", runtime.noProxy), new TeaPair("maxIdleConns", runtime.maxIdleConns), new TeaPair("socks5Proxy", runtime.socks5Proxy), new TeaPair("socks5NetWork", runtime.socks5NetWork), new TeaPair("retry", TeaConverter.buildMap(new TeaPair[]{new TeaPair("retryable", runtime.autoretry), new TeaPair("maxAttempts", Common.defaultNumber(runtime.maxAttempts, 3))})), new TeaPair("backoff", TeaConverter.buildMap(new TeaPair[]{new TeaPair("policy", Common.defaultString(runtime.backoffPolicy, "no")), new TeaPair("period", Common.defaultNumber(runtime.backoffPeriod, 1))})), new TeaPair("ignoreSSL", runtime.ignoreSSL)});
        TeaRequest _lastRequest = null;
        Exception _lastException = null;
        long _now = System.currentTimeMillis();
        int _retryTimes = 0;

        while (Tea.allowRetry((Map) runtime_.get("retry"), _retryTimes, _now)) {
            if (_retryTimes > 0) {
                int backoffTime = Tea.getBackoffTime(runtime_.get("backoff"), _retryTimes);
                if (backoffTime > 0) {
                    Tea.sleep(backoffTime);
                }
            }

            ++_retryTimes;

            try {
                TeaRequest request_ = new TeaRequest();
                String refreshToken = this.getRefreshToken();
                Map<String, Object> realReq = Common.toMap(request);
                request_.protocol = Common.defaultString(this._protocol, "https");
                request_.method = "POST";
                request_.pathname = "/token/refresh";
                request_.headers = TeaConverter.merge(String.class, new Map[]{TeaConverter.buildMap(new TeaPair[]{new TeaPair("user-agent", this.getUserAgent()), new TeaPair("host", Common.defaultString(this._endpoint, "websv.aliyundrive.com")), new TeaPair("content-type", "application/json; charset=utf-8")}), request.headers});


                if (!Common.empty(refreshToken)) {
                    realReq.put("realReq", Collections.singletonMap("refresh_token", refreshToken));
                }

                request_.body = Tea.toReadable(Common.toJSONString(realReq));
                TeaResponse response_ = Tea.doAction(request_, runtime_);
                Map<String, Object> respMap = null;
                Object obj = null;
                if (Common.equalNumber(response_.statusCode, 200)) {
                    obj = Common.readAsJSON(response_.body);
                    respMap = Common.assertAsMap(obj);
                    AccountTokenModel accountTokenModel = (AccountTokenModel) TeaModel.toModel(TeaConverter.buildMap(new TeaPair[]{new TeaPair("body", respMap), new TeaPair("headers", response_.headers)}), new AccountTokenModel());
                    _accessTokenCredential.setAccessToken(accountTokenModel.getBody().getAccessToken());
                    _accessTokenCredential.setExpireTime(accountTokenModel.getBody().getExpireTime());
                    _accessTokenCredential.setRefreshToken(accountTokenModel.getBody().getRefreshToken());
                    return accountTokenModel;
                }

                if (!Common.empty((String) response_.headers.get("x-ca-error-message"))) {
                    throw new TeaException(TeaConverter.buildMap(new TeaPair[]{new TeaPair("data", TeaConverter.buildMap(new TeaPair[]{new TeaPair("requestId", response_.headers.get("x-ca-request-id")), new TeaPair("statusCode", response_.statusCode), new TeaPair("statusMessage", response_.statusMessage)})), new TeaPair("message", response_.headers.get("x-ca-error-message"))}));
                }

                obj = Common.readAsJSON(response_.body);
                respMap = Common.assertAsMap(obj);
                throw new TeaException(TeaConverter.merge(Object.class, new Map[]{TeaConverter.buildMap(new TeaPair[]{new TeaPair("data", TeaConverter.buildMap(new TeaPair[]{new TeaPair("requestId", response_.headers.get("x-ca-request-id")), new TeaPair("statusCode", response_.statusCode), new TeaPair("statusMessage", response_.statusMessage)}))}), respMap}));
            } catch (Exception var18) {
                if (!Tea.isRetryable(var18)) {
                    throw var18;
                }

                _lastException = var18;
            }
        }

        throw new TeaUnretryableException((TeaRequest) _lastRequest, _lastException);
    }

    @Override
    public GetUserModel getUserEx(GetUserRequest request, RuntimeOptions runtime) throws Exception {
//        TeaModel.validateParams(request, "request");
//        TeaModel.validateParams(runtime, "runtime");
        Map<String, Object> runtime_ = TeaConverter.buildMap(new TeaPair[]{new TeaPair("timeouted", "retry"), new TeaPair("readTimeout", runtime.readTimeout), new TeaPair("connectTimeout", runtime.connectTimeout), new TeaPair("localAddr", runtime.localAddr), new TeaPair("httpProxy", runtime.httpProxy), new TeaPair("httpsProxy", runtime.httpsProxy), new TeaPair("noProxy", runtime.noProxy), new TeaPair("maxIdleConns", runtime.maxIdleConns), new TeaPair("socks5Proxy", runtime.socks5Proxy), new TeaPair("socks5NetWork", runtime.socks5NetWork), new TeaPair("retry", TeaConverter.buildMap(new TeaPair[]{new TeaPair("retryable", runtime.autoretry), new TeaPair("maxAttempts", Common.defaultNumber(runtime.maxAttempts, 3))})), new TeaPair("backoff", TeaConverter.buildMap(new TeaPair[]{new TeaPair("policy", Common.defaultString(runtime.backoffPolicy, "no")), new TeaPair("period", Common.defaultNumber(runtime.backoffPeriod, 1))})), new TeaPair("ignoreSSL", runtime.ignoreSSL)});
        TeaRequest _lastRequest = null;
        Exception _lastException = null;
        long _now = System.currentTimeMillis();
        int _retryTimes = 0;

        while (Tea.allowRetry((Map) runtime_.get("retry"), _retryTimes, _now)) {
            if (_retryTimes > 0) {
                int backoffTime = Tea.getBackoffTime(runtime_.get("backoff"), _retryTimes);
                if (backoffTime > 0) {
                    Tea.sleep(backoffTime);
                }
            }

            ++_retryTimes;

            try {
                TeaRequest request_ = new TeaRequest();
                String accesskeyId = this.getAccessKeyId();
                String accessKeySecret = this.getAccessKeySecret();
                String securityToken = this.getSecurityToken();
                String accessToken = this.getAccessToken();
                Map<String, Object> realReq = Common.toMap(request);
                request_.protocol = Common.defaultString(this._protocol, "https");
                request_.method = "POST";
                request_.pathname = this.getPathname(this._nickname, "/v2/user/get");
                request_.headers = TeaConverter.merge(String.class, new Map[]{TeaConverter.buildMap(new TeaPair[]{new TeaPair("user-agent", this.getUserAgent()), new TeaPair("host", Common.defaultString(this._endpoint, "api.aliyundrive.com")), new TeaPair("content-type", "application/json; charset=utf-8")}), request.headers});
                realReq.put("headers", (Object) null);
                if (!Common.empty(accessToken)) {
                    request_.headers.put("authorization", "Bearer " + accessToken + "");
                } else if (!Common.empty(accesskeyId) && !Common.empty(accessKeySecret)) {
                    if (!Common.empty(securityToken)) {
                        request_.headers.put("x-acs-security-token", securityToken);
                    }

                    request_.headers.put("date", Common.getDateUTCString());
                    request_.headers.put("accept", "application/json");
                    request_.headers.put("x-acs-signature-method", "HMAC-SHA1");
                    request_.headers.put("x-acs-signature-version", "1.0");
                    String stringToSign = com.aliyun.roautil.Client.getStringToSign(request_);
                    request_.headers.put("authorization", "acs " + accesskeyId + ":" + com.aliyun.roautil.Client.getSignature(stringToSign, accessKeySecret) + "");
                }

                request_.body = Tea.toReadable(Common.toJSONString(realReq));
                TeaResponse response_ = Tea.doAction(request_, runtime_);
                Map<String, Object> respMap = null;
                Object obj = null;
                if (Common.equalNumber(response_.statusCode, 200)) {
                    obj = Common.readAsJSON(response_.body);
                    respMap = Common.assertAsMap(obj);
                    GetUserModel getUserModel = (GetUserModel) TeaModel.toModel(TeaConverter.buildMap(new TeaPair[]{new TeaPair("body", respMap), new TeaPair("headers", response_.headers)}), new GetUserModel());


                    this._domainId = getUserModel.getBody().domainId;
                    return getUserModel;
                }

                if (!Common.empty((String) response_.headers.get("x-ca-error-message"))) {
                    throw new TeaException(TeaConverter.buildMap(new TeaPair[]{new TeaPair("data", TeaConverter.buildMap(new TeaPair[]{new TeaPair("requestId", response_.headers.get("x-ca-request-id")), new TeaPair("statusCode", response_.statusCode), new TeaPair("statusMessage", response_.statusMessage)})), new TeaPair("message", response_.headers.get("x-ca-error-message"))}));
                }

                obj = Common.readAsJSON(response_.body);
                respMap = Common.assertAsMap(obj);
                throw new TeaException(TeaConverter.merge(Object.class, new Map[]{TeaConverter.buildMap(new TeaPair[]{new TeaPair("data", TeaConverter.buildMap(new TeaPair[]{new TeaPair("requestId", response_.headers.get("x-ca-request-id")), new TeaPair("statusCode", response_.statusCode), new TeaPair("statusMessage", response_.statusMessage)}))}), respMap}));
            } catch (Exception var18) {
                if (!Tea.isRetryable(var18)) {
                    throw var18;
                }

                _lastException = var18;
            }
        }

        throw new TeaUnretryableException((TeaRequest) _lastRequest, _lastException);
    }


    public byte[] downloadPart(String driveId, String fileId, int size, int offset, int length) throws Exception {
        System.out.println("DriveClient.download( " + "driveId = [" + driveId + "], fileId = [" + fileId + "], size = [" + size + "], offset = [" + offset + "], length = [" + length + "]" + " )");


        RuntimeOptions runtime = new RuntimeOptions();
        Map<String, Object> runtime_ = TeaConverter.buildMap(new TeaPair[]{new TeaPair("timeouted", "retry"), new TeaPair("readTimeout", runtime.readTimeout), new TeaPair("connectTimeout", runtime.connectTimeout), new TeaPair("localAddr", runtime.localAddr), new TeaPair("httpProxy", runtime.httpProxy), new TeaPair("httpsProxy", runtime.httpsProxy), new TeaPair("noProxy", runtime.noProxy), new TeaPair("maxIdleConns", runtime.maxIdleConns), new TeaPair("socks5Proxy", runtime.socks5Proxy), new TeaPair("socks5NetWork", runtime.socks5NetWork), new TeaPair("retry", TeaConverter.buildMap(new TeaPair[]{new TeaPair("retryable", runtime.autoretry), new TeaPair("maxAttempts", Common.defaultNumber(runtime.maxAttempts, 3))})), new TeaPair("backoff", TeaConverter.buildMap(new TeaPair[]{new TeaPair("policy", Common.defaultString(runtime.backoffPolicy, "no")), new TeaPair("period", Common.defaultNumber(runtime.backoffPeriod, 1))})), new TeaPair("ignoreSSL", runtime.ignoreSSL)});
        TeaRequest _lastRequest = null;
        Exception _lastException = null;
        long _now = System.currentTimeMillis();
        int _retryTimes = 0;

        while (Tea.allowRetry((Map) runtime_.get("retry"), _retryTimes, _now)) {
            if (_retryTimes > 0) {
                int backoffTime = Tea.getBackoffTime(runtime_.get("backoff"), _retryTimes);
                if (backoffTime > 0) {
                    Tea.sleep(backoffTime);
                }
            }

            ++_retryTimes;

            try {
                String accessToken = this.getAccessToken();
                HttpURLConnection httpConnection = (HttpURLConnection) new URL("https://api.aliyundrive.com/v2/file/download?drive_id=" + driveId + "&file_id=" + fileId).openConnection();

               /* authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOiJhN2U4OGYyMzBkOWU0OWM5YmU2YTI2MWU4OGEyZTFkZiIsImN1c3RvbUpzb24iOiJ7XCJjbGllbnRJZFwiOlwiMjVkelgzdmJZcWt0Vnh5WFwiLFwiZG9tYWluSWRcIjpcImJqMjlcIixcInNjb3BlXCI6W1wiRFJJVkUuQUxMXCIsXCJTSEFSRS5BTExcIixcIkZJTEUuQUxMXCIsXCJVU0VSLkFMTFwiLFwiU1RPUkFHRS5BTExcIixcIlNUT1JBR0VGSUxFLkxJU1RcIixcIkJBVENIXCIsXCJPQVVUSC5BTExcIixcIklNQUdFLkFMTFwiLFwiSU5WSVRFLkFMTFwiLFwiQUNDT1VOVC5BTExcIl0sXCJyb2xlXCI6XCJ1c2VyXCIsXCJyZWZcIjpcImh0dHBzOi8vd3d3LmFsaXl1bmRyaXZlLmNvbS9cIn0iLCJleHAiOjE2MjQ5NDI1ODgsImlhdCI6MTYyNDkzNTMyOH0.nQ7awl3bnqKZrlIzPcXTlFhUedupHZsuw46yw49wdS2AJIpXwGaDTEFTJYYqMYMaxaXlwFkLbjr8QqvifVHuUddmvhYEw5YjQD6UunT2K6GxRFjhutQu_ZKWe0ctaFe_J0gFtNTxJrqxapXL_Ggs2RqjUNPSmBYu_-ZCyA1RrQQ
                user-agent: AlibabaCloud (Mac OS X; x86_64) Java/1.8.0_211-b12 tea-util/0.2.6 TeaDSL/1
                Referer:https://www.aliyundrive.com/
                RANGE: bytes=0-65536
                        */
                httpConnection.setRequestProperty("User-Agent", this.getUserAgent());
                httpConnection.setReadTimeout(60000);
//xxx表示你已下载的文件大小
                httpConnection.setRequestProperty("authorization", "Bearer " + accessToken + "");
                httpConnection.setRequestProperty("Referer", "https://www.aliyundrive.com/");
                httpConnection.setRequestProperty("RANGE", "bytes=" + offset + "-" + (((offset + length) == size) ? "" : (offset + length)));
//                System.out.println(httpConnection+" RANGE:bytes=" + offset + "-"+(((offset+length)==size)?"":(offset+length)));
                byte[] buff = new byte[length];

                if (httpConnection.getResponseCode() == 206 || httpConnection.getResponseCode() == 200) {
                    int buffOffset = 0;
                    int readLength = -1;
                    try (InputStream input = httpConnection.getInputStream()) {
                        do {

                            readLength = input.read(buff, buffOffset, length - buffOffset);
//                            System.out.println("buffOffset = " + buffOffset+" readLength="+readLength);
                            buffOffset += readLength;

                        } while (length != buffOffset);
                    }
//                    System.out.println("DriveClient.download( "+"driveId = [" + driveId + "], fileId = [" + fileId + "], size = [" + size + "], offset = [" + offset + "], length = [" + length + "]"+" )"+new String(buff));

                    return buff;
                }

                throw new TeaException(TeaConverter.merge(Object.class, new Map[]{TeaConverter.buildMap(new TeaPair[]{new TeaPair("data", TeaConverter.buildMap(new TeaPair[]{new TeaPair("statusCode", httpConnection.getResponseCode()), new TeaPair("statusMessage", httpConnection.getResponseMessage())}))})}));
            } catch (Exception var18) {
                if (!Tea.isRetryable(var18)) {
                    throw var18;
                }

                _lastException = var18;
            }
        }

        throw new TeaUnretryableException((TeaRequest) _lastRequest, _lastException);


    }

    private static String composeUrl(TeaRequest request) {
        Map<String, String> queries = request.query;
        String host = (String) request.headers.get("host");
        String protocol = null == request.protocol ? "http" : request.protocol;
        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append(protocol);
        urlBuilder.append("://").append(host);
        if (null != request.pathname) {
            urlBuilder.append(request.pathname);
        }

        if (queries != null && queries.size() > 0) {
            if (urlBuilder.indexOf("?") >= 1) {
                urlBuilder.append("&");
            } else {
                urlBuilder.append("?");
            }

            try {
                Iterator var5 = queries.entrySet().iterator();

                while (var5.hasNext()) {
                    Map.Entry<String, String> entry = (Map.Entry) var5.next();
                    String key = (String) entry.getKey();
                    String val = (String) entry.getValue();
                    if (val != null && !"null".equals(val)) {
                        urlBuilder.append(URLEncoder.encode(key, "UTF-8"));
                        urlBuilder.append("=");
                        urlBuilder.append(URLEncoder.encode(val, "UTF-8"));
                        urlBuilder.append("&");
                    }
                }
            } catch (Exception var9) {
                throw new TeaException(var9.getMessage(), var9);
            }

            int strIndex = urlBuilder.length();
            urlBuilder.deleteCharAt(strIndex - 1);
        }

        return urlBuilder.toString();
    }
}
