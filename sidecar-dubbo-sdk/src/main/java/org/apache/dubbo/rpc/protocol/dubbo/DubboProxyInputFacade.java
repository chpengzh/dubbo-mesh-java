package org.apache.dubbo.rpc.protocol.dubbo;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * @author chpengzh@foxmail.com
 * @date 7/6/21 09:41
 */
public interface DubboProxyInputFacade {

    CompletableFuture<byte[]> invoke(
            byte[] payload,
            Map<String, String> headers,
            Map<String, String> attachments
    );

}
