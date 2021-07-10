package org.apache.dubbo.rpc.protocol.dubbo;

import com.alibaba.com.caucho.hessian.io.Hessian2Output;
import org.apache.dubbo.common.io.Bytes;
import org.apache.dubbo.common.io.UnsafeByteArrayInputStream;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.serialize.Cleanable;
import org.apache.dubbo.common.serialize.ObjectInput;
import org.apache.dubbo.common.serialize.ObjectOutput;
import org.apache.dubbo.common.serialize.hessian2.Hessian2ObjectOutput;
import org.apache.dubbo.common.utils.Assert;
import org.apache.dubbo.common.utils.IOUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.exchange.Request;
import org.apache.dubbo.remoting.exchange.Response;
import org.apache.dubbo.remoting.transport.CodecSupport;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcInvocation;
import org.springframework.util.ReflectionUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.apache.dubbo.common.constants.CommonConstants.GROUP_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.METHOD_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.PATH_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.VERSION_KEY;
import static org.apache.dubbo.remoting.Constants.DUBBO_VERSION_KEY;
import static org.apache.dubbo.rpc.protocol.dubbo.Constants.DECODE_IN_IO_THREAD_KEY;
import static org.apache.dubbo.rpc.protocol.dubbo.Constants.DEFAULT_DECODE_IN_IO_THREAD;
import static org.apache.dubbo.rpc.protocol.dubbo.DubboProxyConstants.METHOD_PARAM_TYPES;

/**
 * DubboProxy服务端Codec
 */
public class DubboProxyServerCodec extends AbstractDubboProxyCodec {

    private static final Class[] PROXY_REQ_PARAM_TYPES = new Class[]{Map.class, Map.class, byte[].class};

    private static final Logger LOGGER = LoggerFactory.getLogger(DubboProxyServerCodec.class);

    private final DubboCodec dubboCodec = new DubboCodec();

    @Override
    protected RpcInvocation decodeRequestBody(
            Channel channel,
            InputStream is,
            byte[] header,
            Request req
    ) throws IOException {
        Assert.notNull(channel, "channel == null");
        Assert.notNull(req, "request == null");
        Assert.notNull(is, "inputStream == null");

        final byte flag = header[2];
        final byte proto = (byte) (flag & SERIALIZATION_MASK);
        class PayloadRpcInvocation extends DecodeableRpcInvocation {

            private volatile boolean hasDecoded;

            private PayloadRpcInvocation(
                    Channel channel,
                    Request request,
                    InputStream is,
                    byte id
            ) {
                super(channel, request, is, id);
            }

            @Override
            public void decode() {
                if (!hasDecoded) {
                    try {
                        decode(channel, is);
                    } catch (Throwable e) {
                        if (LOGGER.isWarnEnabled()) {
                            LOGGER.warn("Decode rpc invocation failed: " + e.getMessage(), e);
                        }
                        req.setBroken(true);
                        req.setData(e);
                    } finally {
                        hasDecoded = true;
                    }
                }
            }

            @Override
            public Object decode(Channel channel, InputStream input) throws IOException {
                ObjectInput in = CodecSupport.getSerialization(channel.getUrl(), proto)
                        .deserialize(channel.getUrl(), input);
                String dubboVersion = in.readUTF();
                req.setVersion(dubboVersion);
                try {
                    Map<String, Object> headers = new HashMap<>();
                    headers.put(DUBBO_VERSION_KEY, dubboVersion);
                    headers.put(PATH_KEY, in.readUTF());
                    headers.put(VERSION_KEY, in.readUTF());
                    headers.put(GROUP_KEY, in.readUTF());
                    headers.put(METHOD_KEY, in.readUTF());
                    headers.put(METHOD_PARAM_TYPES, in.readUTF());

                    //noinspection unchecked
                    Map<String, String> map = (Map<String, String>) in.readObject(Map.class);
                    Map<String, String> attachments = Optional
                            .ofNullable(getAttachments())
                            .orElse(new HashMap<>());
                    if (map != null && map.size() > 0) {
                        attachments.putAll(map);
                    }

                    setAttachment(DUBBO_VERSION_KEY, dubboVersion);
                    setAttachment(PATH_KEY, DubboProxyInputFacade.class.getName());
                    setAttachment(VERSION_KEY, "1.0.0");
                    setMethodName("invoke");
                    setParameterTypes(PROXY_REQ_PARAM_TYPES);

                    // 解析request payload
                    setArguments(new Object[]{in.readBytes(), headers, attachments});
                } catch (ClassNotFoundException e) {
                    throw new IOException(StringUtils.toString("Read invocation data failed.", e));
                } finally {
                    if (in instanceof Cleanable) {
                        ((Cleanable) in).cleanup();
                    }
                }
                return this;
            }
        }

        PayloadRpcInvocation inv;
        if (channel.getUrl().getParameter(DECODE_IN_IO_THREAD_KEY, DEFAULT_DECODE_IN_IO_THREAD)) {
            inv = new PayloadRpcInvocation(channel, req, is, proto);
            inv.decode();
        } else {
            inv = new PayloadRpcInvocation(channel, req,
                    new UnsafeByteArrayInputStream(readMessageData(is)), proto);
        }
        return inv;
    }

