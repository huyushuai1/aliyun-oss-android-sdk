package com.alibaba.sdk.android.oss.internal;

import android.content.Context;
import android.os.Build;

import com.alibaba.sdk.android.oss.ClientConfiguration;
import com.alibaba.sdk.android.oss.callback.OSSCompletedCallback;
import com.alibaba.sdk.android.oss.common.HttpMethod;
import com.alibaba.sdk.android.oss.common.OSSHeaders;
import com.alibaba.sdk.android.oss.common.auth.OSSCredentialProvider;
import com.alibaba.sdk.android.oss.common.utils.DateUtil;
import com.alibaba.sdk.android.oss.common.utils.HttpHeaders;
import com.alibaba.sdk.android.oss.common.utils.OSSUtils;
import com.alibaba.sdk.android.oss.common.utils.VersionInfoUtils;
import com.alibaba.sdk.android.oss.model.AbortMultipartUploadRequest;
import com.alibaba.sdk.android.oss.model.AbortMultipartUploadResult;
import com.alibaba.sdk.android.oss.model.AppendObjectRequest;
import com.alibaba.sdk.android.oss.model.AppendObjectResult;
import com.alibaba.sdk.android.oss.model.CompleteMultipartUploadRequest;
import com.alibaba.sdk.android.oss.model.CompleteMultipartUploadResult;
import com.alibaba.sdk.android.oss.model.CopyObjectRequest;
import com.alibaba.sdk.android.oss.model.CopyObjectResult;
import com.alibaba.sdk.android.oss.model.DeleteObjectRequest;
import com.alibaba.sdk.android.oss.model.DeleteObjectResult;
import com.alibaba.sdk.android.oss.model.GetObjectRequest;
import com.alibaba.sdk.android.oss.model.GetObjectResult;
import com.alibaba.sdk.android.oss.model.HeadObjectRequest;
import com.alibaba.sdk.android.oss.model.HeadObjectResult;
import com.alibaba.sdk.android.oss.model.InitiateMultipartUploadRequest;
import com.alibaba.sdk.android.oss.model.InitiateMultipartUploadResult;
import com.alibaba.sdk.android.oss.model.ListObjectsRequest;
import com.alibaba.sdk.android.oss.model.ListObjectsResult;
import com.alibaba.sdk.android.oss.model.ListPartsRequest;
import com.alibaba.sdk.android.oss.model.ListPartsResult;
import com.alibaba.sdk.android.oss.model.PutObjectRequest;
import com.alibaba.sdk.android.oss.model.PutObjectResult;
import com.alibaba.sdk.android.oss.model.UploadPartRequest;
import com.alibaba.sdk.android.oss.model.UploadPartResult;
import com.alibaba.sdk.android.oss.network.ExecutionContext;
import com.alibaba.sdk.android.oss.network.OSSRequestTask;
import com.squareup.okhttp.Dispatcher;
import com.squareup.okhttp.OkHttpClient;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Created by zhouzhuo on 11/22/15.
 */
public class InternalRequestOperation {

    private volatile URI endpoint;
    private OkHttpClient innerClient;
    private Context applicationContext;
    private OSSCredentialProvider credentialProvider;
    private int maxRetryCount = 2;

    private ExecutorService executorService = Executors.newFixedThreadPool(5);

    public InternalRequestOperation(Context context, URI endpoint, OSSCredentialProvider credentialProvider, ClientConfiguration conf) {
        this.applicationContext = context;
        this.endpoint = endpoint;
        this.credentialProvider = credentialProvider;

        this.innerClient = new OkHttpClient();

        innerClient.setFollowRedirects(false);
        innerClient.setRetryOnConnectionFailure(false);
        innerClient.setCache(null);
        innerClient.setFollowSslRedirects(false);
        innerClient.setRetryOnConnectionFailure(false);

        if (conf != null) {

            innerClient.setConnectTimeout(conf.getConnectionTimeout(), TimeUnit.MILLISECONDS);
            innerClient.setReadTimeout(conf.getSocketTimeout(), TimeUnit.MILLISECONDS);
            innerClient.setWriteTimeout(conf.getSocketTimeout(), TimeUnit.MILLISECONDS);

            Dispatcher dispatcher = new Dispatcher();
            dispatcher.setMaxRequests(conf.getMaxConcurrentRequest());

            innerClient.setDispatcher(dispatcher);

            this.maxRetryCount = conf.getMaxErrorRetry();
        }
    }

