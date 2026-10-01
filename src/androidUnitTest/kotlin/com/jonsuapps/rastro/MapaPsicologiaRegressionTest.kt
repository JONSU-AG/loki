package com.jonsuapps.rastro

import com.jonsuapps.rastro.data.AprenderRepository
import com.jonsuapps.rastro.data.content.JsonContentLoader
import com.jonsuapps.rastro.gamification.GamificationState
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regresión del piloto Psicología (Google AI Studio → RASTRO, mismo sistema
 * genérico que Biología/Lenguaje/Literatura): convivencia legacy+nuevo sin
 * duplicar ni contaminar.
 *
 * Piloto = solo semana01 nueva (2 LessonNodes psi_t01_s01/s02, 20 challenges).
 * Legacy psi_t01_* (semana 1) queda excluido; legacy semanas 2-15 intacto.
 * Usa fixtures espejo de `src/androidUnitTest/resources` (patrón existente).
 */
class MapaPsicologiaRegressionTest {

    private val fixtureRoot = "composeResources/rastro.shared.generated.resources/files/"

    private fun testLoader(): JsonContentLoader {
        val cl = Thread.currentThread().contextClassLoader
            ?: ClassLoader.getSystemClassLoader()
        return JsonContentLoader(
            json = Json { ignoreUnknownKeys = true; isLenient = true },
            readBytesOverride = { path ->
                cl.getResourceAsStream(fixtureRoot + path)?.readBytes()
                    ?: throw IllegalStateException("Recurso no encontrado: $path")
            }
        )
    }

    private fun mapaPsicologia(): List<com.jonsuapps.rastro.model.LessonNode> {
        AprenderRepository.testLoaderOverride = testLoader()
        try {
            return GamificationState().learningPath("psicologia")
        } finally {
            AprenderRepository.testLoaderOverride = null
        }
    }

    @Test
    fun mapaPsicologia_piloto_legacy28_mas2nuevas() {
        val mapa = mapaPsicologia()
        // Legacy semanas 2-15 = 14 semanas x 2 = 28; semana 1 legacy excluida.
        val legacy = mapa.filter { it.id.startsWith("psi_t") && it.challenges.size == 2 }
        assertEquals(28, legacy.size, "Legacy semanas 2-15 intacto (28), semana 1 excluida")
        assertTrue(legacy.none { it.semana == 1 }, "Sin legacy de semana 1 (reemplazado por piloto)")

        val nuevas = mapa.filter { it.id == "psi_t01_s01" || it.id == "psi_t01_s02" }
        assertEquals(2, nuevas.size, "Piloto: 2 lecciones nuevas semana 01")
    }

    @Test
    fun mapaPsicologia_nuevas_unaVez_conTeoriaDeEstudio() {
        val mapa = mapaPsicologia()
        val esperadas = mapOf(
            "psi_t01_s01" to Triple("1.1", "3.1. Definición, Etimología y Evolución Epistemológica", 10),
            "psi_t01_s02" to Triple("1.2", "3.2. Objeto de Estudio de la Psicología", 10)
        )
        for ((id, esp) in esperadas) {
            val l = mapa.filter { it.id == id && it.challenges.size == esp.third }
            assertEquals(1, l.size, "$id nuevo exactamente una vez")
            val lesson = l.first()
            assertEquals("psicologia", lesson.subjectId)
            assertEquals(1, lesson.semana)
            assertEquals(esp.first, lesson.subtema)
            assertEquals(esp.second, lesson.title)
            assertTrue(lesson.theory.resumen.isNotBlank(), "$id con teoría transportada")
            // MATERIAL DE ESTUDIO, no ficha de repaso.
            assertTrue(
                lesson.theory.resumen.length >= 8000,
                "$id teoría desarrollada (resumen=${lesson.theory.resumen.length} chars)"
            )
            assertTrue(lesson.challenges.all { it.subject == "psicologia" && it.semana == 1 })
            assertTrue(lesson.challenges.all { it.correctIndex in 0..3 }, "$id correctIndex válido")
            assertTrue(lesson.challenges.all { it.explanation.isNotBlank() }, "$id explanations presentes")
            assertTrue(lesson.challenges.all { it.options.size == 4 }, "$id 4 opciones")
        }
    }

    @Test
    fun repositorio_psicologia_nuevoPrimero_legacyComoRespaldo() {
        AprenderRepository.testLoaderOverride = testLoader()
        try {
            val nueva = AprenderRepository.getLessonByIdSync("psi_t01_s01")
            assertTrue(nueva != null, "psi_t01_s01 debe resolverse")
            assertEquals(10, nueva.challenges.size, "Nuevo con 10 challenges Google")
            assertEquals("psicologia", nueva.subjectId)

            // Legacy de otra semana intacto y direccionable.
            val legacy = AprenderRepository.getLessonByIdSync("psi_t02_s01")
            assertTrue(legacy != null, "psi_t02_s01 legacy debe seguir resolviéndose")
            assertEquals("psicologia", legacy.subjectId)
        } finally {
            AprenderRepository.testLoaderOverride = null
        }
    }

    @Test
    fun mapaPsicologia_sinContaminacion() {
        val mapa = mapaPsicologia()
        assertTrue(mapa.none { it.id.startsWith("bio_") }, "Sin Biología en Psicología")
        assertTrue(mapa.none { it.id.startsWith("leng_") }, "Sin Lenguaje en Psicología")
        assertTrue(mapa.none { it.id.startsWith("len_t") }, "Sin legacy Lenguaje en Psicología")
        assertTrue(mapa.none { it.id.startsWith("lit_t") }, "Sin Literatura en Psicología")
        assertTrue(mapa.all { it.subjectId == "psicologia" }, "Todo el mapa es psicología")
    }
}
