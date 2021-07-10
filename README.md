



```
Dubbo Consumer Applicaiton					transport layer						Dubbo Mesh Proxy Applicaiton								Dubbo Mesh Proxy Applicaiton				Dubbo Provider Applicaiton		
																						 NettyProxyServer							 			NettyProxyClient


dubboProxyClientCdec#encodeRequest   --->   PayloadRequest					--->   dubboProxyServerCodec#decodeRequest
 											header: 											
 												interface										|
 												version											|
 												group											|- PayloadRpcInvocation      --->       dubboProxyServerCodec#encodeRequest     --->              -----
 												method 														|																						|
 												method_parameters_desc										|																						|
 												attachments													|																						|
 											body:															|															 	
 											    requestPayload												|----- ProxyInvokeChain & ProxyFilter											Business Threadpool
 																											|
 																											|																						|
 																											|																						|
 																											|																						|
 																								|- PayloadRpcResult          <---       dubboProxyServerCodec#decodeResponse    <--- 			  -----
 																								|
 											PayloadResponse										|
 											data:
dubboProxyClientCdec#decodeResponse   <---	    responsePayload				<---  dubboProxyServerCodec#encodeResponse

		
|----------------------------------|															|---------------------------------------------------------|
          
         Dubbo Mesh SDK																							Dubbo Mesh Agent Server
```
