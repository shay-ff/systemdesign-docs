import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A city with its theatres - the search root ("shows in Bengaluru").
 *
 * The admin registers theatres into a city; {@code SearchService} then filters
 * shows by theatre's city. Simple aggregation; no behaviour beyond lookup.
 */
public class City {
    private final String name;
    private final List<Theatre> theatres = new ArrayList<>();

    public City(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("City name cannot be null/empty");
        }
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void addTheatre(Theatre theatre) {
        if (theatre == null) {
            throw new IllegalArgumentException("Theatre cannot be null (city " + name + ")");
        }
        if (theatres.contains(theatre)) {
            throw new IllegalArgumentException(
                "Theatre '" + theatre.getName() + "' already exists in city " + name);
        }
        theatres.add(theatre);
        theatre.setCityName(name);
    }

    public List<Theatre> getTheatres() {
        return Collections.unmodifiableList(theatres);
    }

    @Override
    public String toString() {
        return name + " (" + theatres.size() + " theatres)";
    }
}
