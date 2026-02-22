# OpenAI Spring Boot Demo

A Spring Boot 3 application that demonstrates OpenAI-powered capabilities:

- Chat completion
- Prompt templates with variables
- Structured output (JSON schema + POJO mapping)
- Function calling / tools
- RAG (load PDF/HTML/text and ask with retrieval)
- Streaming chat completion (token stream)
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

### 2) Prompt Template with Variables

**Endpoint**: `POST /ask/template`

**Parameters**:

- `topic` (string)
- `audience` (string)
- `tone` (string)

**How it works**:

This endpoint uses a prompt template with placeholders and binds values at runtime.

Template used in code:

```text
Explain {topic} for {audience} in a {tone} tone.
Keep the answer in 5 bullet points and include one practical example.
```

Variable binding in code:

- `{topic}` -> `topic`
- `{audience}` -> `audience`
- `{tone}` -> `tone`

**Example**:

```bash
curl -X POST "http://localhost:8080/ask/template" \
  -d "topic=vector embeddings" \
  -d "audience=backend developers" \
  -d "tone=practical"
```

**Success Response**:

- `200 OK`
- Body: generated explanation using your variable values

---

### 3) Celebrity Details (Structured Output)

**Endpoint**: `POST /ask/celebrity`

**Parameter**:

- `name` (string): celebrity name

**What it does**:

1. Prompts the model to return JSON only.
2. Validates model JSON against a JSON Schema.
3. Maps validated JSON into a POJO (`CelebrityDetails`).

**Example**:

```bash
curl -X POST "http://localhost:8080/ask/celebrity" \
  -d "name=Shah Rukh Khan"
```

**Sample response**:

```json
{
  "name": "Shah Rukh Khan",
  "profession": "Actor, Film Producer",
  "nationality": "Indian",
  "birthDate": "1965-11-02",
  "knownFor": ["Bollywood films", "Global fanbase"],
  "notableWorks": ["Dilwale Dulhania Le Jayenge", "Swades", "Chak De! India"],
  "awards": ["Padma Shri"],
  "summary": "One of the most influential Indian film actors with a decades-long career."
}
```

---

### 4) Function Calling / Tools

**Endpoint**: `POST /ask/tools`

**Parameter**:

- `request` (string)

**What it does**:

Uses Spring AI `@Tool` methods during chat completion. The model can call tools to fetch celebrity birth year and profession, then compose a final answer.

Available tools in code:

- `getCelebrityBirthYear(name)`
- `getCelebrityProfession(name)`

**Example**:

```bash
curl -X POST "http://localhost:8080/ask/tools" \
  -d "request=Tell me the profession and birth year of Shah Rukh Khan"
```

---

### 5) Chat Completion (Streaming)

**Endpoint**: `POST /ask/stream`

**Parameter**:

- `request` (string): user prompt

**What it does**:

Returns model output incrementally as a stream (`text/event-stream`) instead of waiting for full completion.

**Example (curl, no buffering)**:

```bash
curl -N -X POST "http://localhost:8080/ask/stream" \
  -d "request=Write a short poem about Java"
```

**Example (PowerShell)**:

```powershell
Invoke-WebRequest -Method Post -Uri "http://localhost:8080/ask/stream" -Body @{ request = "Explain streaming in GenAI" }
```

**Success Response**:

- `200 OK`
- `Content-Type: text/event-stream`
- Body: streamed text chunks

---

### 6) RAG: Load Documents

**Endpoint**: `POST /rag/load`

**Parameter**:

- `path` (string): folder path containing documents

**Supported files**:

- `.pdf`, `.html`, `.htm`, `.txt`, `.md`

**Example**:

```bash
curl -X POST "http://localhost:8080/rag/load" \
  -d "path=C:/Users/prati/intelij_workspace/openai/docs"
```

---

### 7) RAG: Ask

**Endpoint**: `POST /rag/ask`

**Parameters**:

- `question` (string)
- `topK` (int, optional, default `4`)

**Example**:

```bash
curl -X POST "http://localhost:8080/rag/ask" \
  -d "question=Summarize pricing conditions" \
  -d "topK=4"
```

---

### 8) Create Embedding + Save to DB

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

### 9) Generate Image

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

### 10) Generate Speech (Text-to-Speech)

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
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/ask/template" -Body @{ topic = "prompt templates"; audience = "Java developers"; tone = "simple" }
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/ask/celebrity" -Body @{ name = "Shah Rukh Khan" }
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/ask/tools" -Body @{ request = "What is the profession and birth year of Tom Cruise?" }
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/rag/load" -Body @{ path = "C:/Users/prati/intelij_workspace/openai/docs" }
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/rag/ask" -Body @{ question = "Give me a summary from loaded docs"; topK = 4 }
curl -N -X POST "http://localhost:8080/ask/stream" -d "request=Stream a short answer"
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/embed" -Body @{ request = "Store this sentence in vector DB" }
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/image" -Body @{ prompt = "A watercolor painting of mountains" }
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/speech" -Body @{ prompt = "This is a generated voice sample" }
```