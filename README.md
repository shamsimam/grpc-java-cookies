# gRPC HTTP Cookies Client Interceptor

An [HTTP cookie](https://developer.mozilla.org/en-US/docs/Web/HTTP/Cookies) is a small piece of data that a server sends to the client.
The client may store it and send it back with the next request to the same server.
Typically, it is used to remember stateful information between requests for the stateless HTTP protocol.
The gRPC Server instructs a client to store cookies by sending a [Set-Cookie header]((https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Set-Cookie)) in the response.

This project provides the implementation of a [gRPC client interceptor](https://grpc.github.io/grpc-java/javadoc/io/grpc/ClientInterceptor.html)
  to add the cross-cutting behavior of managing cookies on the client.
Cookies are managed
  by inspecting [set-cookie HTTP headers](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Set-Cookie) received in the response from the server and
  by forwarding cookies using the [cookie HTTP header](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Cookie) in the request to the server.

The implementation uses a [CookieManager](https://docs.oracle.com/en/java/javase/13/docs/api/java.base/java/net/CookieManager.html),
 initialized with a [CookieStore](https://docs.oracle.com/en/java/javase/13/docs/api/java.base/java/net/CookieStore.html)
 and a [CookiePolicy](https://docs.oracle.com/en/java/javase/13/docs/api/java.base/java/net/CookiePolicy.html),
 which makes policy decisions on cookie acceptance/rejection based on gRPC methods being invoked.

## Usage

1. Add the grpc-java-cookies artifact [as a dependency](https://clojars.org/io.github.shamsimam/grpc-java-cookies) to your project.
For example, you would add the following to your maven project pom:
```
<dependency>
    <groupId>io.github.shamsimam</groupId>
    <artifactId>grpc-java-cookies</artifactId>
    <version>0.0.1</version>
</dependency>
```
2. Attach the interceptor to your gRPC client channel:
```
import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import io.github.shamsimam.CookieStoreInterceptor;

...

public MyClient(String host, int port) {
    final Channel basicChannel = ManagedChannelBuilder.forAddress(host, port).build();
    this.channel = ClientInterceptors.intercept(basicChannel, new CookieStoreInterceptor());
}
```

### Example logs

#### Server configures cookies on the response

```
(Http2FrameLogger.logHeaders) [id: 0x3ae5a407, ...] OUTBOUND HEADERS: streamId=3 headers=GrpcHttp2OutboundHeaders[:status: 200, set-cookie: sessionId=s123456, ...] streamDependency=0 weight=16 exclusive=false padding=0 endStream=false
```

#### Client receives cookies in the response

```
(Http2FrameLogger.logHeaders) [id: 0xfd77d934, ...] INBOUND HEADERS: streamId=3 headers=GrpcHttp2ResponseHeaders[:status: 200, set-cookie: sessionId=s123456, ...] streamDependency=0 weight=16 exclusive=false padding=0 endStream=false
```

#### Client sends cookies on next request

```
(Http2FrameLogger.logHeaders) [id: 0xfd77d934, ...] OUTBOUND HEADERS: streamId=5 headers=GrpcHttp2OutboundHeaders[cookie: sessionId=s123456, ...] streamDependency=0 weight=16 exclusive=false padding=0 endStream=false
```

#### Server receives cookies in the next request

```
(Http2FrameLogger.logHeaders) [id: 0x5933534e, ...] INBOUND HEADERS: streamId=5 headers=GrpcHttp2RequestHeaders[cookie: sessionId=s123456, ...] streamDependency=0 weight=16 exclusive=false padding=0 endStream=false
```

## Building

The grpc-java-cookies implementation is written as a Maven project.
You can use maven to build the project from the command line.
These are a short list of useful commands:

* `mvn compile`: compiles the code and tells you if there are errors
* `mvn test`: compiles the code, runs the tests and lets you know if some fail

## Maintainers

[@shamsimam](https://github.com/shamsimam).

## Contributing

Feel free to dive in! [Open an issue](https://github.com/shamsimam/grpc-java-cookies/issues/new) or submit PRs.

grpc-java-cookies follows the [Contributor Covenant](http://contributor-covenant.org/version/1/3/0/) Code of Conduct.

## License

[GNU General Public License v3](LICENSE) © Shams Imam
