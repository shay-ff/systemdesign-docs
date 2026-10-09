/**
 * A movie in the catalog. Immutable identity + metadata; nothing here ever
 * changes mid-show, so no thread-safety concerns.
 */
public class Movie {
    private final String movieId;
    private final String title;
    private final String language;
    private final int durationMinutes;
    private final String genre;

    public Movie(String movieId, String title, String language, int durationMinutes, String genre) {
        if (movieId == null || movieId.trim().isEmpty()) {
            throw new IllegalArgumentException("Movie id cannot be null/empty");
        }
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Movie title cannot be null/empty (movie " + movieId + ")");
        }
        if (language == null || language.trim().isEmpty()) {
            throw new IllegalArgumentException("Movie language cannot be null/empty (movie " + movieId + ")");
        }
        if (durationMinutes < 1) {
            throw new IllegalArgumentException("Movie duration must be >= 1 minute (got " + durationMinutes + ")");
        }
        if (genre == null || genre.trim().isEmpty()) {
            throw new IllegalArgumentException("Movie genre cannot be null/empty (movie " + movieId + ")");
        }
        this.movieId = movieId;
        this.title = title;
        this.language = language;
        this.durationMinutes = durationMinutes;
        this.genre = genre;
    }

    public String getMovieId() {
        return movieId;
    }

    public String getTitle() {
        return title;
    }

    public String getLanguage() {
        return language;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public String getGenre() {
        return genre;
    }

    @Override
    public String toString() {
        return title + " (" + language + ", " + genre + ", " + durationMinutes + "m)";
    }
}