    public OSSAsyncTask<PutObjectResult> putObject(
            PutObjectRequest request, OSSCompletedCallback<PutObjectRequest, PutObjectResult> completedCallback) {

        RequestMessage requestMessage = new RequestMessage();
        requestMessage.setIsAuthorizationRequired(request.isAuthorizationRequired());
        requestMessage.setEndpoint(endpoint);
        requestMessage.setMethod(HttpMethod.PUT);
        requestMessage.setBucketName(request.getBucketName());
        requestMessage.setObjectKey(request.getObjectKey());
        if (request.getUploadData() != null) {
            requestMessage.setUploadData(request.getUploadData());
        }
        if (request.getUploadFilePath() != null) {
            requestMessage.setUploadFilePath(request.getUploadFilePath());
        }
        if (request.getCallbackParam() != null) {
            requestMessage.getHeaders().put("x-oss-callback", OSSUtils.populateMapToBase64JsonString(request.getCallbackParam()));
        }
        if (request.getCallbackVars() != null) {
            requestMessage.getHeaders().put("x-oss-callback-var", OSSUtils.populateMapToBase64JsonString(request.getCallbackVars()));
        }

        OSSUtils.populateRequestMetadata(requestMessage.getHeaders(), request.getMetadata());

        canonicalizeRequestMessage(requestMessage);

        ExecutionContext<PutObjectRequest> executionContext = new ExecutionContext<PutObjectRequest>(getInnerClient(), request);
        if (completedCallback != null) {
            executionContext.setCompletedCallback(completedCallback);
        }
        executionContext.setProgressCallback(request.getProgressCallback());
        ResponseParser<PutObjectResult> parser = new ResponseParsers.PutObjectReponseParser();

        Callable<PutObjectResult> callable = new OSSRequestTask<PutObjectResult>(requestMessage, parser, executionContext, maxRetryCount);

        return OSSAsyncTask.wrapRequestTask(executorService.submit(callable), executionContext);
    }

    public OSSAsyncTask<AppendObjectResult> appendObject(
            AppendObjectRequest request, OSSCompletedCallback<AppendObjectRequest, AppendObjectResult> completedCallback) {

        RequestMessage requestMessage = new RequestMessage();
        requestMessage.setIsAuthorizationRequired(request.isAuthorizationRequired());
        requestMessage.setEndpoint(endpoint);
        requestMessage.setMethod(HttpMethod.POST);
        requestMessage.setBucketName(request.getBucketName());
        requestMessage.setObjectKey(request.getObjectKey());
        if (request.getUploadData() != null) {
            requestMessage.setUploadData(request.getUploadData());
        }
        if (request.getUploadFilePath() != null) {
            requestMessage.setUploadFilePath(request.getUploadFilePath());
        }
        requestMessage.getParameters().put("append", "");
        requestMessage.getParameters().put("position", String.valueOf(request.getPosition()));

        OSSUtils.populateRequestMetadata(requestMessage.getHeaders(), request.getMetadata());

        canonicalizeRequestMessage(requestMessage);

        ExecutionContext<AppendObjectRequest> executionContext = new ExecutionContext<AppendObjectRequest>(getInnerClient(), request);
        if (completedCallback != null) {
            executionContext.setCompletedCallback(completedCallback);
        }
        executionContext.setProgressCallback(request.getProgressCallback());
        ResponseParser<AppendObjectResult> parser = new ResponseParsers.AppendObjectResponseParser();

        Callable<AppendObjectResult> callable = new OSSRequestTask<AppendObjectResult>(requestMessage, parser, executionContext, maxRetryCount);

        return OSSAsyncTask.wrapRequestTask(executorService.submit(callable), executionContext);
    }

    public OSSAsyncTask<HeadObjectResult> headObject(
            HeadObjectRequest request, OSSCompletedCallback<HeadObjectRequest, HeadObjectResult> completedCallback) {

        RequestMessage requestMessage = new RequestMessage();
        requestMessage.setIsAuthorizationRequired(request.isAuthorizationRequired());
        requestMessage.setEndpoint(endpoint);
        requestMessage.setMethod(HttpMethod.HEAD);
        requestMessage.setBucketName(request.getBucketName());
        requestMessage.setObjectKey(request.getObjectKey());

        canonicalizeRequestMessage(requestMessage);

        ExecutionContext<HeadObjectRequest> executionContext = new ExecutionContext<HeadObjectRequest>(getInnerClient(), request);
        if (completedCallback != null) {
            executionContext.setCompletedCallback(completedCallback);
        }
        ResponseParser<HeadObjectResult> parser = new ResponseParsers.HeadObjectResponseParser();

        Callable<HeadObjectResult> callable = new OSSRequestTask<HeadObjectResult>(requestMessage, parser, executionContext, maxRetryCount);

        return OSSAsyncTask.wrapRequestTask(executorService.submit(callable), executionContext);
    }

