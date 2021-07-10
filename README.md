# Dubbo 透明协议代理


## 问题背景

由于dubbo的协议头定义对service-mesh不太友好，所以dubbo一直以来都没有比较好的mesh化落地方案；

由于我在的公司里面java体系主要也是使用dubbo，因此决定考察一种无痛升级方式，在定制sidecar-agent层与客户端的协议的同时，兼容提供者序列化协议；

这样有一个好处是，升级期间提供者不需要进行改造，而服务消费者改动量也只是一个编码协议与地址变更；

整体协议力促恒如下:

```
Dubbo Consumer Applicaiton                    transport layer                        Dubbo Mesh Proxy Applicaiton                                Dubbo Mesh Proxy Applicaiton                Dubbo Provider Applicaiton        
                                                                                         NettyProxyServer                                         NettyProxyClient

dubboProxyClientCdec#encodeRequest   --->   PayloadRequest                    --->   dubboProxyServerCodec#decodeRequest
                                             header:                                             
                                                 interface                                        |
                                                 version                                          |
                                                 group                                            |- PayloadRpcInvocation      --->       dubboProxyServerCodec#encodeRequest     --->              -----
                                                 method                                                      |                                                                                        |
                                                 method_parameters_desc                                      |                                                                                        |
                                                 attachments                                                 |                                                                                        |
                                             body:                                                           |                                                                 
                                                 requestPayload                                              |----- InvocationChain & AsyncFilter                                            Business Threadpool
                                                                                                             |
                                                                                                             |                                                                                        |
                                                                                                             |                                                                                        |
                                                                                                             |                                                                                        |
                                                                                                 |- PayloadRpcResult          <---       dubboProxyServerCodec#decodeResponse    <---               -----
                                                                                                 |
                                             PayloadResponse                                     |
                                             data:
dubboProxyClientCdec#decodeResponse   <---        responsePayload                <---  dubboProxyServerCodec#encodeResponse

        
|----------------------------------|                                                             |---------------------------------------------------------|
          
         Dubbo Mesh SDK                                                                                            Dubbo Mesh Agent Server
```

## 动动你的小手

1. 本地启动zk作为服务注册发现中心，地址 `zookeeper://127.0.0.1:2181`

2. 启动本地提供者样例, 主方法在`example-provider`中,启动类`com.github.example.ServerMain`

```java
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
```

3. 启动 `sidecar-dubbo-server`，主方法在`org.apache.dubbo.DubboMeshLauncher`

4. 启动本地消费者 `example-provider`，主方法在 `com.github.example.ClientMain`，应用能正常返回.

注意与普通消费相比，唯一的区别即是变更了直连url地址

```java
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

        RpcContext.getContext().setAttachment("abcde", "xxxx");
        System.err.println(JSON.toJSON(testApi.sayHello(new TestModel("chpengzh"), 12)));
    }
}
```

5. 值得注意的是，由于我们的sidecar是使用的codec扩展，因此可以按照dubbo的纯异步模型调参，也可以使用filter扩展

```java
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
        // 1.服务注册发现可以继续使用zk或者别的注册发现中心
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
```
