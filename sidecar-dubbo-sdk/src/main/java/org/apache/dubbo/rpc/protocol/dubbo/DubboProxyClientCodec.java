package org.apache.dubbo.rpc.protocol.dubbo;

import org.apache.dubbo.common.io.Bytes;
import org.apache.dubbo.common.io.UnsafeByteArrayInputStream;
import org.apache.dubbo.common.serialize.ObjectOutput;
import org.apache.dubbo.common.serialize.hessian2.Hessian2ObjectOutput;
import org.apache.dubbo.common.utils.ReflectUtils;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.exchange.Request;
import org.apache.dubbo.remoting.exchange.Response;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcInvocation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

import static org.apache.dubbo.common.constants.CommonConstants.GROUP_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.PATH_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.VERSION_KEY;
import static org.apache.dubbo.rpc.protocol.dubbo.Constants.DECODE_IN_IO_THREAD_KEY;
import static org.apache.dubbo.rpc.protocol.dubbo.Constants.DEFAULT_DECODE_IN_IO_THREAD;

/**
 * DubboProxy客户端Codec
 */
public class DubboProxyClientCodec extends AbstractDubboProxyCodec {

    private final DubboCodec dubboCodec = new DubboCodec();

    @Override
    @SuppressWarnings("Duplicates")
    protected void encodeRequestBody(
            Channel channel,
            ObjectOutput out,
            RpcInvocation inv,
            String version
    ) throws IOException {
        out.writeUTF(version);
        out.writeUTF(inv.getAttachment(PATH_KEY));
        out.writeUTF(inv.getAttachment(VERSION_KEY));
        out.writeUTF(inv.getAttachment(GROUP_KEY));
        out.writeUTF(inv.getMethodName());
        out.writeUTF(ReflectUtils.getDesc(inv.getParameterTypes()));
        out.writeObject(new HashMap<>(inv.getAttachments()));
        out.writeBytes(getRequestPayload(channel, out, inv, version));
        out.flushBuffer();
    }

    private byte[] getRequestPayload(
            Channel channel,
            ObjectOutput out,
            RpcInvocation inv,
            String version
    ) throws IOException {
        ObjectOutput delegateOutput;
        try (ByteArrayOutputStream delegate = new ByteArrayOutputStream()) {
            if (out instanceof Hessian2ObjectOutput) {
                delegateOutput = new DubboProxyHessian2ObjectOutput(delegate);
            } else {
                throw new IOException("DubboProxyClient dose not support serialization type=" + out.getClass().getName());
            }
            dubboCodec.encodeRequestData(channel, delegateOutput, inv, version);
            delegateOutput.flushBuffer();
            return delegate.toByteArray();
        }
    }

    @Override
    protected RpcInvocation decodeRequestBody(Channel channel, InputStream is, byte[] header, Request req) {
        throw new UnsupportedOperationException("DubboProxyClientCodec#decodeRequestBody is not supported.");
    }

    @Override
    protected void encodeResponseBody(Channel channel, ObjectOutput out, Result data, String version) {
        throw new UnsupportedOperationException("DubboProxyClientCodec#decodeRequestBody is not supported.");
    }

    @Override
    protected Result decodeResponseBody(Channel channel, InputStream is, byte[] header, Response res) throws Exception {
        byte flag = header[2], proto = (byte) (flag & SERIALIZATION_MASK);
        // get request id.
        long id = Bytes.bytes2long(header, 4);
        DecodeableRpcResult result;
        if (channel.getUrl().getParameter(DECODE_IN_IO_THREAD_KEY, DEFAULT_DECODE_IN_IO_THREAD)) {
            result = new DecodeableRpcResult(channel, res, is, (Invocation) getRequestData(id), proto);
            result.decode();
        } else {
            result = new DecodeableRpcResult(channel, res, new UnsafeByteArrayInputStream(readMessageData(is)),
                    (Invocation) getRequestData(id), proto);
        }
        return result;
    }

}
