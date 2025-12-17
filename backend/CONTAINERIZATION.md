Containerization overview

What I created:

- Dockerfiles in `backend/auth`, `backend/database`, `backend/endpoints`, `backend/recognition` that COPY only `*.py` and `*.txt` and install `requirements.txt` if present.
- `docker-compose.yml` at project root to build the four services, map ports, and assign static IPs on a custom network.
- `.dockerignore` to keep venv, caches and large model files out of images.

Key details:

- Service ports (host:container): endpoints 8000:8000, auth 8010:8010, database 8020:8020, recognition 8030:8030.
- Static IPs (in-compose): endpoints 172.20.0.2, auth 172.20.0.3, database 172.20.0.4, recognition 172.20.0.5.
- Volumes (compose): recognition mounts `./backend/recognition/models` and `./backend/recognition/dog_photos` and the `yolov8n.pt` file; database mounts `./backend/database/racesFolder` and `./backend/database/data` for persistence.

How to build and run locally:

1. From project root, build and start:

```bash
docker-compose up --build -d
```

2. To view logs:

```bash
docker-compose logs -f endpoints
```

3. To stop and remove:

```bash
docker-compose down
```

Notes and next steps:

- The Dockerfiles intentionally copy only Python and .txt files per your request. If additional non-Python files are required at build time (for building wheels, etc.), you'll need to mount them at runtime or allow copying those files into the image.
- I did not run builds here; please run `docker-compose up --build` locally and report any missing runtime mounts or package issues and I will adjust.
