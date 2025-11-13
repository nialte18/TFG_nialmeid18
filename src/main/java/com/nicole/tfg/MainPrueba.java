package com.nicole.tfg;

import java.util.Map;

public class MainPrueba {

    public static void main(String[] args) {

        // Capa de transacciones: se construye con las 3 BDD
        GestorTransacciones gestor = new GestorTransacciones(
                new Mongo7(),
                new Mongo8(),
                new Redis()
        );

        String clave = "test";
        String valor = "Hola TFG con transacciones";

        try {
            //Abrir conexiones a las 3 bases de datos
            gestor.abrir();

            // Crear y ejecutar una transacción de ESCRITURA
            Transaccion txEscritura = new Transaccion("escritura", clave, valor);
            mostrarTransaccionEscritura(txEscritura);
            gestor.ejecutarEscritura(txEscritura);

            //Crear y ejecutar una transacción de LECTURA
            Transaccion txLectura = new Transaccion("lectura", clave, null);
            mostrarTransaccionLectura(txLectura);
            Map<String, String> resultado = gestor.ejecutarLectura(txLectura);

            // 4. Mostrar resultados y comprobar coherencia entre las 3 BDD
            mostrarResultadosLectura(resultado, valor);

        } catch (Exception e) {
            System.err.println(" Error durante la ejecución:");
            e.printStackTrace();
        } finally {
            // Cerrar siempre las conexiones
            try {
                gestor.cerrar();
            } catch (Exception e) {
                System.err.println("Error al cerrar las conexiones:");
                e.printStackTrace();
            }
        }
    }

    // ----- Métodos auxiliares solo para organizar mejor la salida -----

    private static void mostrarTransaccionEscritura(Transaccion tx) {
        System.out.println("=== TRANSACCIÓN DE ESCRITURA ===");
        System.out.println("ID:        " + tx.getId());
        System.out.println("Instante:  " + tx.getInstante());
        System.out.println("Operación: " + tx.getOperacion());
        System.out.println("Clave:     " + tx.getClave());
        System.out.println("Valor:     " + tx.getValor());
        System.out.println();
    }

    private static void mostrarTransaccionLectura(Transaccion tx) {
        System.out.println("=== TRANSACCIÓN DE LECTURA ===");
        System.out.println("ID:        " + tx.getId());
        System.out.println("Instante:  " + tx.getInstante());
        System.out.println("Operación: " + tx.getOperacion());
        System.out.println("Clave:     " + tx.getClave());
        System.out.println();
    }

    private static void mostrarResultadosLectura(Map<String, String> resultado, String valorEsperado) {
        String v7 = resultado.get("mongo7");
        String v8 = resultado.get("mongo8");
        String vr = resultado.get("redis");

        System.out.println("=== RESULTADOS LECTURA ===");
        System.out.println("Mongo7: " + v7);
        System.out.println("Mongo8: " + v8);
        System.out.println("Redis : " + vr);

        if (valorEsperado.equals(v7) && valorEsperado.equals(v8) && valorEsperado.equals(vr)) {
            System.out.println();
            System.out.println("TODO COHERENTE: las 3 BDD devuelven el mismo valor.");
        } else {
            System.out.println();
            System.out.println("INCONSISTENTE: alguna BDD devuelve un valor distinto.");
        }
    }
}
