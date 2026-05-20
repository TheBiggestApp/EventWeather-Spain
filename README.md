# EventWeather Spain

Sistema distribuido basado en eventos para la captura, almacenamiento y consulta de **datos meteorológicos y de eventos culturales/deportivos** en ciudades de España. Integra tres APIs externas (OpenWeather, PredictHQ y Ticketmaster) mediante un broker de mensajería ActiveMQ y expone los datos unificados a través de una API REST.

---

## Tabla de Contenidos

- [Descripción General](#descripción-general)
- [Justificación de APIs y Estructura del Datamart](#justificación-de-apis-y-estructura-del-datamart)
- [Arquitectura](#arquitectura)
  - [Diagrama de Componentes](#diagrama-de-componentes)
  - [Arquitectura de la Aplicación](#arquitectura-de-la-aplicación)
  - [Flujo de Datos](#flujo-de-datos)
- [Módulos](#módulos)
  - [openweather-module](#openweather-module)
  - [predicthq-module](#predicthq-module)
  - [ticketmaster-module](#ticketmaster-module)
  - [event-store-builder](#event-store-builder)
  - [business-unit](#business-unit)
- [Tecnologías Utilizadas](#tecnologías-utilizadas)
- [Principios y Patrones de Diseño](#principios-y-patrones-de-diseño)
- [Requisitos Previos](#requisitos-previos)
- [Instalación y Configuración](#instalación-y-configuración)
  - [1. Clonar el Repositorio](#1-clonar-el-repositorio)
  - [2. Instalar Apache ActiveMQ](#2-instalar-apache-activemq)
  - [3. Obtener API Keys](#3-obtener-api-keys)
  - [4. Configurar cada Módulo](#4-configurar-cada-módulo)
  - [5. Compilar el Proyecto](#5-compilar-el-proyecto)
- [Ejecución](#ejecución)
  - [Orden de Arranque](#orden-de-arranque)
  - [Ejecutar cada Módulo](#ejecutar-cada-módulo)
- [API REST — Endpoints](#api-rest--endpoints)
  - [Endpoint de depuración](#endpoint-de-depuración)
- [Modelo de Datos](#modelo-de-datos)
  - [Tabla Unificada (OBT)](#tabla-unificada-obt)
  - [Event Store (Sistema de Ficheros)](#event-store-sistema-de-ficheros)
- [Configuración de Ciudades](#configuración-de-ciudades)
- [Estructura del Proyecto](#estructura-del-proyecto)

---

## Descripción General

EventWeather Spain es una plataforma de datos que:

1. **Captura periódicamente** información meteorológica y de eventos de tres fuentes externas.
2. **Publica** cada dato como un evento JSON en topics de ActiveMQ.
3. **Persiste** cada evento de forma inmutable en un Event Store basado en ficheros (NDJSON).
4. **Construye un datamart** (SQLite) con una tabla unificada tipo OBT (One Big Table) que centraliza todos los datos.
5. **Expone una API REST** (Javalin, puerto 7070) para consultar y cruzar datos de clima y eventos.

---

## Justificación de APIs y Estructura del Datamart

### Elección de APIs Externas

#### OpenWeather — Current Weather API

Se eligió OpenWeather por ser la API meteorológica de mayor adopción en entornos de desarrollo gracias a su plan gratuito funcional y su cobertura global. Para el objetivo del proyecto —monitorizar el tiempo en 112 ciudades españolas— ofrece todos los campos necesarios (temperatura, viento, humedad, descripción textual) con una latencia baja y una estructura JSON estable. Se descartaron alternativas como **AEMET** (sin SDK oficial) o **WeatherAPI** (campos inconsistentes en el plan gratuito).

**Frecuencia de captura: 6 horas.** El clima en España cambia a escala horaria en períodos de inestabilidad, pero el equilibrio entre coste de peticiones (límite API) y fidelidad de los datos hace que una captura cada 6 horas sea suficiente para el análisis combinado con eventos culturales, que suelen tener una granularidad diaria.

#### PredictHQ — Events Intelligence API

PredictHQ especializa su modelo de datos en el **impacto cuantificado de eventos** sobre una ubicación geográfica. A diferencia de Ticketmaster (orientado a la venta de entradas) o Eventbrite (solo eventos comerciales), PredictHQ agrega fuentes heterogéneas —deportes, conferencias, festivales, ferias, conciertos— y asigna un `rank` de impacto (0–100) que permite analizar qué eventos movilizan más personas. La búsqueda por radio geográfico (`within=Xkm@lat,lon`) encaja perfectamente con la estructura de ciudad+coordenadas del sistema.

#### Ticketmaster — Discovery API

Ticketmaster complementa a PredictHQ cubriendo el segmento de **entretenimiento comercial** (conciertos, musicales, eventos deportivos de taquilla) con datos de disponibilidad real de entradas. Dado que PredictHQ puede no tener eventos muy locales o de pequeño formato, Ticketmaster actúa como segunda fuente para enriquecer el catálogo de eventos. Su API Discovery es gratuita, bien documentada y sin límite de resultados relevante para el volumen del proyecto.

---

### Estructura del Datamart

#### Decisión: One Big Table (OBT) en SQLite

El datamart adopta el patrón **One Big Table (OBT)**: una única tabla `unified_datamart` que consolida datos de las tres fuentes bajo un esquema común. Esta decisión se tomó por las siguientes razones:

| Criterio | OBT (elegido) | Esquema estrella normalizado |
|----------|--------------|-----------------------------|
| **Complejidad de JOINs** | Sin joins — lecturas directas | JOINs entre 3-4 tablas para cada consulta |
| **Velocidad de consulta** | Alta (un solo scan) | Media (joins costosos sin índices optimizados) |
| **Heterogeneidad de datos** | Los campos no comunes quedan `NULL` — aceptable | Requiere tablas separadas o columnas polimórficas |
| **Escalabilidad del equipo** | Una sola migración de esquema | Varias migraciones coordinadas |
| **Objetivo del sistema** | Análisis exploratorio y API REST | OLTP transaccional (no aplica) |

**SQLite** se eligió como motor de base de datos embebido para evitar la dependencia de un servidor de base de datos externo (PostgreSQL, MySQL), manteniendo el sistema autocontenido y reproducible en cualquier entorno. Para el volumen esperado (< 1 M de filas), SQLite ofrece rendimiento suficiente.

**Índices creados:** `fuente`, `ciudad`, `fecha_inicio` — los tres filtros más usados en los endpoints REST.

---

## Arquitectura

El sistema sigue una **arquitectura dirigida por eventos (Event-Driven Architecture)** con los principios CQRS simplificados:

- **Feeders** (productores): Los tres módulos de captura actúan como fuentes de datos que publican eventos en ActiveMQ.
- **Broker**: Apache ActiveMQ gestiona los topics `Weather`, `PredictHQ` y `Ticketmaster` con suscripciones durables.
- **Event Store**: Persiste cada evento en ficheros NDJSON organizados por topic, fuente y fecha.
- **Business Unit** (consumidor): Se suscribe a los tres topics, procesa los eventos y construye un datamart SQLite unificado accesible vía API REST.

### Diagrama de Componentes

```
┌─────────────────────┐   ┌─────────────────────┐   ┌──────────────────────┐
│  openweather-module │   │  predicthq-module   │   │  ticketmaster-module │
│                     │   │                     │   │                      │
│  OpenWeather API    │   │  PredictHQ API      │   │  Ticketmaster API    │
│  Scheduler: 6h     │   │  Scheduler: 24h     │   │  Scheduler: 24h      │
└────────┬────────────┘   └────────┬────────────┘   └──────────┬───────────┘
         │ publish                 │ publish                   │ publish
         ▼                         ▼                           ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                        Apache ActiveMQ (Broker)                           │
│                                                                            │
│  Topics:  Weather  │  PredictHQ  │  Ticketmaster                          │
└────┬──────────────────────┬──────────────────────┬─────────────────────────┘
     │ subscribe            │ subscribe            │ subscribe
     ▼                      ▼                      ▼
┌───────────────────┐  ┌──────────────────────────────────────────────┐
│ event-store-builder│  │              business-unit                   │
│                   │  │                                              │
│ Ficheros NDJSON   │  │  EventParser → DatamartDB (SQLite)          │
│ eventstore/       │  │  API REST (Javalin :7070)                   │
│ {topic}/{ss}/     │  │  EventStoreReader (carga histórico)         │
│  {YYYYMMDD}.events│  └──────────────────────────────────────────────┘
└───────────────────┘
```

### Arquitectura de la Aplicación

Each module follows a consistent internal layered architecture:

```
┌──────────────────────────────────────────────────────┐
│                    MÓDULOS FEEDER                    │
│  (openweather-module / predicthq-module /            │
│   ticketmaster-module)                               │
│                                                      │
│  ┌──────────┐   ┌──────────┐   ┌────────────────┐   │
│  │ Scheduler│──▶│  Service │──▶│ ActiveMQPublish│   │
│  │(Controller│  │ (HTTP +  │   │  (JMS/Topic)   │   │
│  │ @Scheduled)  │  Parser) │   └────────────────┘   │
│  └──────────┘   └────┬─────┘                        │
│                      │ persist                       │
│                 ┌────▼─────┐                         │
│                 │ SQLite DB│ (respaldo local)         │
│                 └──────────┘                         │
└──────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────┐
│                  BUSINESS-UNIT                       │
│                                                      │
│  ┌────────────────────────────────┐                  │
│  │  Subscriber (JMS / durable)    │                  │
│  └───────────────┬────────────────┘                  │
│                  │ onMessage                         │
│  ┌───────────────▼────────────────┐                  │
│  │  EventParser                   │  ← routing layer │
│  │  (Weather / PredictHQ / TM)    │                  │
│  └───────────────┬────────────────┘                  │
│                  │ insert                            │
│  ┌───────────────▼────────────────┐                  │
│  │  DatamartDB (Singleton)        │  ← persistence   │
│  │  SQLite — unified_datamart     │                  │
│  └───────────────────────────────┘                  │
│                                                      │
│  ┌─────────────────────────────────────────────┐     │
│  │  RestApi (Javalin :7070)                    │     │
│  │  ┌──────────────────────────────────────┐   │     │
│  │  │ /api/weather  /api/events  /api/...  │   │     │
│  │  └──────────────────────────────────────┘   │     │
│  │  ResultSetMapper → JSON                     │     │
│  └─────────────────────────────────────────────┘     │
│                                                      │
│  Services:                                           │
│  ├── HistoricalWeatherService (Open-Meteo Archive)   │
│  └── EventWeatherState (predicción por distancia)    │
└──────────────────────────────────────────────────────┘
```

### Flujo de Datos

```
API Externa → Feeder → JSON Event → ActiveMQ Topic → Subscriber → Datamart SQLite → API REST
                                         │
                                         └──→ Event Store (ficheros NDJSON)
```

Cada evento JSON tiene la estructura mínima:
```json
{
  "ts": "2026-05-19T12:00:00Z",
  "ss": "openweather-feeder",
  "campo1": "...",
  "campo2": "..."
}
```

- `ts`: Timestamp UTC (ISO-8601)
- `ss`: Identificador de la fuente/módulo emisor

---

## Módulos

### openweather-module

| Aspecto | Detalle |
|---------|---------|
| **API** | [OpenWeather Current Weather](https://openweathermap.org/current) |
| **Frecuencia** | Cada **6 horas** |
| **Topic** | `Weather` |
| **Datos capturados** | Temperatura, temp_min, temp_max, humedad, velocidad del viento, descripción |
| **Base local** | SQLite (`clima`) — persistencia de respaldo |

**Clases principales:**
- `WeatherController` — Scheduler que orquesta la captura periódica
- `OpenWeatherService` — Cliente HTTP hacia la API de OpenWeather
- `Clima` — Modelo de datos meteorológicos
- `ActiveMQPublisher` — Publicador de eventos en ActiveMQ

---

### predicthq-module

| Aspecto | Detalle |
|---------|---------|
| **API** | [PredictHQ Events](https://www.predicthq.com/apis) |
| **Frecuencia** | Cada **24 horas** |
| **Topic** | `PredictHQ` |
| **Datos capturados** | ID, título, categoría, fechas, coordenadas, impacto (rank) |
| **Búsqueda** | Por radio geográfico (`within=Xkm@lat,lon`) |
| **Base local** | SQLite (`eventos_phq`) — persistencia de respaldo |

**Clases principales:**
- `PredictHQController` — Scheduler + modo manual para una sola ciudad
- `PredictHQService` — Cliente HTTP con parsing del JSON de la API
- `EventoPHQ` — Modelo de datos de eventos PredictHQ

---

### ticketmaster-module

| Aspecto | Detalle |
|---------|---------|
| **API** | [Ticketmaster Discovery API](https://developer.ticketmaster.com/) |
| **Frecuencia** | Cada **24 horas** |
| **Topic** | `Ticketmaster` |
| **Datos capturados** | ID, nombre, fecha, hora, ciudad |
| **Base local** | SQLite (`eventos`) — persistencia de respaldo |

**Clases principales:**
- `TicketmasterController` — Scheduler periódico
- `TicketmasterService` — Cliente HTTP hacia Ticketmaster Discovery
- `Evento` — Modelo de datos de eventos de entretenimiento

---

### event-store-builder

| Aspecto | Detalle |
|---------|---------|
| **Función** | Persistencia inmutable de **todos** los eventos recibidos |
| **Formato** | NDJSON (una línea JSON por evento) |
| **Estructura** | `eventstore/{topic}/{ss}/{YYYYMMDD}.events` |
| **Suscripción** | Durable (recupera mensajes no consumidos al reiniciar) |

**Clases principales:**
- `EventStoreSubscriber` — Suscriptor durable a los tres topics
- `EventStore` — Escribe cada evento como append-only en el fichero correspondiente

---

### business-unit

| Aspecto | Detalle |
|---------|---------|
| **Función** | Datamart unificado + API REST |
| **Base de datos** | SQLite (`datamart/business_unit.db`) |
| **Esquema** | One Big Table (`unified_datamart`) |
| **API** | Javalin en puerto **7070** |
| **Arranque** | Carga histórico del Event Store + suscripción en tiempo real |

**Clases principales:**
- `Main` — Punto de entrada: carga histórico (`EventStoreReader`), arranca API REST y conecta al broker vía `ActiveMQSubscriber`
- `RestApi` — Definición de 11 endpoints REST (incluye `/api/debug/ciudades`)
- `ResultSetMapper` — Convierte ResultSets SQL a listas de mapas (JSON-ready)
- `EventParser` — Router que clasifica y parsea eventos de las tres fuentes
- `DatamartDB` — Singleton con la conexión SQLite y todas las queries
- `CityResolver` — Resolución inversa de coordenadas a nombre de ciudad
- `EventStoreReader` — Carga batch de ficheros `.events` al arrancar
- `ActiveMQSubscriber` — Suscriptor JMS activo en el arranque (usado por `Main`)
- `BusinessUnitSubscriber` — Suscriptor durable alternativo con reconexión exponencial (no usado en el arranque por defecto)

**Servicios (`services/`):**
- `HistoricalWeatherService` — Estimación meteorológica basada en medias históricas de los últimos 5 años usando la API Open-Meteo Archive. Consulta datos de temperatura y precipitación de la misma fecha en años anteriores para generar una predicción aproximada. Incluye resolución de coordenadas por ciudad a partir de `cities.properties`.
- `EventWeatherState` — Clasificador del estado de predicción meteorológica según la distancia temporal al evento: `PRONOSTICO_CONFIRMADO` (< 5 días), `TENDENCIA_GENERAL` (5–14 días) o `PREDICCION_HISTORICA` (> 14 días).

**Records (DTOs):**
- `WeatherRecord` — Datos meteorológicos
- `PredictHQRecord` — Datos de eventos PredictHQ
- `TicketmasterRecord` — Datos de eventos Ticketmaster

---

## Tecnologías Utilizadas

| Tecnología | Versión | Uso |
|-----------|---------|-----|
| **Java** | 21 | Lenguaje principal |
| **Maven** | 3.x | Build system multi-módulo |
| **Apache ActiveMQ** | 5.15.12 (client) | Broker de mensajería (JMS) |
| **Javalin** | 6.1.3 | API REST ligera |
| **SQLite** | 3.45.1.0 (JDBC) | Base de datos embebida |
| **OkHttp** | 4.12.0 | Cliente HTTP |
| **Gson** | 2.10.1 | Serialización/deserialización JSON |
| **Jackson** | 2.17.0 | Serialización JSON para Javalin |
| **SLF4J** | 2.0.12 | Logging |

---

## Requisitos Previos

- **JDK 21** o superior ([descarga](https://jdk.java.net/21/))
- **Apache Maven 3.8+** ([descarga](https://maven.apache.org/download.cgi))
- **Apache ActiveMQ 5.x** ([descarga](https://activemq.apache.org/components/classic/download/))
- API Keys para:
  - [OpenWeather](https://openweathermap.org/api) (plan gratuito disponible)
  - [PredictHQ](https://www.predicthq.com/apis) (cuenta de desarrollador)
  - [Ticketmaster](https://developer.ticketmaster.com/) (plan gratuito disponible)

---

## Instalación y Configuración

### 1. Clonar el Repositorio

```bash
git clone https://github.com/<tu-usuario>/EventWeather-Spain.git
cd EventWeather-Spain
```

### 2. Instalar Apache ActiveMQ

1. Descargar ActiveMQ Classic desde [activemq.apache.org](https://activemq.apache.org/components/classic/download/)
2. Extraer y ejecutar:

```bash
# Linux/Mac
cd apache-activemq-5.x.x/bin
./activemq start

# Windows
cd apache-activemq-5.x.x\bin
activemq.bat start
```

3. Verificar que está corriendo en `http://localhost:8161/admin` (usuario/contraseña por defecto: `admin/admin`)
4. El broker escucha en `tcp://localhost:61616`

### 3. Obtener API Keys

| Servicio | URL de Registro | Variable de Config |
|----------|----------------|--------------------|
| OpenWeather | https://home.openweathermap.org/users/sign_up | `OPENWEATHER_KEY` |
| PredictHQ | https://signup.predicthq.com/ | `PREDICTHQ_TOKEN` |
| Ticketmaster | https://developer.ticketmaster.com/ | `TICKETMASTER_KEY` |

### 4. Configurar cada Módulo

Cada módulo necesita un fichero `config.properties` en su carpeta `src/main/resources/`. Estos ficheros están en `.gitignore` por seguridad.

**openweather-module/src/main/resources/config.properties:**
```properties
OPENWEATHER_KEY=tu_api_key_aqui
ACTIVEMQ_URL=tcp://localhost:61616
DB_URL=jdbc:sqlite:weather.db
```

**predicthq-module/src/main/resources/config.properties:**
```properties
PREDICTHQ_TOKEN=tu_token_aqui
PREDICTHQ_CATEGORIES=conferences,concerts,festivals,sports,performing-arts
ACTIVEMQ_URL=tcp://localhost:61616
DB_URL=jdbc:sqlite:predicthq.db
```

**ticketmaster-module/src/main/resources/config.properties:**
```properties
TICKETMASTER_KEY=tu_api_key_aqui
ACTIVEMQ_URL=tcp://localhost:61616
DB_URL=jdbc:sqlite:ticketmaster.db
```

**event-store-builder/src/main/resources/config.properties:**
```properties
ACTIVEMQ_URL=tcp://localhost:61616
```

**business-unit/src/main/resources/config.properties:**
```properties
activemq.url=tcp://localhost:61616
ACTIVEMQ_URL=tcp://localhost:61616
API_PORT=7070
datamart.path=datamart/business_unit.db
event.store.path=../event-store-builder/eventstore
```

### 5. Compilar el Proyecto

Desde la raíz del proyecto:

```bash
mvn clean install
```

Esto compilará los 5 módulos y descargará todas las dependencias necesarias.

---

## Ejecución

### Orden de Arranque

Es importante respetar el siguiente orden para evitar pérdida de mensajes:

```
1. Apache ActiveMQ         (broker de mensajería)
2. event-store-builder     (persistencia de eventos)
3. business-unit           (datamart + API REST)
4. openweather-module      (feeder meteorológico)
5. predicthq-module        (feeder de eventos PredictHQ)
6. ticketmaster-module     (feeder de eventos Ticketmaster)
```

### Ejecutar cada Módulo

Cada módulo se ejecuta de forma independiente en su propia terminal:

```bash
# Terminal 1 — Event Store Builder
cd event-store-builder
mvn exec:java -Dexec.mainClass="com.thebiggestapp.app.Main"

# Terminal 2 — Business Unit (API REST)
cd business-unit
mvn exec:java -Dexec.mainClass="com.thebiggestapp.app.Main"

# Terminal 3 — OpenWeather Feeder
cd openweather-module
mvn exec:java -Dexec.mainClass="com.thebiggestapp.app.Main"

# Terminal 4 — PredictHQ Feeder
cd predicthq-module
mvn exec:java -Dexec.mainClass="com.thebiggestapp.app.Main"

# Terminal 5 — Ticketmaster Feeder
cd ticketmaster-module
mvn exec:java -Dexec.mainClass="com.thebiggestapp.app.Main"
```

Alternativamente, puedes ejecutar cada módulo desde tu IDE (IntelliJ IDEA, Eclipse) ejecutando la clase `Main` de cada módulo.

---

## API REST — Endpoints

La API se expone en `http://localhost:7070` y devuelve JSON.

| Método | Endpoint | Descripción | Parámetros |
|--------|----------|-------------|------------|
| GET | `/` | Lista de endpoints disponibles | — |
| GET | `/api/status` | Resumen del datamart (registros por fuente, devuelve lista) | — |
| GET | `/api/weather` | Todo el clima almacenado | — |
| GET | `/api/weather/{ciudad}` | Clima filtrado por ciudad | `ciudad` (path) |
| GET | `/api/events/impact` | Eventos PredictHQ por impacto mínimo | `min` (query, default 0), `ciudad` (query, opcional) |
| GET | `/api/events/categories` | Categorías PredictHQ con totales y media de impacto | — |
| GET | `/api/events/category/{cat}` | Eventos PredictHQ filtrados por categoría | `cat` (path) |
| GET | `/api/events/entertainment` | Eventos Ticketmaster | `ciudad` (query, opcional), `fecha` (query, opcional) |
| GET | `/api/analysis/top-cities` | Ciudades PredictHQ con más actividad | `limit` (query, default 5) |
| GET | `/api/analysis/weather-vs-events/{ciudad}` | Análisis combinado: clima + todos los eventos de una ciudad | `ciudad` (path) |
| GET | `/api/analysis/events-with-weather` | Eventos enriquecidos con datos de clima (JOIN) | `ciudad` (query, opcional), `fecha` (query, YYYY-MM-DD, opcional) |
| GET | `/api/debug/ciudades` | **Debug** — recuento de registros agrupado por fuente y ciudad | — |

### Ejemplos de Uso

#### Estado del datamart
```bash
curl http://localhost:7070/api/status
```
```json
[
  { "tabla": "WEATHER",      "total": 1120 },
  { "tabla": "PREDICTHQ",    "total": 1890 },
  { "tabla": "TICKETMASTER", "total": 530  }
]
```

#### Clima actual en Madrid
```bash
curl http://localhost:7070/api/weather/Madrid
```
```json
[
  {
    "id": "W_Madrid_2026-05-19T12:00:00Z",
    "fuente": "WEATHER",
    "ciudad": "Madrid",
    "titulo": "clear sky",
    "temperatura": 24.3,
    "temp_min": 18.1,
    "temp_max": 27.8,
    "humidity": 32,
    "wind_speed": 3.5,
    "ts": "2026-05-19T12:00:00Z"
  }
]
```

#### Top 10 ciudades con más actividad (PredictHQ)
```bash
curl http://localhost:7070/api/analysis/top-cities?limit=10
```
```json
[
  { "ciudad": "Madrid",    "total_eventos": 312, "avg_impacto": 54.2 },
  { "ciudad": "Barcelona", "total_eventos": 287, "avg_impacto": 61.8 },
  { "ciudad": "Sevilla",   "total_eventos": 145, "avg_impacto": 48.5 }
]
```

#### Eventos enriquecidos con clima (JOIN)
```bash
curl "http://localhost:7070/api/analysis/events-with-weather?ciudad=Sevilla&fecha=2026-05-19"
```
```json
[
  {
    "id": "phq-abc123",
    "ciudad": "Sevilla",
    "titulo": "Feria de Abril",
    "fecha_inicio": "2026-05-19",
    "fuente": "PREDICTHQ",
    "temperatura": 31.2,
    "temp_min": 28.0,
    "temp_max": 34.5,
    "humedad": 28,
    "viento": 2.1,
    "tiempo": "sunny",
    "weather_state": "PRONOSTICO_CONFIRMADO"
  }
]
```

> **Nota:** El endpoint `events-with-weather` expone los campos `id`, `ciudad`, `titulo`, `fecha_inicio`, `fuente`, y los datos de clima (`temperatura`, `temp_min`, `temp_max`, `humedad`, `viento`, `tiempo`). Los campos `impacto`, `venue` y `url` existen en la tabla `unified_datamart` pero **no se proyectan en este JOIN** por diseño.

#### Eventos por categoría
```bash
curl http://localhost:7070/api/events/category/concerts
```
```json
[
  {
    "ciudad": "Barcelona",
    "titulo": "Primavera Sound 2026",
    "fecha_inicio": "2026-05-29",
    "fecha_fin": "2026-06-02",
    "impacto": 92
  }
]
```

#### Depuración: registros por fuente y ciudad
```bash
curl http://localhost:7070/api/debug/ciudades
```
```json
[
  { "fuente": "PREDICTHQ",    "ciudad": "Madrid",    "total": 312 },
  { "fuente": "TICKETMASTER", "ciudad": "Barcelona", "total": 47  },
  { "fuente": "WEATHER",      "ciudad": "Sevilla",   "total": 8   }
]
```

---

## Modelo de Datos

### Tabla Unificada (OBT)

El datamart usa una única tabla `unified_datamart` que almacena los tres tipos de datos diferenciados por la columna `fuente`:

| Columna | Tipo | Fuentes que la usan |
|---------|------|---------------------|
| `id` | TEXT (PK) | Todas |
| `fuente` | TEXT | `WEATHER`, `PREDICTHQ`, `TICKETMASTER` |
| `ts` | TEXT | Todas |
| `ciudad` | TEXT | Todas |
| `titulo` | TEXT | Todas (descripción del clima / nombre del evento) |
| `categoria` | TEXT | PredictHQ, Ticketmaster |
| `fecha_inicio` | TEXT | PredictHQ, Ticketmaster |
| `fecha_fin` | TEXT | PredictHQ |
| `latitud` | REAL | PredictHQ |
| `longitud` | REAL | PredictHQ |
| `temperatura` | REAL | Weather |
| `temp_min` | REAL | Weather |
| `temp_max` | REAL | Weather |
| `wind_speed` | REAL | Weather |
| `humidity` | INTEGER | Weather |
| `impacto` | INTEGER | PredictHQ |
| `venue` | TEXT | Ticketmaster |
| `url` | TEXT | Ticketmaster |
| `ss` | TEXT | Todas |

**Índices:** `fuente`, `ciudad`, `fecha_inicio`

### Event Store (Sistema de Ficheros)

```
eventstore/
├── Weather/
│   └── openweather-feeder/
│       ├── 20260518.events
│       └── 20260519.events
├── PredictHQ/
│   └── predicthq-feeder/
│       └── 20260519.events
└── Ticketmaster/
    └── ticketmaster-feeder/
        └── 20260519.events
```

Cada fichero `.events` contiene una línea JSON por evento (formato NDJSON). Las escrituras son **append-only** e inmutables.

---

## Configuración de Ciudades

El fichero `cities.properties` (ubicado en `src/main/resources/` de cada módulo) define las **112 ciudades** de España cubiertas por el sistema. El formato es:

```properties
NombreCiudad=latitud,longitud,radio_km
```

Ejemplo:
```properties
Madrid=40.4168,-3.7038,30
Barcelona=41.3851,2.1734,30
Sevilla=37.3891,-5.9845,30
```

- Los guiones bajos en los nombres se reemplazan por espacios (`Palma_de_Mallorca` → `Palma de Mallorca`)
- El radio (en km) se usa para búsquedas geográficas en PredictHQ

Las ciudades están organizadas por comunidad autónoma e incluyen las capitales de provincia y las principales ciudades de cada región.

---

## Principios y Patrones de Diseño

### Patrones Arquitectónicos Globales

| Patrón | Descripción | Dónde se aplica |
|--------|-------------|------------------|
| **Event-Driven Architecture (EDA)** | Los módulos se comunican exclusivamente a través de eventos publicados en topics de ActiveMQ. Ningún módulo llama directamente a otro. | Todo el sistema |
| **CQRS simplificado** | Los feeders solo escriben (comandos); la business-unit solo lee y sirve datos (queries). Las responsabilidades de escritura y lectura están separadas en módulos distintos. | Feeders vs. business-unit |
| **Event Sourcing (parcial)** | Cada evento publicado se persiste de forma inmutable en el Event Store antes de ser procesado. Esto permite reconstruir el estado del datamart desde cero relanzando los `.events`. | event-store-builder |
| **One Big Table (OBT)** | El datamart agrupa datos heterogéneos (clima, PredictHQ, Ticketmaster) en una única tabla con columnas `NULL` para campos no aplicables, simplificando las consultas analíticas. | DatamartDB |

---

### Patrones por Módulo

#### openweather-module / predicthq-module / ticketmaster-module

| Patrón | Implementación |
|--------|----------------|
| **Scheduler (Active Object)** | `WeatherController`, `PredictHQController` y `TicketmasterController` usan `ScheduledExecutorService` para ejecutar la captura periódicamente sin bloquear el hilo principal. |
| **Service Layer** | `OpenWeatherService`, `PredictHQService` y `TicketmasterService` encapsulan toda la lógica de comunicación HTTP (OkHttp) y el parsing JSON (Gson), desacoplándola del scheduler. |
| **Publisher-Subscriber** | `ActiveMQPublisher` publica mensajes JMS en un topic sin conocer quién los consume (desacoplamiento total). |
| **Repository (SQLite local)** | `DatabaseManager` actúa como repositorio de respaldo local, aislando la lógica de persistencia de la lógica de negocio. |
| **Value Object / Model** | `Clima`, `EventoPHQ` y `Evento` son modelos de datos inmutables que transportan los datos parseados entre capas. |

#### event-store-builder

| Patrón | Implementación |
|--------|----------------|
| **Durable Subscriber** | `EventStoreSubscriber` usa suscripciones JMS durables (`createDurableSubscriber`) para garantizar que ningún evento se pierde aunque el módulo esté detenido temporalmente. |
| **Append-Only Log** | `EventStore` escribe cada evento como una nueva línea al final del fichero `.events` correspondiente. Nunca modifica ni elimina líneas existentes, garantizando inmutabilidad. |
| **Strategy (ruta de almacenamiento)** | La ruta `eventstore/{topic}/{ss}/{YYYYMMDD}.events` encapsula la estrategia de particionado por topic, fuente y fecha, facilitando búsquedas históricas eficientes. |

#### business-unit

| Patrón | Implementación |
|--------|----------------|
| **Singleton** | `DatamartDB` implementa el patrón Singleton para garantizar una única conexión SQLite compartida por todos los componentes, evitando conflictos de concurrencia en escrituras. |
| **Router / Chain of Responsibility** | `EventParser` examina el campo `ss` de cada evento y delega el parsing al parser concreto (`WeatherRecord`, `PredictHQRecord` o `TicketmasterRecord`). Añadir una nueva fuente solo requiere extender el router. |
| **DTO (Data Transfer Object)** | `WeatherRecord`, `PredictHQRecord` y `TicketmasterRecord` son Java Records que actúan como DTOs inmutables para transferir datos parseados desde los eventos JSON al datamart. |
| **Mapper** | `ResultSetMapper` transforma los `ResultSet` JDBC en listas de `Map<String, Object>` serializables a JSON por Javalin, desacoplando la capa de persistencia de la capa de presentación HTTP. |
| **Exponential Backoff (Retry)** | `BusinessUnitSubscriber` implementa reconexión automática con espera exponencial ante fallos de conexión con ActiveMQ, aumentando la resiliencia del sistema. Disponible como alternativa a `ActiveMQSubscriber`. |
| **Batch Loader** | `EventStoreReader` carga en bloque todos los ficheros `.events` existentes al arrancar la business-unit, garantizando que el datamart refleja el histórico completo antes de servir peticiones. |
| **Utility / Strategy (clasificador)** | `EventWeatherState` es una clase utilitaria estática que aplica lógica condicional para clasificar el estado meteorológico (`PRONOSTICO_CONFIRMADO`, `TENDENCIA_GENERAL`, `PREDICCION_HISTORICA`) según la distancia en días al evento. |
| **Facade** | `HistoricalWeatherService` actúa como fachada frente a la API Open-Meteo Archive, ocultando la complejidad de construcción de URLs, petición HTTP, parsing y cálculo de medias históricas. |

---

## Estructura del Proyecto

```
EventWeather-Spain/
├── pom.xml                          # POM padre (multi-módulo)
├── .gitignore
├── README.md
│
├── openweather-module/              # Feeder OpenWeather
│   ├── pom.xml
│   └── src/main/java/.../
│       ├── Main.java
│       ├── config/Config.java
│       ├── model/Clima.java
│       ├── model/Event.java
│       ├── persistence/DatabaseManager.java
│       ├── publisher/ActiveMQPublisher.java
│       ├── scheduler/WeatherController.java
│       └── services/OpenWeatherService.java
│
├── predicthq-module/                # Feeder PredictHQ
│   ├── pom.xml
│   └── src/main/java/.../
│       ├── Main.java
│       ├── config/Config.java
│       ├── model/Event.java
│       ├── model/EventoPHQ.java
│       ├── persistence/DatabaseManager.java
│       ├── publisher/ActiveMQPublisher.java
│       ├── scheduler/PredictHQController.java
│       └── services/PredictHQService.java
│
├── ticketmaster-module/             # Feeder Ticketmaster
│   ├── pom.xml
│   └── src/main/java/.../
│       ├── Main.java
│       ├── config/Config.java
│       ├── model/Event.java
│       ├── model/Evento.java
│       ├── persistence/DatabaseManager.java
│       ├── publisher/ActiveMQPublisher.java
│       ├── scheduler/TicketmasterController.java
│       └── services/TicketmasterService.java
│
├── event-store-builder/             # Persistencia inmutable
│   ├── pom.xml
│   └── src/main/java/.../
│       ├── Main.java
│       ├── config/Config.java
│       ├── store/EventStore.java
│       └── subscriber/EventStoreSubscriber.java
│
└── business-unit/                   # Datamart + API REST
    ├── pom.xml
    └── src/main/java/.../
        ├── Main.java
        ├── api/RestApi.java
        ├── api/ResultSetMapper.java
        ├── broker/ActiveMQSubscriber.java
        ├── config/Config.java
        ├── datamart/CityResolver.java
        ├── datamart/DatamartDB.java
        ├── datamart/EventParser.java
        ├── datamart/PredictHQRecord.java
        ├── datamart/TicketmasterRecord.java
        ├── datamart/WeatherRecord.java
        ├── services/EventWeatherState.java
        ├── services/HistoricalWeatherService.java
        ├── store/EventStoreReader.java
        └── subscriber/BusinessUnitSubscriber.java
```

---
