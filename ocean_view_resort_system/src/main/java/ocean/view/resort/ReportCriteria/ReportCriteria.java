package ocean.view.resort.ReportCriteria;

import java.time.LocalDate;

public class ReportCriteria {

    private LocalDate dateFrom;
    private LocalDate dateTo;
    private String statusFilter;  // CONFIRMED, CHECKED_OUT, CANCELLED, or null = all
    private String roomTypeFilter;
    private int maxRows = 10000;

    public LocalDate getDateFrom() { return dateFrom; }
    public LocalDate getDateTo() { return dateTo; }
    public String getStatusFilter() { return statusFilter; }
    public String getRoomTypeFilter() { return roomTypeFilter; }
    public int getMaxRows() { return maxRows; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final ReportCriteria criteria = new ReportCriteria();

        public Builder dateFrom(LocalDate from) { criteria.dateFrom = from; return this; }
        public Builder dateTo(LocalDate to) { criteria.dateTo = to; return this; }
        public Builder status(String status) { criteria.statusFilter = status; return this; }
        public Builder roomType(String type) { criteria.roomTypeFilter = type; return this; }
        public Builder maxRows(int n) { criteria.maxRows = n; return this; }

        public ReportCriteria build() { return criteria; }
    }
}