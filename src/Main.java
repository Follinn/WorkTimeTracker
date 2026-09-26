import model.Employee;
import model.Shift;
import model.TimeSegment;
import service.ShiftSplitter;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * Демонстрирует разбиение смены на оплачиваемые временные отрезки.
 */
public class Main {

    /**
     * Создаёт экземпляр класса запуска приложения.
     */
    public Main() {
    }

    /**
     * Запускает пример расчёта и выводит полученные отрезки в консоль.
     *
     * @param args аргументы командной строки; в примере не используются
     */
    public static void main(String[] args) {
        ZoneId zone = ZoneId.of("Europe/Moscow");
        LocalDate date = LocalDate.of(2026, 9, 25);

        Employee employee = new Employee(
                1, "Константин", 30_000
        );

        Shift first = new Shift(
                employee,
                ZonedDateTime.of(date, java.time.LocalTime.of(9, 0), zone),
                ZonedDateTime.of(date, java.time.LocalTime.of(13, 0), zone)
        );

        Shift second = new Shift(
                employee,
                ZonedDateTime.of(date, java.time.LocalTime.of(18, 0), zone),
                ZonedDateTime.of(date, java.time.LocalTime.of(23, 0), zone)
        );

        ShiftSplitter splitter = new ShiftSplitter(
                zone,
                Set.of(),
                Duration.ofHours(8)
        );

        List<TimeSegment> segments = splitter.split(
                List.of(first, second)
        );

        DateTimeFormatter format = DateTimeFormatter.ofPattern("HH:mm");

        for (TimeSegment segment : segments) {
            System.out.printf(
                    "%s–%s | %s | %d мин.%n",
                    segment.getStart().format(format),
                    segment.getEnd().format(format),
                    segment.getType(),
                    segment.getDuration().toMinutes()
            );
        }
    }
}
