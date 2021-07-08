package ooo.reindeer.storage.net.ali.drive;

import com.aliyun.pds.client.models.AccountTokenModel;
import com.aliyun.pds.client.models.RuntimeOptions;
import com.aliyun.pdscredentials.Client;
import com.aliyun.pdscredentials.models.Config;
import com.aliyun.tea.*;
import com.aliyun.teautil.Common;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @ClassName pdsClient
 * @Author songbailin
 * @Date 2021/6/28 13:57
 * @Version 1.0
 * @Description TODO
 */
public class PDSClient extends Client {
    public PDSClient(Config config) {
        super(config);
    }

    @Override
    public void refreshAccessToken() throws NoSuchAlgorithmException, IOException, KeyManagementException, TeaException {
        TeaRequest request_ = new TeaRequest();
        request_.protocol = "https";
        request_.method = "POST";
        request_.pathname = "/token/refresh";
        request_.headers = TeaConverter.merge(String.class, new Map[]{TeaConverter.buildMap(new TeaPair("host", Common.defaultString("", "websv.aliyundrive.com")), new TeaPair("content-type", "application/json; charset=utf-8"))});


        Map<String, Object> realReq = new HashMap<>();
        try {
            realReq.put("realReq", Collections.singletonMap("refresh_token", this.getRefreshToken()));
            realReq.put("refresh_token", this.getRefreshToken());
            realReq.put("grant_type", "refresh_token");
        } catch (Exception e) {
            throw new TeaException("RefreshToken is null", e);
        }


        request_.body = Tea.toReadable(Common.toJSONString(realReq));
        RuntimeOptions runtime = new RuntimeOptions();
        Map<String, Object> runtime_ = TeaConverter.buildMap(new TeaPair[]{new TeaPair("timeouted", "retry"), new TeaPair("readTimeout", runtime.readTimeout), new TeaPair("connectTimeout", runtime.connectTimeout), new TeaPair("localAddr", runtime.localAddr), new TeaPair("httpProxy", runtime.httpProxy), new TeaPair("httpsProxy", runtime.httpsProxy), new TeaPair("noProxy", runtime.noProxy), new TeaPair("maxIdleConns", runtime.maxIdleConns), new TeaPair("socks5Proxy", runtime.socks5Proxy), new TeaPair("socks5NetWork", runtime.socks5NetWork), new TeaPair("retry", TeaConverter.buildMap(new TeaPair[]{new TeaPair("retryable", runtime.autoretry), new TeaPair("maxAttempts", Common.defaultNumber(runtime.maxAttempts, 3))})), new TeaPair("backoff", TeaConverter.buildMap(new TeaPair[]{new TeaPair("policy", Common.defaultString(runtime.backoffPolicy, "no")), new TeaPair("period", Common.defaultNumber(runtime.backoffPeriod, 1))})), new TeaPair("ignoreSSL", runtime.ignoreSSL)});

        TeaResponse response_ = Tea.doAction(request_, runtime_);
        Map<String, Object> respMap = null;
        Object obj = null;
        try {
            if (Common.equalNumber(response_.statusCode, 200)) {
                obj = Common.readAsJSON(response_.body);
                respMap = Common.assertAsMap(obj);
                AccountTokenModel accountTokenModel = (AccountTokenModel) TeaModel.toModel(TeaConverter.buildMap(new TeaPair[]{new TeaPair("body", respMap), new TeaPair("headers", response_.headers)}), new AccountTokenModel());

                this.setAccessToken(accountTokenModel.getBody().getAccessToken());

                this.setExpireTime(accountTokenModel.getBody().getExpireTime());
                this.setRefreshToken(accountTokenModel.getBody().getRefreshToken());
            } else {
                throw new TeaException(response_.statusMessage, new Throwable());
            }
        } catch (Exception e) {
            throw new TeaException(response_.statusMessage, e);
        }
    }

}
