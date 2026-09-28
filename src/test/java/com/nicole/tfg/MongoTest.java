package com.nicole.tfg;

import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import junit.framework.TestCase;

public class MongoTest extends TestCase {
    private interface Operacion {
        void ejecutar() throws Exception;
    }

    // Simulamos fallos del driver sin conectar ni escribir en bases reales.
    private void simularFallo(Mongo mongo, String campo, Class<?> tipo,
                             MongoException causa) throws Exception {
        Object recurso = Proxy.newProxyInstance(tipo.getClassLoader(), new Class<?>[]{tipo},
                (proxy, metodo, argumentos) -> { throw causa; });
        Field field = Mongo.class.getDeclaredField(campo);
        field.setAccessible(true);
        field.set(mongo, recurso);
    }

    private void comprobarFallo(Operacion operacion, String mensaje,
                               MongoException causa) throws Exception {
        try {
            operacion.ejecutar();
            fail("La operacion debe comunicar el fallo");
        } catch (Exception e) {
            assertEquals(mensaje, e.getMessage());
            assertSame(causa, e.getCause());
        }
    }

    public void testUriSinBaseConservaCausa() throws Exception {
        Mongo mongo = new Mongo();
        try {
            mongo.open("mongodb://localhost:27017");
            fail("Debe exigir un nombre de base de datos");
        } catch (Exception e) {
            assertEquals("La URI o la configuracion de Mongo no es valida", e.getMessage());
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        } finally {
            mongo.close();
        }
    }

    public void testLecturaFallidaNoDevuelveNull() throws Exception {
        Mongo mongo = new Mongo();
        MongoException causa = new MongoException("Fallo simulado");
        simularFallo(mongo, "collection", MongoCollection.class, causa);
        comprobarFallo(() -> mongo.read("prueba"), "No se pudo leer de Mongo", causa);
    }

    public void testEscrituraPropagaFallo() throws Exception {
        Mongo mongo = new Mongo();
        MongoException causa = new MongoException("Fallo simulado");
        simularFallo(mongo, "collection", MongoCollection.class, causa);
        comprobarFallo(() -> mongo.write("prueba", "valor"), "No se pudo escribir en Mongo", causa);
    }

    public void testHistoricoPropagaFallo() throws Exception {
        Mongo mongo = new Mongo();
        MongoException causa = new MongoException("Fallo simulado");
        simularFallo(mongo, "collectionHistorico", MongoCollection.class, causa);
        comprobarFallo(() -> mongo.guardarHistorico("id", 0, 1, 1, 0.5, 2),
                "No se pudo guardar el historico en Mongo", causa);
    }

    public void testCierrePropagaFallo() throws Exception {
        Mongo mongo = new Mongo();
        MongoException causa = new MongoException("Fallo simulado");
        simularFallo(mongo, "client", MongoClient.class, causa);
        comprobarFallo(() -> mongo.close(), "No se pudo cerrar el cliente de Mongo", causa);
    }
}
