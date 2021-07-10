package org.apache.dubbo.rpc.protocol.dubbo;

import com.alibaba.com.caucho.hessian.io.Hessian2Output;
import org.apache.dubbo.common.serialize.ObjectOutput;

import java.io.IOException;
import java.io.OutputStream;

/**
 * 因为Hessian2ObjectOutput有自己独立的ThreadLocal上下文，所以这里拷贝了一份
 */
final class DubboProxyHessian2ObjectOutput implements ObjectOutput {

    private final Hessian2Output mH2o;

    DubboProxyHessian2ObjectOutput(OutputStream os) {
        mH2o = new Hessian2Output(os);
        mH2o.init(os);
    }

    @Override
    public void writeBool(boolean v) throws IOException {
        mH2o.writeBoolean(v);
    }

    @Override
    public void writeByte(byte v) throws IOException {
        mH2o.writeInt(v);
    }

    @Override
    public void writeShort(short v) throws IOException {
        mH2o.writeInt(v);
    }

    @Override
    public void writeInt(int v) throws IOException {
        mH2o.writeInt(v);
    }

    @Override
    public void writeLong(long v) throws IOException {
        mH2o.writeLong(v);
    }

    @Override
    public void writeFloat(float v) throws IOException {
        mH2o.writeDouble(v);
    }

    @Override
    public void writeDouble(double v) throws IOException {
        mH2o.writeDouble(v);
    }

    @Override
    public void writeBytes(byte[] b) throws IOException {
        mH2o.writeBytes(b);
    }

    @Override
    public void writeBytes(byte[] b, int off, int len) throws IOException {
        mH2o.writeBytes(b, off, len);
    }

    @Override
    public void writeUTF(String v) throws IOException {
        mH2o.writeString(v);
    }

    @Override
    public void writeObject(Object obj) throws IOException {
        mH2o.writeObject(obj);
    }

    @Override
    public void flushBuffer() throws IOException {
        mH2o.flushBuffer();
    }

    public OutputStream getOutputStream() throws IOException {
        return mH2o.getBytesOutputStream();
    }
}