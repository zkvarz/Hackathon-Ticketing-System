package com.dataart.tickets.ticket;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Binds the {@code type} query parameter (HTS-029 filter) from its canonical wire value
 * ({@code bug|feature|fix}), case-insensitively. Without this, Spring's default enum binding would
 * use {@code Enum.valueOf} (the uppercase constant name) and reject the wire form. An unknown value
 * throws, surfacing as HTTP 400 via the type-mismatch handler.
 */
@Component
public class StringToTicketTypeConverter implements Converter<String, TicketType> {

    @Override
    public TicketType convert(String source) {
        return TicketType.fromWire(source);
    }
}
