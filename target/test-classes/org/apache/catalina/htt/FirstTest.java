package org.apache.catalina.htt;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.junit.Test;

import javax.net.SocketFactory;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class FirstTest extends TomcatBaseTest {

    @Test
    public void first() throws LifecycleException, IOException, InterruptedException {
        Tomcat tomcat = getTomcatInstance();
        Context context = tomcat.addContext("", null);
        Tomcat.addServlet(context, "first", new FirstServlet());
        context.addServletMapping("/hello", "first");
        tomcat.getConnector().setProperty("connectionTimeout", "5000");
        tomcat.start();

        Socket socket = SocketFactory.getDefault().
                createSocket("127.0.0.1", getPort());
        OutputStream os = socket.getOutputStream();
        String requestLine = "POST http://localhost:" + getPort() +
                "/hello HTTP/1.1\r\n";
        os.write(requestLine.getBytes());
        os.write("transfer-encoding: chunked\r\n".getBytes());
        os.write("\r\n".getBytes());

        InputStream is = socket.getInputStream();
        ResponseReaderThread readThread = new ResponseReaderThread(is);
        readThread.start();
        while (!readThread.getResponse().contains("hello, first")) {
            Thread.sleep(500);
        }
        System.out.println(readThread.getResponse());
        readThread.join();
        os.close();
        is.close();
    }

    @Test
    public void second() throws LifecycleException, IOException, InterruptedException {
        Tomcat tomcat = getTomcatInstance();
        Context context = tomcat.addContext("", null);
        Wrapper wrapper = Tomcat.addServlet(context, "first", new FirstServlet());
        wrapper.setAsyncSupported(true);
        context.addServletMapping("/hello", "first");
        Connector connector = tomcat.getConnector();

        tomcat.start();

        ByteChunk byteChunk = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() + "/hello", byteChunk, null);
        System.out.println(rc);

        connector.stop();

        rc = getUrl("http://localhost:" + getPort() + "/hello", byteChunk, null);
        System.out.println(rc);

    }

    private static class ResponseReaderThread extends Thread {

        private final InputStream is;
        private StringBuilder response = new StringBuilder();

        private volatile Exception e = null;

        public ResponseReaderThread(InputStream is) {
            this.is = is;
        }

        public Exception getException() {
            return e;
        }

        public String getResponse() {
            return response.toString();
        }

        @Override
        public void run() {
            try {
                int c = is.read();
                while (c > -1) {
                    response.append((char) c);
                    c = is.read();
                }
            } catch (Exception e) {
                this.e = e;
            }
        }
    }

    public class FirstServlet extends HttpServlet {
        @Override
        protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.getWriter().print("hello, first");
        }
    }

}
