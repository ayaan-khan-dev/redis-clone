public class ExpiringValue {
    private String value;
    private long lastAccessedTime;
    private long expirationTimeSystem;

    public ExpiringValue(String value, long expirationTimeSystem) {
        this.value = value;
        this.expirationTimeSystem = expirationTimeSystem;
        lastAccessedTime = System.currentTimeMillis();
    }

    public ExpiringValue(String value, long expirationTimeSystem, long lastAccessedTime) {
        this.value = value;
        this.expirationTimeSystem = expirationTimeSystem;
        this.lastAccessedTime = lastAccessedTime;
    }

    public String getValue() {
        return value;
    }

    public long getExpirationTimeSystem() {
        return expirationTimeSystem;
    }

    public void Accessed() {
        lastAccessedTime = System.currentTimeMillis();
    }

    public long lastAccessed() {
        return lastAccessedTime;
    }


    public boolean isExpired() {
        if (expirationTimeSystem == 0)
            return false;
        return System.currentTimeMillis() > expirationTimeSystem;
    }
}
