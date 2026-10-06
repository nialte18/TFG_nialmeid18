# TFG - Sistemas Distribuidos

Proyecto desarrollado por Nicole Almeida Terán.

Implementación en Java de lecturas y escrituras sobre MongoDB y Redis. El proyecto está en fase **secuencial**; la concurrencia se abordará más adelante. El trabajo de referencia se denomina «TFG de Conrad» en el seguimiento del proyecto.

## Requisitos y conexiones

- JDK 21 y Maven.
- Para ejecutar los programas, MongoDB y Redis deben estar activos, por ejemplo en sus contenedores Docker.

| Servicio | URI utilizada |
|---|---|
| MongoDB 7 | `mongodb://localhost:27017/tfg_mongo7` |
| MongoDB 8 | `mongodb://localhost:27018/tfg_mongo8` |
| Redis 7 | `redis://localhost:6379` |

Una única clase `Mongo` tiene dos instancias independientes. Tanto `Mongo` como `Redis` implementan `DatabaseClient` y reciben la dirección en `open(String uri)`. Mongo obtiene también el nombre de la base desde la URI.

Las direcciones se definen en el servidor y se pasan a `GestorTransacciones.abrir(uriMongo7, uriMongo8, uriRedis)`. Actualmente no se reciben como argumentos de la línea de comandos.

## Ejecución mediante cliente y servidor

Recorrido: `ClienteTransaccionesSimple → ServidorTransaccionesSimple → GestorTransacciones → bases de datos`.

En una terminal, iniciar el servidor:

```bash
mvn -Pservidor compile exec:java
```

En otra terminal, ejecutar el cliente:

```bash
mvn -Pcliente compile exec:java
```

El servidor escucha en el puerto `5000`. Su bucle `while (true)` acepta una conexión, atiende una petición y vuelve a esperar al siguiente cliente. La atención es secuencial: un cliente que no envía su línea mantiene al servidor esperando. Para detener el proceso desde la terminal, utilizar `Ctrl + C`.

El cliente ejecuta la bateria completa: cinco proporciones de lecturas y nueve bloques, con **45 resumenes y 5.110 peticiones**. Abre una conexion por peticion, espera la respuesta y pasa a la siguiente. Las listas estan definidas en el cliente; no hay argumentos de consola.

El protocolo es `READ porcentajeLectura bloque clave` o `WRITE porcentajeLectura bloque clave valor`. Por ejemplo, `WRITE 0.2 8 cuenta1 valor_1` corresponde a un bloque de 8 con 20 % de lecturas. El servidor valida los datos y responde `OK READ ...`, `OK WRITE ...` o `ERROR ...`. El formato antiguo sin escenario ya no es valido.

Los resumenes distinguen peticiones correctas y fallidas. La latencia media por intento incluye conexion, envio y espera de respuesta; el throughput cuenta respuestas correctas por segundo de tiempo total del bloque. Los calculos usan nanosegundos, sin truncar cada peticion a milisegundos. Estas medidas son distintas de las internas del gestor.

La conexion tiene un limite de espera de 5 segundos y la respuesta de 120 segundos. Los fallos de comunicacion se cuentan como intentos fallidos sin reintentar: no confirman que la operacion no se haya aplicado en el servidor.

## Escenarios de los experimentos secuenciales

`WORKLOADS` indica la **proporción de lecturas**. Las escrituras son el resto:

| Valor | Lecturas | Escrituras |
|---|---:|---:|
| `0.0` | 0 % | 100 % |
| `0.2` | 20 % | 80 % |
| `0.5` | 50 % | 50 % |
| `0.8` | 80 % | 20 % |
| `1.0` | 100 % | 0 % |

Cada escenario recorre bloques de **2, 4, 8, 16, 32, 64, 128, 256 y 512 transacciones**. Los valores están definidos en el código. El número de lecturas se redondea a un entero, por lo que en bloques pequeños el porcentaje real puede diferir del solicitado. Las operaciones se mezclan antes de ejecutarse y utilizan las claves `cuenta1` a `cuenta4`.

Se muestran en consola los recuentos, el tiempo total medido, la latencia media y el throughput (transacciones por segundo). El cliente ejecuta estos escenarios a traves del servidor.

## Histórico y errores

El gestor registra las operaciones que alcanzan ese paso en la colección `historico` de `tfg_mongo7`, con ID, tiempos de inicio y fin, duración, `porcentajeLectura` (proporcion entre 0 y 1) y tamaño de bloque. No es un registro completo de fallos: una excepción anterior impide llegar al guardado. El servidor utiliza la proporcion de lecturas y el bloque recibidos del cliente. Los documentos nuevos usan `porcentajeLectura`; los antiguos conservan `workload` y no se migran automaticamente. El porcentaje de escrituras se calcula como `1 - porcentajeLectura`.

