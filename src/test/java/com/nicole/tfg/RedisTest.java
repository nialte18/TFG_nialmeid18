package com.nicole.tfg;

import junit.framework.TestCase;
import redis.clients.jedis.exceptions.JedisException;

public class RedisTest extends TestCase {

    public void testUriInvalidaConservaLaCausa() throws Exception {
        Redis redis = new Redis();
        try {
            redis.open("redis://host no valido:6379");
            fail("La URI invalida debe producir una excepcion");
        } catch (Exception e) {
            assertEquals("La URI de Redis no es valida", e.getMessage());
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        } finally {
            redis.close();
        }
    }

    // Un pool cerrado permite provocar el fallo sin conectarse a una base real.
    public void testLecturaFallidaNoDevuelveNull() throws Exception {
        Redis redis = new Redis();
        redis.open("redis://localhost:6379");
        redis.close();
        try {
            redis.read("prueba");
            fail("Una lectura fallida debe comunicar el error");
        } catch (Exception e) {
            assertEquals("No se pudo leer de Redis", e.getMessage());
            assertTrue(e.getCause() instanceof JedisException);
        }
    }

    public void testEscrituraFallidaComunicaElError() throws Exception {
        Redis redis = new Redis();
        redis.open("redis://localhost:6379");
        redis.close();
        try {
            redis.write("prueba", "valor");
            fail("Una escritura fallida debe comunicar el error");
        } catch (Exception e) {
            assertEquals("No se pudo escribir en Redis", e.getMessage());
            assertTrue(e.getCause() instanceof JedisException);
        }
    }
}