    @Override
    protected void encodeRequestBody(
            Channel channel,
            ObjectOutput out,
            RpcInvocation inv,
            String version
    ) throws IOException {
        byte[] requestPayload = (byte[]) ((Object[]) inv.getArguments()[2])[0];
        if (out instanceof Hessian2ObjectOutput) {
            write((Hessian2ObjectOutput) out, requestPayload);
        } else {
            throw new IOException("DubboProxyClient#encodeRequestData dose not support serialization type="
                    + out.getClass().getName());
        }
    }

    @Override
    protected Result decodeResponseBody(
            Channel channel,
            InputStream is,
            byte[] header,
            Response res
    ) throws Exception {
        byte flag = header[2], proto = (byte) (flag & SERIALIZATION_MASK);
        long id = Bytes.bytes2long(header, 4);

        // 流拷贝一份，使用所有的 byte[] 作为 value 的 payload
        class ForkedInputStream extends InputStream {

            private final InputStream innerDelegate;

            private final byte[] allBytes;

            private ForkedInputStream(InputStream delegate) throws IOException {
                try (ByteArrayOutputStream out = new ByteArrayOutputStream(delegate.available())) {
                    IOUtils.write(delegate, out);
                    out.flush();
                    allBytes = out.toByteArray();
                    innerDelegate = new ByteArrayInputStream(allBytes);
                }
            }

            private byte[] getAllBytes() {
                return allBytes;
            }

            @Override
            public int read() throws IOException {
                return innerDelegate.read();
            }
        }
        ForkedInputStream forkInput = new ForkedInputStream(is);

        // value 为 payload 的返回值对象
        class PayloadRpcResult extends DecodeableRpcResult {
            private PayloadRpcResult(
                    Channel channel,
                    Response response,
                    InputStream is,
                    Invocation invocation,
                    byte id
            ) {
                super(channel, response, is, invocation, id);
            }

            @Override
            public Object decode(Channel channel, InputStream input) {
                setValue(forkInput.getAllBytes());
                return this;
            }
        }

        DecodeableRpcResult result;
        if (channel.getUrl().getParameter(DECODE_IN_IO_THREAD_KEY, DEFAULT_DECODE_IN_IO_THREAD)) {
            result = new PayloadRpcResult(channel, res, forkInput, (Invocation) getRequestData(id), proto);
            result.decode();
        } else {
            result = new PayloadRpcResult(channel, res,
                    new UnsafeByteArrayInputStream(readMessageData(forkInput)), (Invocation) getRequestData(id), proto);
        }
        return result;
    }

    @Override
    protected void encodeResponseBody(
            Channel channel,
            ObjectOutput out,
            Result result,
            String version
    ) throws IOException {
        if (result.hasException()) {
            dubboCodec.encodeResponseData(channel, out, result, version);
        } else if (out instanceof Hessian2ObjectOutput) {
            byte[] responsePayload = (byte[]) result.getValue();
            write((Hessian2ObjectOutput) out, responsePayload);
        } else {
            throw new IOException("DubboProxyClient#encodeResponseData dose not support serialization type="
                    + out.getClass().getName());
        }
        out.flushBuffer();
    }

    private void write(Hessian2ObjectOutput out, byte[] data) throws IOException {
        try {
            Field mH2o = Hessian2ObjectOutput.class.getDeclaredField("mH2o");
            ReflectionUtils.makeAccessible(mH2o);
            Hessian2Output hessian2Output = (Hessian2Output) ReflectionUtils.getField(mH2o, out);

            Field os = Hessian2Output.class.getDeclaredField("_os");
            ReflectionUtils.makeAccessible(os);
            OutputStream internalOutput = (OutputStream) ReflectionUtils.getField(os, hessian2Output);
            internalOutput.write(data);
            internalOutput.flush();
        } catch (NoSuchFieldException e) {
            throw new IOException(e);
        }
    }
}
