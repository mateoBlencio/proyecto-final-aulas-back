package ar.edu.utn.frc.siga.sysacad.internal.client;

import ar.edu.utn.frc.siga.sysacad.internal.exception.SysacadResponseTooLargeException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Corta la lectura de una respuesta que supera {@code maxBytes}, antes de cargarla entera en memoria. */
public class ResponseSizeLimitInterceptor implements ClientHttpRequestInterceptor {

    private final long maxBytes;

    public ResponseSizeLimitInterceptor(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        ClientHttpResponse response = execution.execute(request, body);
        long declared = response.getHeaders().getContentLength();
        if (declared > maxBytes) {
            response.close();
            throw new SysacadResponseTooLargeException(maxBytes);
        }
        return new LimitedResponse(response, maxBytes);
    }

    private static final class LimitedResponse implements ClientHttpResponse {

        private final ClientHttpResponse delegate;
        private final long maxBytes;

        private LimitedResponse(ClientHttpResponse delegate, long maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }

        @Override
        public InputStream getBody() throws IOException {
            return new LimitedInputStream(delegate.getBody(), maxBytes);
        }

        @Override
        public void close() {
            delegate.close();
        }
    }

    private static final class LimitedInputStream extends FilterInputStream {

        private final long maxBytes;
        private long read;

        private LimitedInputStream(InputStream in, long maxBytes) {
            super(in);
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                count(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int n = super.read(buffer, offset, length);
            if (n > 0) {
                count(n);
            }
            return n;
        }

        private void count(int n) {
            read += n;
            if (read > maxBytes) {
                throw new SysacadResponseTooLargeException(maxBytes);
            }
        }
    }
}
