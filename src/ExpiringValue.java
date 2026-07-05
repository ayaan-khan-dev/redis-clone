public class ExpiringValue {
    private String value;
    private long expirationTime;
    private long lastAccessedTime;

    public ExpiringValue(String value, long expirationTime) {
        this.value = value;
        this.expirationTime = expirationTime;
        lastAccessedTime = System.currentTimeMillis();
    }

    public String getValue() {
        return value;
    }

    public long getExpirationTime() {
        return expirationTime;
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
        return System.currentTimeMillis() > expirationTime;
    }
}
