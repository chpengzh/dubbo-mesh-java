package org.apache.dubbo.rpc.protocol.dubbo;

/**
 * @author chpengzh@foxmail.com
 * @date 7/6/21 10:20
 */
public class DubboProxyClientProtocol extends AbstractDubboProxyProtocol {

    public static final String NAME = "dubbo-proxy-client";

    @Override
    protected String getCodecName() {
        return NAME;
    }

}
