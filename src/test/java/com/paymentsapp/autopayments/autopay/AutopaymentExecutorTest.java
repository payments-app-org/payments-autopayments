package com.paymentsapp.autopayments.autopay;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.paymentsapp.autopayments.autopay.Autopayment.Run;
import com.paymentsapp.autopayments.client.PaymentClient;
import com.paymentsapp.autopayments.client.PaymentOutcome;
import com.paymentsapp.autopayments.client.PricingClient;
import com.paymentsapp.autopayments.client.Quote;
import com.paymentsapp.autopayments.common.RunStatus;
import com.paymentsapp.autopayments.common.Status;
import com.paymentsapp.autopayments.error.AutopaymentsError;
import com.paymentsapp.autopayments.template.Template.ProviderView;
import com.paymentsapp.platform.error.DomainException;
import com.paymentsapp.platform.resilience.ServiceUnavailableException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AutopaymentExecutorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);
    private static final LocalDate NEXT_MONTH = LocalDate.of(2026, 12, 5);
    private static final String PROVIDER = "yota";
    private static final BigDecimal AMOUNT = new BigDecimal("300");

    private final AutopaymentRepository repo = mock(AutopaymentRepository.class);
    private final PricingClient pricing = mock(PricingClient.class);
    private final PaymentClient payments = mock(PaymentClient.class);
    private final AutopaymentExecutor executor = new AutopaymentExecutor(repo, pricing, payments);

    private final UUID main = UUID.randomUUID();
    private final UUID backup = UUID.randomUUID();
    private final UUID paymentId = UUID.randomUUID();
    private Autopayment autopayment;
    private Run run;

    @BeforeEach
    void setUp() {
        autopayment = new Autopayment(UUID.randomUUID(), UUID.randomUUID(), "Yota", new ProviderView(PROVIDER, "MOBILE",
                "Yota", null, "#00AEEF", null), Map.of("phone", "9990000000"), "+7 999 000-00-00", AMOUNT, main, backup,
                ScheduleKind.MONTHLY, 5, null, TODAY, TODAY, Status.ACTIVE, null, null, null);
        run = new Run(UUID.randomUUID(), TODAY, RunStatus.PENDING, null, null, AMOUNT, null, null, null);
        when(repo.get(autopayment.id())).thenReturn(Optional.of(autopayment));
        when(repo.claim(autopayment, TODAY)).thenReturn(run);
        when(repo.quoteToken(any(), any())).thenReturn(Optional.empty());
        when(pricing.billQuote(any(), any(), eq(PROVIDER), eq(AMOUNT))).thenReturn(new Quote(BigDecimal.ZERO, AMOUNT, "token"));
    }

    private void payFrom(UUID account, PaymentOutcome outcome) {
        when(payments.payBill(any(), eq("auto:" + run.id() + ":" + account), eq(account), eq(PROVIDER), any(), eq(AMOUNT),
                eq("token"))).thenReturn(outcome);
    }

    @Test
    void paysFromMainAccount() {
        payFrom(main, PaymentOutcome.success(paymentId, BigDecimal.ZERO));

        executor.run(autopayment.id(), TODAY);

        verify(repo).finish(autopayment, run, RunStatus.SUCCESS, paymentId, BigDecimal.ZERO, "Оплачено", NEXT_MONTH);
    }

    @Test
    void notEnoughMoneyOnMainPaysFromBackup() {
        payFrom(main, PaymentOutcome.notEnoughMoney(UUID.randomUUID(), "Недостаточно средств"));
        payFrom(backup, PaymentOutcome.success(paymentId, BigDecimal.ZERO));

        executor.run(autopayment.id(), TODAY);

        verify(repo).attempt(run.id(), backup, "token");
        verify(repo).finish(autopayment, run, RunStatus.SUCCESS, paymentId, BigDecimal.ZERO, "Оплачено с запасного счёта",
                NEXT_MONTH);
    }

    @Test
    void rejectionFailsRunWithPaymentText() {
        payFrom(main, PaymentOutcome.rejected("Превышен лимит"));

        executor.run(autopayment.id(), TODAY);

        verify(repo).finish(autopayment, run, RunStatus.FAILED, null, null, "Превышен лимит", NEXT_MONTH);
    }

    @Test
    void quoteRejectionFailsRun() {
        when(pricing.billQuote(any(), any(), eq(PROVIDER), eq(AMOUNT)))
                .thenThrow(DomainException.withMessage(AutopaymentsError.QUOTE_FAILED, "Такой перевод недоступен"));

        executor.run(autopayment.id(), TODAY);

        verify(repo).finish(autopayment, run, RunStatus.FAILED, null, null, "Такой перевод недоступен", NEXT_MONTH);
    }

    @Test
    void outageLeavesRunPendingForRetry() {
        when(payments.payBill(any(), anyString(), any(), any(), any(), any(), any()))
                .thenThrow(new ServiceUnavailableException("payment", new RuntimeException("open")));

        executor.run(autopayment.id(), TODAY);

        verify(repo, never()).finish(any(), any(), any(), any(), any(), any(), any());
        verify(repo).attempt(run.id(), main, "token");
    }

    @Test
    void finishedRunIsNotRepeated() {
        when(repo.claim(autopayment, TODAY)).thenReturn(new Run(run.id(), TODAY, RunStatus.SUCCESS, paymentId, main, AMOUNT,
                null, null, null));

        executor.run(autopayment.id(), TODAY);

        verifyNoInteractions(payments, pricing);
    }
}
