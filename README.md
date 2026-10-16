# TFG - Sistemas Distribuidos

Proyecto desarrollado por Nicole Almeida Terán.

## Versión concurrente

Esta rama, `version-concurrente`, parte de la versión secuencial conservada en `correcciones-secuencial`. El recorrido sigue siendo:

`ClienteTransaccionesSimple → ServidorTransaccionesSimple → GestorTransacciones → Mongo7, Mongo8 y Redis`.

El cliente puede enviar varias peticiones sin esperar a que termine la anterior y el servidor las atiende mediante hilos. Se aplica la regla indicada por la tutora: cualquier transacción que intente empezar mientras otra está activa aborta, independientemente de las claves y de si son lecturas o escrituras.

Por tanto, hay peticiones concurrentes, pero solo una transacción admitida ejecuta operaciones de datos cada vez. No hay reintentos automáticos, niveles de consistencia configurables ni rollback.

## Requisitos y ejecución

JDK 21, Maven y las tres bases activas:

| Servicio | URI |
|---|---|
| MongoDB 7 | `mongodb://localhost:27017/tfg_mongo7` |
| MongoDB 8 | `mongodb://localhost:27018/tfg_mongo8` |
| Redis | `redis://localhost:6379` |

Las conexiones se abren una vez, antes de aceptar peticiones. Hay dos instancias de la clase Mongo y un pool de Redis; cada operación obtiene y devuelve su propia conexión Redis.

Desde la raíz del proyecto, iniciar el servidor:

```bash
mvn -Pservidor compile exec:java
```

En otra terminal, ejecutar el cliente:

```bash
mvn -Pcliente compile exec:java
```

Por defecto utiliza 4 hilos. Para elegir otro número:

```bash
mvn -Pcliente compile exec:java "-Dexec.args=8"
```

Se permite una concurrencia del cliente entre 1 y 64. El servidor tiene 16 hilos. Las conexiones que no pueden atenderse todavía esperan en la cola del servidor; no se consideran transacciones activas hasta entrar en el gestor. Una conexión sin enviar su petición tiene un límite de lectura de 5 segundos.

El cliente conserva las cinco proporciones de lecturas (0, 0.2, 0.5, 0.8 y 1) y los nueve bloques (2, 4, 8, 16, 32, 64, 128, 256 y 512): 45 combinaciones y 5.110 peticiones por ejecución. La concurrencia indica el máximo de tareas de envío en curso; el bloque es el total de peticiones, no el número de hilos. Cada bloque termina antes de empezar el siguiente.

Las lecturas se redondean a un número entero y las operaciones se mezclan. Se usan las claves cuenta1 a cuenta4. Los escenarios pequeños pueden no alcanzar exactamente el porcentaje nominal.

Para detener el servidor, Ctrl+C: deja de aceptar conexiones y espera a que terminen las tareas antes de cerrar las bases. El cliente limita la conexión a 5 segundos y la espera de respuesta a 120 segundos.

## Control de colisiones

`ControlConcurrencia` mantiene la transacción activa y un `ReentrantLock`. Los métodos `intentarIniciar` y `finalizar` protegen la comprobación y actualización de ese dato, no las llamadas a las bases.

El inicio de la transacción se fija dentro de la sección protegida de admisión, justo antes de comprobar si hay otra activa. No es la hora de creación del objeto ni la de envío del cliente. La comprobación y el registro son indivisibles respecto a otros hilos. El intervalo termina cuando el gestor entra en `finalizar`, después de las operaciones y antes de persistir el histórico. La escritura del histórico queda fuera de ese intervalo.

Si hay una transacción activa, la nueva queda ABORTADA con motivo COLISION_TEMPORAL y hora final; no lee ni escribe datos de las cuentas. Sí se intenta guardar su histórico. Si el control está libre, queda admitida y sigue usando las tres bases en secuencia. Una nueva admisión después de liberar el control no colisiona.

Los tiempos del histórico son milisegundos Unix: dos sucesos consecutivos pueden compartir el mismo milisegundo. La decisión usa el estado protegido por el lock, no una comparación de marcas redondeadas ni la búsqueda de históricos en Mongo. Así se detecta una transacción aún activa, cuyo tiempo final no se conoce todavía. Para reconstruir casos que suceden dentro del mismo milisegundo, esas marcas por sí solas no permiten conocer su orden exacto.

El gestor libera la transacción admitida en un bloque `finally`, incluso si una operación falla. Una transacción rechazada no puede liberar la posición de otra.

## Estados, respuestas y errores

