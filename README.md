# Hackathon-Ticketing-System
Build a small, working Kanban-style ticket tracker as a three-tier SPA backed by an RDBMS.

> **Developers & AI agents:** start with [`AGENTS.md`](AGENTS.md) — it points to the spec,
> analysis, architecture, and the ticket backlog, and explains the spec-driven workflow.

## Requirements

The original requirements were provided as a Word document (`Hackathon_Ticketing_System_Requirements_v3.docx`). To make them easier to work with using an AI agentic approach (e.g. Claude Code), the document was converted to Markdown with [pandoc](https://pandoc.org/):

```bash
pandoc Hackathon_Ticketing_System_Requirements_v3.docx -t markdown --extract-media=./media -o requirements.md
```

This produces:

- `docs/requirements.md` — the requirements in Markdown, which provides better support for AI agents.
- `docs/media/media/` — the images extracted from the document.

A spec-driven analysis of these requirements — with functional/non-functional requirements, architecture, and explicitly flagged ambiguities, assumptions, and open questions — lives in [`docs/requirements-analysis.md`](docs/requirements-analysis.md).

## Tech Stack & Architecture Decisions

A three-tier application in a single monorepo, with the frontend and backend as separate containers orchestrated by a root `docker-compose.yml`:

| Tier | Decision |
|------|----------|
| **Backend** | Spring Boot (Java) — Spring Security (Argon2id password hashing, endpoint auth), Spring Data JPA + Flyway/Liquibase migrations, Spring Mail for SMTP, Testcontainers for integration tests. |
| **Frontend** | TypeScript + React SPA (e.g. Vite), with a mature drag-and-drop library (e.g. dnd-kit) for the Kanban board. |
| **Database** | PostgreSQL, in its own container. |
| **Email (dev/test)** | Dockerized [Mailpit](https://github.com/axllent/mailpit) captures verification emails locally; the real `relay1.dataart.com` relay is selectable via SMTP environment variables (config-only switch, no code change). |
| **Packaging** | `docker compose up --build` from the repo root brings up database, Mailpit, backend, and frontend. No host-installed runtimes required beyond Docker. |

Planned repository layout:

```
/ (repo root)
├── docker-compose.yml        # orchestrates db, mailpit, backend, frontend
├── .env.example              # documented secrets/config keys
├── backend/                  # Spring Boot app + its Dockerfile
├── frontend/                 # React + TypeScript SPA + its Dockerfile
├── docs/
└── README.md
```

See [`docs/requirements-analysis.md`](docs/requirements-analysis.md) §4.3 for the full decision rationale.

Verify phase:
```bash
Verify phase. Summarize every section you found in requirements.md, list how many requirements/user stories are implied, and list every image file you can see in ./media with a one-line description  
  of each. Do not implement anything yet. 
```
Analyse phase:
```bash
Produce docs/requirements-analysis.md containing: Summary, functional requirements, non-functional requirements, architecture, ambiguities, assumptions, questions. Idea is to make sure if something  
is unclear we explicitly mark it instead of guessing. Follow spec driven development.
```
Clarify SMTP and tech stack discussion:
```bash
Regarding SMTP and relay1.dataart.com, please add clarification: Ddockerized Mailpit can be used for testing instead of real integration. Update analysis md accordingly. Regarding stack/language me  
  as Spring Boot developer prefer java/kotlin so let's think together about this:  I think Spring Boot on Java as BE should be good and Kotlin Multiplatform to support FE requirements listed, what do  
  you think? And BE and FE will be separate containers although code be within this same single repository, may sit simply in a separate folders if it make sense and good practice in general? 
```
Frontend choice:
```bash
TypeScript + React SPA, Spring Boot backend, separate containers in a monorepo. Let me record these decisions in both files
```
Ambiguities:
```bash
Regarding ambiguities. Let's compare session-cookie vs bearer-token (me as BE mostly used to bearer-token but I believe it's stateless?) for this particular project. Same question for you for uuid   
vs numeric ids. I reviewed AMB-1,2,3,4,5,6,8,9,10  and confirm proposed column can be used as confirmed requirement, update analysis md again.  Regarding AMB-7 need a recommendation and related to   
session vs bearer topic is it? 
```
Plan:
```bash
Let's plan. I was thinking to create docs/architecture.md to make sure to include folder structure etc and then use this information in the ticket(s). Let's create epics stories and implementation   
tickets (structure best how you like). Make sure to include everything in the actual implementation md filles (titled accordingly for understanding similiar to task in jira and even numbered for     
visibility and tracking)  to actually implement, test and run specific ticket. Each implementation ticket must be separate for BE and separate implementation for FE for a single story. For example,  
BE must have implementation, at least unit/mockito test converage for positive, nagative and boundary at least for each story/implementation if integration test is done then containers are used to   
make sure it can run on any env by any developer locally. I'm not FE expert but some tests need to be done as well if it's possible. Browser tests may be out of scope as too long to implement by AI?
Let me know any questions and answers to above as well from your point of view.                                                                                                                        
Do not code yet, only plan at the moment.       
```
Change first task:
```bash
Let's also not forget about modifying gitignore file. We need to make sure IntelliJ files are not pushed to the repo. 
If it's suitable for the HTS-001 task (first one) please add gitignore requirement.
Also what do we need to make sure that tickets can be done if we pass this project to other developers and/or AI tools? For example, can we create AGENTS.MD file for example? To mention folder structure and specific  critical md files to start the implementation if it make sense? Also, can we mention in AGENTS file that if requireements changes please modify such and such file.
```
