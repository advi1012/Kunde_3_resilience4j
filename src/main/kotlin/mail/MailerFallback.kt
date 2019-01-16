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

import de.hska.kunde.config.logger
import org.springframework.stereotype.Service

/**
 * Fallback-Implementierung zum Mailer.
 * @author [Jürgen Zimmermann](mailto:Juergen.Zimmermann@HS-Karlsruhe.de)
 */
@Service
class MailerFallback {
    /**
     * Fallback-Function für [Mailer.send], die von _resilience4j_ im Fehlerfall aufgerufen wird.
     * @param mail Daten der nicht-verschickten Email.
     */
    fun send(mail: Mail): Boolean {
        logger.error("Fehler beim Senden der Email: {}", mail)
        return false
    }

    private companion object {
        val logger = logger()
    }
}
