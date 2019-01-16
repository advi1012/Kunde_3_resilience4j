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

import de.hska.kunde.config.logger
import de.hska.kunde.config.security.CustomUserDetails
import de.hska.kunde.config.security.CustomUserDetailsService
import de.hska.kunde.db.CriteriaUtil.getCriteria
import de.hska.kunde.db.update
import de.hska.kunde.entity.Kunde
import de.hska.kunde.mail.Mailer
import org.springframework.cache.annotation.CacheConfig
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.context.annotation.Lazy
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.exists
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.findAll
import org.springframework.data.mongodb.core.findById
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.remove
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.MultiValueMap
import org.springframework.validation.annotation.Validated
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.toMono
import java.time.Duration.ofMillis
import java.util.UUID.randomUUID
import javax.validation.Valid

@Suppress("TooManyFunctions")
/**
 * Anwendungslogik für Kunden.
 *
 * [Klassendiagramm](../../../../docs/images/KundeService.png)
 *
 * @author [Jürgen Zimmermann](mailto:Juergen.Zimmermann@HS-Karlsruhe.de)
 */
@Service
@Validated
// TODO https://jira.spring.io/browse/SPR-14235
// https://stackoverflow.com/questions/49731127/caching-in-spring-5-webflux#answer-49731328
@CacheConfig(cacheNames = ["kunde_id"])
class KundeService(
    // Annotation im zugehoerigen Parameter des Java-Konstruktors
    private val mongoTemplate: ReactiveMongoTemplate,
    @param:Lazy private val userService: CustomUserDetailsService,
    @param:Lazy private val mailer: Mailer
) {

    /**
     * Einen Kunden anhand seiner ID suchen.
     *
     * @param id Die Id des gesuchten Kunden.
     * @return Der gefundene Kunde oder ein leeres Mono-Objekt.
     */
    @Cacheable(key = "#id")
    fun findById(id: String) = mongoTemplate.findById<Kunde>(id).timeout(timeoutShort)

    /**
     * Alle Kunden ermitteln.
     *
     * @return Alle Kunden
     */
    fun findAll() = mongoTemplate.findAll<Kunde>().timeout(timeoutShort)

    /**
     * Kunden anhand von Suchkriterien ermitteln.
     *
     * @param queryParams Suchkriterien.
     * @return Gefundene Kunden.
     */
    @Suppress("ReturnCount")
    fun find(queryParams: MultiValueMap<String, String>): Flux<Kunde> {
        if (queryParams.isEmpty()) {
            return findAll()
        }

        val criteria = getCriteria(queryParams)
        if (criteria.contains(null)) {
            return Flux.empty()
        }

        val query = Query()
        criteria.filterNotNull()
                .forEach { query.addCriteria(it) }
        logger.debug("{}", query)
        // http://www.baeldung.com/spring-data-mongodb-tutorial
        return mongoTemplate.find<Kunde>(query).timeout(timeoutLong)
    }

    /**
     * Einen neuen Kunden anlegen.
     *
     * @param kunde Das Objekt des neu anzulegenden Kunden.
     * @return Der neu angelegte Kunde mit generierter ID.
     * @throws InvalidAccountException falls die Benutzerkennung nicht korrekt
     *      ist.
     * @throws EmailExistsException falls die Emailadresse bereits existiert.
     */
    @Transactional
    fun create(@Valid kunde: Kunde): Mono<Kunde> {
        // CustomUserDetails nicht @NotNull: nicht in der Mongo-Collection gespeichert
        kunde.user ?: throw InvalidAccountException()

        val email = kunde.email
        return mongoTemplate.exists<Kunde>(Query(Kunde::email isEqualTo email))
            .timeout(timeoutShort)
            .map {
                if (it) {
                    throw EmailExistsException(email)
                }
                kunde
            }
            .flatMap(::createUser)
            .flatMap { userDetails -> create(kunde, userDetails) }
            .doOnSuccess(mailer::send)
    }

    private fun createUser(kunde: Kunde): Mono<CustomUserDetails>? {
        val kundeUser = kunde.user ?: throw InvalidAccountException()
        val user = CustomUserDetails(
            id = null,
            username = kundeUser.username,
            password = kundeUser.password,
            authorities = listOf(SimpleGrantedAuthority("ROLE_KUNDE")))
        logger.trace("User wird angelegt: {}", user)
        return userService.create(user).timeout(timeoutShort)
    }

    private fun create(kunde: Kunde, user: CustomUserDetails): Mono<Kunde> {
        val neuerKunde = kunde.copy(
            email = kunde.email.toLowerCase(),
            username = user.username,
            id = randomUUID().toString())
        neuerKunde.user = user
        logger.trace("Kunde mit user: {}", kunde)
        return mongoTemplate.save(neuerKunde).timeout(timeoutShort)
    }

    /**
     * Einen vorhandenen Kunden aktualisieren.
     *
     * @param kunde Das Objekt mit den neuen Daten.
     * @param id ID des Kunden.
     * @param version Versionsnummer.
     * @return Der aktualisierte Kunde oder ein leeres Mono-Objekt, falls
     *      es keinen Kunden mit der angegebenen ID gibt.
     * @throws InvalidVersionException falls die Versionsnummer nicht korrekt
     *      ist.
     * @throws EmailExistsException falls die Emailadresse bereits existiert.
     */
    @CacheEvict(key = "#id")
    @Transactional
    fun update(@Valid kunde: Kunde, id: String, version: String) =
        mongoTemplate.findById<Kunde>(id)
            .timeout(timeoutShort)
            .flatMap { kundeDb ->
                logger.trace("update: kundeDb={}, version={}", kundeDb, version)
                checkVersion(kundeDb, version)
                checkEmail(kundeDb, kunde.email)
                        .flatMap { update(kundeDb, kunde) }
            }

    private fun checkVersion(kundeDb: Kunde, versionStr: String) {
        // Gibt es eine neuere Version in der DB?
        val version = versionStr.toIntOrNull() ?: throw InvalidVersionException(versionStr)
        val versionDb = kundeDb.version ?: 0
        if (version < versionDb) {
            throw InvalidVersionException(versionStr)
        }
    }

    private fun checkEmail(kundeDb: Kunde, neueEmail: String): Mono<Kunde> {
        // Hat sich die Emailadresse ueberhaupt geaendert?
        if (kundeDb.email == neueEmail) {
            logger.trace("Email nicht geaendert: {}", kundeDb)
            return kundeDb.toMono()
        }
        logger.trace("Email geaendert: {} -> {}", neueEmail, kundeDb)

        // Gibt es die neue Emailadresse bei einem existierenden Kunden?
        return mongoTemplate.exists<Kunde>(Query(Kunde::email isEqualTo neueEmail))
            .timeout(timeoutShort)
            .map<Kunde> { found ->
                if (found) {
                    logger.trace("Neue Email existiert bereits: {}", neueEmail)
                    throw EmailExistsException(neueEmail)
                }
                kundeDb
            }
    }

    private fun update(kundeDb: Kunde, kunde: Kunde): Mono<Kunde> {
        kundeDb.update(kunde)
        logger.trace("Abspeichern des geaenderten Kunden: {}", kundeDb)
        return mongoTemplate.save(kundeDb).timeout(timeoutShort)
    }

    /**
     * Einen vorhandenen Kunden in der DB löschen.
     *
     * @param id Die ID des zu löschenden Kunden.
     * @return true falls es zur ID ein Kundenobjekt gab, das gelöscht
     *      wurde; false sonst.
     */
    // erfordert zusaetzliche Konfiguration in SecurityConfig
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @CacheEvict(key = "#id")
    @Transactional
    fun deleteById(id: String) = mongoTemplate.findById<Kunde>(id)
        .timeout(timeoutShort)
        // EmptyResultDataAccessException bei delete(), falls es zur
        // gegebenen ID kein Objekt gibt
        // http://docs.spring.io/spring/docs/current/javadoc-api/org/...
        // ...springframework/dao/EmptyResultDataAccessException.html
        .delayUntil { mongoTemplate.remove<Kunde>(Query(Kunde::id isEqualTo id)).timeout(timeoutShort) }

    /**
     * Einen vorhandenen Kunden löschen.
     *
     * @param email Die Email des zu löschenden Kunden.
     * @return true falls es zur Email ein Kundenobjekt gab, das gelöscht
     *      wurde; false sonst.
     */
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Transactional
    fun deleteByEmail(email: String) =
        mongoTemplate.remove<Kunde>(Query(Kunde::email isEqualTo email))

    @Suppress("MagicNumber")
    companion object {
        private val logger = logger()
        private val timeoutShort = ofMillis(500)
        private val timeoutLong = ofMillis(2000)
    }
}
