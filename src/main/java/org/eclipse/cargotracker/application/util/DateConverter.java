package org.eclipse.cargotracker.application.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Date converter utility that uses UTC timezone standardization for cloud-native
 * time handling across distributed environments. Replaces ZoneId.systemDefault()
 * with ZoneOffset.UTC to ensure consistent time handling across all cloud instances.
 */
// TODO [Clean Code] Make this a CDI singleton?
public class DateConverter {
  public static final String DATE_FORMAT = "M/d/yyyy";
  public static final String DATE_TIME_FORMAT = "M/d/yyyy h:m a";

  // Use UTC for all date/time formatting to ensure consistency across distributed cloud instances
  private static final DateTimeFormatter DATE_FORMATTER =
      DateTimeFormatter.ofPattern(DATE_FORMAT).withZone(ZoneOffset.UTC);

  private static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern(DATE_TIME_FORMAT).withZone(ZoneOffset.UTC);

  private DateConverter() {}

  public static LocalDate toDate(String date) {
    return LocalDate.parse(date, DATE_FORMATTER);
  }

  public static LocalDateTime toDateTime(String datetime) {
    return LocalDateTime.parse(datetime, DATE_TIME_FORMATTER);
  }

  public static String toString(LocalDateTime dateTime) {
    return dateTime.format(DATE_TIME_FORMATTER);
  }

  public static String toString(LocalDate date) {
    return date.format(DATE_FORMATTER);
  }
}
