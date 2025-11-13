@"
# TFG - Sistemas Distribuidos
Proyecto desarrollado por Nicole Almeida Terán

##  Descripción
Implementación de operaciones básicas de **lectura y escritura** sobre tres contenedores Docker:
- **MongoDB 7** → `localhost:27017`
- **MongoDB 8** → `localhost:27018`
- **Redis 7** → `localhost:6379`

#  Ejecutar prueba local (MainPrueba)
mvn -Pprueba exec:java

# Ejecutar el servidor de transacciones
mvn -Pservidor exec:java

# Ejecutar el cliente de transacciones
mvn -Pcliente exec:java
