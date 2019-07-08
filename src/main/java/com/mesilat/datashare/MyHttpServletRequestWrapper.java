package com.mesilat.datashare;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

public class MyHttpServletRequestWrapper extends HttpServletRequestWrapper {
    private final ServletInputStream stream;

    @Override
    public ServletInputStream getInputStream() throws IOException {
        return stream;
    }

    public MyHttpServletRequestWrapper(HttpServletRequest request, ServletInputStream stream){
        super(request);
        this.stream = stream;
    }
    public MyHttpServletRequestWrapper(HttpServletRequest request, byte[] data){
        super(request);
        this.stream = new MyServletInputStream(new ByteArrayInputStream(data));
    }

    public class MyServletInputStream extends ServletInputStream {
        private final ByteArrayInputStream stream;

        @Override
        public int read() throws IOException {
            return stream.read();
        }

        public MyServletInputStream(ByteArrayInputStream stream){
            this.stream = stream;
        }
    }
}