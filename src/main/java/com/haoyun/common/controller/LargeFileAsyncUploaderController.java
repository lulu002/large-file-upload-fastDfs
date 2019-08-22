package com.haoyun.common.controller;

import com.haoyun.common.asyncListener.FileUploadAsyncListener;
import com.haoyun.common.exceptpion.UploadFileNotFoundException;
import com.haoyun.common.helper.LargeFileUploaderHelper;
import com.haoyun.common.helper.SchoolThreadLocalContainer;
import com.haoyun.common.uploader.FileUploader;
import com.haoyun.common.uploader.WriteChunkCompletionListener;
import com.haoyun.entity.InitializationConfiguration;
import com.haoyun.entity.LargeFileUploadChunkResult;
import com.haoyun.entity.LargeFileUploadResult;
import com.haoyun.entity.upload.FileUploadConfiguration;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.lang3.time.DateUtils;
import com.haoyun.common.exceptpion.ParameterMissException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.AsyncContext;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Async
@RestController
@RequestMapping("/largeUploader")
public class LargeFileAsyncUploaderController {
    /**
     * 日志
     */
    private static final Logger logger = LoggerFactory.getLogger(LargeFileAsyncUploaderController.class);
    /**
     * 执行的线程
     */
    private static final ExecutorService executorService = Executors.newFixedThreadPool(16);

    /**
     * 上传优化效果
     */
    @Autowired
    private LargeFileUploaderHelper largeFileUploaderHelper;

    @Autowired
    SchoolThreadLocalContainer container;

    /**
     * 异步上传
     *
     * @param httpServletRequest
     * @param httpServletResponse
     */
    @RequestMapping("/asyncFileUploader")
    public void asyncFileUploader(final HttpServletRequest httpServletRequest, final HttpServletResponse httpServletResponse) {
        container.populate(httpServletRequest, httpServletResponse);
        try {
            FileUploadConfiguration fileUploadConfiguration = this.largeFileUploaderHelper.extractFileUploadConfiguration(httpServletRequest);
            UUID fileId = fileUploadConfiguration.getFileId();
            UUID clientId = largeFileUploaderHelper.getClientId(httpServletRequest, httpServletResponse);
            InitializationConfiguration initializationConfiguration = largeFileUploaderHelper.getConfig(true);
            ConcurrentHashMap<String, LargeFileUploadResult> largeFileUploadResultMap = initializationConfiguration.getLargeFileUploadResultMap();
            LargeFileUploadResult largeFileUploadResult = largeFileUploadResultMap.get(fileId.toString());
            LargeFileUploadChunkResult largeFileUploadChunkResult = largeFileUploadResult.getLargeFileUploadChunkResultMap().get(fileId + "@" + fileUploadConfiguration.getPartNumber());
            if (null != largeFileUploadChunkResult) {
                largeFileUploadChunkResult.setError(false);
                largeFileUploadResult.setError(false);
                largeFileUploaderHelper.updateEntity(clientId.toString(), fileId.toString(), largeFileUploadResult);
            }
            /*开启同步策略*/
            final AsyncContext asyncContext = httpServletRequest.startAsync();
            asyncContext.setTimeout(DateUtils.MILLIS_PER_HOUR);
            asyncContext.addListener(new FileUploadAsyncListener(fileUploadConfiguration.getFileId()) {
                @Override
                public void clean(ServletRequest request) {
                    container.populate((HttpServletRequest) request, httpServletResponse);
                    try {
                        InitializationConfiguration initializationConfiguration = largeFileUploaderHelper.getConfig(true);
                        ConcurrentHashMap<String, LargeFileUploadResult> largeFileUploadResultMap = initializationConfiguration.getLargeFileUploadResultMap();
                        LargeFileUploadResult largeFileUploadResult = largeFileUploadResultMap.get(fileId.toString());
                        ConcurrentHashMap<String, LargeFileUploadChunkResult> chunkResultConcurrentHashMap = largeFileUploadResult.getLargeFileUploadChunkResultMap();
                        long sum = 0;
                        for (LargeFileUploadChunkResult largeFileUploadChunkResult : chunkResultConcurrentHashMap.values()) {
                            sum += largeFileUploadChunkResult.getStatus() ? largeFileUploadChunkResult.getLength() : 0;
                        }
                        if (sum == largeFileUploadResult.getOriginalFileSizeInBytes()) {
                            largeFileUploadResult.setFileComplete(true);
                            largeFileUploadResult.setStatus(new AtomicBoolean(true));
                            largeFileUploadResultMap.put(fileId.toString(), largeFileUploadResult);
                            initializationConfiguration.setLargeFileUploadResultMap(largeFileUploadResultMap);
                            largeFileUploaderHelper.updateEntity(clientId.toString(), initializationConfiguration);
                        }
                    } catch (Exception e) {
                        FileUploadConfiguration fileUploadConfiguration = null;
                        try {
                            fileUploadConfiguration = largeFileUploaderHelper.extractFileUploadConfiguration(httpServletRequest);
                            UUID fileId = fileUploadConfiguration.getFileId();
                            UUID clientId = largeFileUploaderHelper.getClientId(httpServletRequest, httpServletResponse);
                            largeFileUploaderHelper.processException(clientId, fileId, e);
                        } catch (ParameterMissException | FileUploadException | IOException | UploadFileNotFoundException e1) {
                            e1.printStackTrace();
                        }
                    }
                }
            });
            FileUploader fileUploader = new FileUploader(clientId.toString(), fileUploadConfiguration, executorService, largeFileUploadResult, new WriteChunkCompletionListener() {
                @Override
                public void start() {
                    container.populate(httpServletRequest, httpServletResponse);
                }

                @Override
                public void error(Exception e) {
                    logger.error(e.getMessage());
                    if (e.getMessage() != null) {
                        largeFileUploaderHelper.processException(clientId, fileId, e);
                    }
                    logger.info(String.format("%s\t\trequest complete by error", Thread.currentThread().getName()));
                    asyncContext.complete();
                }

                @Override
                public void success() {
                    logger.info(String.format("%s\t\trequest complete by success", Thread.currentThread().getName()));
                    asyncContext.complete();
                }
            }, largeFileUploaderHelper);
            executorService.submit(fileUploader);
        } catch (Exception e) {
            e.printStackTrace();
            FileUploadConfiguration fileUploadConfiguration = null;
            try {
                fileUploadConfiguration = this.largeFileUploaderHelper.extractFileUploadConfiguration(httpServletRequest);
                UUID fileId = fileUploadConfiguration.getFileId();
                UUID clientId = largeFileUploaderHelper.getClientId(httpServletRequest, httpServletResponse);
                largeFileUploaderHelper.processException(clientId, fileId, e);
            } catch (ParameterMissException | FileUploadException | IOException | UploadFileNotFoundException e1) {
                e1.printStackTrace();
            }
        }
    }
}
