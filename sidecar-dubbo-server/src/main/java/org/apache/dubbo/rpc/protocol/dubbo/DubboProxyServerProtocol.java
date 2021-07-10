package org.apache.dubbo.rpc.protocol.dubbo;

/**
 * @author chpengzh@foxmail.com
 * @date 7/6/21 10:21
 */
public class DubboProxyServerProtocol extends AbstractDubboProxyProtocol {

    public static final String NAME = "dubbo-proxy-server";

    @Override
    protected String getCodecName() {
        return NAME;
    }

}
