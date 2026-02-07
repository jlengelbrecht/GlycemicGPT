<div align="center">
  <h1>📋 Changelog</h1>
  <p><strong>GlycemicGPT</strong> - Your AI-powered diabetes management companion</p>
  <p>
    <a href="https://github.com/jlengelbrecht/GlycemicGPT/releases">
      <img src="https://img.shields.io/github/v/release/jlengelbrecht/GlycemicGPT?style=flat-square&color=blue" alt="Latest Release">
    </a>
  </p>
</div>

> [!NOTE]
> All notable changes to this project will be documented in this file.
> This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [0.1.5](https://github.com/jlengelbrecht/GlycemicGPT/compare/v0.1.4...v0.1.5) (2026-02-07)


### 👷 CI/CD

* **docker:** add release notes update and image cleanup ([#18](https://github.com/jlengelbrecht/GlycemicGPT/issues/18)) ([6066aac](https://github.com/jlengelbrecht/GlycemicGPT/commit/6066aac70cdef1db2c3fb5273d0159c7528d69af))

## [0.1.4](https://github.com/jlengelbrecht/GlycemicGPT/compare/v0.1.3...v0.1.4) (2026-02-07)


### 👷 CI/CD

* **docker:** add container build and push workflow for ghcr.io ([#16](https://github.com/jlengelbrecht/GlycemicGPT/issues/16)) ([d7d2dee](https://github.com/jlengelbrecht/GlycemicGPT/commit/d7d2dee75fe0f3c7212fce2d8dca079aa41a0fdd))

## [0.1.3](https://github.com/jlengelbrecht/GlycemicGPT/compare/v0.1.2...v0.1.3) (2026-02-07)


### 👷 CI/CD

* **autolabeler:** enhance PR labeling with JSON config and template parsing ([#14](https://github.com/jlengelbrecht/GlycemicGPT/issues/14)) ([750b432](https://github.com/jlengelbrecht/GlycemicGPT/commit/750b4329d232ad33676d4d8c36ba183ecd421e63))

## [0.1.2](https://github.com/jlengelbrecht/GlycemicGPT/compare/v0.1.1...v0.1.2) (2026-02-07)


### 🐛 Bug Fixes

* **ci:** use homebot.0 for Renovate to enable dependency dashboard ([#10](https://github.com/jlengelbrecht/GlycemicGPT/issues/10)) ([c935eef](https://github.com/jlengelbrecht/GlycemicGPT/commit/c935eef7a9cd35ff1f965fb8263dd27e729ad3b8))

## [0.1.1](https://github.com/jlengelbrecht/GlycemicGPT/compare/v0.1.0...v0.1.1) (2026-02-07)


### ✨ Features

* add FastAPI backend, Next.js frontend, and K8s deployment ([f2ae4b3](https://github.com/jlengelbrecht/GlycemicGPT/commit/f2ae4b3fc4424915acc447fe1d03ddc9ced02d95))
* **api:** add Control-IQ activity parsing and aggregation ([1b7f86f](https://github.com/jlengelbrecht/GlycemicGPT/commit/1b7f86f36dbc5451ade93a81b8d8a556d57544df))
* **api:** add IoB projection engine with decay curve ([#1](https://github.com/jlengelbrecht/GlycemicGPT/issues/1)) ([51ae57d](https://github.com/jlengelbrecht/GlycemicGPT/commit/51ae57dc3b43f394bd2dce2e6ea5da09d6ad59e9))
* **web:** add dashboard layout and navigation ([#3](https://github.com/jlengelbrecht/GlycemicGPT/issues/3)) ([0f1bec3](https://github.com/jlengelbrecht/GlycemicGPT/commit/0f1bec301224026eb75597a4e3289b93ab614364))
* **web:** add data freshness indicator component ([ff58074](https://github.com/jlengelbrecht/GlycemicGPT/commit/ff58074ce264a1ada627be6469f039d36e10cbd2))


### 🐛 Bug Fixes

* **ci:** resolve workflow failures for renovate, release, and auto-label ([#4](https://github.com/jlengelbrecht/GlycemicGPT/issues/4)) ([018fd47](https://github.com/jlengelbrecht/GlycemicGPT/commit/018fd47fae1d283f4e050ce61f0500890788cac6))
* **ci:** skip self-approval for release PR auto-merge ([#8](https://github.com/jlengelbrecht/GlycemicGPT/issues/8)) ([af8bda1](https://github.com/jlengelbrecht/GlycemicGPT/commit/af8bda15e314afb34b302bcfd62107e00e24003b))
* **ci:** use homebot.0 for release PR and enable auto-merge ([#6](https://github.com/jlengelbrecht/GlycemicGPT/issues/6)) ([229e8a9](https://github.com/jlengelbrecht/GlycemicGPT/commit/229e8a9cb2a936b83889c2c6626246b432792ff1))
