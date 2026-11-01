package jobtrack;

public enum Stage {
    SAVED,
    APPLIED,
    INTERVIEW,
    OFFER,
    ACCEPTED,
    REJECTED,
    WITHDRAWN;

    public boolean canMoveTo(Stage next) {
        return switch (this) {
            case SAVED -> next == APPLIED || next == WITHDRAWN;
            case APPLIED -> next == INTERVIEW || next == REJECTED || next == WITHDRAWN;
            case INTERVIEW -> next == OFFER || next == REJECTED || next == WITHDRAWN;
            case OFFER -> next == ACCEPTED || next == REJECTED || next == WITHDRAWN;
            case ACCEPTED, REJECTED, WITHDRAWN -> false;
        };
    }
}
