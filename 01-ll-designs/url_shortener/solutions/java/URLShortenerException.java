/**
 * Custom exception for URL shortener operations.
 */
public class URLShortenerException extends Exception {
    
    public URLShortenerException(String message) {
        super(message);
    }
    
    public URLShortenerException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * Exception for invalid URLs.
     */
    public static class InvalidURLException extends URLShortenerException {
        public InvalidURLException(String url) {
            super("Invalid URL: " + url);
        }
    }
    
    /**
     * Exception for custom alias conflicts.
     */
    public static class AliasConflictException extends URLShortenerException {
        public AliasConflictException(String alias) {
            super("Custom alias '" + alias + "' is already taken");
        }
    }
    
    /**
     * Exception for expired URLs.
     */
    public static class ExpiredURLException extends URLShortenerException {
        public ExpiredURLException(String shortCode) {
            super("URL with code '" + shortCode + "' has expired");
        }
    }
    
    /**
     * Exception for not found URLs.
     */
    public static class URLNotFoundException extends URLShortenerException {
        public URLNotFoundException(String shortCode) {
            super("URL with code '" + shortCode + "' not found");
        }
    }
}