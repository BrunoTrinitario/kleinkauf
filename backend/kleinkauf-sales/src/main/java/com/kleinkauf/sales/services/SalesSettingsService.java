package com.kleinkauf.sales.services;

import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.sales.constants.ErrorMessages;
import com.kleinkauf.sales.dto.PaymentDetailsDto;
import com.kleinkauf.sales.dto.SalesSettingsDto;
import com.kleinkauf.sales.entities.SalesSettings;
import com.kleinkauf.sales.repositories.SalesSettingsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/*
Configuracion de ventas de la tienda (datos para transferir y plazo de vencimiento).
Mientras no se configure, se usan los valores por defecto y no se pueden crear pedidos.
 */
@Service
public class SalesSettingsService {

    static final int MAX_EXPIRATION_HOURS = 720;
    private static final Pattern ALIAS = Pattern.compile("^[A-Za-z0-9.-]{6,20}$");
    private static final Pattern CBU = Pattern.compile("^\\d{22}$");

    @Autowired
    private SalesSettingsRepository repository;

    public SalesSettings current() {
        return repository.findFirstByOrderByIdAsc().orElseGet(SalesSettings::new);
    }

    public SalesSettingsDto get() {
        return toDto(current());
    }

    public SalesSettingsDto update(SalesSettingsDto dto) throws ApiException {
        String alias = blankToNull(dto.alias());
        String cbu = blankToNull(dto.cbu());
        String holder = blankToNull(dto.holder());
        String bank = blankToNull(dto.bank());
        int hours = dto.expirationHours() == null ? SalesSettings.DEFAULT_EXPIRATION_HOURS : dto.expirationHours();
        if ((alias != null && !ALIAS.matcher(alias).matches())
                || (cbu != null && !CBU.matcher(cbu).matches())
                || (holder != null && holder.length() > 100)
                || (bank != null && bank.length() > 100)
                || hours < 1 || hours > MAX_EXPIRATION_HOURS) {
            throw new ApiException(ErrorMessages.INVALID_SETTINGS, HttpStatus.BAD_REQUEST);
        }

        SalesSettings settings = current();
        settings.setAlias(alias);
        settings.setCbu(cbu);
        settings.setHolder(holder);
        settings.setBank(bank);
        settings.setExpirationHours(hours);
        settings.setUpdatedAt(LocalDateTime.now());
        return toDto(repository.save(settings));
    }

    // Para recibir transferencias hace falta el titular y el alias o el CBU
    public static boolean isPaymentConfigured(SalesSettings settings) {
        return settings.getHolder() != null && (settings.getAlias() != null || settings.getCbu() != null);
    }

    public static PaymentDetailsDto paymentDetails(SalesSettings settings) {
        return new PaymentDetailsDto(settings.getAlias(), settings.getCbu(), settings.getHolder(), settings.getBank());
    }

    private static SalesSettingsDto toDto(SalesSettings s) {
        return new SalesSettingsDto(s.getAlias(), s.getCbu(), s.getHolder(), s.getBank(), s.getExpirationHours());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
