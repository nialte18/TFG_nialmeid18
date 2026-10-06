package com.nicole.tfg;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Historico de transacciones para la ejecucion secuencial del servidor. */
public class HistoricoCsv {
    private final Path archivo;

    public HistoricoCsv(Path archivo) {
        this.archivo = archivo;
    }

    public void guardar(String id, long inicio, long fin, long duracion,
                        double porcentajeLectura, int bloque) throws IOException {
        Path padre = archivo.toAbsolutePath().getParent();
        Files.createDirectories(padre);
        boolean cabecera = !Files.exists(archivo) || Files.size(archivo) == 0;
        try (BufferedWriter writer = Files.newBufferedWriter(archivo, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            if (cabecera) {
                writer.write("id,tiempoInicio,tiempoFinal,duracionMs,porcentajeLectura,bloque");
                writer.newLine();
            }
            // Escapar texto conforme al formato CSV. El resto son numeros.
            writer.write("\"" + id.replace("\"", "\"\"") + "\"," + inicio + "," + fin + ","
                    + duracion + "," + porcentajeLectura + "," + bloque);
            writer.newLine();
        } catch (IOException e) {
            throw new IOException("No se pudo guardar el historico CSV en " + archivo.toAbsolutePath(), e);
        }
    }
}
