package school.sorokin.antifraud.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import school.sorokin.antifraud.admin.AntifraudSettings;
import school.sorokin.antifraud.domain.db.AntifraudCheckEntity;
import school.sorokin.antifraud.domain.db.AntifraudCheckRepository;
import school.sorokin.commonlibs.antifraud.AntifraudCheckDto;
import school.sorokin.commonlibs.antifraud.AntifraudCheckRequest;
import school.sorokin.commonlibs.antifraud.AntifraudCheckResponse;
import school.sorokin.commonlibs.antifraud.AntifraudReason;
import school.sorokin.commonlibs.payments.PaymentType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Возвращает и сохраняет бизнес-решение. Имитация HTTP-сбоев находится в контроллере.
 */
@Service
public class AntifraudService {

    private static final Logger log = LoggerFactory.getLogger(AntifraudService.class);
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("10000000");

    private final AntifraudSettings runtimeState;
    private final AntifraudCheckRepository checks;
    // Всё, что выше лимита, получает бизнес-отказ без HTTP 5xx.
    private final BigDecimal riskLimit;

    public AntifraudService(
            AntifraudSettings runtimeState,
            @Value("${app.simulation.risk-limit}") BigDecimal riskLimit,
            AntifraudCheckRepository checks
    ) {
        this.runtimeState = runtimeState;
        this.riskLimit = riskLimit;
        this.checks = checks;
    }

    public AntifraudCheckDto getCheck(String requestId) {
        return checks.findByRequestId(requestId)
                .map(this::toDto)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Проверка не найдена")
                );
    }

    public AntifraudCheckResponse checkPayment(AntifraudCheckRequest request) {
        validateAmount(request.amount());
        if (request.requestId() != null) {
            var previous = checks.findByRequestId(request.requestId());
            if (previous.isPresent()) {
                return savedResponse(request, toDto(previous.get()));
            }
        }

        String requestId = (request.requestId() == null || request.requestId().isBlank())
                ? UUID.randomUUID().toString()
                : request.requestId();

        if ("reject".equals(runtimeState.getMode())) {
            return saveDecision(request, requestId, AntifraudReason.FORCED_REJECTION);
        }

        boolean limitedPaymentType = request.paymentType() == PaymentType.CARD
                || request.paymentType() == PaymentType.QR;

        if (limitedPaymentType && request.amount().compareTo(riskLimit) > 0) {
            return saveDecision(request, requestId, AntifraudReason.RISK_LIMIT);
        }

        return saveDecision(request, requestId, AntifraudReason.APPROVED);
    }

    private AntifraudCheckResponse saveDecision(
            AntifraudCheckRequest request,
            String requestId,
            AntifraudReason reason
    ) {
        checks.insertIfAbsent(
                requestId,
                request.amount(),
                request.paymentType().name(),
                reason == AntifraudReason.APPROVED,
                reason.name(),
                Instant.now()
        );
        // Конкурентный retry мог сохранить решение первым: возвращаем именно его
        return savedResponse(request, getCheck(requestId));
    }

    private AntifraudCheckResponse savedResponse(
            AntifraudCheckRequest request,
            AntifraudCheckDto saved
    ) {
        if (saved.amount().compareTo(request.amount()) != 0 || saved.paymentType() != request.paymentType()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "requestId уже относится к другой проверке");
        }
        log.info("Antifraud saved check requestId={} approved={} reason={}",
                saved.requestId(), saved.approved(), saved.reason());
        return new AntifraudCheckResponse(
                saved.approved(),
                saved.reason(),
                saved.requestId()
        );
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null
                || amount.signum() <= 0
                || amount.scale() > 2
                || amount.compareTo(MAX_AMOUNT) > 0
        ) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid amount");
        }
    }

    private AntifraudCheckDto toDto(AntifraudCheckEntity entity) {
        return new AntifraudCheckDto(
                entity.getRequestId(), entity.getAmount(), entity.getPaymentType(),
                entity.isApproved(), entity.getReason(), entity.getCheckedAt()
        );
    }
}
