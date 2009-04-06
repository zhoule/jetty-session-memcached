package memcachedsession.jetty;

import java.net.InetSocketAddress;

import javax.servlet.http.HttpSession;

import junit.framework.TestCase;

import memcachedsession.jetty.MemcachedSessionIdManager;
import memcachedsession.jetty.MemcachedSessionManager;
import memcachedsession.jetty.TwoSessionInstancesTest.TestHttpRequest;
import net.spy.memcached.MemcachedClient;

import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.ContextHandler;
import org.mortbay.jetty.servlet.SessionHandler;

public class SessionTimeoutTest extends TestCase {
	
	private MemcachedSessionIdManager idManager;
	private MemcachedSessionManager sessionManager;
	private SessionHandler handler;
	private Server server;
	private Process process;
	private MemcachedClient memcached;
	private TestHttpRequest request;
	
	
	protected void setUp() throws Exception {
		process = Runtime.getRuntime().exec("memcached -m 1024 -p 8888");
		idManager = new MemcachedSessionIdManager();
		server = new Server();
		sessionManager = new MemcachedSessionManager();
		sessionManager.setMaxInactiveInterval(3);
		handler = new SessionHandler(sessionManager);
		idManager.setWorkerName("node0");
		sessionManager.setIdManager(idManager);
		sessionManager.setScavengePeriodMs(500);
		sessionManager.setMaxCookieAge(10000);
		sessionManager.setRefreshCookieAge(10000);
		ContextHandler context = new ContextHandler();
		sessionManager.setSessionHandler(handler);
		server.setHandler(context);
		context.setHandler(handler);
		server.start();
		
		memcached = new MemcachedClient(new InetSocketAddress("localhost", 8888));
		request = new TestHttpRequest();
		
		
	}
	
	protected void tearDown() throws Exception {
		server.stop();
		process.destroy();
	}
	
	public void dstestWaitingForInvalidation() throws Exception {
		
		HttpSession session = sessionManager.newHttpSession(request);
		session.setAttribute("foo", 1000);
		
		assertNotNull(memcached.get(session.getId()));
		assertEquals(1000, memcached.get(session.getId() + "/foo"));
		
		Thread.sleep(4000);
		
		assertNull(memcached.get(session.getId()));
		assertNull(memcached.get(session.getId() + "/foo"));
	}
	
	public void testRefreshingAccessed() throws Exception {
		
		HttpSession session = sessionManager.newHttpSession(request);
//		assertNotNull(sessionManager.getSessionCookie(session, "/context", false));
		
		assertNotNull(sessionManager.access(session, false));
		session.setAttribute("foo", 1000);
		
		assertNotNull(memcached.get(session.getId()));
		assertEquals(1000, memcached.get(session.getId() + "/foo"));
		
		Thread.sleep(2000);
		
		assertNotNull(memcached.get(session.getId()));
		assertEquals(1000, memcached.get(session.getId() + "/foo"));
		
		HttpSession session2 = sessionManager.getHttpSession(sessionManager.getNodeId(session));
		assertNotNull(sessionManager.access(session2, false));
		
		Thread.sleep(2000);
		
		assertNotNull(memcached.get(session.getId()));
		assertEquals(1000, memcached.get(session.getId() + "/foo"));
		
		Thread.sleep(3000);
		
		assertNull(memcached.get(session.getId()));
		assertNull(memcached.get(session.getId() + "/foo"));
	}

}
