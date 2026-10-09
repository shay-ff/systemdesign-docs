import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Admin/catalog flow: register cities, theatres, screens (with seat layout),
 * movies, and schedule shows. Scheduling a show automatically fans the show
 * out to SearchService (browsable) and BookingService (seats lockable) -
 * the admin never talks to those services directly.
 *
 * In a real system this is a back-office with permissions; for LLD the point
 * is the wiring: ONE place where a new Show becomes visible to search and
 * bookable by users, atomically in one call.
 */
public class AdminService {
    private final SearchService searchService;
    private final BookingService bookingService;

    public AdminService(SearchService searchService, BookingService bookingService) {
        if (searchService == null) {
            throw new IllegalArgumentException("SearchService cannot be null");
        }
        if (bookingService == null) {
            throw new IllegalArgumentException("BookingService cannot be null");
        }
        this.searchService = searchService;
        this.bookingService = bookingService;
    }

    public City registerCity(String name) {
        City city = new City(name);
        searchService.addCity(city);
        System.out.println("    [admin] registered city " + city);
        return city;
    }

    public Theatre registerTheatre(String cityName, String theatreId, String name) {
        City city = searchService.getCity(cityName);
        Theatre theatre = new Theatre(theatreId, name);
        city.addTheatre(theatre);
        System.out.println("    [admin] theatre " + theatre + " added to " + city.getName());
        return theatre;
    }

    public Screen addScreen(String theatreId, String screenId, String name,
                            String[][] rowLayout) {
        // rowLayout: rows of {rowLabel, fromNumber, toNumber, SeatType.name()}
        Theatre theatre = findTheatre(theatreId);
        Screen screen = theatre.addScreen(screenId, name);
        for (String[] row : rowLayout) {
            SeatType type = SeatType.valueOf(row[3]);
            screen.addRow(row[0], Integer.parseInt(row[1]), Integer.parseInt(row[2]), type);
        }
        System.out.println("    [admin] screen " + screen + " laid out in theatre " + theatre.getName());
        return screen;
    }

    /**
     * Schedules a show - the moment a new show becomes real. Creates the
     * Show (which instantiates one ShowSeat per physical seat), then makes it
     * searchable and lockable. Single entry point = no "searchable but not
     * bookable" windows.
     */
    public Show scheduleShow(String theatreId, String screenId, String showId,
                             Movie movie, LocalDateTime startTime, double priceMultiplier) {
        Theatre theatre = findTheatre(theatreId);
        Screen screen = theatre.getScreen(screenId);
        Show show = new Show(showId, movie, screen, theatre, startTime, priceMultiplier);
        searchService.addShow(show);
        bookingService.registerShow(show);
        System.out.println("    [admin] show " + show + " scheduled and live (searchable + bookable)");
        return show;
    }

    private Theatre findTheatre(String theatreId) {
        if (theatreId == null || theatreId.trim().isEmpty()) {
            throw new IllegalArgumentException("Theatre id cannot be null/empty");
        }
        for (City city : searchService.getCities()) {
            for (Theatre theatre : city.getTheatres()) {
                if (theatre.getTheatreId().equals(theatreId)) {
                    return theatre;
                }
            }
        }
        throw new IllegalArgumentException("Theatre not found: " + theatreId);
    }
}
