package com.paymentsapp.autopayments.client;

// Вид операции в pricing и тип платежа в payment: автоплатёж — оплата поставщику, автопополнение — между своими счетами
public enum OperationType {
    BILL,
    OWN_TRANSFER
}
