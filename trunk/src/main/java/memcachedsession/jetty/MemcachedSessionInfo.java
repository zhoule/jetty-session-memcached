package memcachedsession.jetty;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;


public class MemcachedSessionInfo implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1263226275532071956L;
	private long created;
	private boolean idChanged;
	private long maxIdleMs;
	private long accessed;
	
	private Set<String> keys = new HashSet<String>();
	
	public long getCreated() {
		return created;
	}
	
	public void setCreated(long created) {
		this.created = created;
		this.accessed = created;
	}
	
	public boolean isIdChanged() {
		return idChanged;
	}
	
	public void setIdChanged(boolean idChanged) {
		this.idChanged = idChanged;
	}
	
	public long getMaxIdleMs() {
		return maxIdleMs;
	}

	public void setMaxIdleMs(long maxIdleMs) {
		this.maxIdleMs = maxIdleMs;
	}

	public long getAccessed() {
		return accessed;
	}

	public void setAccessed(long accessed) {
		this.accessed = accessed;
	}

	public Set<String> getKeys() {
		return keys;
	}

	public void setKeys(Set<String> keys) {
		this.keys = keys;
	}
}