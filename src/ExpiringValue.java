public class ExpiringValue {
    private String value;
    private long expirationTime;

    public ExpiringValue(String value, long expirationTime) {
        this.value = value;
        this.expirationTime = expirationTime;
    }

    public String getValue() {
        return value;
    }

    public long getExpirationTime() {
        return expirationTime;
    }

    public boolean isExpired() {
        if (expirationTime == 0)
            return false;
        return System.currentTimeMillis() > expirationTime;
    }
}
