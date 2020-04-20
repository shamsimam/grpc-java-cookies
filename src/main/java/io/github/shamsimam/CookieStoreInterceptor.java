package io.github.shamsimam;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;
import io.grpc.ForwardingClientCallListener.SimpleForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import io.grpc.MethodDescriptor;

import java.net.CookieManager;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A client interceptor to manage cookies by inspecting set-cookie headers received in the response
 * from the server and by forwarding cookies using the cookie header in the request to the server.
 * <p>
 * The implementation uses a CookieManager, initialized with a CookieStore and a CookiePolicy,
 * which makes policy decisions on cookie acceptance/rejection based on gRPC methods being invoked.
 *
 * @author <a href="https://shamsimam.github.io/">Shams Imam</a> (shams.imam@gmail.com)
 */
public class CookieStoreInterceptor implements ClientInterceptor {

    private static final Logger logger = Logger.getLogger(CookieStoreInterceptor.class.getName());

    private static final Key<String> SET_COOKIE_KEY = createMetadataKey("set-cookie");
    private static final Key<String> SET_COOKIE2_KEY = createMetadataKey("set-cookie2");

    protected static final List<Key<String>> SET_COOKIE_KEYS = Arrays.asList(SET_COOKIE_KEY, SET_COOKIE2_KEY);

    private static Key<String> createMetadataKey(String headerKey) {
        return Key.of(headerKey, Metadata.ASCII_STRING_MARSHALLER);
    }

    // The CookieManager used to store and filter cookies
    private final CookieManager cookieManager;
    // Whether or not to assume prior knowledge that the client is using plaintext
    // Plaintext connections will have secure cookies excluded from being sent to the server.
    private final boolean usePlainText;

    /**
     * Create a new CookieStoreInterceptor.
     *
     * The created CookieStoreInterceptor instance assumes the client uses encrypted connections and
     * forwards secure cookies to the server.
     * The instance uses a default CookieManager with default cookie store and accept policy.
     */
    public CookieStoreInterceptor() {
        this(new CookieManager());
    }

    /**
     * Create a new CookieStoreInterceptor.
     *
     * The created CookieStoreInterceptor instance assumes the client uses encrypted connections and
     * forwards secure cookies to the server.
     *
     * @param cookieManager the CookieManager used to store and filter cookies
     */
    public CookieStoreInterceptor(final CookieManager cookieManager) {
        this(false, cookieManager);
    }

    /**
     * Create a new CookieStoreInterceptor.
     *
     * @param usePlainText  whether or not to assume prior knowledge that the client is using plaintext.
     * @param cookieManager the CookieManager used to store and filter cookies
     */
    public CookieStoreInterceptor(final boolean usePlainText, final CookieManager cookieManager) {
        this.usePlainText = usePlainText;
        this.cookieManager = cookieManager;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
        final MethodDescriptor<ReqT, RespT> methodDescriptor,
        final CallOptions callOptions,
        final Channel nextChannel) {

        return new SimpleForwardingClientCall<ReqT, RespT>(nextChannel.newCall(methodDescriptor, callOptions)) {

            @Override
            public void start(final Listener<RespT> responseListener, final Metadata requestHeaders) {

                final String authority = retrieveAuthority(callOptions, nextChannel);
                final String fullMethodName = methodDescriptor.getFullMethodName();
                final URI callPathUri = createUri(authority, fullMethodName);
                // adds cookies to the request headers
                addRequestCookies(callPathUri, requestHeaders);
                super.start(new SimpleForwardingClientCallListener<RespT>(responseListener) {
                    @Override
                    public void onHeaders(final Metadata responseHeaders) {
                        // extract the cookies from the response
                        processResponseCookies(callPathUri, responseHeaders);
                        super.onHeaders(responseHeaders);
                    }
                }, requestHeaders);
            }
        };
    }

    protected String retrieveAuthority(final CallOptions callOptions, final Channel channel) {
        final String callOptionsAuthority = callOptions.getAuthority();
        if (callOptionsAuthority != null) {
            return callOptionsAuthority;
        }
        final String channelAuthority = channel.authority();
        if (channelAuthority != null) {
            return channelAuthority;
        }
        throw new IllegalStateException("authority cannot be determined for request!");
    }

    protected void addRequestCookies(final URI callPathUri, final Metadata requestHeaders) {
        final Map<String, List<String>> httpCookies = getCookies(callPathUri, requestHeaders);
        if (!httpCookies.isEmpty()) {
            for (final Map.Entry<String, List<String>> entry : httpCookies.entrySet()) {
                final String headerKey = entry.getKey();
                final List<String> headerValues = entry.getValue();
                if (headerValues != null) {
                    final Key<String> metadataKey = createMetadataKey(headerKey);
                    for (final String headerValue : headerValues) {
                        requestHeaders.put(metadataKey, headerValue);
                    }
                }
            }
        }
    }

    protected void processResponseCookies(final URI callPathUri, final Metadata responseHeaders) {
        final Map<String, List<String>> headers = new HashMap<>(SET_COOKIE_KEYS.size());
        for (final Key<String> headerKey : SET_COOKIE_KEYS) {
            if (responseHeaders.containsKey(headerKey)) {
                final String headerValue = String.valueOf(responseHeaders.get(headerKey));
                headers.put(headerKey.originalName(), Collections.singletonList(headerValue));
            }
        }
        if (!headers.isEmpty()) {
            try {
                cookieManager.put(callPathUri, headers);
            } catch (final Throwable th) {
                logger.log(Level.SEVERE, "error is parsing cookies", th);
            }
        }
    }

    private URI createUri(final String authority, final String fullMethodName) {
        final String url = (usePlainText ? "http://" : "https://") + authority + "/" + fullMethodName;
        return URI.create(url);
    }

    private Map<String, List<String>> getCookies(final URI callPathUri, final Metadata requestMetadata) {
        try {
            final Set<String> requestMetadataKeys = requestMetadata.keys();
            final Map<String, List<String>> requestHeaders = new HashMap<>(requestMetadataKeys.size());
            for (final String key : requestMetadataKeys) {
                final Key<String> metadataKey = createMetadataKey(key);
                final Iterable<String> metadataValues = requestMetadata.getAll(metadataKey);
                if (metadataValues != null) {
                    final List<String> headerValues = new ArrayList<>();
                    for (final String metadataValue : metadataValues) {
                        if (metadataValue != null) {
                            headerValues.add(metadataValue);
                        }
                    }
                    requestHeaders.put(key, headerValues);
                }
            }
            return cookieManager.get(callPathUri, requestHeaders);
        } catch (Throwable th) {
            logger.log(Level.SEVERE, "error in retrieving cookies", th);
        }
        return Collections.emptyMap();
    }
}
