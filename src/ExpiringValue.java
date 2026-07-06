public class ExpiringValue {
    private String value;
    private long expirationTime;
    private long lastAccessedTime;
    private long expirationTimeSystem;

    public ExpiringValue(String value, long expirationTime) {
        this.value = value;
        this.expirationTime = expirationTime;
        expirationTimeSystem = expirationTime + System.currentTimeMillis();
        lastAccessedTime = System.currentTimeMillis();
    }

    public String getValue() {
        return value;
    }

    public long getExpirationTime() {
        return expirationTime;
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
        if (expirationTime == 0)
            return false;
        return System.currentTimeMillis() > expirationTimeSystem;
    }
}
