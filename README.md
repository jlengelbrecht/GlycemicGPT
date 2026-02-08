<p align="center">
  <img src="assets/logo.png" alt="GlycemicGPT Logo" width="200" height="200">
</p>

<h1 align="center">GlycemicGPT</h1>

<p align="center">
  <strong>AI-powered diabetes management platform - your on-call endo at home.</strong>
</p>

<p align="center">
  <a href="#overview">Overview</a> •
  <a href="#quick-start">Quick Start</a> •
  <a href="#architecture">Architecture</a> •
  <a href="#development">Development</a> •
  <a href="#disclaimer">Disclaimer</a>
</p>

---

> **IMPORTANT SAFETY WARNING**
>
> This software is **NOT** designed to replace your endocrinologist or healthcare provider. GlycemicGPT provides AI-generated suggestions only and should be used as a supplementary tool alongside professional medical care.

---

> **🚧 DEVELOPMENT STATUS: NOT FUNCTIONAL**
>
> This project is currently in **active development** and is **not yet functional**. What you see here is the foundational framework of the application - it does not connect to real CGM/pump data or provide actual AI analysis yet.
>
> **Do not attempt to deploy this for actual diabetes management.** We are building in public, but the software is not ready for any real-world use. Check back for updates as we progress toward a functional release.

---

## Overview

GlycemicGPT bridges the gap between diabetes device data (Dexcom G7 CGM, Tandem t:slim pump) and actionable AI-powered insights.

**Key Principles:**

- **Suggestions only** - never controls medical devices
- **BYOAI architecture** - users bring their own AI (Claude/OpenAI)
- **Self-hosted** - Docker/Kubernetes deployment
- **Safety-first** - pre-validation layer, emergency escalation

## Quick Start

```bash
# Clone the repository
git clone https://github.com/jlengelbrecht/GlycemicGPT.git
cd GlycemicGPT

# Copy environment file
cp .env.example .env

# Start all services
docker compose up
```

Services will be available at:

- **Web UI:** http://localhost:3000
- **API:** http://localhost:8000
- **API Docs:** http://localhost:8000/docs

## Architecture

| Component | Technology |
|-----------|------------|
| Frontend | Next.js 15, React 19, Tailwind CSS, shadcn/ui |
| Backend | FastAPI, Python 3.12 |
| Database | PostgreSQL 16, SQLAlchemy 2.0 |
| Cache | Redis 7 |

## Development

```bash
# Start development environment
./scripts/dev.sh

# Or with docker compose directly
docker compose up --build
```

## License

This project is licensed under the **GNU General Public License v3.0 (GPL-3.0)**. See the [LICENSE](LICENSE) file for details.

---

## Disclaimer

> **USE AT YOUR OWN RISK**

### This Software is Not Medical Advice

GlycemicGPT is experimental open-source software intended for educational and informational purposes only. It is **NOT** approved by the FDA or any regulatory body for medical use.

### AI Limitations

**AI can and will make mistakes.** Large language models (LLMs) are known to:

- **Hallucinate** - generate plausible-sounding but incorrect information
- **Misinterpret data** - draw incorrect conclusions from your glucose readings
- **Provide outdated information** - not reflect the latest medical guidelines
- **Lack context** - not understand your complete medical history

### Critical Warnings

1. **Do not replace professional medical care.** Always consult with your endocrinologist, diabetes educator, or healthcare provider before making any changes to your diabetes management.

2. **Verify all suggestions.** Any insulin dosing, carb ratio, or correction factor suggestions from AI must be verified with your healthcare team before use.

3. **This is not a medical device.** GlycemicGPT does not control any medical devices and provides suggestions only.

4. **Use extreme caution.** Incorrect diabetes management can result in severe hypoglycemia, diabetic ketoacidosis (DKA), or other life-threatening conditions.

### Limitation of Liability

THE AUTHORS AND CONTRIBUTORS OF THIS SOFTWARE ARE NOT LIABLE FOR ANY DAMAGES, INJURIES, OR ADVERSE HEALTH OUTCOMES RESULTING FROM THE USE OF THIS SOFTWARE. BY USING GLYCEMICGPT, YOU ACKNOWLEDGE THAT:

- You are using this software at your own risk
- You will not rely solely on AI-generated suggestions for medical decisions
- You understand that AI can make errors and hallucinate
- You will maintain regular care with qualified healthcare professionals
- You accept full responsibility for any decisions made based on this software's output

**If you experience a diabetes emergency, contact your healthcare provider or emergency services immediately. Do not rely on this software for emergency medical guidance.**

---

<p align="center">
  <sub>Built with care for the diabetes community. Stay safe. 💙</sub>
</p>
