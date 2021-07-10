package com.github.example;

import com.alibaba.fastjson.JSON;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ConsumerConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.rpc.RpcContext;

/**
 * @author chen.pengzhi (chpengzh@foxmail.com)
 */
public class ClientMain {

    public static void main(String[] args) throws InterruptedException {
        ApplicationConfig application = new ApplicationConfig();
        application.setName("sidecar-dubbo");

        ConsumerConfig consumer = new ConsumerConfig();
        consumer.setVersion("1.0.0");
        consumer.setGroup("some_group");
        consumer.setTimeout(1000000);

        RegistryConfig registry = new RegistryConfig();
        registry.setAddress("zookeeper://127.0.0.1:2181");

        ReferenceConfig<TestApi> reference = new ReferenceConfig<>();
        reference.setInterface(TestApi.class);
        reference.setApplication(application);
        reference.setRegistry(registry);
        reference.setConsumer(consumer);
        reference.setUrl("dubbo-proxy-client://127.0.0.1:8999");
        TestApi testApi = reference.get();

//        while (true) {
//            try {
        RpcContext.getContext().setAttachment("abcde", "xxxx");
        System.err.println(JSON.toJSON(testApi.sayHello(new TestModel("chpengzh"), 12)));
//                Thread.sleep(10000L);
//            } catch (Throwable er) {
//                er.printStackTrace();
//            }
//        }

    }
}
