package com.nicole.tfg;

import java.io.*;
import java.nio.file.*;
import java.util.List;
import junit.framework.TestCase;

public class ResumenExperimentosCsvTest extends TestCase {
    public void testBateriaCsvYFormulas() throws Exception {
        Path dir = Files.createTempDirectory("resumen-test");
        Path archivo;
        try {
            try (ResumenExperimentosCsv csv = new ResumenExperimentosCsv(dir)) {
                archivo = csv.getRuta();
                ClienteTransaccionesSimple.ejecutarExperimentos(
                        c -> "OK " + c.split(" ")[0] + " prueba",
                        new PrintStream(OutputStream.nullOutputStream()), csv);
            }
            List<String> filas = Files.readAllLines(archivo);
            assertEquals(46, filas.size());
            int total = 0;
            for (String fila : filas.subList(1, filas.size())) {
                String[] c = fila.split(",");
                int bloque = Integer.parseInt(c[2]);
                total += bloque;
                assertEquals(bloque, Integer.parseInt(c[3]) + Integer.parseInt(c[4]));
                assertEquals(bloque, Integer.parseInt(c[5]));
                assertEquals("0", c[6]);
                assertEquals(Long.parseLong(c[8]) / 1e6 / bloque, Double.parseDouble(c[10]), 1e-9);
                assertEquals(bloque * 1e9 / Long.parseLong(c[7]), Double.parseDouble(c[11]), 1e-9);
            }
            assertEquals(5110, total);
            try (ResumenExperimentosCsv segunda = new ResumenExperimentosCsv(dir)) {
                assertFalse(archivo.equals(segunda.getRuta()));
                assertEquals(filas, Files.readAllLines(archivo));
            }
        } finally {
            try (var archivos = Files.list(dir)) {
                for (Path p : archivos.toList()) Files.delete(p);
            }
            Files.delete(dir);
        }
    }

    public void testErrorCsvSePropaga() throws Exception {
        Path dir = Files.createTempDirectory("resumen-error-test");
        ResumenExperimentosCsv csv = new ResumenExperimentosCsv(dir);
        csv.close();
        try {
            ClienteTransaccionesSimple.ejecutarExperimentos(c -> "ERROR simulado",
                    new PrintStream(OutputStream.nullOutputStream()), csv);
            fail("Debe comunicar el fallo de guardado");
        } catch (IOException esperado) {
            // Un fallo del archivo no se confunde con un fallo de red.
        } finally {
            Files.delete(csv.getRuta());
            Files.delete(dir);
        }
    }
}
