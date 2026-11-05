package com.paymentsapp.autopayments.error;

import com.paymentsapp.platform.error.ErrorCode;
import org.springframework.http.HttpStatus;

// Отказы autopayments: неверные данные шаблона, автоплатежа или автопополнения, недоступный сосед.
// Отказ соседа (biller, pricing, payment) приходит его текстом, шаблон здесь — на случай, если текста нет.
public enum AutopaymentsError implements ErrorCode {

    TEMPLATE_NOT_FOUND(HttpStatus.NOT_FOUND, "Шаблон не найден"),
    AUTOPAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Автоплатёж не найден"),
    TOPUP_NOT_FOUND(HttpStatus.NOT_FOUND, "Автопополнение не найдено"),
    PROVIDER_REQUIRED(HttpStatus.BAD_REQUEST, "Не выбран поставщик"),
    PROVIDER_NOT_FOUND(HttpStatus.BAD_REQUEST, "Поставщик не найден"),
    AMOUNT_REQUIRED(HttpStatus.BAD_REQUEST, "Укажите сумму платежа"),
    SOURCE_ACCOUNT_INVALID(HttpStatus.BAD_REQUEST, "Счёт списания не найден — выберите кошелёк или карту нашего банка"),
    BACKUP_SAME_AS_SOURCE(HttpStatus.BAD_REQUEST, "Запасной счёт должен отличаться от основного"),
    BILL_INVALID(HttpStatus.BAD_REQUEST, "Проверьте данные платежа"),
    QUOTE_FAILED(HttpStatus.UNPROCESSABLE_CONTENT, "Не удалось рассчитать комиссию"),
    PAYMENT_REJECTED(HttpStatus.UNPROCESSABLE_CONTENT, "Платёж отклонён"),
    TRANSFER_REJECTED(HttpStatus.UNPROCESSABLE_CONTENT, "Перевод отклонён"),
    TEMPLATE_NAME_LENGTH(HttpStatus.BAD_REQUEST, "Название шаблона — от 1 до %d символов"),
    SCHEDULE_KIND_REQUIRED(HttpStatus.BAD_REQUEST, "Выберите вид автоплатежа: каждый месяц или по периоду"),
    DAY_OF_MONTH_RANGE(HttpStatus.BAD_REQUEST, "Число месяца — от %d до %d"),
    PERIOD_RANGE(HttpStatus.BAD_REQUEST, "Период — от 1 до %d дней"),
    START_DATE_RANGE(HttpStatus.BAD_REQUEST, "Первый платёж — с сегодняшнего дня и не позже %s"),
    STATUS_INVALID(HttpStatus.BAD_REQUEST, "Статус — ACTIVE или PAUSED"),
    DESTINATION_INVALID(HttpStatus.BAD_REQUEST, "Выберите, куда переводить: кошелёк или карту нашего банка"),
    TOPUP_SOURCE_INVALID(HttpStatus.BAD_REQUEST, "Выберите, откуда списать"),
    SBP_BANK_REQUIRED(HttpStatus.BAD_REQUEST, "Выберите банк, из которого пополнять"),
    SBP_BANK_INVALID(HttpStatus.BAD_REQUEST, "Выберите банк"),
    SAME_ACCOUNTS(HttpStatus.BAD_REQUEST, "Счёт списания и счёт зачисления должны отличаться"),
    TOPUP_KIND_REQUIRED(HttpStatus.BAD_REQUEST, "Выберите тип автопополнения"),
    TOPUP_AMOUNT_MIN(HttpStatus.BAD_REQUEST, "Сумма пополнения — от %s"),
    THRESHOLD_REQUIRED(HttpStatus.BAD_REQUEST, "Укажите баланс, при котором пополнять"),
    TARGET_BALANCE_MIN(HttpStatus.BAD_REQUEST, "Баланс, который поддерживать, — от %s"),
    MONTHLY_LIMIT_MIN(HttpStatus.BAD_REQUEST, "Лимит в месяц — от %s"),
    MONTHLY_LIMIT_BELOW_AMOUNT(HttpStatus.BAD_REQUEST, "Лимит в месяц не может быть меньше суммы пополнения"),
    TOPUP_NOT_AVAILABLE(HttpStatus.UNPROCESSABLE_CONTENT, "Такое пополнение недоступно"),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Сервис временно недоступен, попробуйте позже");

    private final HttpStatus status;
    private final String template;

    AutopaymentsError(HttpStatus status, String template) {
        this.status = status;
        this.template = template;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String template() {
        return template;
    }
}
