package com.github.example;

import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ProviderConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;

import java.util.concurrent.CountDownLatch;

/**
 * @author chen.pengzhi (chpengzh@foxmail.com)
 */
public class ServerMain {

    public static void main(String[] args) throws InterruptedException {
        ApplicationConfig application = new ApplicationConfig();
        application.setName("sidecar-dubbo");

        ProviderConfig provider = new ProviderConfig();
        provider.setVersion("1.0.0");
        provider.setGroup("some_group");

        ProtocolConfig protocol = new ProtocolConfig();
        protocol.setPort(9000);

        RegistryConfig registry = new RegistryConfig();
        registry.setAddress("zookeeper://127.0.0.1:2181");

        ServiceConfig<TestApi> service = new ServiceConfig<>();
        service.setInterface(TestApi.class);
        service.setRef((name, order) -> "hello, " + name.getName());
        service.setApplication(application);
        service.setRegistry(registry);
        service.setProvider(provider);
        service.setProtocol(protocol);
        service.export();

        new CountDownLatch(1).await();
    }
}
