import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Base62 encoder for generating short codes from integers and strings.
 */
public class Base62Encoder {
    private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = ALPHABET.length();
    
    /**
     * Encode an integer to base62 string.
     * @param num The number to encode
     * @return Base62 encoded string
     */
    public static String encode(long num) {
        if (num == 0) {
            return String.valueOf(ALPHABET.charAt(0));
        }
        
        StringBuilder result = new StringBuilder();
        while (num > 0) {
            result.append(ALPHABET.charAt((int)(num % BASE)));
            num /= BASE;
        }
        
        return result.reverse().toString();
    }
    
    /**
     * Decode a base62 string to integer.
     * @param encoded The base62 string to decode
     * @return Decoded integer
     * @throws IllegalArgumentException if string contains invalid characters
     */
    public static long decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            throw new IllegalArgumentException("Encoded string cannot be null or empty");
        }
        
        long num = 0;
        for (char c : encoded.toCharArray()) {
            int index = ALPHABET.indexOf(c);
            if (index == -1) {
                throw new IllegalArgumentException("Invalid character in base62 string: " + c);
            }
            num = num * BASE + index;
        }
        
        return num;
    }
    
    /**
     * Generate a base62 code from a string using MD5 hash.
     * @param text The input string
     * @param length Desired length of the result
     * @return Base62 encoded string of specified length
     */
    public static String generateFromString(String text, int length) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(text.getBytes());
            
            // Convert first 8 bytes to long
            long hashValue = 0;
            for (int i = 0; i < Math.min(8, hash.length); i++) {
                hashValue = (hashValue << 8) | (hash[i] & 0xFF);
            }
            
            // Make sure it's positive
            hashValue = Math.abs(hashValue);
            
            String encoded = encode(hashValue);
            
            // Pad or truncate to desired length
            if (encoded.length() < length) {
                return String.format("%-" + length + "s", encoded).replace(' ', '0');
            } else {
                return encoded.substring(0, length);
            }
            
        } catch (NoSuchAlgorithmException e) {
            // Fallback to simple hash
            int hashCode = Math.abs(text.hashCode());
            String encoded = encode(hashCode);
            
            if (encoded.length() < length) {
                return String.format("%-" + length + "s", encoded).replace(' ', '0');
            } else {
                return encoded.substring(0, length);
            }
        }
    }
    
    /**
     * Check if a string is valid base62.
     * @param str String to check
     * @return true if valid base62, false otherwise
     */
    public static boolean isValidBase62(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        
        for (char c : str.toCharArray()) {
            if (ALPHABET.indexOf(c) == -1) {
                return false;
            }
        }
        
        return true;
    }
}