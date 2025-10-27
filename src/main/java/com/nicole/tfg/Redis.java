package com.nicole.tfg;

import redis.clients.jedis.Jedis;

public class Redis {

      public static void main(String[] args) {
        String host = "localhost";
        int port = 6379;

        try (Jedis jedis = new Jedis(host, port)) {
            System.out.println("Conectado a Redis en " + host + ":" + port);

            // Escribir
            String clave = "mensaje";
            String valor = "Hola Redis";
            jedis.set(clave, valor);
            System.out.println("Valor guardado: " + clave + " = " + valor);

            // Leer
            String leido = jedis.get(clave);
            System.out.println("Valor leído: " + leido);

        } catch (Exception e) {
            System.out.println("Error al conectar con Redis:");
            e.printStackTrace();
        }
    }
    
}
