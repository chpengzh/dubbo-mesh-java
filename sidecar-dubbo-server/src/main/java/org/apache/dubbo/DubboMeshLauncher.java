package org.apache.dubbo;

import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ConsumerConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.protocol.dubbo.DubboProxyInputFacade;
import org.apache.dubbo.rpc.service.GenericService;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * @author chen.pengzhi (chpengzh@foxmail.com)
 */
public class DubboMeshLauncher {

    public static void main(String[] args) throws IOException {
        ApplicationConfig application = new ApplicationConfig();
        application.setName("sidecar-dubbo-srever");

        //
        // Dubbo Protocol 定义：
        // 1.使用协议名称 dubbo-proxy-server
        // 2.因为是纯异步实现，所以只需要核心数相等的线程即可，减少cpu调度消耗
        // 3.相应的要调整等待队列大小，防止因为并发执行耗时误差调至的拒绝策略
        //
        ProtocolConfig protocol = new ProtocolConfig();
        protocol.setPort(8999);
        protocol.setName("dubbo-proxy-server");
        protocol.setDispatcher("direct");

        //
        // Dubbo Registry
        // 1.服务注册发现可以继续使用zk或者dubbo-disf
        // 2.亦或是灰度期间使用双注册模式
        //
        RegistryConfig registry = new RegistryConfig();
        registry.setAddress("zookeeper://127.0.0.1:2181");
        registry.setRegister(false);

        //
        // Dubbo Consumer 定义
        // 1.使用泛化调用声明是为了保障proxy在没有接口依赖的情况下不进行运行时校验
        // 2.使用异步配置
        // 2.使用全局的注册发现中心
        //
        ConsumerConfig consumer = new ConsumerConfig();
        consumer.setAsync(true);
        consumer.setRegistry(registry);
        consumer.setApplication(application);

        //
        // Dubbo Reference 定义
        // 1.以下信息个性数据来自于业务应用初始化上报
        // 2.使用的编解码协议是 dubbo-proxy-server
        //
        ReferenceConfig<GenericService> reference = new ReferenceConfig<>();
        reference.setInterface("com.github.example.TestApi");
        reference.setVersion("1.0.0");
        reference.setGroup("some_group");
        reference.setTimeout(1000000);
        reference.setProtocol("dubbo-proxy-server");
        reference.setConsumer(consumer);
        reference.setGeneric(true);
        consumer.setCheck(false);
        GenericService outputFacade = reference.get();

        //
        // 代理接口服务
        // 1.所有的应用的出口流量均在这个服务中进行处理
        // 2.可以支持到Filter实现异步逻辑扩展
        //
        ServiceConfig<DubboProxyInputFacade> service = new ServiceConfig<>();
        service.setInterface(DubboProxyInputFacade.class);
        service.setRef((payloads, headers, attachment) -> {
            // 这里是在业务线程池里面进行的
            System.out.println(outputFacade.$invoke("", new String[0], new Object[]{payloads}));
            //noinspection unchecked
            return (CompletableFuture) RpcContext.getContext().getFuture();
        });
        service.setVersion("1.0.0");
        service.setGroup("");
        service.setApplication(application);
        service.setRegistry(registry);
        service.setProtocol(protocol);
        service.export();

        System.in.read();
    }

}
