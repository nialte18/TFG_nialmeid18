package com.nicole.tfg;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Un archivo por ejecucion, con una fila por escenario y bloque. */
public class ResumenExperimentosCsv implements Closeable {
    private final Path ruta;
    private final BufferedWriter writer;

    public ResumenExperimentosCsv(Path directorio) throws IOException {
        Files.createDirectories(directorio);
        String fecha = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        ruta = Files.createTempFile(directorio, "resumen_experimentos_" + fecha + "_", ".csv");
        writer = Files.newBufferedWriter(ruta, StandardCharsets.UTF_8);
        try {
            writer.write("proporcionLecturas,proporcionEscrituras,bloque,lecturas,escrituras,correctas,fallidas,tiempoTotalNs,tiempoPeticionesNs,tiempoTotalMs,latenciaMediaMs,throughputPeticionesSegundo");
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            try { writer.close(); } catch (IOException cierre) { e.addSuppressed(cierre); }
            throw e;
        }
    }

    public Path getRuta() { return ruta; }

    public void guardar(double proporcionLecturas, int bloque, int lecturas, int correctas,
                        int fallidas, long tiempoTotalNs, long tiempoPeticionesNs) throws IOException {
        double throughput = tiempoTotalNs > 0 ? correctas * 1_000_000_000.0 / tiempoTotalNs : 0;
        writer.write(proporcionLecturas + "," + (1 - proporcionLecturas) + "," + bloque + ","
                + lecturas + "," + (bloque - lecturas) + "," + correctas + "," + fallidas + ","
                + tiempoTotalNs + "," + tiempoPeticionesNs + "," + tiempoTotalNs / 1_000_000.0 + ","
                + tiempoPeticionesNs / 1_000_000.0 / bloque + "," + throughput);
        writer.newLine();
        writer.flush();
    }

    @Override
    public void close() throws IOException { writer.close(); }
}
