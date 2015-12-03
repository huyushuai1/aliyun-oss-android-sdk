package com.alibaba.sdk.android;

import com.alibaba.sdk.android.oss.ClientException;
import com.alibaba.sdk.android.oss.OSS;
import com.alibaba.sdk.android.oss.OSSClient;
import com.alibaba.sdk.android.oss.ServiceException;
import com.alibaba.sdk.android.oss.common.OSSConstants;
import com.alibaba.sdk.android.oss.common.auth.OSSCustomSignerCredentialProvider;
import com.alibaba.sdk.android.oss.common.auth.OSSPlainTextAKSKCredentialProvider;
import com.alibaba.sdk.android.oss.common.utils.BinaryUtil;
import com.alibaba.sdk.android.oss.common.utils.IOUtils;
import com.alibaba.sdk.android.oss.common.OSSLog;
import com.alibaba.sdk.android.oss.common.auth.OSSCredentialProvider;
import com.alibaba.sdk.android.oss.common.auth.OSSFederationCredentialProvider;
import com.alibaba.sdk.android.oss.common.auth.OSSFederationToken;
import com.alibaba.sdk.android.oss.common.utils.OSSUtils;
import com.alibaba.sdk.android.oss.internal.OSSAsyncTask;
import com.alibaba.sdk.android.oss.model.PutObjectRequest;
import com.alibaba.sdk.android.oss.common.auth.HmacSHA1Signature;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;


import org.apache.http.client.CredentialsProvider;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import android.test.AndroidTestCase;

/**
 * Created by LK on 15/12/2.
 */
