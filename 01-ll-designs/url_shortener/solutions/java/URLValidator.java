import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Pattern;

/**
 * Utility class for URL validation and sanitization.
 */
public class URLValidator {
    
    // URL pattern for basic validation
    private static final Pattern URL_PATTERN = Pattern.compile(
        "^https?://" +                           // Protocol
        "(?:(?:[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?\\.)+[A-Z]{2,6}\\.?|" + // Domain
        "localhost|" +                           // localhost
        "\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})" + // IP address
        "(?::\\d+)?" +                          // Optional port
        "(?:/?|[/?]\\S+)$",                     // Path
        Pattern.CASE_INSENSITIVE
    );
    
    // Maximum URL length (common browser limit)
    private static final int MAX_URL_LENGTH = 2048;
    
    /**
     * Validate if a URL is properly formatted and accessible.
     * @param url The URL to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        
        // Check length
        if (url.length() > MAX_URL_LENGTH) {
            return false;
        }
        
        // Basic pattern check
        if (!URL_PATTERN.matcher(url).matches()) {
            return false;
        }
        
        // Try to create URL object for additional validation
        try {
            new URL(url);
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }
    
    /**
     * Sanitize a URL by adding protocol if missing and trimming whitespace.
     * @param url The URL to sanitize
     * @return Sanitized URL
     */
    public static String sanitizeUrl(String url) {
        if (url == null) {
            return null;
        }
        
        url = url.trim();
        
        // Add https:// if no protocol specified
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        
        return url;
    }
    
    /**
     * Extract domain from URL.
     * @param url The URL to extract domain from
     * @return Domain name or null if invalid
     */
    public static String extractDomain(String url) {
        try {
            URL urlObj = new URL(url);
            return urlObj.getHost();
        } catch (MalformedURLException e) {
            return null;
        }
    }
    
    /**
     * Check if URL is from a safe domain (basic security check).
     * @param url The URL to check
     * @return true if considered safe, false otherwise
     */
    public static boolean isSafeDomain(String url) {
        String domain = extractDomain(url);
        if (domain == null) {
            return false;
        }
        
        domain = domain.toLowerCase();
        
        // Block known malicious patterns (this is a basic example)
        String[] blockedPatterns = {
            "malware",
            "phishing",
            "spam",
            "suspicious"
        };
        
        for (String pattern : blockedPatterns) {
            if (domain.contains(pattern)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Validate custom alias format.
     * @param alias The custom alias to validate
     * @return true if valid format, false otherwise
     */
    public static boolean isValidCustomAlias(String alias) {
        if (alias == null || alias.trim().isEmpty()) {
            return false;
        }
        
        alias = alias.trim();
        
        // Check length
        if (alias.length() < 3 || alias.length() > 50) {
            return false;
        }
        
        // Check characters (alphanumeric, hyphens, underscores only)
        return alias.matches("^[a-zA-Z0-9_-]+$");
    }
    
    /**
     * Check if alias is reserved (system reserved words).
     * @param alias The alias to check
     * @return true if reserved, false otherwise
     */
    public static boolean isReservedAlias(String alias) {
        if (alias == null) {
            return false;
        }
        
        String[] reservedWords = {
            "api", "admin", "www", "mail", "ftp", "localhost",
            "help", "support", "about", "contact", "terms",
            "privacy", "login", "register", "dashboard"
        };
        
        String lowerAlias = alias.toLowerCase();
        for (String reserved : reservedWords) {
            if (lowerAlias.equals(reserved)) {
                return true;
            }
        }
        
        return false;
    }
}