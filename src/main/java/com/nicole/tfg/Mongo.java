package com.nicole.tfg;

import com.mongodb.client.*;
import com.mongodb.ConnectionString;
import com.mongodb.MongoException;
import org.bson.Document;

public class Mongo implements DatabaseClient {

    private MongoClient client; // cliente java de MongoDB,maneja la conexión con el serv Mongo
    private MongoCollection<Document> collection; // la "tabla" donde se guardarán los datos
    private MongoCollection<Document> collectionHistorico; // información de las Transacciones

    @Override
    public void open(String uri) throws Exception {
        try {
            // La URI incluye el servidor, el puerto y el nombre de la base de datos.
            ConnectionString connectionString = new ConnectionString(uri);
            String nombreBase = connectionString.getDatabase();
            if (nombreBase == null || nombreBase.isBlank()) {
                throw new IllegalArgumentException("La URI de Mongo debe incluir el nombre de la base de datos");
            }
            client = MongoClients.create(connectionString);
            MongoDatabase database = client.getDatabase(nombreBase);
            collection = database.getCollection("mensajes");
            collectionHistorico = database.getCollection("historico");
        } catch (IllegalArgumentException e) {
            throw new Exception("La URI o la configuracion de Mongo no es valida", e);
        } catch (MongoException e) {
            throw new Exception("No se pudo abrir el cliente de Mongo", e);
        }
    }

    @Override
    public void write(String key, String value) throws Exception {
        try {
            Document doc = new Document("_id", key).append("mensaje", value);

            collection.replaceOne(
                    new Document("_id", key),
                    doc,
                    new com.mongodb.client.model.ReplaceOptions().upsert(true)
            );
        } catch (MongoException e) {
            throw new Exception("No se pudo escribir en Mongo", e);
        }
    }

    @Override
    public String read(String key) throws Exception {
        try {
            Document res = collection.find(new Document("_id", key)).first();
            return (res == null) ? null : res.getString("mensaje");
        } catch (MongoException e) {
            throw new Exception("No se pudo leer de Mongo", e);
        }
    }

    @Override
    public void close() throws Exception {
        try {
            if (client != null) client.close();
        } catch (MongoException e) {
            throw new Exception("No se pudo cerrar el cliente de Mongo", e);
        }
    }
    public void guardarHistorico(String id,
                             long tiempoInicio,
                             long tiempoFinal,
                             long duracion,
                             double porcentajeLectura,
                             int bloque, Transaccion.Estado estado) throws Exception {
        try {
            Document historicoDoc = new Document("_id", id)
                    .append("tiempoInicio", tiempoInicio)
                    .append("tiempoFinal", tiempoFinal)
                    .append("duracionMs", duracion)
                    .append("porcentajeLectura", porcentajeLectura)
                    .append("bloque", bloque)
                    .append("estado", estado.name());

            collectionHistorico.insertOne(historicoDoc);
        } catch (MongoException e) {
            throw new Exception("No se pudo guardar el historico en Mongo", e);
        }
    }

}
