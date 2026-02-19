# OpenAI Spring Boot Demo

A Spring Boot 3 application that demonstrates four OpenAI-powered capabilities:

- Chat completion
- Text embeddings stored in PostgreSQL (`pgvector`)
- Image generation and local file save
- Text-to-speech generation and local audio file save

## Tech Stack

- Java 21
- Spring Boot 3.5.x
- Spring AI OpenAI starter
- PostgreSQL + `pgvector`

## Project Structure

- `src/main/java/com/example/openai/controllers/ChatController.java` — REST endpoints
- `src/main/java/com/example/openai/services/OpenAiChatService.java` — OpenAI and DB logic
- `src/main/resources/application.properties` — model and datasource configuration
- `src/main/resources/schema.sql` — `documents` table and `vector` extension

## Prerequisites

1. Java 21 installed
2. PostgreSQL running on `localhost:5432`
3. Database `vectordb` created
4. `pgvector` extension available in PostgreSQL
5. OpenAI API key in environment variable `OPENAI_KEY`

Example (PowerShell):

```powershell
$env:OPENAI_KEY="your_openai_api_key"
```

## Database Setup

This app auto-runs `schema.sql` on startup (`spring.sql.init.mode=always`).

`schema.sql` creates:

```sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS documents (
    id SERIAL PRIMARY KEY,
    content TEXT,
    embedding vector(1536)
);
```

## Configuration

From `application.properties`:

- Chat model: `gpt-4o-mini`
- Image model: `gpt-image-1`
- TTS model: `gpt-4o-mini-tts`
- TTS voice: `alloy`
- TTS format: `mp3`

Datasource defaults:

- URL: `jdbc:postgresql://localhost:5432/vectordb`
- Username: `postgres`
- Password: `postgres`

## Run the Application

```bash
./gradlew bootRun
```

On Windows:

```powershell
.\gradlew.bat bootRun
```

Default server URL: `http://localhost:8080`

## API Details

All endpoints are `POST` endpoints and accept request parameters (`@RequestParam`), not JSON body.

---

### 1) Chat Completion

**Endpoint**: `POST /ask`

**Parameter**:

- `request` (string): user prompt

**Example**:

```bash
curl -X POST "http://localhost:8080/ask" \
  -d "request=Explain Spring AI in simple words"
```

**Success Response**:

- `200 OK`
- Body: plain text model response

**Possible fallback responses**:

- `No output returned by model.`
- `Model returned empty content.`

---

### 2) Create Embedding + Save to DB

**Endpoint**: `POST /embed`

**Parameter**:

- `request` (string): text to embed

**What it does**:

1. Generates embedding vector using OpenAI embedding model.
2. Stores `content` and `embedding` in `documents` table.
3. Returns the embedding as `float[]` JSON array.

**Example**:

```bash
curl -X POST "http://localhost:8080/embed" \
  -d "request=Spring AI supports embedding models"
```

**Success Response**:

- `200 OK`
- Body: JSON array of floats (length typically 1536)

---

### 3) Generate Image

**Endpoint**: `POST /image`

**Parameter**:

- `prompt` (string): image generation prompt

**What it does**:

1. Calls image model (`gpt-image-1`).
2. Reads either image URL or base64 payload from model response.
3. Saves image to `generated-images/` with timestamped filename.
4. Returns saved file path message.

**Example**:

```bash
curl -X POST "http://localhost:8080/image" \
  -d "prompt=A futuristic city skyline at sunset"
```

**Success Response**:

- `200 OK`
- Body example: `Saved image to: C:\...\generated-images\A_futuristic_city_skyline_at_sunset_20260220_102500.png`

**Possible fallback responses**:

- `No image returned by model.`
- `Image URL or base64 data not returned by model.`
- `Failed to save image: <reason>`

---

### 4) Generate Speech (Text-to-Speech)

**Endpoint**: `POST /speech`

**Parameter**:

- `prompt` (string): text to convert to speech

**What it does**:

1. Calls OpenAI Audio Speech API (`/v1/audio/speech`) with configured model/voice.
2. Saves MP3 file to `generated-audio/` with timestamped filename.
3. Returns saved file path message.

**Example**:

```bash
curl -X POST "http://localhost:8080/speech" \
  -d "prompt=Hello from Spring AI text to speech"
```

**Success Response**:

- `200 OK`
- Body example: `Saved audio to: C:\...\generated-audio\Hello_from_Spring_AI_text_to_speech_20260220_102609.mp3`

**Possible fallback responses**:

- `OPENAI_KEY is not set.`
- `Failed to generate audio: HTTP <status_code>`
- `Failed to save audio: <reason>`

## Notes

- Generated files are written relative to the app working directory.
- Filenames are sanitized and truncated for filesystem safety.
- If needed, update model names and options in `application.properties`.

## Quick Test Commands (PowerShell)

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/ask" -Body @{ request = "What is vector embedding?" }
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/embed" -Body @{ request = "Store this sentence in vector DB" }
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/image" -Body @{ prompt = "A watercolor painting of mountains" }
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/speech" -Body @{ prompt = "This is a generated voice sample" }
```