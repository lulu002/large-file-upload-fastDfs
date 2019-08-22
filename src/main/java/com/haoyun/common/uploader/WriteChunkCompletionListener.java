package com.haoyun.common.uploader;

public interface WriteChunkCompletionListener {

    void start();

    void error(Exception e);

    void success();
}
