package memcachedsession.jetty;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.security.Principal;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletInputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import junit.framework.TestCase;

import memcachedsession.jetty.MemcachedSessionIdManager;
import memcachedsession.jetty.MemcachedSessionManager;
import net.spy.memcached.MemcachedClient;

import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.ContextHandler;
import org.mortbay.jetty.servlet.SessionHandler;

public class TwoSessionInstancesTest extends TestCase {

	private MemcachedSessionIdManager idManager;
	private MemcachedSessionManager sessionManager;
	private SessionHandler handler;
	private Server server;
	private Process process;
	private MemcachedClient memcached;
	private TestHttpRequest request;
	private HttpSession session1;
	private HttpSession session2;
	
	protected void setUp() throws Exception {
		idManager = new MemcachedSessionIdManager();
		server = new Server();
		process = Runtime.getRuntime().exec("memcached -m 1024 -p 8888");
		sessionManager = new MemcachedSessionManager();
		handler = new SessionHandler(sessionManager);
		idManager.setWorkerName("node0");
		sessionManager.setIdManager(idManager);
		ContextHandler context = new ContextHandler();
		sessionManager.setSessionHandler(handler);
		server.setHandler(context);
		context.setHandler(handler);
		server.start();
		
		memcached = new MemcachedClient(new InetSocketAddress("localhost", 8888));
		request = new TestHttpRequest();
		session1 = sessionManager.newHttpSession(request);
		session1.setAttribute("foo", 1000);
		session2 = sessionManager.newHttpSession(request);
		session2.setAttribute("foo", 2000);
	}

	protected void tearDown() throws Exception {
		server.stop();
		process.destroy();
	}
	
	public void testPresenceOfTwoSessions() {
				
		assertTrue(!session1.getId().equals(session2.getId()));
		
		assertEquals(2, sessionManager.getSessionMap().size());
		assertEquals(session1, sessionManager.getSession(session1.getId()));
		assertEquals(session2, sessionManager.getSession(session2.getId()));
		
		assertEquals(2, sessionManager.getSessions());
	}
	
	public void testInvalidateSession() {
		
		sessionManager.invalidateSessions();
		
		assertEquals(2, sessionManager.getSessionMap().size());
		assertEquals(1000, memcached.get(session1.getId() + "/foo"));
		assertEquals(2000, memcached.get(session2.getId() + "/foo"));
	}
	
	public void testStoppingServer() throws Exception {
		
		server.stop();
		
		assertEquals(2, sessionManager.getSessionMap().size());
		assertEquals(1000, memcached.get(session1.getId() + "/foo"));
		assertEquals(2000, memcached.get(session2.getId() + "/foo"));
	}
	
	public void testSessionRemoval() throws Exception {
		
		sessionManager.removeSession(session1.getId());
		
		assertEquals(1, sessionManager.getSessionMap().size());
		assertEquals(null, memcached.get(session1.getId() + "/foo"));
		assertEquals(2000, memcached.get(session2.getId() + "/foo"));
	}
	
	
	static class TestHttpRequest implements HttpServletRequest {

		@Override
		public String getAuthType() {
			return null;
		}

		@Override
		public String getContextPath() {
			return null;
		}

		@Override
		public Cookie[] getCookies() {
			return null;
		}

		@Override
		public long getDateHeader(String arg0) {
			return 0;
		}

		@Override
		public String getHeader(String arg0) {
			return null;
		}

		@SuppressWarnings("unchecked")
		@Override
		public Enumeration getHeaderNames() {
			return null;
		}

		@SuppressWarnings("unchecked")
		@Override
		public Enumeration getHeaders(String arg0) {
			return null;
		}

		@Override
		public int getIntHeader(String arg0) {
			return 0;
		}

		@Override
		public String getMethod() {
			return null;
		}

		@Override
		public String getPathInfo() {
			return null;
		}

		@Override
		public String getPathTranslated() {
			return null;
		}

		@Override
		public String getQueryString() {
			return null;
		}

		@Override
		public String getRemoteUser() {
			return null;
		}

		@Override
		public String getRequestURI() {
			return null;
		}

		@Override
		public StringBuffer getRequestURL() {
			return null;
		}

		@Override
		public String getRequestedSessionId() {
			return null;
		}

		@Override
		public String getServletPath() {
			return null;
		}

		@Override
		public HttpSession getSession() {
			return null;
		}

		@Override
		public HttpSession getSession(boolean arg0) {
			return null;
		}

		@Override
		public Principal getUserPrincipal() {
			return null;
		}

		@Override
		public boolean isRequestedSessionIdFromCookie() {
			return false;
		}

		@Override
		public boolean isRequestedSessionIdFromURL() {
			return false;
		}

		@Override
		public boolean isRequestedSessionIdFromUrl() {
			return false;
		}

		@Override
		public boolean isRequestedSessionIdValid() {
			return false;
		}

		@Override
		public boolean isUserInRole(String arg0) {
			return false;
		}

		@Override
		public Object getAttribute(String arg0) {
			return null;
		}

		@SuppressWarnings("unchecked")
		@Override
		public Enumeration getAttributeNames() {
			return null;
		}

		@Override
		public String getCharacterEncoding() {
			return null;
		}

		@Override
		public int getContentLength() {
			return 0;
		}

		@Override
		public String getContentType() {
			return null;
		}

		@Override
		public ServletInputStream getInputStream() throws IOException {
			return null;
		}

		@Override
		public String getLocalAddr() {
			return null;
		}

		@Override
		public String getLocalName() {
				return null;
		}

		@Override
		public int getLocalPort() {
			return 0;
		}

		@Override
		public Locale getLocale() {
			return null;
		}

		@SuppressWarnings("unchecked")
		@Override
		public Enumeration getLocales() {
			return null;
		}

		@Override
		public String getParameter(String arg0) {
			return null;
		}

		@SuppressWarnings("unchecked")
		@Override
		public Map getParameterMap() {
			return null;
		}

		@SuppressWarnings("unchecked")
		@Override
		public Enumeration getParameterNames() {
			return null;
		}

		@Override
		public String[] getParameterValues(String arg0) {
			return null;
		}

		@Override
		public String getProtocol() {
			return null;
		}

		@Override
		public BufferedReader getReader() throws IOException {
			return null;
		}

		@Override
		public String getRealPath(String arg0) {
			return null;
		}

		@Override
		public String getRemoteAddr() {
			return null;
		}

		@Override
		public String getRemoteHost() {
			return null;
		}

		@Override
		public int getRemotePort() {
			return 0;
		}

		@Override
		public RequestDispatcher getRequestDispatcher(String arg0) {
			return null;
		}

		@Override
		public String getScheme() {
			return null;
		}

		@Override
		public String getServerName() {
			return null;
		}

		@Override
		public int getServerPort() {
			return 0;
		}

		@Override
		public boolean isSecure() {
			return false;
		}

		@Override
		public void removeAttribute(String arg0) {
			
		}

		@Override
		public void setAttribute(String arg0, Object arg1) {
			
		}

		@Override
		public void setCharacterEncoding(String arg0) throws UnsupportedEncodingException {
			
		}

		
	}

}
