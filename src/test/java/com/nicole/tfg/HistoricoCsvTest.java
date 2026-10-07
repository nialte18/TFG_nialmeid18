package com.nicole.tfg;

import java.nio.file.*;
import java.util.*;
import junit.framework.TestCase;

public class HistoricoCsvTest extends TestCase {
    private Path directorio;
    private Path archivo;
    protected void setUp() throws Exception {
        directorio = Files.createTempDirectory("tfg-csv-test-");
        archivo = directorio.resolve("historico.csv");
    }
    protected void tearDown() throws Exception {
        Files.deleteIfExists(archivo);
        Files.deleteIfExists(directorio);
    }
    public void testAnadeFilasYUnaSolaCabecera() throws Exception {
        new HistoricoCsv(archivo).guardar("id,\"uno\"", 10, 20, 10, 0.2, 8, Transaccion.Estado.FINALIZADA);
        new HistoricoCsv(archivo).guardar("dos", 20, 30, 10, 0.8, 16, Transaccion.Estado.ABORTADA);
        List<String> filas = Files.readAllLines(archivo);
        assertEquals(3, filas.size());
        assertEquals("id,tiempoInicio,tiempoFinal,duracionMs,porcentajeLectura,bloque,estado", filas.get(0));
        assertEquals("\"id,\"\"uno\"\"\",10,20,10,0.2,8,FINALIZADA", filas.get(1));
        assertEquals("\"dos\",20,30,10,0.8,16,ABORTADA", filas.get(2));
    }
    private static class MongoSimulado extends Mongo {
        boolean fallar;
        String id;
        double proporcion;
        int bloque;
        @Override public void write(String k, String v) {}
        @Override public String read(String k) { return "valor"; }
        @Override public void guardarHistorico(String id, long i, long f, long d, double p, int b, Transaccion.Estado estado) throws Exception {
            if (fallar) throw new Exception("Fallo Mongo simulado");
            this.id = id; proporcion = p; bloque = b;
        }
    }
    public void testGestorGuardaLecturaYEscrituraEnAmbosDestinos() throws Exception {
        MongoSimulado mongo = new MongoSimulado();
        GestorTransacciones gestor = new GestorTransacciones(mongo, mongo, mongo, new HistoricoCsv(archivo));
        Transaccion tx = new Transaccion("escritura", "c", "v");
        gestor.ejecutarEscritura(tx, 0.2, 8);
        assertEquals(tx.getId(), mongo.id);
        assertEquals(0.2, mongo.proporcion, 0.0);
        assertEquals(8, mongo.bloque);
        assertTrue(Files.readAllLines(archivo).get(1).startsWith("\"" + mongo.id + "\","));
        gestor.ejecutarLectura(new Transaccion("lectura", "c", null), 1.0, 4);
        assertEquals(3, Files.readAllLines(archivo).size());
        assertTrue(Files.readAllLines(archivo).get(2).endsWith(",1.0,4,FINALIZADA"));
    }
    public void testCsvSeIntentaAunqueFalleMongo() throws Exception {
        MongoSimulado mongo = new MongoSimulado();
        mongo.fallar = true;
        GestorTransacciones gestor = new GestorTransacciones(mongo, mongo, mongo, new HistoricoCsv(archivo));
        try {
            gestor.ejecutarEscritura(new Transaccion("escritura", "c", "v"), 0.2, 8);
            fail("Debe comunicar el fallo de Mongo");
        } catch (Exception e) { assertEquals("Fallo Mongo simulado", e.getMessage()); }
        assertEquals(2, Files.readAllLines(archivo).size());
    }
    public void testFalloCsvNoOcultaGuardadoEnMongo() throws Exception {
        MongoSimulado mongo = new MongoSimulado();
        GestorTransacciones gestor = new GestorTransacciones(mongo, mongo, mongo, new HistoricoCsv(directorio));
        Transaccion tx = new Transaccion("escritura", "c", "v");
        try {
            gestor.ejecutarEscritura(tx, 0.2, 8);
            fail("Debe comunicar el fallo CSV");
        } catch (java.io.IOException e) { assertNotNull(e.getCause()); }
        assertEquals(tx.getId(), mongo.id);
    }
}
