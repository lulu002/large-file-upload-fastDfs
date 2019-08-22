package uploderTest.entity;

import org.apache.http.client.CookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import com.haoyun.entity.InitializationConfiguration;
import com.haoyun.entity.LargeFileUploadResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uploderTest.uploadTest.FileUploaderTest;

import java.util.Map;
import java.util.concurrent.ExecutorService;


public class ConfigEntity extends Thread{

    private static final Logger logger = LoggerFactory.getLogger(ConfigEntity.class);
    private ExecutorService service;
    private CookieStore cookieStore;
    public ConfigEntity( ExecutorService service,CookieStore cookieStore){
        this.service = service;
        this.cookieStore = cookieStore;
    }

    @Override
    public void run() {
        InitializationConfiguration configuration =  FileUploaderTest.getConfig(cookieStore);
        Map<String,LargeFileUploadResult> largeFileUploadResultMap = configuration.getLargeFileUploadResultMap();
        Integer i = 0;
        for (LargeFileUploadResult largeFileUploadResult : largeFileUploadResultMap.values()) {
            if(largeFileUploadResult.getStatus().get()){
                i ++;
            }
        }
        //重复提交自己执行
        logger.info("\n\n上传完成个数："+i+"\n\n");
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        this.service.submit(this);
    }
}
