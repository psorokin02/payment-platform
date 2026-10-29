package school.sorokin.payment.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import school.sorokin.commonlibs.antifraud.AntifraudCheckResponse;
import school.sorokin.commonlibs.events.PaymentEvent;
import school.sorokin.commonlibs.payments.PaymentStatus;
import school.sorokin.payment.admin.PaymentSettings;
import school.sorokin.payment.api.dto.CreatePaymentRequest;
import school.sorokin.payment.api.dto.PaymentDto;
import school.sorokin.payment.cache.PaymentCache;
import school.sorokin.payment.client.AntifraudClient;
import school.sorokin.payment.domain.db.CustomerEntity;
import school.sorokin.payment.domain.db.CustomerRepository;
import school.sorokin.payment.domain.db.PaymentEntity;
import school.sorokin.payment.domain.db.PaymentRepository;
import school.sorokin.payment.kafka.PaymentEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Сценарии платежа: HTTP-проверка, запись в БД, обновление кэша и отправка события. */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("10000000");

    private final PaymentRepository paymentRepository;
    private final PaymentEntityMapper mapper;
    private final CustomerRepository customerRepository;
    private final AntifraudClient antifraudClient;
    private final PaymentSettings settings;
    private final PaymentEventPublisher eventPublisher;
    private final PaymentCache paymentCache;
    private final TransactionTemplate transactions;

    public PaymentService(
            PaymentRepository paymentRepository,
            PaymentEntityMapper mapper,
            CustomerRepository customerRepository,
            AntifraudClient antifraudClient,
            PaymentSettings settings,
            PaymentEventPublisher eventPublisher,
            PaymentCache paymentCache,
            PlatformTransactionManager transactionManager
    ) {
        this.paymentRepository = paymentRepository;
        this.mapper = mapper;
        this.customerRepository = customerRepository;
        this.antifraudClient = antifraudClient;
        this.settings = settings;
        this.eventPublisher = eventPublisher;
        this.paymentCache = paymentCache;
        this.transactions = new TransactionTemplate(transactionManager);
        // Запись владеет своей транзакцией: execute вернётся только после её commit.
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public PaymentDto createPayment(CreatePaymentRequest request) {
        validateAmount(request.amount());
        log.info("Payment create requested customerId={} amount={} paymentType={}",
                request.customerId(), request.amount(), request.paymentType());

        // Сетевой вызов выполняется до открытия транзакции PostgreSQL.
        AntifraudCheckResponse decision = antifraudClient.checkPayment(request.amount(), request.paymentType());
        PaymentDto result = transactions.execute(status -> savePayment(request, decision));

        log.info("Payment saved paymentId={} status={} reason={} checkId={}",
                result.id(), result.status(), result.rejectionReason(), result.antifraudRequestId());
        sendKafkaEvent(result, decision.approved() ? "PaymentCreated" : "PaymentRejected", result.createdAt());
        return result;
    }

    public PaymentDto confirmPayment(long id) {
        PaymentDto result = transactions.execute(status -> {
            PaymentEntity payment = findPaymentOrThrow(id);
            payment.confirm();
            return mapper.toDto(payment);
        });

        log.info("Payment confirmed paymentId={}", id);
        paymentCache.invalidatePaymentCache(id);
        sendKafkaEvent(result, "PaymentConfirmed", Instant.now());
        return result;
    }

    @Transactional(readOnly = true)
    public List<PaymentDto> getPayments() {
        log.info("Reading payments fetchMode={}", settings.getFetchMode());

        if ("nplus1".equals(settings.getFetchMode())) {
            List<PaymentEntity> payments = paymentRepository.findAllByOrderByIdDesc();
            return payments.stream().map(mapper::toDto).toList(); // избегаем lazyInit
        }

        List<PaymentEntity> payments = paymentRepository.findLatestWithCustomers();
        return payments.stream().map(mapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public PaymentDto getPayment(long id) {
        return mapper.toDto(findPaymentOrThrow(id));
    }

    private PaymentDto savePayment(CreatePaymentRequest request, AntifraudCheckResponse decision) {
        CustomerEntity customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer not found"));
        PaymentEntity payment = buildPaymentEntity(request, decision, customer);
        return mapper.toDto(paymentRepository.save(payment));
    }

    private void sendKafkaEvent(PaymentDto payment, String type, Instant occurredAt) {
        eventPublisher.publishEvent(new PaymentEvent(
                UUID.randomUUID().toString(),
                payment.id(),
                payment.amount(),
                payment.customer(),
                type,
                occurredAt,
                payment.paymentType(),
                payment.status(),
                payment.rejectionReason()
        ));
    }

    private PaymentEntity buildPaymentEntity(
            CreatePaymentRequest request,
            AntifraudCheckResponse decision,
            CustomerEntity customer
    ) {
        String description = request.description() == null ? "Оплата заказа" : request.description();
        if (description.length() > 160) {
            description = description.substring(0, 160);
        }
        return new PaymentEntity(
                customer,
                request.amount(),
                description,
                decision.approved() ? PaymentStatus.NEW : PaymentStatus.REJECTED,
                request.paymentType(),
                decision.approved() ? null : decision.reason(),
                decision.requestId()
        );
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0
                || amount.scale() > 2
                || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Сумма должна быть положительной, до 10 млн, с точностью до копеек"
            );
        }
    }

    private PaymentEntity findPaymentOrThrow(long id) {
        return paymentRepository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Платёж " + id + " не найден"));
    }
}