    public OSSAsyncTask<GetObjectResult> getObject(
            GetObjectRequest request, OSSCompletedCallback<GetObjectRequest, GetObjectResult> completedCallback) {

        RequestMessage requestMessage = new RequestMessage();
        requestMessage.setIsAuthorizationRequired(request.isAuthorizationRequired());
        requestMessage.setEndpoint(endpoint);
        requestMessage.setMethod(HttpMethod.GET);
        requestMessage.setBucketName(request.getBucketName());
        requestMessage.setObjectKey(request.getObjectKey());

        if (request.getRange() != null) {
            requestMessage.getHeaders().put(OSSHeaders.RANGE, request.getRange().toString());
        }

        canonicalizeRequestMessage(requestMessage);

        ExecutionContext<GetObjectRequest> executionContext = new ExecutionContext<GetObjectRequest>(getInnerClient(), request);
        if (completedCallback != null) {
            executionContext.setCompletedCallback(completedCallback);
        }
        ResponseParser<GetObjectResult> parser = new ResponseParsers.GetObjectResponseParser();

        Callable<GetObjectResult> callable = new OSSRequestTask<GetObjectResult>(requestMessage, parser, executionContext, maxRetryCount);

        return OSSAsyncTask.wrapRequestTask(executorService.submit(callable), executionContext);
    }

    public OSSAsyncTask<CopyObjectResult> copyObject(
            CopyObjectRequest request, OSSCompletedCallback<CopyObjectRequest, CopyObjectResult> completedCallback) {

        RequestMessage requestMessage = new RequestMessage();
        requestMessage.setIsAuthorizationRequired(request.isAuthorizationRequired());
        requestMessage.setEndpoint(endpoint);
        requestMessage.setMethod(HttpMethod.PUT);
        requestMessage.setBucketName(request.getDestinationBucketName());
        requestMessage.setObjectKey(request.getDestinationKey());

        OSSUtils.populateCopyObjectHeaders(request, requestMessage.getHeaders());

        canonicalizeRequestMessage(requestMessage);

        ExecutionContext<CopyObjectRequest> executionContext = new ExecutionContext<CopyObjectRequest>(getInnerClient(), request);
        if (completedCallback != null) {
            executionContext.setCompletedCallback(completedCallback);
        }
        ResponseParser<CopyObjectResult> parser = new ResponseParsers.CopyObjectResponseParser();

        Callable<CopyObjectResult> callable = new OSSRequestTask<CopyObjectResult>(requestMessage, parser, executionContext, maxRetryCount);

        return OSSAsyncTask.wrapRequestTask(executorService.submit(callable), executionContext);
    }

    public OSSAsyncTask<DeleteObjectResult> deleteObject(
            DeleteObjectRequest request, OSSCompletedCallback<DeleteObjectRequest, DeleteObjectResult> completedCallback) {

        RequestMessage requestMessage = new RequestMessage();
        requestMessage.setIsAuthorizationRequired(request.isAuthorizationRequired());
        requestMessage.setEndpoint(endpoint);
        requestMessage.setMethod(HttpMethod.DELETE);
        requestMessage.setBucketName(request.getBucketName());
        requestMessage.setObjectKey(request.getObjectKey());

        canonicalizeRequestMessage(requestMessage);

        ExecutionContext<DeleteObjectRequest> executionContext = new ExecutionContext<DeleteObjectRequest>(getInnerClient(), request);
        if (completedCallback != null) {
            executionContext.setCompletedCallback(completedCallback);
        }
        ResponseParser<DeleteObjectResult> parser = new ResponseParsers.DeleteObjectResponseParser();

        Callable<DeleteObjectResult> callable = new OSSRequestTask<DeleteObjectResult>(requestMessage, parser, executionContext, maxRetryCount);

        return OSSAsyncTask.wrapRequestTask(executorService.submit(callable), executionContext);
    }