public class OSSAuthenticationTest extends AndroidTestCase {
    private OSS oss;
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Thread.sleep(5 * 1000); // for logcat initialization
        OSSLog.enableLog();
    }

    public void testCustomSignCredentialProvider() throws Exception {
        final OSSCredentialProvider credentialProvider = new OSSCustomSignerCredentialProvider() {
            @Override
            public String signContent(String content) {
                String signature = "";
                try {
                    signature = OSSUtils.sign(OSSTestConfig.AK, OSSTestConfig.SK, content);
                    assertNotNull(signature);
                    OSSLog.logD(signature);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
                return signature;
            }
        };
        oss = new OSSClient(getContext(), OSSTestConfig.ENDPOINT, credentialProvider);
        assertNotNull(oss);
        PutObjectRequest put = new PutObjectRequest(OSSTestConfig.ANDROID_TEST_BUCKET, "file1m",
                OSSTestConfig.FILE_DIR + "file1m");
        OSSTestConfig.TestPutCallback putCallback = new OSSTestConfig.TestPutCallback();
        OSSAsyncTask putTask = oss.asyncPutObject(put, putCallback);
        putTask.waitUntilFinished();
        assertEquals(200, putCallback.result.getStatusCode());
    }

    public void testValidCustomSignCredentialProvider() throws Exception {
        final OSSCredentialProvider credentialProvider = new OSSCustomSignerCredentialProvider() {
            @Override
            public String signContent(String content) {
                String signature = "";
                try {
                    signature = OSSUtils.sign("wrong-AK", "wrong-SK", content);
                    assertNotNull(signature);
                    OSSLog.logD(signature);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
                return signature;
            }
        };
        oss = new OSSClient(getContext(), OSSTestConfig.ENDPOINT, credentialProvider);
        assertNotNull(oss);
        PutObjectRequest put = new PutObjectRequest(OSSTestConfig.ANDROID_TEST_BUCKET, "file1m",
                OSSTestConfig.FILE_DIR + "file1m");
        OSSTestConfig.TestPutCallback putCallback = new OSSTestConfig.TestPutCallback();
        OSSAsyncTask putTask = oss.asyncPutObject(put, putCallback);
        putTask.waitUntilFinished();
        assertNotNull(putCallback.serviceException);
        assertEquals(403, putCallback.serviceException.getStatusCode());
    }

    public void testPutObjectWithNullFederationCredentialProvider() throws Exception {
        final OSSCredentialProvider credetialProvider = new OSSFederationCredentialProvider() {
        @Override
        public OSSFederationToken getFederationToken() {
            return null;
        }
    };
        oss = new OSSClient(getContext(), OSSTestConfig.ENDPOINT, credetialProvider);
        assertNotNull(oss);
        PutObjectRequest put = new PutObjectRequest(OSSTestConfig.ANDROID_TEST_BUCKET, "file1m",
                OSSTestConfig.FILE_DIR + "file1m");
        OSSTestConfig.TestPutCallback putCallback = new OSSTestConfig.TestPutCallback();
        OSSAsyncTask putTask = oss.asyncPutObject(put, putCallback);
        putTask.waitUntilFinished();
        assertNull(putCallback.result);
        assertNotNull(putCallback.clientException);
        assertTrue(putCallback.clientException.getMessage().contains("Can't get a federation token"));
    }

    public void testPutObjectWithWrongAKSKCredentiaProvider() throws Exception {
        final String AK = "wrongAK";
        final String SK = "wrongSK";
        final OSSCredentialProvider credentialProvider = new OSSPlainTextAKSKCredentialProvider(AK, SK);
        oss = new OSSClient(getContext(), OSSTestConfig.ENDPOINT, credentialProvider);
        assertNotNull(oss);
        PutObjectRequest put = new PutObjectRequest(OSSTestConfig.ANDROID_TEST_BUCKET, "file1m",
                OSSTestConfig.FILE_DIR + "file1m");
        OSSTestConfig.TestPutCallback putCallback = new OSSTestConfig.TestPutCallback();
        OSSAsyncTask putTask = oss.asyncPutObject(put, putCallback);
        putTask.waitUntilFinished();
        assertNull(putCallback.result);
        assertEquals(403, putCallback.serviceException.getStatusCode());
    }



    public void testPresignObjectURL() throws Exception {
        String url = oss.presignConstrainedObjectURL(OSSTestConfig.ANDROID_TEST_BUCKET, "file1m", 15 * 60);

        OSSLog.logD("[testPresignConstrainedObjectURL] - " + url);
        Request request = new Request.Builder().url(url).build();
        Response resp = new OkHttpClient().newCall(request).execute();

        assertEquals(1024 * 1000, resp.body().contentLength());
    }

    public void testPresignPublicObjectURL() throws Exception {
        String url = oss.presignPublicObjectURL(OSSTestConfig.ANDROID_TEST_BUCKET, "file1m");
        OSSLog.logD("[testPresignPublicObjectURL] - " + url);
    }

    public void testPresignObjectURLWithWrongBucket() throws Exception {
        oss = new OSSClient(getContext(), OSSTestConfig.ENDPOINT,
                new OSSPlainTextAKSKCredentialProvider(OSSTestConfig.AK, OSSTestConfig.SK));
        try {
            String url = oss.presignConstrainedObjectURL("wrong-bucket", "file1m", 15 * 60);
            Request request = new Request.Builder().url(url).build();
            Response response = new OkHttpClient().newCall(request).execute();
            assertEquals(404, response.code());
            assertEquals("Not Found", response.message());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void testPresignObjectURLWithWrongObjectKey() throws Exception {
        oss = new OSSClient(getContext(), OSSTestConfig.ENDPOINT,
                new OSSPlainTextAKSKCredentialProvider(OSSTestConfig.AK, OSSTestConfig.SK));
        try {
            String url = oss.presignConstrainedObjectURL(OSSTestConfig.ANDROID_TEST_BUCKET, "wrong-key", 15 * 60);
            Request request = new Request.Builder().url(url).build();
            Response response = new OkHttpClient().newCall(request).execute();
            assertEquals(404, response.code());
            assertEquals("Not Found", response.message());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void testPresignObjectURLWithExpiredTime() throws Exception {
        oss = new OSSClient(getContext(), OSSTestConfig.ENDPOINT,
                new OSSPlainTextAKSKCredentialProvider(OSSTestConfig.AK, OSSTestConfig.SK));
        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String url = oss.presignConstrainedObjectURL(OSSTestConfig.ANDROID_TEST_BUCKET, "file1m", 1);
                    latch1.await();
                    Request request = new Request.Builder().url(url).build();
                    Response response = new OkHttpClient().newCall(request).execute();
                    assertEquals(403, response.code());
                    assertEquals("Forbidden", response.message());
                    latch2.countDown();
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
        latch2.await(20, TimeUnit.SECONDS);
        latch1.countDown();
        latch2.await();
        OSSLog.logD("testPresignObjectURLWithExpiredTime success.");
    }
}
