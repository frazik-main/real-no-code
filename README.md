# Real No Code

Spring Boot app that generates standalone HTML pages from prompts and lets you continue exploring by clicking elements on the generated page.
It uses the web server's request/response cycle to turn natural-language input into HTML interfaces for each request.

See `ROADMAP.md` for the current (non-final) implementation roadmap.

## Providers
The generator supports three provider values:

- `copilot` (Copilot CLI)
- `gemini` (Google Gemini API)
- `local` (LM Studio OpenAI-compatible endpoint)

Provider can be selected in the launcher UI or sent as `provider` in POST requests.

## Configuration
Tracked config in `src/main/resources/application.properties` reads real values from environment variables or a local `.env` file.

### Local setup
1. Copy `.env.example` to `.env`
2. Fill in only the values you need, especially `AI_GEMINI_API_KEY`
3. Keep `.env` private — it is ignored by git

Example `.env` values:

```dotenv
AI_DEFAULT_PROVIDER=copilot
AI_TIMEOUT_SECONDS=300
AI_COPILOT_COMMAND=copilot
AI_GEMINI_API_KEY=your-real-key-here
AI_GEMINI_MODEL=gemini-1.5-flash
AI_LM_STUDIO_BASE_URL=http://localhost:1234
AI_LM_STUDIO_MODEL=local-model
```

You can also use real OS environment variables instead of `.env`.

## Run

### Local (Gradle)
```bat
gradlew.bat test
gradlew.bat bootRun
```

Then open `http://localhost:8080`.

### Docker

Build and run with a single command:

```bash
docker build -t real-no-code .
docker run --rm -p 8080:8080 \
  -e AI_DEFAULT_PROVIDER=gemini \
  -e AI_GEMINI_API_KEY=your-real-key-here \
  real-no-code
```

Then open `http://localhost:8080`.

### Docker Compose (recommended)

1. Copy `.env.example` to `.env` and fill in the values you need (at minimum `AI_GEMINI_API_KEY` when using the Gemini provider).
2. Start the app:

```bash
docker compose up --build
```

3. Open `http://localhost:8080`.

> **Local LM Studio:** when using `AI_DEFAULT_PROVIDER=local` the compose file automatically points `AI_LM_STUDIO_BASE_URL` to `http://host.docker.internal:1234`, so your host-side LM Studio instance is reachable from inside the container without any extra configuration.

## Controllers
- `/` controller (`CopilotController`): serves the main launcher UI (`GET /`) and generates pages from form input (`POST /generate`).
- `/business` controller (`BusinessController`): serves business-mode UI routes (`GET /business` and `GET /business/**`) and generates business idea pages (`POST /business/generate`).


# 💠 Project Real No Code (PoC)

> **The UI is the Runtime.** > software where backends don't exist, and interfaces are generated as "pure" HTML/JS on-demand by LLMs.

---

## 🚀 The Vision
Most AI applications today use AI to *write code* that a developer then deploys. **Real No Code** skips the middleman. It treats the LLM as a "just-in-time" rendering engine.

Instead of a fixed UI, the application "hallucinates" the necessary interface, logic, and styling in real-time based on user intent. This moves software from **Static Pixels** to **Fluid Probability.**

---

## 🛠 Current State: Proof of Concept
This is a **Research Preview**. The goal is to prove that a zero-middleware architecture is possible by rendering LLM output directly to every device via the browser.

### Two-Phase Goal:
* **Short Term (Local Prototyping):** A lightning-fast "Business Idea Generator." Describe a business tool (e.g., "I need a CRM for a pet shop with a local database"), and the UI/Logic is rendered instantly on your machine.
* **Long Term (The Internet Scale):** Solving the "Security-Creativity Paradox" to allow these generated interfaces to run safely in the open wild.

---

## 🛡 The "Security Moat" (Experimental)
We are fully aware that letting an LLM render raw HTML/JS is a security nightmare (XSS, Prompt Injection, etc.). **Real No Code is a laboratory to solve this.**

---

## 🏗 Architecture
* **Zero Backend:** No traditional server-side logic or middleware.
* **Pure HTML/JS:** No intermediate frameworks (React/Vue) required for the end-user.
* **Local-First:** Designed to run with local LLMs (Ollama/Llama.cpp) via WebGPU for total privacy.

---

## 🏃 Getting Started (Local Prototyping)
Since this is an on-demand application generator, it requires zero setup for traditional "apps."

1.  **Clone the repo:**
    ```bash
    git clone [https://github.com/your-username/real-no-code](https://github.com/your-username/real-no-code)
    ```
2.  **Connect your Local LLM:**
    Ensure your local inference server (e.g., Ollama) is running on `localhost:11434`.
3.  **Launch the Orchestrator:**
    Start the app (gradlew.bat bootRun) and open http://localhost:8080.
4.  **Prompt your Business Idea:**
    > *"Generate a real-time analytics dashboard for my local log files."*

---

## ⚖️ License
This project is licensed under the **GPL-3.0**.

---

## ⚠️ Disclaimer
*This project executes AI-generated code. Running this outside of a local, isolated environment is currently discouraged. Use at your own risk while we work on the safety primitives.*
