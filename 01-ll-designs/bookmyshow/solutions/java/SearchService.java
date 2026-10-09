import java.util.ArrayList;
import java.util.List;

/**
 * Browses the catalog: shows by city, optionally filtered by movie and a
 * time-of-day window.
 *
 * Deliberately a READ-ONLY service on plain in-memory lists - O(shows) per
 * query is the right interview answer at this scale; the scale-up story
 * (city index, movie index, cached seat maps) is in explanation.md.
 *
 * Lives beside (not inside) BookingService: search reads the catalog while
 * booking mutates seat rows - separate concerns, separately evolvable.
 */
public class SearchService {
    private final List<Show> shows = new ArrayList<>();
    private final List<City> cities = new ArrayList<>();

    public void addCity(City city) {
        if (city == null) {
            throw new IllegalArgumentException("City cannot be null");
        }
        for (City existing : cities) {
            if (existing.getName().equalsIgnoreCase(city.getName())) {
                throw new IllegalArgumentException("City " + city.getName() + " already registered");
            }
        }
        cities.add(city);
    }

    /** Registers a show for search. Called by the admin flow. */
    public void addShow(Show show) {
        if (show == null) {
            throw new IllegalArgumentException("Show cannot be null");
        }
        for (Show existing : shows) {
            if (existing.getShowId().equals(show.getShowId())) {
                throw new IllegalArgumentException("Show " + show.getShowId() + " already registered");
            }
        }
        shows.add(show);
    }

    public List<Show> getShows() {
        return new ArrayList<>(shows);
    }

    public List<City> getCities() {
        return new ArrayList<>(cities);
    }

    public City getCity(String name) {
        for (City city : cities) {
            if (city.getName().equalsIgnoreCase(name)) {
                return city;
            }
        }
        throw new IllegalArgumentException("City not found: " + name);
    }

    /**
     * Find shows by city (+ optional movie title / time window). Linear scan
     * is intentional and stated: at interview scale this is defensible; the
     * production answer is indexes (see explanation.md).
     */
    public List<Show> findShows(String cityName, String movieTitle, Integer afterHour,
                                Integer beforeHour) {
        if (cityName == null || cityName.trim().isEmpty()) {
            throw new IllegalArgumentException("City name cannot be null/empty");
        }
        if (afterHour != null && beforeHour != null && afterHour > beforeHour) {
            throw new IllegalArgumentException("Time window invalid: after " + afterHour
                + "h but before " + beforeHour + "h");
        }
        List<Show> result = new ArrayList<>();
        for (Show show : shows) {
            if (!cityName.equalsIgnoreCase(show.getTheatre().getCityName())) {
                continue;
            }
            if (movieTitle != null && !movieTitle.trim().isEmpty()
                && !show.getMovie().getTitle().toLowerCase()
                    .contains(movieTitle.trim().toLowerCase())) {
                continue;
            }
            int hour = show.getStartTime().getHour();
            if (afterHour != null && hour < afterHour) {
                continue;
            }
            if (beforeHour != null && hour >= beforeHour) {
                continue;
            }
            result.add(show);
        }
        // Stable show order: by start time then id - readable demo output.
        result.sort((a, b) -> {
            int byTime = a.getStartTime().compareTo(b.getStartTime());
            return byTime != 0 ? byTime : a.getShowId().compareTo(b.getShowId());
        });
        return result;
    }
}
