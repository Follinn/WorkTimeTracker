package exception;

/**
 * Сообщает о превышении нормы фактически отработанного времени за период.
 */
public final class WorkNormExceededException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * Создаёт исключение с описанием превышения нормы.
     *
     * @param message описание превышения нормы
     */
    public WorkNormExceededException(String message) {
        super(message);
    }
}
