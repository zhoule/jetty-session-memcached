package memcachedsession.jetty;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Future;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import net.spy.memcached.MemcachedClient;

import org.mortbay.jetty.Server;
import org.mortbay.jetty.SessionIdManager;
import org.mortbay.jetty.servlet.AbstractSessionIdManager;

public class MemcachedSessionIdManager extends AbstractSessionIdManager implements SessionIdManager {
	
	private MemcachedClient _memcached;
	
	{
		try {
			_memcached = new MemcachedClient(new InetSocketAddress("localhost", 8888));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	public MemcachedSessionIdManager() {
		super(null);
	}
	
	public MemcachedSessionIdManager(Server server) {
		super(server);
	}

	public MemcachedSessionIdManager(Server server, Random random) {
		super(server, random);
	}

	@Override
	@SuppressWarnings("unchecked")
	public void addSession(HttpSession session) {
		synchronized (MemcachedSessionManager.JSESSION_MONITOR) {
			List<String> jsessions = (List<String>) _memcached.get(MemcachedSessionManager._JSESSIONS);
			if (jsessions == null) {
				jsessions = new LinkedList<String>();
			}

			jsessions.add(((MemcachedSessionManager.Session) session).getClusterId());

			Future<Boolean> ok = _memcached.set(MemcachedSessionManager._JSESSIONS, MemcachedSessionManager.DISTANT_FUTURE, jsessions);
			try {
				ok.get();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}

	@Override
	public String getClusterId(String nodeId) {
		int dot = nodeId.lastIndexOf('.');
        return (dot > 0) ? nodeId.substring(0, dot) : nodeId;
	}

	@Override
	public String getNodeId(String clusterId, HttpServletRequest request) {
		if (_workerName != null)
			return clusterId + '.' + _workerName;
		return clusterId;
	}

	@Override
	public boolean idInUse(String id) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void invalidateAll(String id) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void removeSession(HttpSession session) {
		// TODO Auto-generated method stub
		
	}	
}
