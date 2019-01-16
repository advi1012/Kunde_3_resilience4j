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
package de.hska.kunde.service

import de.hska.kunde.entity.Kunde
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.count
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.findById
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import java.time.Duration

/**
 * Anwendungslogik für Werte zu Kunden (für "Software Engineering").
 * @author [Jürgen Zimmermann](mailto:Juergen.Zimmermann@HS-Karlsruhe.de)
 */
@Service
class KundeValuesService(private val mongoTemplate: ReactiveMongoTemplate) {
    /**
     * Die Anzahl aller Kunden ermitteln.
     * @return Die Anzahl aller Kunden.
     */
    fun anzahlKunden() = mongoTemplate.count<Kunde>()

    /**
     * Nachnamen anhand eines Präfix ermitteln. Projektionen in Spring Data
     * würden "nullable" Properties in den Data-Klassen erfordern.
     *
     * @param prefix Präfix für Nachnamen
     * @return Gefundene Nachnamen
     */
    fun findNachnamenByPrefix(prefix: String): Flux<String> {
         val query = Query(where(Kunde::nachname).regex("^$prefix", "i"))
         return mongoTemplate.find<Kunde>(query)
             .timeout(TIMEOUT_SHORT)
             .map { it.nachname }
             .distinct()
    }

    /**
     * Emailadressen anhand eines Präfix ermitteln.
     * @param prefix Präfix für Emailadressen.
     * @return Gefundene Emailadressen.
     */
    fun findEmailsByPrefix(prefix: String): Flux<String> {
        val query = Query(where(Kunde::email).regex("^$prefix", "i"))
        return mongoTemplate.find<Kunde>(query)
            .timeout(TIMEOUT_SHORT)
            .map { it.email }
    }

    /**
     * Version zur Kunde-ID ermitteln.
     * @param id Kunde-ID.
     * @return Versionsnummer.
     */
    fun findVersionById(id: String) =
        mongoTemplate.findById<Kunde>(id)
                .timeout(TIMEOUT_SHORT)
                .map { it.version }

    private companion object {
        @Suppress("MagicNumber")
        val TIMEOUT_SHORT: Duration = Duration.ofMillis(500)
    }
}
