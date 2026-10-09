/**
 * Immutable value object for a floor number. Why a class and not a bare int?
 *
 * 1. Validation lives in ONE place. Every path that constructs a Floor is
 *    guaranteed to hold a floor in [1, maxFloors]; no service ever re-checks
 *    "is floor 13 real in this building?".
 * 2. The building context travels WITH the number (a Floor of 15 is meaningless
 *    without knowing the building has 15 floors — now that is impossible to
 *    construct by accident).
 * 3. equals/hashCode make it a proper value object: Floor(3, 10) == Floor(3, 10).
 */
public final class Floor {

    private final int number;
    private final int maxFloors;

    public Floor(int number, int maxFloors) {
        if (maxFloors < 1) {
            throw new IllegalArgumentException(
                    "A building needs at least 1 floor, got maxFloors=" + maxFloors);
        }
        if (number < 1 || number > maxFloors) {
            throw new IllegalArgumentException(
                    "Floor " + number + " is outside the building (valid: 1.." + maxFloors + ")");
        }
        this.number = number;
        this.maxFloors = maxFloors;
    }

    public int getNumber() {
        return number;
    }

    public Floor above() {
        return new Floor(number + 1, maxFloors);
    }

    public Floor below() {
        return new Floor(number - 1, maxFloors);
    }

    public boolean isTop() {
        return number == maxFloors;
    }

    public boolean isGround() {
        return number == 1;
    }

    /** Signed distance: positive = target is above us. */
    public int distanceTo(Floor target) {
        return target.number - this.number;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Floor)) {
            return false;
        }
        Floor other = (Floor) o;
        return this.number == other.number && this.maxFloors == other.maxFloors;
    }

    @Override
    public int hashCode() {
        return 31 * number + maxFloors;
    }

    @Override
    public String toString() {
        return String.valueOf(number);
    }
}