    public OSSAsyncTask<ListObjectsResult> listObjects(
            ListObjectsRequest request, OSSCompletedCallback<ListObjectsRequest, ListObjectsResult> completedCallback) {

        RequestMessage requestMessage = new RequestMessage();
        requestMessage.setIsAuthorizationRequired(request.isAuthorizationRequired());
        requestMessage.setEndpoint(endpoint);
        requestMessage.setMethod(HttpMethod.GET);
        requestMessage.setBucketName(request.getBucketName());

        canonicalizeRequestMessage(requestMessage);

        OSSUtils.populateListObjectsRequestParameters(request, requestMessage.getParameters());

        ExecutionContext<ListObjectsRequest> executionContext = new ExecutionContext<ListObjectsRequest>(getInnerClient(), request);
        if (completedCallback != null) {
            executionContext.setCompletedCallback(completedCallback);
        }
        ResponseParser<ListObjectsResult> parser = new ResponseParsers.ListObjectsResponseParser();

        Callable<ListObjectsResult> callable = new OSSRequestTask<ListObjectsResult>(requestMessage, parser, executionContext, maxRetryCount);

        return OSSAsyncTask.wrapRequestTask(executorService.submit(callable), executionContext);
    }

    public OSSAsyncTask<InitiateMultipartUploadResult> initMultipartUpload(
            InitiateMultipartUploadRequest request, OSSCompletedCallback<InitiateMultipartUploadRequest, InitiateMultipartUploadResult> completedCallback) {

        RequestMessage requestMessage = new RequestMessage();
        requestMessage.setIsAuthorizationRequired(request.isAuthorizationRequired());
        requestMessage.setEndpoint(endpoint);
        requestMessage.setMethod(HttpMethod.POST);
        requestMessage.setBucketName(request.getBucketName());
        requestMessage.setObjectKey(request.getObjectKey());
        requestMessage.getParameters().put("uploads", "");

        OSSUtils.populateRequestMetadata(requestMessage.getHeaders(), request.getMetadata());

        canonicalizeRequestMessage(requestMessage);

        ExecutionContext<InitiateMultipartUploadRequest> executionContext = new ExecutionContext<InitiateMultipartUploadRequest>(getInnerClient(), request);
        if (completedCallback != null) {
            executionContext.setCompletedCallback(completedCallback);
        }
        ResponseParser<InitiateMultipartUploadResult> parser = new ResponseParsers.InitMultipartResponseParser();

        Callable<InitiateMultipartUploadResult> callable = new OSSRequestTask<InitiateMultipartUploadResult>(requestMessage, parser, executionContext, maxRetryCount);

        return OSSAsyncTask.wrapRequestTask(executorService.submit(callable), executionContext);
    }

    public OSSAsyncTask<UploadPartResult> uploadPart(
            UploadPartRequest request, OSSCompletedCallback<UploadPartRequest, UploadPartResult> completedCallback) {

        RequestMessage requestMessage = new RequestMessage();
        requestMessage.setIsAuthorizationRequired(request.isAuthorizationRequired());
        requestMessage.setEndpoint(endpoint);
        requestMessage.setMethod(HttpMethod.PUT);
        requestMessage.setBucketName(request.getBucketName());
        requestMessage.setObjectKey(request.getObjectKey());

        requestMessage.getParameters().put("uploadId", request.getUploadId());
        requestMessage.getParameters().put("partNumber", String.valueOf(request.getPartNumber()));
        requestMessage.setUploadData(request.getPartContent());

        canonicalizeRequestMessage(requestMessage);

        ExecutionContext<UploadPartRequest> executionContext = new ExecutionContext<UploadPartRequest>(getInnerClient(), request);
        if (completedCallback != null) {
            executionContext.setCompletedCallback(completedCallback);
        }
        executionContext.setProgressCallback(request.getProgressCallback());
        ResponseParser<UploadPartResult> parser = new ResponseParsers.UploadPartResponseParser();

        Callable<UploadPartResult> callable = new OSSRequestTask<UploadPartResult>(requestMessage, parser, executionContext, maxRetryCount);

        return OSSAsyncTask.wrapRequestTask(executorService.submit(callable), executionContext);
    }

    public OSSAsyncTask<CompleteMultipartUploadResult> completeMultipartUpload(
            CompleteMultipartUploadRequest request, OSSCompletedCallback<CompleteMultipartUploadRequest, CompleteMultipartUploadResult> completedCallback) {

        RequestMessage requestMessage = new RequestMessage();
        requestMessage.setIsAuthorizationRequired(request.isAuthorizationRequired());
        requestMessage.setEndpoint(endpoint);
        requestMessage.setMethod(HttpMethod.POST);
        requestMessage.setBucketName(request.getBucketName());
        requestMessage.setObjectKey(request.getObjectKey());

        requestMessage.getParameters().put("uploadId", request.getUploadId());
        requestMessage.setUploadData(OSSUtils.buildXMLFromPartEtagList(request.getPartETags()).getBytes());

        canonicalizeRequestMessage(requestMessage);

        ExecutionContext<CompleteMultipartUploadRequest> executionContext = new ExecutionContext<CompleteMultipartUploadRequest>(getInnerClient(), request);
        if (completedCallback != null) {
            executionContext.setCompletedCallback(completedCallback);
        }
        ResponseParser<CompleteMultipartUploadResult> parser = new ResponseParsers.CompleteMultipartUploadResponseParser();

        Callable<CompleteMultipartUploadResult> callable = new OSSRequestTask<CompleteMultipartUploadResult>(requestMessage, parser, executionContext, maxRetryCount);

        return OSSAsyncTask.wrapRequestTask(executorService.submit(callable), executionContext);
    }

