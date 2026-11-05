package com.paymentsapp.autopayments.topup;

// MONTHLY — каждый месяц в число на amount; BY_BALANCE — когда баланс <= threshold, на amount;
// CONSTANT_BALANCE — когда баланс < targetBalance, на разницу до него
public enum TopupKind {
    MONTHLY,
    BY_BALANCE,
    CONSTANT_BALANCE
}
