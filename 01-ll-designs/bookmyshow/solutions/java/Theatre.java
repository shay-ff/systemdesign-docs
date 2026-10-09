import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A theatre (multiplex) in a city - owns screens. Shows are scheduled per
 * screen by the admin; the theatre itself only maintains the screen catalog
 * so {@code SearchService} can enumerate screens.
 */
public class Theatre {
    private final String theatreId;
    private final String name;
    private volatile String cityName;
    private final Map<String, Screen> screensById = new LinkedHashMap<>();

    public Theatre(String theatreId, String name) {
        if (theatreId == null || theatreId.trim().isEmpty()) {
            throw new IllegalArgumentException("Theatre id cannot be null/empty");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Theatre name cannot be null/empty (theatre " + theatreId + ")");
        }
        this.theatreId = theatreId;
        this.name = name;
    }

    public Screen addScreen(String screenId, String screenName) {
        if (screensById.containsKey(screenId)) {
            throw new IllegalArgumentException("Screen " + screenId
                + " already exists in theatre " + name);
        }
        Screen screen = new Screen(screenId, screenName);
        screensById.put(screenId, screen);
        return screen;
    }

    public Screen getScreen(String screenId) {
        Screen screen = screensById.get(screenId);
        if (screen == null) {
            throw new IllegalArgumentException("Screen " + screenId + " not found in theatre " + name);
        }
        return screen;
    }

    public List<Screen> getScreens() {
        return new ArrayList<>(screensById.values());
    }

    public Map<String, Screen> getScreensById() {
        return Collections.unmodifiableMap(screensById);
    }

    public String getTheatreId() {
        return theatreId;
    }

    public String getName() {
        return name;
    }

    /**
     * City this theatre sits in. Set when the admin registers the theatre
     * into a city (City.addTheatre calls back) - avoids bidirectional
     * navigation from Show -> Theatre -> City requiring a City registry
     * lookup on every search.
     */
    public String getCityName() {
        return cityName;
    }

    /** Called by {@link City#addTheatre} at registration time. */
    void setCityName(String cityName) {
        if (cityName == null || cityName.trim().isEmpty()) {
            throw new IllegalArgumentException("City name cannot be null/empty (theatre " + name + ")");
        }
        this.cityName = cityName;
    }

    @Override
    public String toString() {
        return name + " (" + screensById.size() + " screens)";
    }
}
