package com.nicole.tfg;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class Redis implements DatabaseClient{
    private JedisPool pool; // pool de conexiones 

    @Override
    public void open() throws Exception {
        pool = new JedisPool("localhost", 6379);
    }

    @Override
    public void write(String key, String value) throws Exception {
        try (Jedis jedis = pool.getResource()) {
            jedis.set(key, value);
        }
    }

    @Override
    public String read(String key) throws Exception {
        try (Jedis jedis = pool.getResource()) {
            return jedis.get(key);
        }
    }

    @Override
    public void close() throws Exception {
        if (pool != null) {
            pool.close();
        }
    }
    

}
