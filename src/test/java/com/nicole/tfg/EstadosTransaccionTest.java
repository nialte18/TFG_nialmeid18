package com.nicole.tfg;

import java.nio.file.*;
import junit.framework.TestCase;

public class EstadosTransaccionTest extends TestCase {
    private Path dir;
    private Path archivo;
    protected void setUp() throws Exception {
        dir = Files.createTempDirectory("estados-test");
        archivo = dir.resolve("historico.csv");
    }
    protected void tearDown() throws Exception {
        Files.deleteIfExists(archivo);
        Files.delete(dir);
    }
    private static class Base extends Mongo {
        Exception falloOperacion;
        Exception falloHistorico;
        Transaccion.Estado registrado;
        long fin;
        int operaciones;
        public void write(String k, String v) throws Exception {
            operaciones++;
            if (falloOperacion != null) throw falloOperacion;
        }
        public String read(String k) throws Exception { write(k, null); return "v"; }
        public void guardarHistorico(String id, long i, long f, long d, double p, int b,
                                     Transaccion.Estado estado) throws Exception {
            if (falloHistorico != null) throw falloHistorico;
            registrado = estado;
            fin = f;
        }
    }
    public void testLecturaYEscrituraFinalizadas() throws Exception {
        Base base = new Base();
        GestorTransacciones gestor = new GestorTransacciones(base, base, base, new HistoricoCsv(archivo));
        for (String tipo : new String[]{"lectura", "escritura"}) {
            Transaccion tx = new Transaccion(tipo, "c", "v");
            assertEquals(Transaccion.Estado.INICIADA, tx.getEstado());
            assertNull(tx.getTFinal());
            if (tipo.equals("lectura")) assertEquals(3, gestor.ejecutarLectura(tx, 0.5, 2).size());
            else gestor.ejecutarEscritura(tx, 0.5, 2);
            assertEquals(Transaccion.Estado.FINALIZADA, tx.getEstado());
            assertEquals(tx.getEstado(), base.registrado);
            assertEquals(base.fin, tx.getTFinal().getTime());
        }
        assertEquals(6, base.operaciones);
        assertEquals(3, Files.readAllLines(archivo).size());
    }
    public void testFalloEnSegundaBaseAbortaYRegistra() throws Exception {
        Base primera = new Base(), segunda = new Base(), tercera = new Base();
        segunda.falloOperacion = new Exception("Fallo operacion");
        GestorTransacciones gestor = new GestorTransacciones(primera, segunda, tercera, new HistoricoCsv(archivo));
        for (String tipo : new String[]{"lectura", "escritura"}) {
            Transaccion tx = new Transaccion(tipo, "c", "v");
            try {
                if (tipo.equals("lectura")) gestor.ejecutarLectura(tx, 0.5, 2);
                else gestor.ejecutarEscritura(tx, 0.5, 2);
                fail();
            } catch (Exception e) { assertSame(segunda.falloOperacion, e); }
            assertEquals(Transaccion.Estado.ABORTADA, tx.getEstado());
            assertEquals(tx.getEstado(), primera.registrado);
            assertNotNull(tx.getTFinal());
        }
        assertEquals(0, tercera.operaciones);
        assertTrue(Files.readAllLines(archivo).get(2).endsWith(",ABORTADA"));
    }
    public void testFalloHistoricoNoCambiaResultadoYConservaErrorOriginal() throws Exception {
        Base base = new Base();
        base.falloHistorico = new Exception("Fallo historico");
        GestorTransacciones gestor = new GestorTransacciones(base, base, base, new HistoricoCsv(archivo));
        Transaccion correcta = new Transaccion("lectura", "c", null);
        try { gestor.ejecutarLectura(correcta, 1, 2); fail(); }
        catch (Exception e) { assertSame(base.falloHistorico, e); }
        assertEquals(Transaccion.Estado.FINALIZADA, correcta.getEstado());
        assertNotNull(correcta.getTFinal());
        base.falloOperacion = new Exception("Fallo operacion");
        Transaccion abortada = new Transaccion("escritura", "c", "v");
        try { gestor.ejecutarEscritura(abortada, 0, 2); fail(); }
        catch (Exception e) {
            assertSame(base.falloOperacion, e);
            assertSame(base.falloHistorico, e.getSuppressed()[0]);
        }
        assertEquals(Transaccion.Estado.ABORTADA, abortada.getEstado());
        assertTrue(Files.readAllLines(archivo).get(1).endsWith(",FINALIZADA"));
        assertTrue(Files.readAllLines(archivo).get(2).endsWith(",ABORTADA"));
    }
    public void testNoReejecutaTransaccionTerminada() throws Exception {
        Base base = new Base();
        GestorTransacciones gestor = new GestorTransacciones(base, base, base, new HistoricoCsv(archivo));
        Transaccion tx = new Transaccion("escritura", "c", "v");
        gestor.ejecutarEscritura(tx, 0, 2);
        try { gestor.ejecutarEscritura(tx, 0, 2); fail(); }
        catch (IllegalStateException esperado) { }
        assertEquals(3, base.operaciones);
        assertEquals(Transaccion.Estado.FINALIZADA, tx.getEstado());
        assertEquals(2, Files.readAllLines(archivo).size());
    }
    public void testRechazaCsvAntiguoSinModificarlo() throws Exception {
        String antiguo = "id,tiempoInicio,tiempoFinal,duracionMs,porcentajeLectura,bloque\n";
        Files.writeString(archivo, antiguo);
        try {
            new HistoricoCsv(archivo).guardar("id", 0, 1, 1, 0, 2, Transaccion.Estado.FINALIZADA);
            fail();
        } catch (java.io.IOException esperado) { }
        assertEquals(antiguo, Files.readString(archivo));
    }
}