Mongo y Redis capturan errores de sus bibliotecas, añaden un mensaje sobre la operación y propagan la excepción conservando su causa. El servidor captura fallos al atender a un cliente y continúa con el siguiente.

Este tratamiento no implementa rollback ni garantiza atomicidad entre las tres bases: si una escritura posterior falla, las anteriores pueden haber quedado aplicadas. Crear un cliente o pool tampoco garantiza por sí solo que el servicio esté disponible.

## Historico CSV (ademas de MongoDB)

El gestor intenta guardar cada registro tanto en MongoDB 7 como en `resultados/historico_transacciones.csv`. La ruta es relativa al directorio desde el que se inicia el servidor; usando los comandos de este README desde la raiz, queda dentro del proyecto.

La carpeta y el archivo se crean al guardar la primera transaccion. El CSV utiliza UTF-8 y comas como separadores, con estas columnas:

```csv
id,tiempoInicio,tiempoFinal,duracionMs,porcentajeLectura,bloque
```

Los tiempos de inicio y fin son milisegundos desde la epoca Unix. `porcentajeLectura` es una proporcion entre 0 y 1 (0.2 significa 20 %). Son los mismos valores enviados al historico de Mongo; el ID corresponde a `_id` en Mongo. La duracion interna se mide antes de guardar en ambos destinos. No contiene los 45 resumenes del cliente.

Cada registro se anade sin borrar filas anteriores; la cabecera solo se escribe si el archivo esta vacio o no existe. Al reiniciar el servidor se sigue anexando al mismo archivo. Para separar sesiones se puede renombrar o mover el CSV con el servidor detenido. Los historicos antiguos de Mongo no se exportan retroactivamente.

En Excel, importar mediante Datos > Desde texto/CSV, seleccionando UTF-8, delimitador coma y punto como separador decimal para `porcentajeLectura` si la configuracion regional lo requiere. El archivo es CSV, no un libro `.xlsx`. La carpeta `resultados/` se excluye de Git para no subir datos generados automaticamente.

Si falla un destino se intenta el otro y se comunica el error; no hay atomicidad entre el archivo y Mongo. Puede existir un registro en un destino y no en el otro. Si falla una operacion de datos antes de alcanzar el registro del historico, no se registra en ninguno de los dos.

## CSV de resumenes para las graficas

Cada ejecucion del cliente crea un archivo nuevo `resultados/resumen_experimentos_FECHA_IDENTIFICADOR.csv` y muestra su ruta. Una ejecucion completa contiene 45 filas (5 mezclas por 9 bloques), que resumen 5.110 peticiones. No sobrescribe ni mezcla los resumenes de ejecuciones anteriores. Si se interrumpe, el archivo puede contener menos filas.

Incluye proporciones nominales, bloque, lecturas/escrituras realmente enviadas, correctas/fallidas, tiempo total del bloque y suma de tiempos de peticion en nanosegundos, tiempo total en ms, latencia media en ms y throughput en peticiones correctas por segundo. Se mide en el cliente, incluyendo conexion y respuesta del servidor. La escritura de este resumen queda fuera del intervalo medido.

- Tiempo total (ms): `tiempoTotalNs / 1000000`.
- Latencia media por intento (ms): `tiempoPeticionesNs / 1000000 / bloque`.
- Throughput: `correctas * 1000000000 / tiempoTotalNs`.

Para preparar las graficas en Excel: Datos > Desde texto/CSV, elegir este archivo, delimitador coma, UTF-8 y configuracion regional con punto decimal. Crear una tabla por metrica con bloques en filas y proporciones de lectura en columnas; insertar un grafico de lineas. No se necesita interpretar el log de consola ni ejecutar Python. El libro Excel entregado contiene una copia de un CSV concreto; no se actualiza automaticamente al ejecutar Java otra vez.

Este archivo es distinto de `historico_transacciones.csv`, que sigue acumulando las duraciones internas del gestor. No deben mezclarse ambas medidas. Los resultados de una sola ejecucion son preliminares, sin estimacion de variabilidad. En estas graficas varia el bloque secuencial; en el TFG de Conrad se estudian tambien clientes concurrentes.

## Pruebas automáticas

```bash
mvn test
```

Hay pruebas de Mongo, Redis y del recorrido de los experimentos. Se comprueban las 45 combinaciones, la transferencia de metadatos, la validacion, el recuento de errores y un intercambio por sockets locales con gestor simulado. Verifican configuración inválida y propagación de errores conservando la causa. Utilizan fallos simulados o un pool cerrado; no necesitan servicios activos ni escriben en las bases reales. No sustituyen las pruebas de integración ni los experimentos de rendimiento.

## Pendiente

- Validar los experimentos completos mediante cliente y servidor contra las bases reales.
- Ejecutar los nuevos escenarios de 80 % y 100 % de lecturas y revisar los resultados.
- Preparar las gráficas cuando quede aclarado el diseño experimental.
- Abordar la concurrencia después de validar la fase secuencial.
