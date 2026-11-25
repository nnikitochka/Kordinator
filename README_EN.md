> [Русский](README.md) | **English**

**Kordinator is an independent music backend for managing a personal music library.** It scans local music folders, indexes tracks and metadata, and provides a REST API for searching, streaming music with transcoding, and managing playlists.

With a complete local music library available, Kordinator runs autonomously without an internet connection or access to third-party resources. Connecting external sources is optional; when connected, they only enrich local data and do not replace it.

---

## 📌 Project status

The backend is under active development. The API and settings are changing frequently, and the project is not yet ready for production deployment.

---

## 🚧 Roadmap to the first release

- [ ] Complete the custom REST API for the music library, search, playback, and administration.
- [ ] Implement a pluggable mechanism for enriching local data from external sources.
- [ ] Move the remaining application code and test infrastructure to Kotlin; remove obsolete implementations and support for legacy protocols.
- [ ] Complete the transition to Kordinator's own data model and storage.
- [ ] Verify end-to-end flows: library scanning, search, streaming, transcoding, playlists, authentication, and access control.
- [ ] Prepare setup and deployment documentation, and verify the build and release packaging.
