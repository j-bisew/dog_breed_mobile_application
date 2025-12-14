# Endpoints Documentation

This document lists the HTTP endpoints exposed by the Endpoints Server (backend/endpoints/endpointsServer.py).

All POST endpoints expect `Content-Type: application/json` unless otherwise noted. When an endpoint forwards requests to another internal service, the status code and response body from the downstream service are forwarded where applicable.

---

## POST /registerUser

- Description: Register a new user (forwards to the Auth service `/register`).
- Request Content-Type: `application/json`
- Request body shape:

```json
{
  "registrationData": {
    "name": "FullName", # One word, all alphanumeric
    "username": "username", # One word, all alphanumeric
    "email": "user@example.com",
    "password": "plaintext!" # Min 8 characters, at least one special character present (among !@#$%^&*()-+?_=,<>/ )
  }
}
```

- Response:
  - `201` — Registration successful (returns `Registration successful`), or other status forwarded from the Auth service.
- Auth: Not required.

---

## POST /loginUser

- Description: Authenticate a user (forwards to the Auth service `/login`). The endpoint returns the raw response from the Auth service (commonly a token or error).
- Request Content-Type: `application/json`
- Request body shape:

```json
{
  "loginData": {
    "username": "username",
    "password": "password"
  }
}
```

- Response:
  - `200` — Authentication successful; body is whatever the Auth service returns (often JSON containing a token).
  - Other statuses are forwarded from the Auth service.
- Auth: Not required.

---

## POST /getDogBreedInfo

- Description: Accepts a user-submitted photo (multipart/form-data, field name `photo`), sends the photo to the Recognition service to identify the breed, then queries the Database service for full breed information and returns that JSON.
- Request Content-Type: `multipart/form-data; boundary=...` (must include the `photo` form field)
- Example client usage (curl):

```bash
curl -X POST \
  -H "Authorization: Bearer <token>" \
  -F "photo=@/path/to/photo.jpg" \
  http://<host>:8000/getDogBreedInfo
```

- Internal flow / expected shapes:
  - Recognition request (JSON sent to recognition server):
    ```json
    { "photoData": "<binary encoded as latin1 string>" }
    ```
  - Recognition response (expected):
    ```json
    { "breedName": "Poodle", "assurance": 0.95 }
    ```
  - Database request (JSON sent to DB server):
    ```json
    { "raceRequestData": { "name": "poodle" } }
    ```
  - Database response: forwarded JSON describing the breed (structure depends on the Database service).
- Responses / errors:
  - `200` — JSON breed info from Database service.
  - `400` — Missing or unsupported `Content-Type`, or missing `photo` field.
  - `401` — Unauthorized (when Authorization header is missing or token invalid).
  - Other statuses forwarded from recognition or database services.
- Auth: Required. The server checks the `Authorization` header and verifies the token with the Auth service. Token may be provided as `Bearer <token>` or raw token string.

---

## POST /submitDogBreedFeedback

- Description: Submit feedback that correctness of recognition of particular breed was questioned by a user. This increments a counter in the Database service (`/incrementDogRaceQuestionedCount`).
- Request Content-Type: `application/json`
- Request body shape:

```json
{
  "raceNameData": {
    "raceName": "poodle" # Must be the same as provided with /getDogBreedInfo
  }
}
```

- Response:
  - `200` — `Feedback submitted successfully` (or forwarded Database response on success).
  - Other statuses forwarded from the Database service.
- Auth: Required. The endpoint checks `Authorization` header and validates the token with the Auth service.

---

## GET /

- Description: Root health/welcome endpoint returning a short HTML greeting.
- Response: `200` text/html `Welcome to the Endpoints Server!`
- Auth: Not required.

---

## Authorization details

- Endpoints requiring authorization: `/getDogBreedInfo`, `/submitDogBreedFeedback`.
- The server expects an `Authorization` header. The header value may be either `Bearer <token>` or `<token>`. The token is verified by the Auth server via its `/verifyToken` endpoint. If verification fails, the endpoint returns `401 Unauthorized`.

## Content-Type notes

- JSON payloads should use `Content-Type: application/json`.
- Photo uploads must use `multipart/form-data` with a `photo` field. The server expects binary data and encodes it as latin1 when forwarding to the Recognition service.

## Downstream services (from code)

- Auth service: 127.0.0.1:8010
- Database service: 127.0.0.1:8020
- Recognition service: 127.0.0.1:8030

If you want, I can add example curl requests for each endpoint, or update the doc with the exact DB response schema once the Database service schema is known.
