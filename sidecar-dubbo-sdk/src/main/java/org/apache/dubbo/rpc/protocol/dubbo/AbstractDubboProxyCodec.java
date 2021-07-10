package org.apache.dubbo.rpc.protocol.dubbo;

import org.apache.dubbo.common.Version;
import org.apache.dubbo.common.io.Bytes;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.serialize.ObjectInput;
import org.apache.dubbo.common.serialize.ObjectOutput;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.exchange.Request;
import org.apache.dubbo.remoting.exchange.Response;
import org.apache.dubbo.remoting.exchange.codec.ExchangeCodec;
import org.apache.dubbo.remoting.transport.CodecSupport;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcInvocation;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author chen.pengzhi (chpengzh@foxmail.com)
 */
public abstract class AbstractDubboProxyCodec extends DubboCodec {

    private static final Logger log = LoggerFactory.getLogger(AbstractDubboProxyCodec.class);

    /**
     * Client -> Proxy 请求编码
     *
     * @param channel 业务连接channel
     * @param out     编码输出流
     * @param data    业务数据
     * @param version dubbo版本
     * @throws IOException 编码异常
     */
    protected abstract void encodeRequestBody(
            Channel channel,
            ObjectOutput out,
            RpcInvocation data,
            String version
    ) throws IOException;

    /**
     * Proxy 接受请求解码
     *
     * @param channel 业务连接channel
     * @param is      输入流
     * @param header  请求header
     * @param req     业务请求体
     * @return {@link RpcInvocation} 解码请求体
     * @throws Exception 解码异常
     */
    protected abstract RpcInvocation decodeRequestBody(
            Channel channel,
            InputStream is,
            byte[] header,
            Request req
    ) throws Exception;

    /**
     * Proxy -> Server 请求编码
     *
     * @param channel 业务连接channel
     * @param out     输出流
     * @param data    业务请求体
     * @param version dubbo版本
     * @throws IOException 编码异常
     */
    protected abstract void encodeResponseBody(
            Channel channel,
            ObjectOutput out,
            Result data,
            String version
    ) throws IOException;

    /**
     * 业务响应解码
     *
     * @param channel
     * @param is
     * @param header
     * @param res
     * @return
     * @throws Exception
     */
    protected abstract Result decodeResponseBody(
            Channel channel,
            InputStream is,
            byte[] header,
            Response res
    ) throws Exception;

    @Override
    protected void encodeRequestData(
            Channel channel,
            ObjectOutput out,
            Object data,
            String version
    ) throws IOException {
        RpcInvocation inv = (RpcInvocation) data;
        encodeRequestBody(channel, out, inv, version);
    }

    @Override
    protected void encodeResponseData(
            Channel channel,
            ObjectOutput out,
            Object data,
            String version
    ) throws IOException {
        Result result = (Result) data;
        encodeResponseBody(channel, out, result, version);
    }

    @Override
    protected Object decodeBody(Channel channel, InputStream is, byte[] header) {
        byte flag = header[2], proto = (byte) (flag & SERIALIZATION_MASK);
        // get request id.
        long id = Bytes.bytes2long(header, 4);
        if ((flag & FLAG_REQUEST) == 0) {
            // decode response.
            Response res = new Response(id);
            if ((flag & FLAG_EVENT) != 0) {
                res.setEvent(true);
            }
            // get status.
            byte status = header[3];
            res.setStatus(status);
            try {
                if (status == Response.OK) {
                    Object data;
                    if (res.isHeartbeat()) {
                        ObjectInput in = CodecSupport.deserialize(channel.getUrl(), is, proto);
                        data = decodeHeartbeatData(channel, in);
                    } else if (res.isEvent()) {
                        ObjectInput in = CodecSupport.deserialize(channel.getUrl(), is, proto);
                        data = decodeEventData(channel, in);
                    } else {
                        data = decodeResponseBody(channel, is, header, res);
                    }
                    res.setResult(data);
                } else {
                    ObjectInput in = CodecSupport.deserialize(channel.getUrl(), is, proto);
                    res.setErrorMessage(in.readUTF());
                }
            } catch (Throwable t) {
                if (log.isWarnEnabled()) {
                    log.warn("Decode response failed: " + t.getMessage(), t);
                }
                res.setStatus(Response.CLIENT_ERROR);
                res.setErrorMessage(StringUtils.toString(t));
            }
            return res;
        } else {
            // decode request.
            Request req = new Request(id);
            req.setVersion(Version.getProtocolVersion());
            req.setTwoWay((flag & FLAG_TWOWAY) != 0);
            if ((flag & FLAG_EVENT) != 0) {
                req.setEvent(true);
            }
            try {
                Object data;
                ObjectInput in = CodecSupport.deserialize(channel.getUrl(), is, proto);
                if (req.isHeartbeat()) {
                    data = decodeHeartbeatData(channel, in);
                } else if (req.isEvent()) {
                    data = decodeEventData(channel, in);
                } else {
                    data = decodeRequestBody(channel, is, header, req);
                }
                req.setData(data);
            } catch (Throwable t) {
                if (log.isWarnEnabled()) {
                    log.warn("Decode request failed: " + t.getMessage(), t);
                }
                // bad request
                req.setBroken(true);
                req.setData(t);
            }

            return req;
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    byte[] readMessageData(InputStream is) throws IOException {
        if (is.available() > 0) {
            byte[] result = new byte[is.available()];
            is.read(result);
            return result;
        }
        return new byte[]{};
    }
}
