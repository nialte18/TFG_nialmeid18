package com.nicole.tfg;

import java.net.URI;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisException;

public class Redis implements DatabaseClient{
    private JedisPool pool; // pool de conexiones 

    @Override
    public void open(String uri) throws Exception {
        try {
            pool = new JedisPool(URI.create(uri));
        } catch (IllegalArgumentException e) {
            throw new Exception("La URI de Redis no es valida", e);
        } catch (JedisException e) {
            throw new Exception("No se pudo crear el pool de conexiones de Redis", e);
        }
    }

    @Override
    public void write(String key, String value) throws Exception {
        try (Jedis jedis = pool.getResource()) {
            jedis.set(key, value);
        } catch (JedisException e) {
            // Comunicamos el fallo al gestor conservando su causa original.
            throw new Exception("No se pudo escribir en Redis", e);
        }
    }

    @Override
    public String read(String key) throws Exception {
        try (Jedis jedis = pool.getResource()) {
            return jedis.get(key);
        } catch (JedisException e) {
            throw new Exception("No se pudo leer de Redis", e);
        }
    }

    @Override
    public void close() throws Exception {
        if (pool != null) {
            try {
                pool.close();
            } catch (JedisException e) {
                throw new Exception("No se pudo cerrar el pool de conexiones de Redis", e);
            }
        }
    }
    

}
