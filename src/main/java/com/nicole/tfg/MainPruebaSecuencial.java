package com.nicole.tfg;

import java.util.Random;

public class MainPruebaSecuencial {

        public static void main(String[] args) {

                try {

                        Mongo7 mongo7 = new Mongo7();
                        Mongo8 mongo8 = new Mongo8();
                        Redis redis = new Redis();
                        GestorTransacciones gestor = new GestorTransacciones(mongo7, mongo8, redis);
                        gestor.abrir();
                        // cargas de porcentaje de escritura 
                        double[] workloads = { 0.5, 0.8, 1.0 };
                        // bloques en potencias de 2, es el nº  de transacciones
                        int[] bloques = { 2, 4, 8, 16, 32, 64, 128 };
                        Random random = new Random();
                        // Cambiamos a varias claves
                        String[] claves = {
                                        "cuenta1",
                                        "cuenta2",
                                        "cuenta3",
                                        "cuenta4"
                        };

                        for (double porcentajeEscritura: workloads) {
                                for (int totalTransacciones : bloques) {

                                        int lecturas = 0;
                                        int escrituras = 0;
                                        long tiempoTotal = 0;

                                        System.out.println("\n========================");
                                        System.out.println("WORKLOAD: " + (porcentajeEscritura * 100) + "% lecturas");
                                        System.out.println("TRANSACCIONES: " + totalTransacciones);
                                        System.out.println("========================");
                                        // ejecutar transacciones
                                        for (int i = 0; i < totalTransacciones; i++) {
                                                // Elegir lectura/escritura
                                                double decision = random.nextDouble();
                                                // Elegir clave aleatoria
                                                String clave = claves[random.nextInt(claves.length)];
                                                long inicio = System.nanoTime();

                                                // Escritura
                                                if (decision < porcentajeEscritura) {
                                                        String valor = "valor_" + i;
                                                        Transaccion tx = new Transaccion(
                                                                        "escritura",
                                                                        clave,
                                                                        valor);

                                                        gestor.ejecutarEscritura(tx, porcentajeEscritura, totalTransacciones);

                                                        escrituras++;

                                                }
                                                // ESCRITURA

                                                else {

                                                        
                                                                Transaccion tx = new Transaccion(
                                                                "lectura",
                                                                clave,
                                                                 null);

                                                        gestor.ejecutarLectura(tx,porcentajeEscritura, totalTransacciones);

                                                        lecturas++;

                                                }

                                                long fin = System.nanoTime();

                                                long duracion = (fin - inicio) / 1_000_000;

                                                tiempoTotal += duracion;
                                        }

                                        // =========================
                                        // RESULTADOS
                                        // =========================

                                        System.out.println("\nRESULTADOS");

                                        System.out.println("Lecturas: "
                                                        + lecturas);

                                        System.out.println("Escrituras: "
                                                        + escrituras);

                                        System.out.println("Tiempo total: "
                                                        + tiempoTotal + " ms");

                                        System.out.println("Latencia media: "
                                                        + (tiempoTotal / totalTransacciones)
                                                        + " ms");
                                }
                        }

                       // Cerramos conexiones 

                        gestor.cerrar();

                } catch (Exception e) {

                        e.printStackTrace();

                }
        }
}