package io.github.shamsimam;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import junit.framework.TestCase;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="https://shamsimam.github.io/">Shams Imam</a> (shams.imam@gmail.com)
 */
public class CookieStoreInterceptorTest extends TestCase {

    private static final Metadata.Key<String> COOKIE_KEY =
        Metadata.Key.of("cookie", Metadata.ASCII_STRING_MARSHALLER);

    private static final Metadata.Key<String> SET_COOKIE_KEY =
        Metadata.Key.of("set-cookie", Metadata.ASCII_STRING_MARSHALLER);

    private static String cookieString(HttpCookie srcCookie) {
        return srcCookie.toString().replaceAll("\"", "");
    }

    private CookieStoreInterceptor interceptor;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        final CookieManager cookieManager = new CookieManager();
        interceptor = new CookieStoreInterceptor(cookieManager);
    }

    @Override
    protected void tearDown() throws Exception {
        interceptor = null;
        super.tearDown();
    }

    public void testSessionCookieReceived() {

        final URI path = URI.create("https://www.test.com/grpc.Service/GetCookies");
        final Metadata requestHeaders = new Metadata();
        final Metadata responseHeaders = new Metadata();
        final HttpCookie srcCookie = new HttpCookie("foo", "bar");
        responseHeaders.put(SET_COOKIE_KEY, srcCookie.toString());
        interceptor.processResponseCookies(path, responseHeaders);

        final Metadata dummyResponseHeaders = new Metadata();
        final Metadata actualRequestHeaders = runInterceptCall(path, requestHeaders, dummyResponseHeaders);

        assertNotNull(actualRequestHeaders);
        assertCookies(actualRequestHeaders, srcCookie);
    }

    public void testMultipleCookiesReceived() {

        final URI path = URI.create("https://www.test.com/grpc.Service/GetCookies");
        final Metadata requestHeaders = new Metadata();
        final Metadata responseHeaders1 = new Metadata();
        final HttpCookie srcCookie1 = new HttpCookie("foo", "bar");
        responseHeaders1.put(SET_COOKIE_KEY, srcCookie1.toString());
        interceptor.processResponseCookies(path, responseHeaders1);
        final Metadata responseHeaders2 = new Metadata();
        final HttpCookie srcCookie2 = new HttpCookie("lorem", "ipsum");
        responseHeaders2.put(SET_COOKIE_KEY, srcCookie2.toString());
        interceptor.processResponseCookies(path, responseHeaders2);

        final Metadata dummyResponseHeaders = new Metadata();
        final Metadata actualRequestHeaders = runInterceptCall(path, requestHeaders, dummyResponseHeaders);

        assertNotNull(actualRequestHeaders);
        assertCookies(actualRequestHeaders, srcCookie1, srcCookie2);
    }

    public void testMultipleCookiesOnSingleHeaderAcceptsFirst() {

        final URI path = URI.create("https://www.test.com/grpc.Service/GetCookies");
        final Metadata requestHeaders = new Metadata();
        final Metadata responseHeaders = new Metadata();
        final HttpCookie srcCookie1 = new HttpCookie("foo", "bar");
        final HttpCookie srcCookie2 = new HttpCookie("lorem", "ipsum");
        responseHeaders.put(SET_COOKIE_KEY, srcCookie1.toString() + "; " + srcCookie2.toString());
        interceptor.processResponseCookies(path, responseHeaders);

        final Metadata dummyResponseHeaders = new Metadata();
        final Metadata actualRequestHeaders = runInterceptCall(path, requestHeaders, dummyResponseHeaders);

        assertNotNull(actualRequestHeaders);
        assertCookies(actualRequestHeaders, srcCookie1);
    }

    public void testPermanentCookieReceived() {

        final URI path = URI.create("https://www.test.com/grpc.Service/GetCookies");
        final Metadata requestHeaders = new Metadata();
        final Metadata responseHeaders = new Metadata();
        final HttpCookie srcCookie = new HttpCookie("foo", "bar");
        srcCookie.setMaxAge(2592000);
        responseHeaders.put(SET_COOKIE_KEY, srcCookie.toString());
        interceptor.processResponseCookies(path, responseHeaders);

        final Metadata dummyResponseHeaders = new Metadata();
        final Metadata actualRequestHeaders = runInterceptCall(path, requestHeaders, dummyResponseHeaders);

        assertNotNull(actualRequestHeaders);
        assertCookies(actualRequestHeaders, srcCookie);
    }

    public void testSessionCookieOverwrite() {

        final URI path = URI.create("https://www.test.com/grpc.Service/GetCookies");
        final Metadata responseHeaders = new Metadata();
        final HttpCookie srcCookie = new HttpCookie("foo", "bar");
        responseHeaders.put(SET_COOKIE_KEY, srcCookie.toString());
        interceptor.processResponseCookies(path, responseHeaders);

        final Metadata dummyResponseHeaders1 = new Metadata();
        final HttpCookie newCookie = new HttpCookie("foo", "foe");
        dummyResponseHeaders1.put(SET_COOKIE_KEY, newCookie.toString());
        final Metadata requestHeaders1 = new Metadata();
        final Metadata actualRequestHeaders1 = runInterceptCall(path, requestHeaders1, dummyResponseHeaders1);

        assertNotNull(actualRequestHeaders1);
        assertCookies(actualRequestHeaders1, srcCookie);

        final Metadata dummyResponseHeaders2 = new Metadata();
        final Metadata requestHeaders2 = new Metadata();
        final Metadata actualRequestHeaders2 = runInterceptCall(path, requestHeaders2, dummyResponseHeaders2);

        assertNotNull(actualRequestHeaders2);
        assertCookies(actualRequestHeaders2, newCookie);
    }

    public void testExpiredMaxAgeCookieIgnored() {

        final URI path = URI.create("https://www.test.com/grpc.Service/GetCookies");
        final Metadata requestHeaders = new Metadata();
        final Metadata responseHeaders = new Metadata();
        final HttpCookie srcCookie = new HttpCookie("foo", "bar");
        responseHeaders.put(SET_COOKIE_KEY, srcCookie.toString() + "; Max-Age=0");
        interceptor.processResponseCookies(path, responseHeaders);

        final Metadata dummyResponseHeaders = new Metadata();
        final Metadata actualRequestHeaders = runInterceptCall(path, requestHeaders, dummyResponseHeaders);

        assertNotNull(actualRequestHeaders);
        assertTrue(actualRequestHeaders.keys().isEmpty());
    }

    public void testExpiredExpiresCookieIgnored() {

        final URI path = URI.create("https://www.test.com/grpc.Service/GetCookies");
        final Metadata requestHeaders = new Metadata();
        final Metadata responseHeaders = new Metadata();
        final HttpCookie srcCookie = new HttpCookie("foo", "bar");
        responseHeaders.put(SET_COOKIE_KEY, srcCookie.toString() + "; Expires=Wed, 21 Oct 2015 07:28:00 GMT");
        interceptor.processResponseCookies(path, responseHeaders);

        final Metadata dummyResponseHeaders = new Metadata();
        final Metadata actualRequestHeaders = runInterceptCall(path, requestHeaders, dummyResponseHeaders);

        assertNotNull(actualRequestHeaders);
        assertTrue(actualRequestHeaders.keys().isEmpty());
    }

    public void testMatchingDomainCookieAccepted() {

        final URI path = URI.create("https://www.test.com/grpc.Service/GetCookies");
        final Metadata requestHeaders = new Metadata();
        final Metadata responseHeaders = new Metadata();
        final HttpCookie srcCookie = new HttpCookie("foo", "bar");
        responseHeaders.put(SET_COOKIE_KEY, srcCookie.toString() + "; Domain=www.test.com");
        interceptor.processResponseCookies(path, responseHeaders);

        final Metadata dummyResponseHeaders = new Metadata();
        final Metadata actualRequestHeaders = runInterceptCall(path, requestHeaders, dummyResponseHeaders);

        assertNotNull(actualRequestHeaders);
        assertCookies(actualRequestHeaders, srcCookie);
    }

    public void testMatchingHttpCookieAccepted() {

        final URI path = URI.create("https://www.test.com/grpc.Service/GetCookies");
        final Metadata requestHeaders = new Metadata();
        final Metadata responseHeaders = new Metadata();
        final HttpCookie srcCookie = new HttpCookie("foo", "bar");
        responseHeaders.put(SET_COOKIE_KEY, srcCookie.toString() + "; HttpOnly");
        interceptor.processResponseCookies(path, responseHeaders);

        final Metadata dummyResponseHeaders = new Metadata();
        final Metadata actualRequestHeaders = runInterceptCall(path, requestHeaders, dummyResponseHeaders);

        assertNotNull(actualRequestHeaders);
        assertCookies(actualRequestHeaders, srcCookie);
    }

    public void testMatchingSecureCookieAcceptedOverHttps() {

        final URI path = URI.create("https://www.test.com/grpc.Service/GetCookies");
        final Metadata requestHeaders = new Metadata();
        final Metadata responseHeaders = new Metadata();
        final HttpCookie srcCookie = new HttpCookie("foo", "bar");
        responseHeaders.put(SET_COOKIE_KEY, srcCookie.toString() + "; Secure;");
        interceptor.processResponseCookies(path, responseHeaders);

        final Metadata dummyResponseHeaders = new Metadata();
        final Metadata actualRequestHeaders = runInterceptCall(path, requestHeaders, dummyResponseHeaders);

        assertNotNull(actualRequestHeaders);
        assertCookies(actualRequestHeaders, srcCookie);
    }

    public void testMatchingSecureCookieRejectedOverHttp() {

        final CookieManager cookieManager = new CookieManager();
        interceptor = new CookieStoreInterceptor(true, cookieManager);

        final URI path = URI.create("http://www.test.com/grpc.Service/GetCookies");
        final Metadata requestHeaders = new Metadata();
        final Metadata responseHeaders = new Metadata();
        final HttpCookie srcCookie = new HttpCookie("foo", "bar");
        responseHeaders.put(SET_COOKIE_KEY, srcCookie.toString() + "; Secure;");
        interceptor.processResponseCookies(path, responseHeaders);

        final Metadata dummyResponseHeaders = new Metadata();
        final Metadata actualRequestHeaders = runInterceptCall(path, requestHeaders, dummyResponseHeaders);

        assertNotNull(actualRequestHeaders);
        assertTrue(actualRequestHeaders.keys().isEmpty());
    }

    public void testInvalidDomainCookieIgnored() {

        final URI path = URI.create("https://www.test.com/grpc.Service/GetCookies");
        final Metadata requestHeaders = new Metadata();
        final Metadata responseHeaders = new Metadata();
        final HttpCookie srcCookie = new HttpCookie("foo", "bar");
        responseHeaders.put(SET_COOKIE_KEY, srcCookie.toString() + "; Domain=www.domain.com");
        interceptor.processResponseCookies(path, responseHeaders);

        final Metadata dummyResponseHeaders = new Metadata();
        final Metadata actualRequestHeaders = runInterceptCall(path, requestHeaders, dummyResponseHeaders);

        assertNotNull(actualRequestHeaders);
        assertTrue(actualRequestHeaders.keys().isEmpty());
    }

    public void testSubDomainCookieIgnored() {

        final URI path = URI.create("http://test.com/grpc.Service/GetCookies");
        final Metadata requestHeaders = new Metadata();
        final Metadata responseHeaders = new Metadata();
        final HttpCookie srcCookie = new HttpCookie("foo", "bar");
        responseHeaders.put(SET_COOKIE_KEY, srcCookie.toString() + "; Domain=www.test.com");
        interceptor.processResponseCookies(path, responseHeaders);

        final Metadata dummyResponseHeaders = new Metadata();
        final Metadata actualRequestHeaders = runInterceptCall(path, requestHeaders, dummyResponseHeaders);

        assertNotNull(actualRequestHeaders);
        assertTrue(actualRequestHeaders.keys().isEmpty());
    }

    public void testPathRespectedInCookie() {

        final URI getPath = URI.create("http://test.com/grpc.Service/GetCookies");
        final URI postPath = URI.create("http://test.com/grpc.Service/PostCookies");

        final HttpCookie srcCookie = new HttpCookie("foo", "bar");
        srcCookie.setVersion(0);
        final Metadata responseHeaders = new Metadata();
        responseHeaders.put(SET_COOKIE_KEY, srcCookie.toString() + "; Path=/grpc.Service/GetCookies");
        interceptor.processResponseCookies(getPath, responseHeaders);

        final Metadata requestHeaders1 = new Metadata();
        final Metadata responseHeaders1 = new Metadata();
        final Metadata actualRequestHeaders1 = runInterceptCall(getPath, requestHeaders1, responseHeaders1);

        assertNotNull(actualRequestHeaders1);
        assertCookies(actualRequestHeaders1, srcCookie);

        final Metadata requestHeaders2 = new Metadata();
        final Metadata responseHeaders2 = new Metadata();
        final Metadata actualRequestHeaders2 = runInterceptCall(postPath, requestHeaders2, responseHeaders2);

        assertNotNull(actualRequestHeaders2);
        assertTrue(actualRequestHeaders2.keys().isEmpty());
    }

    public void testCookieForwardedInSubDomains() {

        final URI basePath = URI.create("http://test.com/");
        final HttpCookie srcCookie = new HttpCookie("foo", "bar");
        srcCookie.setVersion(0);
        final Metadata responseHeaders = new Metadata();
        responseHeaders.put(SET_COOKIE_KEY, srcCookie.toString() + "; Path=/grpc.Service/");
        interceptor.processResponseCookies(basePath, responseHeaders);


        final URI getPath = URI.create("http://test.com/grpc.Service/GetCookies");
        final Metadata requestHeaders1 = new Metadata();
        final Metadata responseHeaders1 = new Metadata();
        final Metadata actualRequestHeaders1 = runInterceptCall(getPath, requestHeaders1, responseHeaders1);

        assertNotNull(actualRequestHeaders1);
        assertCookies(actualRequestHeaders1, srcCookie);

        final URI postPath = URI.create("http://test.com/grpc.Service/PostCookies");
        final Metadata requestHeaders2 = new Metadata();
        final Metadata responseHeaders2 = new Metadata();
        final Metadata actualRequestHeaders2 = runInterceptCall(postPath, requestHeaders2, responseHeaders2);

        assertNotNull(actualRequestHeaders2);
        assertCookies(actualRequestHeaders2, srcCookie);

        final URI diffPath = URI.create("http://test.com/grpc.Health/GetCookies");
        final Metadata requestHeaders3 = new Metadata();
        final Metadata responseHeaders3 = new Metadata();
        final Metadata actualRequestHeaders3 = runInterceptCall(diffPath, requestHeaders3, responseHeaders3);

        assertNotNull(actualRequestHeaders3);
        assertTrue(actualRequestHeaders3.keys().isEmpty());
    }

    private void assertCookies(final Metadata requestHeaders, final HttpCookie... expectedCookies) {
        assertTrue(requestHeaders.containsKey(COOKIE_KEY));
        final Iterable<String> actualCookies = requestHeaders.getAll(COOKIE_KEY);
        assertNotNull(actualCookies);
        final List<String> actualCookieList = new ArrayList<>();
        actualCookies.forEach(actualCookieList::add);

        final List<String> expectedCookieList = new ArrayList<>();
        for (final HttpCookie expectedCookie : expectedCookies) {
            expectedCookieList.add(cookieString(expectedCookie));
        }
        assertEquals(expectedCookieList, actualCookieList);
    }

    private Metadata runInterceptCall(final URI uri, final Metadata requestHeaders, final Metadata responseHeaders) {

        final AtomicReference<Metadata> actualRequestHeadersStore = new AtomicReference<>(null);

        final StringMarshaller marshaller = new StringMarshaller();
        final MethodDescriptor<String, String> methodDescriptor = MethodDescriptor.
            newBuilder(marshaller, marshaller).
            setFullMethodName(uri.getPath().substring(1)).
            setType(MethodDescriptor.MethodType.UNARY).
            build();
        final CallOptions callOptions = CallOptions.DEFAULT.withAuthority(uri.getAuthority());
        final Channel channel = new Channel() {
            @Override
            public <RequestT, ResponseT> ClientCall<RequestT, ResponseT> newCall(
                final MethodDescriptor<RequestT, ResponseT> methodDescriptor,
                final CallOptions callOptions) {
                return new ClientCall<RequestT, ResponseT>() {
                    @Override
                    public void start(final Listener<ResponseT> responseListener, final Metadata requestHeaders) {
                        actualRequestHeadersStore.set(requestHeaders);
                        responseListener.onHeaders(responseHeaders);
                    }

                    @Override
                    public void request(final int numMessages) {
                        // do nothing
                    }

                    @Override
                    public void cancel(@Nullable final String message, @Nullable final Throwable cause) {
                        // do nothing
                    }

                    @Override
                    public void halfClose() {
                        // do nothing
                    }

                    @Override
                    public void sendMessage(final RequestT message) {
                        // do nothing
                    }
                };
            }

            @Override
            public String authority() {
                return null;
            }
        };

        final ClientCall<String, String> clientCall = interceptor.interceptCall(methodDescriptor, callOptions, channel);
        final ClientCall.Listener<String> responseListener = new ClientCall.Listener<String>() {
            @Override
            public void onHeaders(Metadata headers) {
                super.onHeaders(headers);
            }

            @Override
            public void onMessage(String message) {
                super.onMessage(message);
            }

            @Override
            public void onClose(Status status, Metadata trailers) {
                super.onClose(status, trailers);
            }

            @Override
            public void onReady() {
                super.onReady();
            }
        };
        clientCall.start(responseListener, requestHeaders);

        return actualRequestHeadersStore.get();
    }

    private static class StringMarshaller implements MethodDescriptor.Marshaller<String> {
        @Override
        public InputStream stream(final String value) {
            return new ByteArrayInputStream(value.getBytes());
        }

        @Override
        public String parse(final InputStream stream) {
            try {
                final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                final byte[] data = new byte[1024];

                int numRead;
                while ((numRead = stream.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, numRead);
                }

                buffer.flush();
                byte[] byteArray = buffer.toByteArray();

                return new String(byteArray, StandardCharsets.UTF_8);
            } catch (final Throwable throwable) {
                return null;
            }
        }
    }
}
