/*
 * Copyright (C) 2016 - present Juergen Zimmermann, Hochschule Karlsruhe
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.hska.kunde.mail

import de.hska.kunde.config.MailAddressProps
import de.hska.kunde.config.logger
import de.hska.kunde.entity.Kunde
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.circuitbreaker.autoconfigure.CircuitBreakerProperties
import io.vavr.control.Try
import org.springframework.integration.support.MessageBuilder.withPayload
import org.springframework.stereotype.Service

/**
 * Kafka-Client, der Nachrichten sendet, die als Email verschickt werden sollen.
 *
 * @author [Jürgen Zimmermann](mailto:Juergen.Zimmermann@HS-Karlsruhe.de)
 */
@Service
class Mailer(
    private val mailOutput: MailOutput,
    private val props: MailAddressProps,
    circuitBreakerRegistry: CircuitBreakerRegistry,
    circuitBreakerProperties: CircuitBreakerProperties,
    private val fallback: MailerFallback
) {
    // Circuit Breaker mit Namen "mail" und Konfigurationsdaten aus application.yml / Config-Server ebenfalls mit Namen "mail"
    private val circuitBreaker =
        circuitBreakerRegistry.circuitBreaker("mail") { circuitBreakerProperties.createCircuitBreakerConfig("mail") }

    /**
     * Nachricht senden, dass es einen neuen Kunden gibt.
     * @param neuerKunde Das Objekt des neuen Kunden.
     */
    fun send(neuerKunde: Kunde) {
        val mail = Mail(
                to = props.sales,
                from = props.from,
                subject = "Neuer Kunde ${neuerKunde.id}",
                body = "<b>Neuer Kunde:</b> <i>${neuerKunde.nachname}</i>")
        logger.trace("$mail")

        val sendSupplier =
            CircuitBreaker.decorateSupplier(circuitBreaker) {
                mailOutput.getChannel().send(withPayload(mail).build(), timeout)
            }

        Try.ofSupplier(sendSupplier)
            .recover { throwable ->
                logger.warn("sendFallback: ${throwable.message}")
                // gleicher Rueckgabetype wie oben mailOutput.getChannel().send(...)
                fallback.send(mail)
            }
            .get()
    }

    private companion object {
        val timeout = 500L
        val logger = logger()
    }
}