| Resultado | Estado | Motivo |
|---|---|---|
| Operaciones completadas en las tres bases | FINALIZADA | NINGUNO |
| Otra transacción activa | ABORTADA | COLISION_TEMPORAL |
| Fallo al validar o ejecutar la operación | ABORTADA | ERROR_OPERACION |

Protocolo de petición:

```text
READ proporcionLecturas bloque clave
WRITE proporcionLecturas bloque clave valor
```

Respuestas: `OK READ ...`, `OK WRITE ...`, `ABORTADA COLISION_TEMPORAL` o `ERROR ...`.

Si las operaciones se completan y falla el histórico, el estado sigue siendo FINALIZADA, pero el servidor responde ERROR. Si hay colisión y falla el registro, también responde ERROR: el cliente no tiene confirmación completa del aborto registrado. Los errores de comunicación no demuestran que la operación no se haya ejecutado.

Mongo y CSV se intentan guardar independientemente. Si fallan la operación y el histórico, se conserva la excepción original y se añade la del histórico como excepción suprimida. ABORTADA por error de operación no deshace posibles escrituras parciales en las bases.

## Archivos de resultados

Todos se guardan en `resultados/`, relativa al directorio de ejecución. Esta carpeta queda excluida de Git.

### Histórico por transacción

El gestor guarda en la colección `historico` de `tfg_mongo7` y en:

`resultados/historico_transacciones_concurrentes.csv`

Columnas:

```csv
id,tiempoInicio,tiempoFinal,duracionMs,porcentajeLectura,bloque,estado,motivoAborto
```

El CSV acumula filas de distintas ejecuciones. El servidor comparte un único escritor cuyo método `guardar` es `synchronized`: la comprobación de cabecera y la escritura de cada fila quedan protegidas entre los hilos de este servidor. No coordina varios procesos de servidor escribiendo al mismo archivo.

Los CSV secuenciales antiguos no se modifican. Los registros nuevos de Mongo incluyen motivoAborto; los documentos antiguos se conservan.

### Resúmenes para gráficas

El cliente crea un archivo nuevo por ejecución:

`resultados/resumen_concurrente_FECHA_IDENTIFICADOR.csv`

Incluye los campos anteriores de resumen y añade `concurrencia`, `abortadasColision` y `errores`. Una ejecución completa tiene 45 filas. Si se interrumpe puede tener menos.

- `correctas`: respuestas OK.
- `abortadasColision`: respuestas ABORTADA COLISION_TEMPORAL.
- `errores`: otras respuestas o fallos de comunicación.
- `fallidas = abortadasColision + errores`.
- `bloque = correctas + abortadasColision + errores`.
- Tiempo total: desde que se comienzan a entregar las tareas hasta recoger todos los resultados del bloque, sin incluir el guardado del resumen.
- Latencia media: suma de tiempos desde conexión hasta respuesta (o error), dividida entre todos los intentos. Incluye abortos y errores, no solo éxitos; excluye la espera previa en la cola del cliente.
- Throughput: respuestas correctas divididas entre segundos del bloque.

No se debe comparar una reducción de latencia como una mejora por sí sola: rechazar peticiones rápidamente puede bajar la media sin aumentar el trabajo completado. Las comparaciones deben incluir los abortos y errores.

Para abrir el CSV en Excel: Datos > Desde texto/CSV, UTF-8 y delimitador coma. En Transformar datos, convertir los decimales con configuración regional Inglés (Estados Unidos). Usar bloques en filas y proporciones de lectura en columnas; excluir Total general de las gráficas y etiquetar ejes y unidades. Guardar el libro con gráficas como .xlsx.

## Pruebas

```bash
mvn test
```

Las pruebas no requieren bases activas. Incluyen colisiones simultáneas, lecturas y escrituras sobre claves distintas, intercambio por sockets, liberación del control tras un error, filas CSV concurrentes, los 45 resúmenes y separación entre colisiones y errores.

Las pruebas con bases reales son una comprobación adicional; una ejecución no permite sacar conclusiones estadísticas. Los resultados secuenciales anteriores se conservan como referencia, pero los experimentos comparativos requieren condiciones controladas y repeticiones.

## Referencias y siguiente fase

Se utiliza el TFG de Conrad como referencia general y el TFG de Pablo para estudiar concurrencia, registro y el autómata de la página 35. La regla temporal de esta fase es la simplificación indicada por la tutora; no reproduce el criterio de Pablo para escrituras sobre las mismas cuentas.

Quedan para después los niveles de consistencia de lectura y escritura, con las indicaciones de la tutora. No se han implementado en esta rama.
