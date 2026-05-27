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
## Funcionalidades actuales
- Ejecución secuencial de transacciones
- Workloads configurables:
  - 50/50
  - 80/20
  - 100/0
- Persistencia del histórico de transacciones en MongoDB
- Cálculo de latencia media
- Exportación de resultados para análisis en Excel

## Histórico de transacciones
Cada transacción ejecutada se almacena en la colección `historico` de MongoDB 7 con la siguiente información:
- ID de transacción
- Tiempo de inicio
- Tiempo final
- Duración en ms
- Workload
- Tamaño de bloque

## Resultados experimentales
Los datos obtenidos pueden exportarse a CSV y analizarse posteriormente mediante tablas dinámicas y gráficas en Excel.
