# EventWeather Spain

Sistema distribuido basado en eventos para la captura, almacenamiento y consulta de **datos meteorológicos y de eventos culturales/deportivos** en ciudades de España. Integra tres APIs externas (OpenWeather, PredictHQ y Ticketmaster) mediante un broker de mensajería ActiveMQ y expone los datos unificados a través de una API REST.

---

## Tabla de Contenidos

- [Descripción General](#descripción-general)
- [Arquitectura](#arquitectura)
  - [Diagrama de Componentes](#diagrama-de-componentes)
  - [Flujo de Datos](#flujo-de-datos)
- [Módulos](#módulos)
  - [openweather-module](#openweather-module)
  - [predicthq-module](#predicthq-module)
  - [ticketmaster-module](#ticketmaster-module)
  - [event-store-builder](#event-store-builder)
  - [business-unit](#business-unit)
- [Tecnologías Utilizadas](#tecnologías-utilizadas)
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
- `Main` — Punto de entrada: carga histórico, arranca API, conecta al broker
- `RestApi` — Definición de 10 endpoints REST
- `ResultSetMapper` — Convierte ResultSets SQL a listas de mapas (JSON-ready)
- `EventParser` — Router que clasifica y parsea eventos de las tres fuentes
- `DatamartDB` — Singleton con la conexión SQLite y todas las queries
- `CityResolver` — Resolución inversa de coordenadas a nombre de ciudad
- `EventStoreReader` — Carga batch de ficheros `.events` al arrancar
- `BusinessUnitSubscriber` — Suscriptor durable con reconexión exponencial
- `ActiveMQSubscriber` — Suscriptor no-durable (alternativo)

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
| GET | `/api/status` | Resumen del datamart (registros por fuente) | — |
| GET | `/api/weather` | Todo el clima almacenado | — |
| GET | `/api/weather/{ciudad}` | Clima filtrado por ciudad | `ciudad` (path) |
| GET | `/api/events/impact` | Eventos PredictHQ por impacto mínimo | `min` (query, default 0), `ciudad` (query, opcional) |
| GET | `/api/events/categories` | Categorías PredictHQ con totales y media de impacto | — |
| GET | `/api/events/category/{cat}` | Eventos PredictHQ filtrados por categoría | `cat` (path) |
| GET | `/api/events/entertainment` | Eventos Ticketmaster | `ciudad` (query, opcional), `fecha` (query, opcional) |
| GET | `/api/analysis/top-cities` | Ciudades con más actividad | `limit` (query, default 5) |
| GET | `/api/analysis/weather-vs-events/{ciudad}` | Análisis combinado: clima + todos los eventos de una ciudad | `ciudad` (path) |
| GET | `/api/analysis/events-with-weather` | Eventos enriquecidos con datos de clima (JOIN) | `ciudad` (query, opcional), `fecha` (query, YYYY-MM-DD, opcional) |

### Ejemplos de Uso

```bash
# Estado del datamart
http://localhost:7070/api/status

# Clima en Madrid
http://localhost:7070/api/weather/Madrid

# Top 10 ciudades con más eventos
http://localhost:7070/api/analysis/top-cities?limit=10

# Eventos con clima para Sevilla en un día especifico
http://localhost:7070/api/analysis/events-with-weather?ciudad=Sevilla&fecha=2026-05-19
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
