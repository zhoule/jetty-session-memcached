package memcachedsession.jetty;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSessionActivationListener;
import javax.servlet.http.HttpSessionEvent;

import net.spy.memcached.MemcachedClient;

import org.mortbay.jetty.SessionManager;
import org.mortbay.jetty.servlet.AbstractSessionManager;

public class MemcachedSessionManager extends AbstractSessionManager implements SessionManager, Runnable {
	
	public static final String _JSESSIONS = "_JSESSIONS";
	
	public static final int DISTANT_FUTURE = Integer.MAX_VALUE;
	public static final Object JSESSION_MONITOR = new Object();
	
	private MemcachedClient _memcached;
	private long _scavengePeriodMs = 30000;
	private ScheduledExecutorService _scheduler;
	private ScheduledFuture<?> _scavenger;

	
	public MemcachedSessionManager() {
		try {
			_memcached = new MemcachedClient(new InetSocketAddress("localhost", 8888));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public void doStart() throws Exception {
		super.doStart();
		
		_scheduler = Executors.newSingleThreadScheduledExecutor();
		scheduleScavenging();
	}
	
	 public void doStop() throws Exception {
		if (_scavenger != null) {
			_scavenger.cancel(true);
		}
		if (_scheduler != null) {
			_scheduler.shutdownNow();
		}
		
		super.doStop();
	}

	private void scheduleScavenging() {
		if (_scavenger != null) {
			_scavenger.cancel(true);
		}
		
		long scavengePeriod = getScavengePeriodMs();
		if (scavengePeriod > 0 && _scheduler != null) {
			_scavenger = _scheduler.scheduleWithFixedDelay(this, scavengePeriod, scavengePeriod, TimeUnit.MILLISECONDS);
		}
	}

	@Override
	public void addSession(org.mortbay.jetty.servlet.AbstractSessionManager.Session session) {
		
		Session memSession = (MemcachedSessionManager.Session) session;
		String clusterId = memSession.getClusterId();
		
		// assuming session is new
		MemcachedSessionInfo sessionInfo = new MemcachedSessionInfo();
		sessionInfo.setCreated(memSession.getCreationTime());
		sessionInfo.setMaxIdleMs(memSession.getMaxInactiveInterval() * 1000);
		
		Future<Boolean> ok = _memcached.set(clusterId, DISTANT_FUTURE, sessionInfo);
		try {
			ok.get();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public Session getSession(String idInCluster) {
		
		MemcachedSessionInfo sessionInfo =  (MemcachedSessionInfo) _memcached.get(idInCluster);
		
		if (sessionInfo != null) {
			return new Session(sessionInfo, idInCluster);
		}
		return null;
	}

	@Override
	public Map<String, Session> getSessionMap() {
		synchronized (JSESSION_MONITOR) {
			Map<String, Session> sessions = new HashMap<String, Session>();

			for (String clusterId : getClusterIds()) {
				sessions.put(clusterId, new Session((MemcachedSessionInfo) _memcached
						.get(clusterId), clusterId));
			}

			return sessions;
		}
	}

	@Override
	public int getSessions() {
		synchronized (JSESSION_MONITOR) {
			List<String> clusterIds = getClusterIds();
			return clusterIds.size();
		}
	}

	@Override
	protected void invalidateSessions() {
		// do nothing!
	}

	@SuppressWarnings("unchecked")
	private List<String> getClusterIds() {
		List<String> clusterIds = (List<String>) _memcached.get(_JSESSIONS);
		if (clusterIds == null) {
			return Collections.emptyList();
		}
		return clusterIds;
	}

	@Override
	protected Session newSession(HttpServletRequest request) {
		return new Session(request);
	}

	@Override
	protected void removeSession(String idInCluster) {
		MemcachedSessionInfo sessionInfo = (MemcachedSessionInfo) _memcached.get(idInCluster);

		for (String key : sessionInfo.getKeys()) {
			_memcached.delete(idInCluster + "/" + key);
		}

		_memcached.delete(idInCluster);
		
		synchronized (JSESSION_MONITOR) {
			List<String> clusterIds = getClusterIds();
			clusterIds.remove(idInCluster);

			_memcached.set(_JSESSIONS, DISTANT_FUTURE, clusterIds);
		}
	}
	
	@Override
	public void run() {
		scavenge();		
	}
	
	private void scavenge() {
		// don't attempt to scavenge if we are shutting down
		if (isStopping() || isStopped())
			return;

		Thread thread = Thread.currentThread();
		ClassLoader oldLoader = thread.getContextClassLoader();
		
		if (_loader!=null) {
            thread.setContextClassLoader(_loader);
		}
		
		try {
			
			long now = System.currentTimeMillis();
			
			List<String> clusterIds = getClusterIds();
			for (Iterator<String> iter = clusterIds.iterator(); iter.hasNext();) {
				String clusterId = iter.next();
				
				MemcachedSessionInfo sessionInfo = (MemcachedSessionInfo) _memcached.get(clusterId);
				long idleTime = sessionInfo.getMaxIdleMs();
				long accessed = sessionInfo.getAccessed();
				
				if (idleTime > 0 && accessed + idleTime < now) {
					
					removeSession(clusterId);
				}
			}
			
			
			
		} finally {
			thread.setContextClassLoader(oldLoader);
		}

	}
	
	public long getScavengePeriodMs() {
		return _scavengePeriodMs;
	}

	public void setScavengePeriodMs(long periodMs) {
		_scavengePeriodMs = periodMs;
		scheduleScavenging();
	}

	public class Session extends AbstractSessionManager.Session {
		
		/**
		 * 
		 */
		private static final long serialVersionUID = -2428097351341507674L;

		protected Session(HttpServletRequest request) {
			super(request);
		}
		
		protected Session(MemcachedSessionInfo sessionInfo, String clusterId) {
			super(sessionInfo.getCreated(), clusterId);
			_idChanged = sessionInfo.isIdChanged();
		}

		@SuppressWarnings("unchecked")
		@Override
		protected Map newAttributeMap() {
			throw new UnsupportedOperationException();
		}

		@Override
		public synchronized void setAttribute(String name, Object value) {
			if (value == null) {
				removeAttribute(name);
				return;
			}
			
			if (value instanceof HttpSessionActivationListener) {
				((HttpSessionActivationListener) value).sessionWillPassivate(new HttpSessionEvent(this));
			}
			MemcachedSessionInfo sessionInfo = (MemcachedSessionInfo) _memcached.get(_clusterId);
			Set<String> keys = sessionInfo.getKeys();
						
			Future<Boolean> ok1 = null;
			
			if (!keys.contains(name)) {
				keys.add(name);
				ok1 = _memcached.set(_clusterId, DISTANT_FUTURE, sessionInfo);
			}
			
			Future<Boolean> ok2 = _memcached.set(_clusterId + "/" + name, DISTANT_FUTURE, value);
			
			try {
				if (ok1 != null) ok1.get();
				ok2.get();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public synchronized Object getAttribute(String name) {
			Object value = _memcached.get(_clusterId + "/" + name);
			if (value instanceof HttpSessionActivationListener) {
				((HttpSessionActivationListener) value).sessionDidActivate(new HttpSessionEvent(this));
			}
			
			return value;
		}

		@Override
		public synchronized Enumeration<String> getAttributeNames() {
			MemcachedSessionInfo sessionInfo =  (MemcachedSessionInfo) _memcached.get(_clusterId);
			if (sessionInfo == null) {
				return Collections.enumeration(Collections.<String>emptySet());
			}
			return Collections.enumeration(sessionInfo.getKeys());
			
		}

		@Override
		public synchronized void removeAttribute(String name) {
			MemcachedSessionInfo sessionInfo = (MemcachedSessionInfo) _memcached.get(_clusterId);
			Set<String> keys = sessionInfo.getKeys();
			
			Future<Boolean> ok1 = null;;
			if (keys.contains(name)) {
				keys.remove(name);
				
				ok1 = _memcached.set(_clusterId, DISTANT_FUTURE, sessionInfo);
			}
			
			Future<Boolean> ok2 = _memcached.delete(_clusterId + "/" + name);
			
			try {
				if (ok1 != null) ok1.get();
				ok2.get();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		protected synchronized void willPassivate() {
			// all are already passivated
		}

		@Override
		protected synchronized void didActivate() {
			// 
		}
		
		@Override
		protected String getClusterId() {
			return _clusterId;
		}
		
		@Override
		public void setIdChanged(final boolean changed) {
			super.setIdChanged(changed);

			MemcachedSessionInfo sessionInfo = (MemcachedSessionInfo) _memcached.get(_clusterId);
			if (sessionInfo != null) {
				sessionInfo.setIdChanged(changed);

				Future<Boolean> ok1 = _memcached.set(_clusterId, DISTANT_FUTURE, sessionInfo);

				try {
					ok1.get();
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		}
		
		@Override
		public boolean isIdChanged() {
			
			MemcachedSessionInfo sessionInfo = (MemcachedSessionInfo) _memcached.get(_clusterId);
			if (sessionInfo != null) {
				_idChanged = sessionInfo.isIdChanged();
			}

			return super.isIdChanged();
		}
		
		@Override
		public void setMaxInactiveInterval(int secs) {
			super.setMaxInactiveInterval(secs);

			MemcachedSessionInfo sessionInfo = (MemcachedSessionInfo) _memcached.get(_clusterId);
			if (sessionInfo != null) {
				sessionInfo.setMaxIdleMs(_maxIdleMs);

				Future<Boolean> ok1 = _memcached.set(_clusterId, DISTANT_FUTURE, sessionInfo);

				try {
					ok1.get();
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		}

		@Override
		public int getMaxInactiveInterval() {
			
			MemcachedSessionInfo sessionInfo = (MemcachedSessionInfo) _memcached.get(_clusterId);
			if (sessionInfo != null) {
				_maxIdleMs = sessionInfo.getMaxIdleMs();
			}
			
			return super.getMaxInactiveInterval();
		}

		private MemcachedSessionManager getOuterType() {
			return MemcachedSessionManager.this;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getOuterType().hashCode();
			result = prime * result + ((_clusterId == null) ? 0 : _clusterId.hashCode());
			result = prime * result + ((_nodeId == null) ? 0 : _nodeId.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (!(obj instanceof Session))
				return false;
			Session other = (Session) obj;
			if (!getOuterType().equals(other.getOuterType()))
				return false;
			if (_clusterId == null) {
				if (other._clusterId != null)
					return false;
			} else if (!_clusterId.equals(other._clusterId))
				return false;
			if (_nodeId == null) {
				if (other._nodeId != null)
					return false;
			} else if (!_nodeId.equals(other._nodeId))
				return false;
			return true;
		}
				
	}	
}