    public OSSAsyncTask<AbortMultipartUploadResult> abortMultipartUpload(
            AbortMultipartUploadRequest request, OSSCompletedCallback<AbortMultipartUploadRequest, AbortMultipartUploadResult> completedCallback) {

        RequestMessage requestMessage = new RequestMessage();
        requestMessage.setIsAuthorizationRequired(request.isAuthorizationRequired());
        requestMessage.setEndpoint(endpoint);
        requestMessage.setMethod(HttpMethod.DELETE);
        requestMessage.setBucketName(request.getBucketName());
        requestMessage.setObjectKey(request.getObjectKey());

        requestMessage.getParameters().put("uploadId", request.getUploadId());

        canonicalizeRequestMessage(requestMessage);

        ExecutionContext<AbortMultipartUploadRequest> executionContext = new ExecutionContext<AbortMultipartUploadRequest>(getInnerClient(), request);
        if (completedCallback != null) {
            executionContext.setCompletedCallback(completedCallback);
        }
        ResponseParser<AbortMultipartUploadResult> parser = new ResponseParsers.AbortMultipartUploadResponseParser();

        Callable<AbortMultipartUploadResult> callable = new OSSRequestTask<AbortMultipartUploadResult>(requestMessage, parser, executionContext, maxRetryCount);

        return OSSAsyncTask.wrapRequestTask(executorService.submit(callable), executionContext);
    }

    public OSSAsyncTask<ListPartsResult> listParts(
            ListPartsRequest request, OSSCompletedCallback<ListPartsRequest, ListPartsResult> completedCallback) {

        RequestMessage requestMessage = new RequestMessage();
        requestMessage.setIsAuthorizationRequired(request.isAuthorizationRequired());
        requestMessage.setEndpoint(endpoint);
        requestMessage.setMethod(HttpMethod.GET);
        requestMessage.setBucketName(request.getBucketName());
        requestMessage.setObjectKey(request.getObjectKey());

        requestMessage.getParameters().put("uploadId", request.getUploadId());

        canonicalizeRequestMessage(requestMessage);

        ExecutionContext<ListPartsRequest> executionContext = new ExecutionContext<ListPartsRequest>(getInnerClient(), request);
        if (completedCallback != null) {
            executionContext.setCompletedCallback(completedCallback);
        }
        ResponseParser<ListPartsResult> parser = new ResponseParsers.ListPartsResponseParser();

        Callable<ListPartsResult> callable = new OSSRequestTask<ListPartsResult>(requestMessage, parser, executionContext, maxRetryCount);

        return OSSAsyncTask.wrapRequestTask(executorService.submit(callable), executionContext);
    }

    private boolean checkIfHttpdnsAwailable() {
        if (applicationContext == null) {
            return false;
        }

        boolean IS_ICS_OR_LATER = Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH;

        String proxyHost;

        if (IS_ICS_OR_LATER) {
            proxyHost = System.getProperty("http.proxyHost");
        } else {
            proxyHost = android.net.Proxy.getHost(applicationContext);
        }
        return proxyHost == null;
    }

    public OkHttpClient getInnerClient() {
        return innerClient.clone();
    }

    private void canonicalizeRequestMessage(RequestMessage message) {
        Map<String, String> header = message.getHeaders();

        if (header.get(OSSHeaders.DATE) == null) {
            header.put(OSSHeaders.DATE, DateUtil.currentFixedSkewedTimeInRFC822Format());
        }

        if (message.getMethod() == HttpMethod.POST || message.getMethod() == HttpMethod.PUT) {
            if (header.get(OSSHeaders.CONTENT_TYPE) == null) {
                String determineContentType = OSSUtils.determineContentType(null,
                        message.getUploadFilePath(), message.getObjectKey());
                header.put(OSSHeaders.CONTENT_TYPE, determineContentType);
            }
        }

        message.setIsHttpdnsEnable(checkIfHttpdnsAwailable());
        message.setCredentialProvider(credentialProvider);

        message.getHeaders().put(HttpHeaders.USER_AGENT, VersionInfoUtils.getUserAgent());
    }
}
